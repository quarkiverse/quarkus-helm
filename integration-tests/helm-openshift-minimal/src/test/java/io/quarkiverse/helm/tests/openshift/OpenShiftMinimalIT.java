package io.quarkiverse.helm.tests.openshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;

public class OpenShiftMinimalIT {

    private static final String CHART_NAME = "quarkus-helm-integration-tests-openshift-minimal";
    private static final String ROOT_CONFIG_NAME = "app";

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        Map chart = Serialization.yamlMapper()
                .readValue(getResourceAsStream("Chart.yaml"), Map.class);
        assertNotNull(chart, "Chart is null!");
        assertEquals(CHART_NAME, chart.get("name"));
        // Values.yaml manifest
        assertNotNull(getResourceAsStream("values.yaml"));
        // templates
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
        assertNotNull(getResourceAsStream("templates/imagestream.yaml"));
        // notes
        assertNotNull(getResourceAsStream("templates/NOTES.txt"));
    }

    @Test
    public void valuesShouldContainExpectedData() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values.yaml"), Map.class);
        assertNotNull(values, "Values is null!");

        assertNotNull(values.containsKey(ROOT_CONFIG_NAME), "Does not contain `" + ROOT_CONFIG_NAME + "`");
        assertNotNull(values.get(ROOT_CONFIG_NAME) instanceof Map, "Value `" + ROOT_CONFIG_NAME + "` is not a map!");
        Map<String, Object> helmExampleValues = (Map<String, Object>) values.get(ROOT_CONFIG_NAME);

        // Should contain image
        assertNotNull(helmExampleValues.get("image"));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "openshift").resolve(CHART_NAME).resolve(file).toFile());
    }
}
