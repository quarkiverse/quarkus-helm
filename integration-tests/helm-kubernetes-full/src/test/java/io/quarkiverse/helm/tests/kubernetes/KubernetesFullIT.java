package io.quarkiverse.helm.tests.kubernetes;

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

public class KubernetesFullIT {

    private static final String CHART_NAME = "my-chart";
    private static final String ROOT_CONFIG_NAME = "app";

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        assertNotNull(getResourceAsStream("Chart.yaml"));
        assertNotNull(getResourceAsStream("values.yaml"));
        assertNotNull(getResourceAsStream("LICENSE"));
        assertNotNull(getResourceAsStream("README.md"));
        assertNotNull(getResourceAsStream("requirements.yml"));
        assertNotNull(getResourceAsStream("values.schema.json"));
        assertNotNull(getResourceAsStream("values.dev.yaml"));
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
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
        // Should contain replicas
        assertEquals(3, helmExampleValues.get("replicas"));
        // Should NOT contain not-found: as this property is ignored
        assertNull(helmExampleValues.get("not-found"));
        // Should contain number
        assertEquals(12, helmExampleValues.get("typesNumber"));
        // Should contain boolean
        assertEquals(true, helmExampleValues.get("typesBool"));
    }

    @Test
    public void valuesShouldContainExpectedDataInDevProfile() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values.dev.yaml"),
                        Map.class);
        assertNotNull(values, "Values is null!");

        assertNotNull(values.containsKey(ROOT_CONFIG_NAME), "Does not contain `" + ROOT_CONFIG_NAME + "`");
        assertNotNull(values.get(ROOT_CONFIG_NAME) instanceof Map, "Value `" + ROOT_CONFIG_NAME + "` is not a map!");
        Map<String, Object> helmExampleValues = (Map<String, Object>) values.get(ROOT_CONFIG_NAME);

        // Should contain image
        assertNotNull(helmExampleValues.get("image"));
        // Should contain replicas
        assertEquals(3, helmExampleValues.get("replicas"));
        // Should NOT contain not-found: as this property is ignored
        assertNull(helmExampleValues.get("not-found"));
        // Should contain vcs-url with the value from properties
        assertEquals("Only for DEV!", helmExampleValues.get("commitId"));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file).toFile());
    }
}
