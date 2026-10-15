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
                Collections.emptyMap(),
                Collections.emptyList());

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
                Collections.emptyMap(),
                Collections.emptyList());

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
                Collections.emptyMap(),
                Collections.emptyList());

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

    @Test
    public void shouldHonorPathConditionsInConditionalHelmTemplates() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart-conditions";
        Path inputDir = tempDir.resolve("input-conditions");
        Path outputDir = tempDir.resolve("output-conditions");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        Project project = buildProject(inputDir);

        Map<String, ExpressionConfig> expressions = new LinkedHashMap<>();
        // Must not be applied anywhere: no document is named wanaku-router-crd-validating-role-binding
        expressions.put("0", expression(
                "(kind == ClusterRoleBinding && metadata.name == wanaku-router-crd-validating-role-binding).metadata.name",
                "{{ .Release.Namespace }}-wanaku-router-crd-validating-role-binding"));
        // Must only be applied to the RoleBinding branch
        expressions.put("1", expression(
                "(kind == RoleBinding && metadata.name == wanaku-service-catalog-role-binding).roleRef.name",
                "{{ .Release.Namespace }}-wanaku-service-catalog-cluster-role"));
        // Must only be applied to the ClusterRoleBinding branch
        expressions.put("2", expression(
                "(kind == ClusterRoleBinding).metadata.name",
                "{{ .Release.Namespace }}-wanaku-service-catalog-role-binding"));

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir, expressions);

        // Conditional multi-branch template, as generated by the quarkus-operator-sdk for each controller
        String helmTemplate = "{{ if eq $.Values.app.envs.NAMESPACES \"JOSDK_WATCH_CURRENT\" }}\n" +
                "apiVersion: rbac.authorization.k8s.io/v1\n" +
                "kind: RoleBinding\n" +
                "metadata:\n" +
                "  name: wanaku-service-catalog-role-binding\n" +
                "  namespace: {{ .Release.Namespace }}\n" +
                "roleRef:\n" +
                "  kind: ClusterRole\n" +
                "  apiGroup: rbac.authorization.k8s.io\n" +
                "  name: wanaku-service-catalog-cluster-role\n" +
                "subjects:\n" +
                "  - kind: ServiceAccount\n" +
                "    name: wanaku-operator\n" +
                "{{ else }}\n" +
                "apiVersion: rbac.authorization.k8s.io/v1\n" +
                "kind: ClusterRoleBinding\n" +
                "metadata:\n" +
                "  name: wanaku-service-catalog-role-binding\n" +
                "roleRef:\n" +
                "  kind: ClusterRole\n" +
                "  apiGroup: rbac.authorization.k8s.io\n" +
                "  name: wanaku-service-catalog-cluster-role\n" +
                "subjects:\n" +
                "  - kind: ServiceAccount\n" +
                "    name: wanaku-operator\n" +
                "{{ end }}\n";
        Map<String, byte[]> additionalTemplates = new LinkedHashMap<>();
        additionalTemplates.put("wanaku-service-catalog-crd-role-binding.yaml", helmTemplate.getBytes());

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                buildGeneratedFiles(),
                additionalTemplates,
                Collections.emptyMap(),
                Collections.emptyList());

        Path outputFile = outputDir.resolve(chartName).resolve("templates")
                .resolve("wanaku-service-catalog-crd-role-binding.yaml");
        assertTrue(Files.exists(outputFile), "Additional template file should exist");

        String content = Files.readString(outputFile);
        assertFalse(content.contains("wanaku-router-crd-validating-role-binding"),
                "An expression whose metadata.name condition matches no document must not be applied. Actual content:\n"
                        + content);
        assertTrue(content.contains("name: {{ .Release.Namespace }}-wanaku-service-catalog-cluster-role"),
                "The roleRef.name of the RoleBinding branch should be rewritten. Actual content:\n" + content);
        assertTrue(content.contains("name: wanaku-service-catalog-role-binding\n"),
                "The metadata.name of the RoleBinding branch must not be rewritten. Actual content:\n" + content);
        assertTrue(content.contains("name: {{ .Release.Namespace }}-wanaku-service-catalog-role-binding"),
                "The metadata.name of the ClusterRoleBinding branch should be rewritten. Actual content:\n" + content);
        assertTrue(content.contains("name: wanaku-service-catalog-cluster-role\n"),
                "The roleRef.name of the ClusterRoleBinding branch must not be rewritten. Actual content:\n" + content);
        assertTrue(content.contains("{{ if eq $.Values.app.envs.NAMESPACES \"JOSDK_WATCH_CURRENT\" }}"),
                "Helm control-flow directives should be preserved. Actual content:\n" + content);
        assertTrue(content.contains("{{ end }}"),
                "Helm control-flow directives should be preserved. Actual content:\n" + content);
    }

    // see: https://github.com/quarkiverse/quarkus-helm/issues/462
    @Test
    public void shouldNotLeakProfileValuesIntoOtherValuesFilesWhenUsingMaps() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart-values-map";
        Path inputDir = tempDir.resolve("input-values-map");
        Path outputDir = tempDir.resolve("output-values-map");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Custom per-profile values file overriding one of the map entries
        Files.writeString(inputDir.resolve("values.dev.yaml"),
                "app:\n" +
                        "  ingress:\n" +
                        "    annotations:\n" +
                        "      \"alb.ingress.kubernetes.io/certificate-arn\": dev-certificate-arn\n");

        Project project = buildProject(inputDir);

        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("alb.ingress.kubernetes.io/certificate-arn", "CHANGE_ME");
        annotations.put("alb.ingress.kubernetes.io/scheme", "internal");

        Map<String, ValueReferenceConfig> valueReferences = new LinkedHashMap<>();
        valueReferences.put("ingress.annotations", valueReference(null, null, annotations));
        // placeholder value so that the "dev" profile (and hence values.dev.yaml) is generated
        valueReferences.put("env_dev.profile", valueReference("dev", "dev", Collections.emptyMap()));

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir) {
            @Override
            public Map<String, ValueReferenceConfig> values() {
                return valueReferences;
            }
        };

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                buildGeneratedFiles(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList());

        String prodValues = Files.readString(outputDir.resolve(chartName).resolve("values.yaml"));
        assertTrue(prodValues.contains("CHANGE_ME"),
                "The values.yaml file should keep the default map values. Actual content:\n" + prodValues);
        assertFalse(prodValues.contains("dev-certificate-arn"),
                "Values from the dev profile should not leak into values.yaml. Actual content:\n" + prodValues);

        String devValues = Files.readString(outputDir.resolve(chartName).resolve("values.dev.yaml"));
        assertTrue(devValues.contains("dev-certificate-arn"),
                "The values.dev.yaml file should contain the profile override. Actual content:\n" + devValues);
        assertTrue(devValues.contains("internal"),
                "The values.dev.yaml file should inherit the remaining map values. Actual content:\n" + devValues);
    }

    // see: https://github.com/quarkiverse/quarkus-helm/issues/462
    @Test
    public void shouldNotSplitDottedKeysFromCustomValuesFile() throws IOException {
        QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

        String chartName = "test-chart-dotted-keys";
        Path inputDir = tempDir.resolve("input-dotted-keys");
        Path outputDir = tempDir.resolve("output-dotted-keys");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Custom values file using map keys that contain dots (Kubernetes annotations)
        Files.writeString(inputDir.resolve("values.yaml"),
                "app:\n" +
                        "  ingress:\n" +
                        "    annotations:\n" +
                        "      alb.ingress.kubernetes.io/backend-protocol: HTTP\n" +
                        "      kubernetes.io/ingress.class: alb\n" +
                        "  replicas: 3\n");

        Project project = buildProject(inputDir);

        HelmChartConfig helmConfig = new TestHelmChartConfig(chartName, inputDir);

        listener.writeHelmFiles(
                chartName,
                project,
                helmConfig,
                Collections.emptyList(),
                inputDir,
                outputDir,
                buildGeneratedFiles(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList());

        String values = Files.readString(outputDir.resolve(chartName).resolve("values.yaml"));
        assertTrue(values.contains("alb.ingress.kubernetes.io/backend-protocol"),
                "Annotation keys with dots should be preserved as-is. Actual content:\n" + values);
        assertTrue(values.contains("kubernetes.io/ingress.class"),
                "Annotation keys with dots should be preserved as-is. Actual content:\n" + values);
        assertFalse(values.contains("alb:"),
                "Annotation keys with dots should not be split into nested maps. Actual content:\n" + values);
        assertTrue(values.contains("replicas: 3"),
                "Nested properties without dotted keys should keep working. Actual content:\n" + values);
    }

    private static ValueReferenceConfig valueReference(String value, String profile, Map<String, String> valueAsMap) {
        return new ValueReferenceConfig() {
            @Override
            public Optional<String> property() {
                return Optional.empty();
            }

            @Override
            public Optional<java.util.List<String>> paths() {
                return Optional.empty();
            }

            @Override
            public Optional<String> profile() {
                return Optional.ofNullable(profile);
            }

            @Override
            public Optional<String> value() {
                return Optional.ofNullable(value);
            }

            @Override
            public Optional<Integer> valueAsInt() {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> valueAsBool() {
                return Optional.empty();
            }

            @Override
            public Map<String, String> valueAsMap() {
                return valueAsMap;
            }

            @Override
            public Optional<java.util.List<String>> valueAsList() {
                return Optional.empty();
            }

            @Override
            public Optional<String> expression() {
                return Optional.empty();
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }

            @Override
            public Optional<Integer> minimum() {
                return Optional.empty();
            }

            @Override
            public Optional<Integer> maximum() {
                return Optional.empty();
            }

            @Override
            public Optional<String> pattern() {
                return Optional.empty();
            }

            @Override
            public boolean required() {
                return false;
            }
        };
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

    private static ExpressionConfig expression(String path, String expression) {
        return new ExpressionConfig() {
            @Override
            public String path() {
                return path;
            }

            @Override
            public String expression() {
                return expression;
            }
        };
    }

    private static class TestHelmChartConfig implements HelmChartConfig {
        private final String chartName;
        private final Path inputDir;
        private final boolean useHelmExpressions;
        private final Map<String, ExpressionConfig> customExpressions;

        TestHelmChartConfig(String chartName, Path inputDir) {
            this(chartName, inputDir, false);
        }

        TestHelmChartConfig(String chartName, Path inputDir, boolean useHelmExpressions) {
            this(chartName, inputDir, useHelmExpressions, null);
        }

        TestHelmChartConfig(String chartName, Path inputDir, Map<String, ExpressionConfig> customExpressions) {
            this(chartName, inputDir, false, customExpressions);
        }

        private TestHelmChartConfig(String chartName, Path inputDir, boolean useHelmExpressions,
                Map<String, ExpressionConfig> customExpressions) {
            this.chartName = chartName;
            this.inputDir = inputDir;
            this.useHelmExpressions = useHelmExpressions;
            this.customExpressions = customExpressions;
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
            if (customExpressions != null) {
                return customExpressions;
            }
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
