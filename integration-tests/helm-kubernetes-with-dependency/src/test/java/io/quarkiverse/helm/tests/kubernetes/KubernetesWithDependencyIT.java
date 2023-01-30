package io.quarkiverse.helm.tests.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;

public class KubernetesWithDependencyIT {

    private static final String CHART_NAME = "quarkus-helm-integration-tests-kubernetes-with-dependency";
    private static final String ROOT_CONFIG_NAME = "app";

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        assertTrue(Stream.of(Paths.get("target", "helm", "kubernetes").toFile().listFiles())
                .anyMatch(f -> f.getName().startsWith(CHART_NAME) && f.getName().endsWith("-helm.tar.gz")));

        assertNotNull(getResourceAsStream("charts/postgresql-11.6.22.tgz"));
    }

    @Test
    public void valuesFileShouldContainDependencyValues() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("values.yaml"), Map.class);
        assertNotNull(values.containsKey(ROOT_CONFIG_NAME), "Does not contain `" + ROOT_CONFIG_NAME + "`");
        Map<String, Object> app = (Map<String, Object>) values.get("app");
        assertEquals("NodePort", app.get("serviceType"));
        Map<String, Object> dependencyValues = (Map<String, Object>) values.get("postgresql");
        Map<String, Object> global = (Map<String, Object>) dependencyValues.get("global");
        Map<String, Object> postgresql = (Map<String, Object>) global.get("postgresql");
        Map<String, Object> auth = (Map<String, Object>) postgresql.get("auth");
        assertEquals("my_db_name", auth.get("database"));
        assertEquals("secret", auth.get("postgresPassword"));
        assertEquals("value", auth.get("key"));
    }

    @Test
    public void chartFileShouldContainExpectedData() throws IOException {
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(getResourceAsStream("Chart.yaml"), Map.class);
        List<Object> dependencies = (List<Object>) values.get("dependencies");
        Map<String, Object> postgresql = (Map<String, Object>) dependencies.get(0);
        assertEquals("postgresql", postgresql.get("name"));
        assertEquals("11.6.22", postgresql.get("version"));
        assertEquals("postgresql", postgresql.get("alias"));
        assertFalse(postgresql.containsKey("enabled"));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file).toFile());
    }
}
