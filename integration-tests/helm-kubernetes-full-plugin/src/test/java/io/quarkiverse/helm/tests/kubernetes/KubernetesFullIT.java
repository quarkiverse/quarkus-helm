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
        assertNotNull(getResourceAsStream("values-dev.yaml"));
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
        // TODO: Uncomment it after https://github.com/quarkusio/quarkus/pull/33979 is merged and we use the correct Quarkus
        //  version.
        //assertTrue(Strings.read(getResourceAsStream("templates/deployment.yaml"))
        //        .contains(Strings.read(KubernetesFullIT.class.getResourceAsStream("/expected-livenessProbe.yaml"))));*/
        assertNotNull(getResourceAsStream("templates/NOTES.txt"));
        assertNotNull(getResourceAsStream("crds/crontabs.stable.example.com.yaml"));
    }

    @Test
    public void chartsShouldContainExpectedData() throws IOException {
        Map<String, Object> chart = Serialization.yamlMapper()
                .readValue(getResourceAsStream("Chart.yaml"), Map.class);
        assertNotNull(chart, "Chart.yaml is null!");

        assertNotNull(chart.containsKey("annotations"), "Does not contain `annotations` from the user Charts.yml!");
        assertEquals(CHART_NAME, chart.get("name"), "The name was not replaced with the generated value!");
    }

    @Test
    public void valuesShouldContainExpectedData() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values.yaml"), Map.class);
        assertNotNull(values, "Values is null!");

        assertNotNull(values.containsKey(ROOT_CONFIG_NAME), "Does not contain `" + ROOT_CONFIG_NAME + "`");
        assertNotNull(values.get(ROOT_CONFIG_NAME) instanceof Map, "Value `" + ROOT_CONFIG_NAME + "` is not a map!");

        // Rootless properties
        assertEquals("rootless-property", values.get("prop"));

        // App
        Map<String, Object> app = (Map<String, Object>) values.get(ROOT_CONFIG_NAME);
        // Should contain image
        assertEquals("registry.com/name:version", app.get("image"));
        // Should contain replicas
        assertEquals(3, app.get("replicas"));
        // Should NOT contain not-found: as this property is ignored
        assertNull(app.get("notFound"));
        // Should contain number
        assertEquals(12, app.get("typesNumber"));
        // Should contain boolean
        assertEquals(true, app.get("typesBool"));
        // Should contain overridden value
        assertEquals("override-host-in-helm", app.get("host"));
        // Should contain foo
        assertEquals("bar", app.get("foo"));
        // Should add properties set as conditions in dependencies
        assertEquals(true, app.get("dependencyBeEnabled"));
        // Should contain "my-prop" because validation is disabled
        assertEquals("test", app.get("my-prop"));

        Map<String, Object> dependencyA = (Map<String, Object>) values.get("depA");
        // Should have properties from custom values.yaml file
        assertEquals("hi!", dependencyA.get("prop"));
    }

    @Test
    public void valuesShouldContainExpectedDataInDevProfile() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values-dev.yaml"),
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
        assertNull(helmExampleValues.get("notFound"));
        // Should contain foo with the value from properties
        assertEquals("Only for DEV!", helmExampleValues.get("foo"));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm").resolve(CHART_NAME).resolve(file).toFile());
    }
}
