package io.quarkiverse.helm.tests.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class KubernetesWithTemplatesIT {

    private static final String CHART_NAME = "my-chart-with-templates";
    private static final String FAVORITE = "favorite";
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
        // templates
        assertNotNull(getResourceAsStream("templates/service.yaml"));
        assertNotNull(getResourceAsStream("templates/_helpers.tpl"));
        assertEquals(readString(KubernetesWithTemplatesIT.class.getResourceAsStream("/expected-configmap.yaml")),
                readString(getResourceAsStream("templates/configmap.yaml")));
        assertEquals(readString(KubernetesWithTemplatesIT.class.getResourceAsStream("/expected-ingress.yaml")),
                readString(getResourceAsStream("templates/ingress.yaml")));
    }

    @Test
    public void valuesShouldContainExpectedData() throws IOException {
        Map<String, Object> values = mapper.readValue(getResourceAsStream("values.yaml"), Map.class);
        assertNotNull(values, "Values is null!");

        Map<String, Object> app = (Map<String, Object>) values.get("app");

        assertTrue(app.containsKey(FAVORITE), "Does not contain `" + FAVORITE + "`");
        assertTrue(app.get(FAVORITE) instanceof Map, "Value `" + FAVORITE + "` is not a map!");
        Map<String, String> favoriteValues = (Map<String, String>) app.get(FAVORITE);

        // Should contain car
        assertEquals("Ford", favoriteValues.get("car"));
        assertEquals("Apple", favoriteValues.get("fruit"));
    }

    private InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file).toFile());
    }

    private static String readString(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
}
