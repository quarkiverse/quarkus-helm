package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.helm.deployment.utils.HelmTarArchiver;
import land.oras.Config;
import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.Manifest;
import land.oras.Registry;

/**
 * PoC for quarkiverse/quarkus-helm#550: push a Helm chart tarball to an OCI registry using oras-java.
 *
 * Run with a local registry:
 *
 * <pre>
 * docker run -d -p 5000:5000 registry:2
 * mvn -pl deployment test -Dtest=OciPushPocTest -Dhelm.oci.poc.registry=localhost:5000/charts
 * helm pull oci://localhost:5000/charts/poc-chart --version 0.1.0 --plain-http
 * </pre>
 */
@EnabledIfSystemProperty(named = "helm.oci.poc.registry", matches = ".+")
class OciPushPocTest {

    private static final String HELM_CONFIG_MEDIA_TYPE = "application/vnd.cncf.helm.config.v1+json";
    private static final String HELM_CHART_CONTENT_MEDIA_TYPE = "application/vnd.cncf.helm.chart.content.v1.tar+gzip";

    private static final String CHART_NAME = "poc-chart";
    private static final String CHART_VERSION = "0.1.0";

    @TempDir
    Path tempDir;

    @Test
    void pushChartToOciRegistry() throws Exception {
        File tarball = createChartTarball();
        String repository = System.getProperty("helm.oci.poc.registry");
        boolean insecure = Boolean.parseBoolean(System.getProperty("helm.oci.poc.insecure", "true"));

        // Equivalent of `helm push <tarball> oci://<repository>`: the chart name becomes the repository
        // and the chart version becomes the tag
        ContainerRef ref = ContainerRef.parse(repository + "/" + CHART_NAME + ":" + CHART_VERSION);
        Registry registry = Registry.builder()
                .defaults()
                .withInsecure(insecure)
                .build();

        // Config: Chart.yaml metadata as JSON
        String chartJson = """
                {"apiVersion":"v2","name":"%s","version":"%s","description":"PoC chart pushed with oras-java"}"""
                .formatted(CHART_NAME, CHART_VERSION);
        Layer configBlob = registry.pushBlob(ref, chartJson.getBytes(StandardCharsets.UTF_8));
        Config config = Config.fromBlob(HELM_CONFIG_MEDIA_TYPE, configBlob);

        // Single layer: the chart tarball
        Layer chartLayer = registry.pushBlob(ref, tarball.toPath(), Map.of())
                .withMediaType(HELM_CHART_CONTENT_MEDIA_TYPE);

        Manifest manifest = Manifest.empty()
                .withConfig(config)
                .withLayers(List.of(chartLayer))
                .withAnnotations(Map.of(
                        "org.opencontainers.image.title", CHART_NAME,
                        "org.opencontainers.image.version", CHART_VERSION,
                        "org.opencontainers.image.created", Instant.now().toString()));
        registry.pushManifest(ref, manifest);

        Manifest pushed = registry.getManifest(ref);
        assertEquals(HELM_CONFIG_MEDIA_TYPE, pushed.getConfig().getMediaType());
        assertEquals(HELM_CHART_CONTENT_MEDIA_TYPE, pushed.getLayers().get(0).getMediaType());
        assertEquals(chartLayer.getDigest(), pushed.getLayers().get(0).getDigest());
    }

    private File createChartTarball() throws Exception {
        Path chartDir = tempDir.resolve("chart").resolve(CHART_NAME);
        Files.createDirectories(chartDir.resolve("templates"));
        Files.writeString(chartDir.resolve("Chart.yaml"), """
                apiVersion: v2
                name: %s
                version: %s
                description: PoC chart pushed with oras-java
                """.formatted(CHART_NAME, CHART_VERSION));
        Files.writeString(chartDir.resolve("values.yaml"), "replicas: 1\n");
        Files.writeString(chartDir.resolve("templates").resolve("configmap.yaml"), """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: {{ .Chart.Name }}
                data:
                  replicas: "{{ .Values.replicas }}"
                """);

        List<File> files;
        try (Stream<Path> paths = Files.walk(chartDir)) {
            files = paths.map(Path::toFile).collect(Collectors.toList());
        }
        File tarball = tempDir.resolve(CHART_NAME + "-" + CHART_VERSION + ".tgz").toFile();
        return HelmTarArchiver.createTarBall(tarball, chartDir.getParent().toFile(), files, "tgz", null);
    }
}
