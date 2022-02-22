package io.quarkiverse.helm.tests.kubernetes;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class KubernetesIT {

    private static final String CHART_NAME = "quarkus-helm-integration-tests-kubernetes";

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        assertNotNull(getResourceAsStream("Chart.yaml"));
        assertNotNull(getResourceAsStream("values.yaml"));
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
        assertNotNull(getResourceAsStream("templates/NOTES.txt"));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm").resolve(CHART_NAME).resolve(file).toFile());
    }
}
