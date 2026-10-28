package io.quarkiverse.helm.tests.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;

public class KubernetesWithInitTaskIT {

    private static final String CHART_NAME = "quarkus-helm-integration-tests-kubernetes-with-init-task";

    @Test
    public void shouldInitTaskJobBeAnnotatedAsHelmHook() throws IOException {
        Map<String, Object> job = Serialization.yamlMapper()
                .readValue(getResourceAsStream("templates/job.yaml"), Map.class);
        assertNotNull(job, "Job is null!");
        assertEquals("Job", job.get("kind"));

        Map<String, Object> metadata = (Map<String, Object>) job.get("metadata");
        assertEquals(CHART_NAME + "-flyway-init", metadata.get("name"));
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertNotNull(annotations, "Job annotations are null!");
        assertEquals("post-install,post-upgrade", annotations.get("helm.sh/hook"));
        assertEquals("before-hook-creation", annotations.get("helm.sh/hook-delete-policy"));
    }

    @Test
    public void shouldDeploymentNotBeAnnotatedAsHelmHook() throws IOException {
        // the deployment template contains Helm expressions, so it's not a valid YAML file
        String deployment = Files.readString(getChartFile("templates/deployment.yaml"));
        assertFalse(deployment.contains("helm.sh/hook"), "Deployment should not be annotated as Helm hook!");
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(getChartFile(file).toFile());
    }

    private final Path getChartFile(String file) {
        return Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file);
    }
}
