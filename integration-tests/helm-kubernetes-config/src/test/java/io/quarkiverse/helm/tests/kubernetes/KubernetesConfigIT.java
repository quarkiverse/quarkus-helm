package io.quarkiverse.helm.tests.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class KubernetesConfigIT {

    private static final String CHART_NAME = "quarkus-helm-it-kubernetes-config";
    private static final String ROOT_CONFIG_NAME = "app";
    private static ObjectMapper mapper;

    @BeforeAll
    public static void init() {
        mapper = new ObjectMapper(new YAMLFactory());
    }

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        Map chart = mapper.readValue(getResourceAsStream("Chart.yaml"), Map.class);
        assertNotNull(chart, "Chart is null!");
        assertEquals(CHART_NAME, chart.get("name"));
        // Values.yaml manifest
        assertNotNull(getResourceAsStream("values.yaml"));
        // templates
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
        assertNotNull(getResourceAsStream("templates/configmap.yaml"));
        // notes
        assertNotNull(getResourceAsStream("templates/NOTES.txt"));
    }

    private InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file).toFile());
    }
}
