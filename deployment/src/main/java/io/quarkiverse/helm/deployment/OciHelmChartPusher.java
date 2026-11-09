package io.quarkiverse.helm.deployment;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dekorate.utils.Serialization;
import io.quarkiverse.helm.model.Chart;
import land.oras.Annotations;
import land.oras.ArtifactType;
import land.oras.Config;
import land.oras.ContainerRef;
import land.oras.LocalPath;
import land.oras.OCI;
import land.oras.Registry;
import land.oras.exception.OrasException;
import land.oras.utils.Const;

/**
 * Pushes the chart to an OCI registry the same way `helm push <tarball> oci://<url>` does: the chart is stored at
 * `<url>/<chart name>:<chart version>`, with the chart metadata as config and the tarball as the only layer.
 */
final class OciHelmChartPusher implements HelmChartPusher {

    static final String OCI_SCHEME = "oci://";

    private static final Logger LOGGER = Logger.getLogger(OciHelmChartPusher.class);

    private static final String CHART_YAML = "Chart.yaml";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void push(File tarball, HelmRepository repository) throws IOException {
        if (!tarball.getName().endsWith(".tgz") && !tarball.getName().endsWith(".tar.gz")) {
            throw new RuntimeException("Pushing to an OCI registry requires a gzip-compressed Helm chart, but got '"
                    + tarball.getName() + "'. Set the property `quarkus.helm.extension` to `tgz` or `tar.gz`.");
        }

        Chart chart = readChart(tarball);
        ContainerRef ref = ContainerRef.parse(reference(repository.url().get(), chart));

        Map<String, String> annotations = new HashMap<>();
        annotations.put(Const.ANNOTATION_TITLE, chart.getName());
        annotations.put(Const.ANNOTATION_IMAGE_VERSION, chart.getVersion());
        if (isNotEmpty(chart.getDescription())) {
            annotations.put(Const.ANNOTATION_DESCRIPTION, chart.getDescription());
        }

        // oras-java doesn't shut down its default executor, which would leave non-daemon threads behind after the build.
        // TODO: revisit when https://github.com/oras-project/oras-java/issues/963 is fixed in a new oras-java release.
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            registry(repository, executor).pushArtifact(ref,
                    ArtifactType.from(Const.HELM_CONFIG_MEDIA_TYPE),
                    Annotations.ofManifest(annotations),
                    config(chart),
                    OCI.PushOptions.defaults(),
                    LocalPath.of(tarball.toPath(), Const.HELM_CONTENT_MEDIA_TYPE));
        } catch (OrasException e) {
            throw new RuntimeException("Couldn't push the Helm chart to the OCI registry: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
        LOGGER.info("Helm chart was successfully pushed to the OCI registry as '" + ref + "'.");
    }

    /**
     * Same reference `helm push` uses. Helm replaces `+` in the version with `_` because `+` isn't allowed in OCI tags.
     */
    static String reference(String url, Chart chart) {
        String repository = url.substring(OCI_SCHEME.length());
        if (repository.endsWith("/")) {
            repository = repository.substring(0, repository.length() - 1);
        }
        return repository + "/" + chart.getName() + ":" + chart.getVersion().replace('+', '_');
    }

    private static Registry registry(HelmRepository repository, ExecutorService executor) {
        Registry.Builder builder = Registry.builder();
        if (isNotEmpty(repository.getUsername()) && isNotEmpty(repository.getPassword())) {
            builder = builder.defaults(repository.getUsername(), repository.getPassword());
        } else {
            // credentials from the environment, e.g. `~/.docker/config.json`
            builder = builder.defaults();
        }
        return builder.withInsecure(repository.insecure()).withExecutorService(executor).build();
    }

    private static Config config(Chart chart) throws IOException {
        byte[] data = JSON.writeValueAsBytes(chart);
        String descriptor = """
                {"mediaType":"%s","digest":"sha256:%s","size":%d,"data":"%s"}"""
                .formatted(Const.HELM_CONFIG_MEDIA_TYPE, sha256(data), data.length,
                        Base64.getEncoder().encodeToString(data));
        return Config.fromJson(descriptor);
    }

    private static Chart readChart(File tarball) throws IOException {
        try (InputStream file = new BufferedInputStream(Files.newInputStream(tarball.toPath()));
                TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(file))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String[] path = entry.getName().split("/");
                if (path.length == 2 && CHART_YAML.equals(path[1])) {
                    return Serialization.unmarshal(new String(tar.readAllBytes(), StandardCharsets.UTF_8), Chart.class);
                }
            }
        }
        throw new RuntimeException("Couldn't find the '" + CHART_YAML + "' file in the Helm chart '" + tarball.getName()
                + "'.");
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
