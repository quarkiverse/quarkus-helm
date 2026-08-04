package io.quarkiverse.helm.spi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.helm.model.Chart;
import io.quarkiverse.helm.model.ValuesSchema;
import io.quarkus.builder.item.MultiBuildItem;

public final class HelmChartBuildItem extends MultiBuildItem {

    private final String deploymentTarget;

    private final Chart chart;
    private final ValuesSchema valuesSchema;
    private final Map<String, Map<String, Object>> values;
    private final Map<String, String> templates;
    private final Optional<String> notes;
    private final Optional<String> readme;

    // All arguments constructor
    public HelmChartBuildItem(String deploymentTarget, Chart chart, ValuesSchema valuesSchema,
            Map<String, Map<String, Object>> values,
            Map<String, String> templates, Optional<String> notes, Optional<String> readme) {
        this.deploymentTarget = deploymentTarget;
        this.chart = chart;
        this.valuesSchema = valuesSchema;
        this.values = values;
        this.templates = templates;
        this.notes = notes;
        this.readme = readme;
    }

    public static class Builder {
        private String deploymentTarget;
        private Chart chart;
        private ValuesSchema valuesSchema;
        private Map<String, Map<String, Object>> values;
        private Map<String, String> templates;
        private Optional<String> notes;
        private Optional<String> readme;

        public Builder withDeploymentTarget(String deploymentTarget) {
            this.deploymentTarget = deploymentTarget;
            return this;
        }

        public Builder withChart(Chart chart) {
            this.chart = chart;
            return this;
        }

        public Builder withValuesSchema(ValuesSchema valuesSchema) {
            this.valuesSchema = valuesSchema;
            return this;
        }

        public Builder withValues(Map<String, Map<String, Object>> values) {
            this.values = values;
            return this;
        }

        public Builder withTemplates(Map<String, String> templates) {
            this.templates = templates;
            return this;
        }

        public Builder withNotes(Optional<String> notes) {
            this.notes = notes;
            return this;
        }

        public Builder withReadme(Optional<String> readme) {
            this.readme = readme;
            return this;
        }

        public HelmChartBuildItem build() {
            return new HelmChartBuildItem(deploymentTarget, chart, valuesSchema, values, templates, notes, readme);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HelmChartBuildItem read(Path dir, Function<String, Object> deserializer) {
        return readAsBuilder(dir, path -> deserializer.apply(Files.readString(path))).build();
    }

    public interface Deserializer {
        Object deserialize(Path path) throws IOException;
    }

    public static HelmChartBuildItem.Builder readAsBuilder(Path dir, Deserializer deserializer) {
        try {
            Path chartYamlPath = dir.resolve("Chart.yaml");
            Path valuesYamlPath = dir.resolve("values.yaml");
            Path valuesSchemaPath = dir.resolve("values.schema.json");
            Path templatesDir = dir.resolve("templates");
            Path notesPath = templatesDir.resolve("NOTES.txt");
            Path readmePath = dir.resolve("README.md");

            Chart chart = (Chart) deserializer.deserialize(chartYamlPath);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> values = (Map<String, Map<String, Object>>) deserializer
                    .deserialize(valuesYamlPath);
            ValuesSchema valuesSchema = (ValuesSchema) deserializer.deserialize(valuesSchemaPath);

            Map<String, String> templates = new HashMap<>();
            if (Files.isDirectory(templatesDir)) {
                Files.list(templatesDir).forEach(templatePath -> {
                    try {
                        if (!Files.isDirectory(templatePath)) {
                            templates.put(templatePath.getFileName().toString(), Files.readString(templatePath));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            Optional<String> notes = Files.exists(notesPath) ? Optional.of(Files.readString(notesPath)) : Optional.empty();
            Optional<String> readme = Files.exists(readmePath) ? Optional.of(Files.readString(readmePath)) : Optional.empty();

            return HelmChartBuildItem.builder()
                    .withChart(chart)
                    .withValues(values)
                    .withValuesSchema(valuesSchema)
                    .withTemplates(templates)
                    .withNotes(notes)
                    .withReadme(readme);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read HelmChartBuildItem from file system", e);
        }
    }

    public void write(Path dir, Function<Object, String> serializer) {
        Path targetDir = dir.resolve(deploymentTarget);
        Path chartDir = targetDir.resolve(getName());
        Path chartYamlPath = chartDir.resolve("Chart.yaml");
        Path valuesYamlPath = chartDir.resolve("values.yaml");
        Path valuesSchemaPath = chartDir.resolve("values.schema.json");
        Path templatesDir = chartDir.resolve("templates");
        Path notesPath = templatesDir.resolve("NOTES.txt");
        Path readmePath = chartDir.resolve("README.md");

        try {
            if (!Files.exists(templatesDir)) {
                Files.createDirectories(templatesDir);
            }
            Files.write(chartYamlPath, serializer.apply(chart).getBytes());
            Files.write(valuesYamlPath, serializer.apply(values).getBytes());
            Files.write(valuesSchemaPath, serializer.apply(valuesSchema).getBytes());
            Files.write(notesPath, serializer.apply(notes).getBytes());

            readme.ifPresent(r -> {
                try {
                    Files.write(readmePath, serializer.apply(r).getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            Files.write(readmePath, serializer.apply(readme).getBytes());
            for (Map.Entry<String, String> entry : templates.entrySet()) {
                String templateFileName = entry.getKey();
                String templateContent = entry.getValue();
                Path templatePath = templatesDir.resolve(templateFileName);
                Files.write(templatePath, serializer.apply(templateContent).getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return chart.getName();
    }

    public String getDeploymentTarget() {
        return deploymentTarget;
    }

    public Chart getChart() {
        return chart;
    }

    public ValuesSchema getValuesSchema() {
        return valuesSchema;
    }

    public Map<String, Map<String, Object>> getValues() {
        return values;
    }

    public Map<String, String> getTemplates() {
        return templates;
    }

    public Optional<String> getNotes() {
        return notes;
    }

    public Optional<String> getReadme() {
        return readme;
    }
}
