package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.dekorate.project.BuildInfo;
import io.dekorate.project.Project;

public class QuarkusHelmWriterSessionListenerTest {

    @TempDir
    Path tempDir;

    private Project buildProject(Path root) {
        return new Project(root, "", "", "",
                new BuildInfo("test-app", "1.0.0", "jar", "maven", "3.8", null, null, null));
    }

    @Test
    public void shouldApplyExpressionsToAdditionalTemplates() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart";
        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        Project project = buildProject(inputDir);

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir);

        Map<String, byte[]> generatedFiles = buildGeneratedFiles();
        Map<String, byte[]> additionalTemplates = buildAdditionalTemplates();

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                generatedFiles,
                additionalTemplates,
                Collections.emptyMap());

        Path clusterRoleBindingFile = outputDir.resolve(chartName).resolve("templates").resolve("clusterrolebinding.yaml");
        assertTrue(Files.exists(clusterRoleBindingFile),
                "ClusterRoleBinding template file should exist");

        String content = Files.readString(clusterRoleBindingFile);
        assertTrue(content.contains("{{ .Release.Namespace }}-my-cluster-role"),
                "The roleRef.name in the ClusterRoleBinding should be updated by the expression. Actual content:\n" + content);
        assertFalse(content.contains("name: my-cluster-role\n"),
                "The original roleRef.name should have been replaced. Actual content:\n" + content);
    }

    @Test
    public void shouldWriteRawContentWhenAdditionalTemplateIsNotParseableYaml() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart-unparseable";
        Path inputDir = tempDir.resolve("input2");
        Path outputDir = tempDir.resolve("output2");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        Project project = buildProject(inputDir);

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir);

        Map<String, byte[]> generatedFiles = buildGeneratedFiles();

        Map<String, byte[]> additionalTemplates = new LinkedHashMap<>();
        additionalTemplates.put("_helpers.tpl", "{{- define \"test.helper\" -}}test{{- end }}".getBytes());

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                generatedFiles,
                additionalTemplates,
                Collections.emptyMap());

        Path helpersFile = outputDir.resolve(chartName).resolve("templates").resolve("_helpers.tpl");
        assertTrue(Files.exists(helpersFile),
                "Helper template file should exist");

        String content = Files.readString(helpersFile);
        assertTrue(content.contains("{{- define \"test.helper\" -}}test{{- end }}"),
                "Unparseable additional template should be written as-is. Actual content:\n" + content);
    }

    // see: https://github.com/wanaku-ai/wanaku/issues/1376
    @Test
    public void shouldApplyExpressionsToHelmTemplate() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart-helm";
        Path inputDir = tempDir.resolve("input-helm");
        Path outputDir = tempDir.resolve("output-helm");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        Project project = buildProject(inputDir);

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir, true);

        Map<String, byte[]> generatedFiles = buildGeneratedFiles();

        // Template Helm com {{ }} - simulando template do JOSDK
        Map<String, byte[]> additionalTemplates = new LinkedHashMap<>();
        String helmTemplate = "apiVersion: rbac.authorization.k8s.io/v1\n" +
                "kind: ClusterRoleBinding\n" +
                "metadata:\n" +
                "  name: {{ .Chart.Name }}-crd-validating-role-binding\n" +
                "roleRef:\n" +
                "  kind: ClusterRole\n" +
                "  apiGroup: rbac.authorization.k8s.io\n" +
                "  name: {{ $.Release.Namespace }}-josdk-crd-validating-cluster-role\n" +
                "subjects:\n" +
                "  - kind: ServiceAccount\n" +
                "    name: {{ .Chart.Name }}\n" +
                "    namespace: {{ .Release.Namespace }}\n";
        additionalTemplates.put("validating-clusterrolebinding.yaml", helmTemplate.getBytes());

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                generatedFiles,
                additionalTemplates,
                Collections.emptyMap());

        Path outputFile = outputDir.resolve(chartName).resolve("templates").resolve("validating-clusterrolebinding.yaml");
        assertTrue(Files.exists(outputFile), "Helm template file should exist");

        String resultContent = Files.readString(outputFile);
        assertTrue(resultContent.contains("name: {{ .Release.Namespace }}-my-custom-cluster-role"),
                "The roleRef.name should be updated by the expression. Actual content:\n" + resultContent);
        assertTrue(resultContent.contains("{{ .Chart.Name }}-crd-validating-role-binding"),
                "Original Helm template directives should be preserved. Actual content:\n" + resultContent);
        assertTrue(resultContent.contains("namespace: {{ .Release.Namespace }}"),
                "Other Helm directives should be preserved. Actual content:\n" + resultContent);
    }

    private Map<String, byte[]> buildGeneratedFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String deploymentYaml = "apiVersion: apps/v1\n" +
                "kind: Deployment\n" +
                "metadata:\n" +
                "  name: test-app\n" +
                "spec:\n" +
                "  replicas: 1\n" +
                "  selector:\n" +
                "    matchLabels:\n" +
                "      app: test-app\n" +
                "  template:\n" +
                "    metadata:\n" +
                "      labels:\n" +
                "        app: test-app\n" +
                "    spec:\n" +
                "      containers:\n" +
                "        - name: test-app\n" +
                "          image: test-app:latest\n" +
                "          ports:\n" +
                "            - containerPort: 8080\n";
        files.put("kubernetes.yml", deploymentYaml.getBytes());
        return files;
    }

    private Map<String, byte[]> buildAdditionalTemplates() {
        Map<String, byte[]> templates = new LinkedHashMap<>();
        String yaml = "apiVersion: rbac.authorization.k8s.io/v1\n" +
                "kind: ClusterRoleBinding\n" +
                "metadata:\n" +
                "  name: my-cluster-role-binding\n" +
                "subjects:\n" +
                "  - kind: ServiceAccount\n" +
                "    name: my-service-account\n" +
                "roleRef:\n" +
                "  kind: ClusterRole\n" +
                "  name: my-cluster-role\n" +
                "  apiGroup: rbac.authorization.k8s.io\n";
        templates.put("clusterrolebinding.yaml", yaml.getBytes());
        return templates;
    }

    private static class TestHelmChartConfig implements HelmChartConfig {
        private final String chartName;
        private final Path inputDir;
        private final boolean useHelmExpressions;

        TestHelmChartConfig(String chartName, Path inputDir) {
            this(chartName, inputDir, false);
        }

        TestHelmChartConfig(String chartName, Path inputDir, boolean useHelmExpressions) {
            this.chartName = chartName;
            this.inputDir = inputDir;
            this.useHelmExpressions = useHelmExpressions;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Optional<String> name() {
            return Optional.of(chartName);
        }

        @Override
        public Optional<String> home() {
            return Optional.empty();
        }

        @Override
        public Optional<java.util.List<String>> sources() {
            return Optional.empty();
        }

        @Override
        public Optional<String> version() {
            return Optional.empty();
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }

        @Override
        public Optional<java.util.List<String>> keywords() {
            return Optional.empty();
        }

        @Override
        public Map<String, MaintainerConfig> maintainers() {
            return Collections.emptyMap();
        }

        @Override
        public Optional<String> icon() {
            return Optional.empty();
        }

        @Override
        public String apiVersion() {
            return "v2";
        }

        @Override
        public Optional<String> condition() {
            return Optional.empty();
        }

        @Override
        public Optional<String> tags() {
            return Optional.empty();
        }

        @Override
        public Optional<String> appVersion() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> deprecated() {
            return Optional.empty();
        }

        @Override
        public Map<String, String> annotations() {
            return Collections.emptyMap();
        }

        @Override
        public Optional<String> kubeVersion() {
            return Optional.empty();
        }

        @Override
        public Map<String, HelmDependencyConfig> dependencies() {
            return Collections.emptyMap();
        }

        @Override
        public Optional<String> type() {
            return Optional.empty();
        }

        @Override
        public String valuesRootAlias() {
            return "app";
        }

        @Override
        public String notes() {
            return "";
        }

        @Override
        public String extension() {
            return "tar.gz";
        }

        @Override
        public Optional<String> tarFileClassifier() {
            return Optional.empty();
        }

        @Override
        public boolean createTarFile() {
            return false;
        }

        @Override
        public boolean createValuesSchemaFile() {
            return false;
        }

        @Override
        public boolean createReadmeFile() {
            return false;
        }

        @Override
        public Map<String, ValueReferenceConfig> values() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, ExpressionConfig> expressions() {
            Map<String, ExpressionConfig> exprs = new LinkedHashMap<>();
            if (useHelmExpressions) {
                // Expression para template Helm (com {{ }})
                exprs.put("0", new ExpressionConfig() {
                    @Override
                    public String path() {
                        return "(kind == ClusterRoleBinding).roleRef.name";
                    }

                    @Override
                    public String expression() {
                        return "{{ .Release.Namespace }}-my-custom-cluster-role";
                    }
                });
            } else {
                // Expression original para YAML puro
                exprs.put("0", new ExpressionConfig() {
                    @Override
                    public String path() {
                        return "(kind == ClusterRoleBinding && metadata.name == my-cluster-role-binding).roleRef.name";
                    }

                    @Override
                    public String expression() {
                        return "{{ .Release.Namespace }}-my-cluster-role";
                    }
                });
            }
            return exprs;
        }

        @Override
        public Map<String, AddIfStatementConfig> addIfStatement() {
            return Collections.emptyMap();
        }

        @Override
        public String inputDirectory() {
            return inputDir.toString();
        }

        @Override
        public String outputDirectory() {
            return "helm";
        }

        @Override
        public HelmRepository repository() {
            return new HelmRepository() {
                @Override
                public boolean push() {
                    return false;
                }

                @Override
                public Optional<String> deploymentTarget() {
                    return Optional.empty();
                }

                @Override
                public Optional<HelmRepositoryType> type() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> url() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> username() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> password() {
                    return Optional.empty();
                }
            };
        }

        @Override
        public boolean mapSystemProperties() {
            return true;
        }

        @Override
        public boolean disableNamingValidation() {
            return true;
        }

        @Override
        public String valuesProfileSeparator() {
            return ".";
        }

        @Override
        public ValuesSchemaConfig valuesSchema() {
            return new ValuesSchemaConfig() {
                @Override
                public String title() {
                    return "Values";
                }

                @Override
                public Map<String, ValuesSchemaPropertyConfig> properties() {
                    return Collections.emptyMap();
                }
            };
        }
    }
}
