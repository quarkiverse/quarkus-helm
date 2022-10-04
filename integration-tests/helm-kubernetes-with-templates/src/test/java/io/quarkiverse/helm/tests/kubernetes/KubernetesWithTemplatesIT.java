package io.quarkiverse.helm.tests.kubernetes;

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
import io.dekorate.utils.Strings;

public class KubernetesWithTemplatesIT {

    private static final String CHART_NAME = "my-chart-with-templates";

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        Map chart = Serialization.yamlMapper()
                .readValue(getResourceAsStream("Chart.yaml"), Map.class);
        assertNotNull(chart, "Chart is null!");
        assertEquals(CHART_NAME, chart.get("name"));
        // templates
        assertNotNull(getResourceAsStream("templates/service.yaml"));
        assertNotNull(getResourceAsStream("templates/_helpers.tpl"));
        assertEquals(Strings.read(KubernetesWithTemplatesIT.class.getResourceAsStream("/expected-configmap.yaml")),
                Strings.read(getResourceAsStream("templates/configmap.yaml")));
    }

    private final InputStream getResourceAsStream(String file) throws FileNotFoundException {
        return new FileInputStream(Paths.get("target", "helm", "kubernetes").resolve(CHART_NAME).resolve(file).toFile());
    }
}
