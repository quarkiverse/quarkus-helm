package io.quarkiverse.helm.tests.knative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;

public class KnativeIT {

    private static final String CHART_NAME = "quarkus-helm-integration-tests-knative-minimal";
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
        assertNotNull(getResourceAsStream("templates/service.yaml"));
        // notes
        assertNotNull(getResourceAsStream("templates/NOTES.txt"));

        // And, also to generate the OpenShift helm chart (this is necessary to reproduce some issues in Knative generation):
        assertNotNull(getResourceAsStream("openshift", "Chart.yaml"));

    }

    @Test
    public void valuesShouldContainExpectedData() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values.yaml"), Map.class);
        assertNotNull(values, "Values is null!");

        assertNotNull(values.containsKey(ROOT_CONFIG_NAME), "Does not contain `" + ROOT_CONFIG_NAME + "`");
        assertNotNull(values.get(ROOT_CONFIG_NAME) instanceof Map, "Value `" + ROOT_CONFIG_NAME + "` is not a map!");
        Map<String, Object> app = (Map<String, Object>) values.get(ROOT_CONFIG_NAME);

        // Should NOT contain the port
        assertNull(getHttpPortFor(app, "livenessProbe"));
        assertNull(getHttpPortFor(app, "readinessProbe"));
    }

    private Object getHttpPortFor(Map<String, Object> values, String probeName) {
        Map<String, Object> probe = (Map<String, Object>) values.get(probeName);
        Map<String, Object> httpGet = (Map<String, Object>) probe.get("httpGet");
        return httpGet.get("port");
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return getResourceAsStream("knative", file);
    }

    private final InputStream getResourceAsStream(String target, String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", target).resolve(CHART_NAME).resolve(file).toFile());
    }
}
