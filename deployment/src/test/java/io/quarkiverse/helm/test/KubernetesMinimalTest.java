package io.quarkiverse.helm.test;

import static io.dekorate.helm.config.HelmBuildConfigGenerator.HELM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.dekorate.helm.model.Chart;
import io.dekorate.utils.Serialization;
import io.quarkiverse.helm.test.assets.Endpoint;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.builder.Version;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class KubernetesMinimalTest {

    private static final String CHART_NAME = "myChart";
    private static final String ROOT_CONFIG_NAME = "unset";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setForcedDependencies(
                    Collections.singletonList(
                            new AppArtifact("io.quarkus", "quarkus-kubernetes", Version.getVersion())))
            .withApplicationRoot((jar) -> jar.addClasses(Endpoint.class))
            .withConfigurationResource("application-k8s-minimal.properties");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldHelmManifestsBeGenerated() throws IOException {
        Chart chart = Serialization.yamlMapper()
                .readValue(getResourceAsStream("Chart.yaml"), Chart.class);
        assertNotNull(chart, "Chart is null!");
        assertEquals(CHART_NAME, chart.getName());
        // Values.yaml manifest
        assertNotNull(getResourceAsStream("values.yaml"));
        // templates
        assertNotNull(getResourceAsStream("templates/deployment.yaml"));
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
        return new FileInputStream(prodModeTestResults.getBuildDir().resolve(HELM).resolve(CHART_NAME).resolve(file).toFile());
    }
}
