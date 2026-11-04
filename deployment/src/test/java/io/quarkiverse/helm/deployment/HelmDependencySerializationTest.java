package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;
import io.quarkiverse.helm.model.Chart;
import io.quarkiverse.helm.model.HelmDependency;

public class HelmDependencySerializationTest {

    @Test
    public void shouldDeserializeDependencyWithoutEnabled() {
        HelmDependency dependency = Serialization.unmarshal("""
                name: postgresql
                alias: database
                version: 18.2.4
                repository: https://charts.bitnami.com/bitnami
                condition: database.enabled
                tags:
                  - database
                """, HelmDependency.class);

        assertEquals("postgresql", dependency.getName());
        assertEquals("database", dependency.getAlias());
        assertEquals("18.2.4", dependency.getVersion());
        assertEquals("https://charts.bitnami.com/bitnami", dependency.getRepository());
        assertEquals("database.enabled", dependency.getCondition());
        assertArrayEquals(new String[] { "database" }, dependency.getTags());
        assertNull(dependency.getEnabled());
    }

    @Test
    public void shouldPreserveExplicitEnabledValues() {
        for (boolean enabled : new boolean[] { true, false }) {
            HelmDependency dependency = Serialization.unmarshal("name: postgresql\nenabled: " + enabled,
                    HelmDependency.class);

            assertEquals(enabled, dependency.getEnabled());
        }
    }

    @Test
    public void shouldRoundTripChartWithDependency() {
        Chart chart = new Chart();
        chart.setName("test-chart");
        chart.setDependencies(List.of(new HelmDependency("postgresql", "database", "18.2.4",
                "https://charts.bitnami.com/bitnami", null, null, true)));

        Chart restored = Serialization.unmarshal(Serialization.asYaml(chart), Chart.class);

        assertEquals("test-chart", restored.getName());
        assertEquals(1, restored.getDependencies().size());
        assertEquals("postgresql", restored.getDependencies().getFirst().getName());
        assertNull(restored.getDependencies().getFirst().getEnabled());
    }
}
