package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.quarkiverse.helm.deployment.utils.HelmTarArchiver;
import land.oras.ContainerRef;
import land.oras.Manifest;
import land.oras.Registry;
import land.oras.utils.Const;

@Testcontainers(disabledWithoutDocker = true)
class OciHelmChartPusherTest {

    @Container
    static final GenericContainer<?> REGISTRY = new GenericContainer<>("registry:2").withExposedPorts(5000);

    @TempDir
    Path tempDir;

    @Test
    void pushesChartAsHelmOciArtifact() throws Exception {
        File tarball = createChartTarball(tempDir, "myapp", "1.0.0+build.1");

        HelmChartUploader.pushToHelmRepository(tarball,
                HelmChartUploaderTest.repository(HelmRepositoryType.OCI, "oci://" + registryAddress() + "/charts/", null,
                        null));

        // `+` is not allowed in OCI tags, so it's replaced with `_` as `helm push` does
        ContainerRef ref = ContainerRef.parse(registryAddress() + "/charts/myapp:1.0.0_build.1");
        Registry registry = Registry.builder().defaults().withInsecure(true).build();
        Manifest manifest = registry.getManifest(ref);

        assertEquals(Const.HELM_CONFIG_MEDIA_TYPE, manifest.getConfig().getMediaType());
        assertEquals(1, manifest.getLayers().size());
        assertEquals(Const.HELM_CONTENT_MEDIA_TYPE, manifest.getLayers().get(0).getMediaType());
        assertEquals("myapp", manifest.getAnnotations().get(Const.ANNOTATION_TITLE));
        assertEquals("1.0.0+build.1", manifest.getAnnotations().get(Const.ANNOTATION_IMAGE_VERSION));
        assertEquals("My application", manifest.getAnnotations().get(Const.ANNOTATION_DESCRIPTION));

        String config = new String(registry.getBlob(ref.withDigest(manifest.getConfig().getDigest())),
                StandardCharsets.UTF_8);
        assertTrue(config.contains("\"name\":\"myapp\""), config);
        assertTrue(config.contains("\"version\":\"1.0.0+build.1\""), config);
    }

    private String registryAddress() {
        return REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(5000);
    }

    static File createChartTarball(Path tempDir, String name, String version) throws Exception {
        Path chartDir = tempDir.resolve("chart").resolve(name);
        Files.createDirectories(chartDir.resolve("templates"));
        Files.writeString(chartDir.resolve("Chart.yaml"), """
                apiVersion: v2
                name: %s
                version: %s
                description: My application
                """.formatted(name, version));
        Files.writeString(chartDir.resolve("values.yaml"), "replicas: 1\n");
        Files.writeString(chartDir.resolve("templates").resolve("configmap.yaml"), """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: {{ .Chart.Name }}
                """);

        List<File> files;
        try (Stream<Path> paths = Files.walk(chartDir)) {
            files = paths.map(Path::toFile).collect(Collectors.toList());
        }
        File tarball = tempDir.resolve(name + "-" + version + ".tar.gz").toFile();
        return HelmTarArchiver.createTarBall(tarball, chartDir.getParent().toFile(), files, "tar.gz", null);
    }
}
