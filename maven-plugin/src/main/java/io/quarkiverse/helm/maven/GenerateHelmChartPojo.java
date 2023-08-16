package io.quarkiverse.helm.maven;

import static io.github.yamlpath.utils.StringUtils.EMPTY;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_API_VERSION;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_NOTES;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_VALUES_SCHEMA_TITLE;
import static io.quarkiverse.helm.common.utils.Constants.HELM_INVALID_CHARACTERS;
import static io.quarkiverse.helm.common.utils.Constants.NAME_FORMAT_REG_EXP;
import static org.apache.commons.lang3.StringUtils.defaultString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.dekorate.ConfigReference;
import io.dekorate.helm.config.HelmChartConfig;
import io.dekorate.helm.config.HelmChartConfigBuilder;
import io.dekorate.helm.config.HelmDependencyBuilder;
import io.dekorate.helm.config.ValuesSchemaBuilder;
import io.dekorate.helm.config.ValuesSchemaProperty;
import io.dekorate.helm.config.ValuesSchemaPropertyBuilder;
import io.quarkiverse.helm.common.QuarkusHelmWriterSessionListener;

@Mojo(name = "generate-helm-chart", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateHelmChartPojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    /**
     * Enabled the generation of Helm files.
     */
    @Parameter(defaultValue = "true")
    private boolean enabled;

    /**
     * The input folder in which to place the input manifests. These files will be used as inputs to populate the
     * generated Helm files.
     * It also supports absolute paths.
     * By default, it will use the folder "src/main/k8s".
     */
    @Parameter(required = true, defaultValue = "src/main/k8s")
    private String inputK8sDirectory;

    /**
     * The input folder in which to place the user-defined Helm files. These files will be used as inputs to populate the
     * generated Helm files.
     * At the moment, the supported Helm files are: README.md, LICENSE, values.schema.json, app-readme.md or app-README.md,
     * questions.yml or questions.yaml, the "crds" directory, and requirements.yml or requirements.yaml.
     *
     * Moreover, you can provide a custom `values.yaml` or `Chart.yaml` and the content will be merged with the auto-generated
     * configuration.
     *
     * It also supports absolute paths.
     *
     * By default, it will use the folder "src/main/helm".
     */
    @Parameter(required = true, defaultValue = "src/main/helm")
    private String inputHelmDirectory;

    /**
     * The output folder in which to place the Helm generated folder.
     * It also supports absolute paths.
     * By default, it will be generated in the folder named "helm".
     */
    @Parameter(required = false, defaultValue = "target/helm")
    private String outputDirectory;

    /**
     * Extension of the Helm tarball file. Default is `tar.gz`.
     */
    @Parameter(defaultValue = "tar.gz")
    public String extension;

    /**
     * Classifier to be appended into the generated Helm tarball file.
     */
    @Parameter
    public String tarFileClassifier;

    /**
     * If Helm tar file is generated.
     */
    @Parameter(defaultValue = "false")
    public boolean createTarFile;

    /**
     * Whether to generate the `values.schema.json` file that is used to validate the Helm Chart input values.
     */
    @Parameter(defaultValue = "true")
    public boolean createValuesSchemaFile;

    /**
     * Whether to generate the `README.md` file that includes the Chart description and table with the configurable parameters
     * and their default values.
     */
    @Parameter(defaultValue = "true")
    public boolean createReadmeFile;

    /**
     * Configuration for the separator string in the filename of profile specific values files i.e. values.profile.yaml,
     * defaults to "."
     */
    @Parameter(defaultValue = ".")
    public String valuesProfileSeparator;

    /**
     * If true, the naming validation will be disabled.
     * The naming validation rejects property names that contain "-" characters.
     */
    @Parameter(defaultValue = "false")
    public boolean disableNamingValidation;

    /**
     * If true, the naming validation will be disabled.
     * The naming validation rejects property names that contain "-" characters.
     */
    @Parameter(defaultValue = "true")
    public boolean mapPreConfiguredPropertiesInValues;

    @Parameter
    private Chart chart;

    @Override
    public void execute() throws MojoFailureException {
        if (enabled) {
            validate();
            doGenerateResources();
        }
    }

    private void doGenerateResources() throws MojoFailureException {
        // Deduct folders
        Path inputFolder = resolveDirectory(inputHelmDirectory);
        Path outputFolder = resolveDirectory(outputDirectory);
        deleteOutputHelmFolderIfExists(outputFolder);

        // Dekorate session writer
        final QuarkusHelmWriterSessionListener helmWriter = new QuarkusHelmWriterSessionListener();
        final Set<File> inputResources = readInputResources();

        // Config
        HelmChartConfig dekorateHelmChartConfig = toDekorateHelmChartConfig();
        List<ConfigReference> valueReferencesFromUser = toValueReferences();

        // generate!
        Map<String, String> generated = helmWriter.writeHelmFiles(dekorateHelmChartConfig,
                valueReferencesFromUser,
                getConfigReferences(),
                inputFolder,
                outputFolder,
                inputResources,
                valuesProfileSeparator);
    }

    private List<ConfigReference> getConfigReferences() {
        if (!mapPreConfiguredPropertiesInValues) {
            return Collections.emptyList();
        }

        List<ConfigReference> defaultConfigReferences = new LinkedList<>();
        defaultConfigReferences.add(new ConfigReference.Builder("replicas", "spec.replicas")
                .withDescription("The number of desired pods.")
                .withMinimum(0)
                .build());
        defaultConfigReferences.add(new ConfigReference.Builder("image", "spec.template.spec.containers.image")
                .withDescription("The container image to use.")
                .build());
        defaultConfigReferences.add(new ConfigReference.Builder("host", "(kind == Ingress).spec.rules.host")
                .withDescription("The host under which the application is going to be exposed.")
                .build());
        defaultConfigReferences.add(new ConfigReference.Builder("host",
                new String[] { "(kind == Ingress).spec.rules.host", "(kind == Route).spec.host" })
                .withDescription("The host under which the application is going to be exposed.")
                .build());

        return defaultConfigReferences;
    }

    private void validate() throws MojoFailureException {
        if (chart == null) {
            return;
        }

        if (StringUtils.isNotEmpty(chart.name) && !chart.name.matches(NAME_FORMAT_REG_EXP)) {
            throw new MojoFailureException(String.format("Wrong name '%s'. Regular expression used for validation "
                    + "is '%s'", chart.name, NAME_FORMAT_REG_EXP));
        }

        if (chart.addIfStatement != null) {
            for (AddIfStatement addIfStatement : chart.addIfStatement) {
                String name = addIfStatement.property;
                if (StringUtils.isEmpty(addIfStatement.onResourceKind) && StringUtils.isEmpty(addIfStatement.onResourceName)) {
                    throw new IllegalStateException(String.format("Either 'addIfStatement.onResourceKind' "
                            + "or 'addIfStatement.onResourceKind' must be provided. Problematic property is '%s'", name));
                }

                if (!disableNamingValidation && HELM_INVALID_CHARACTERS.stream().anyMatch(name::contains)) {
                    throw new RuntimeException(
                            String.format("The property of the `addIfStatement` '%s' is invalid. Can't use '-' characters."
                                    + "You can disable the naming validation using "
                                    + "`<disableNamingValidation>true</disableNamingValidation>`", name));
                }
            }
        }

        if (!disableNamingValidation) {
            if (chart.dependencies != null) {
                for (HelmDependency dependency : chart.dependencies) {
                    String name = dependency.name;
                    if (StringUtils.isNotEmpty(dependency.condition)
                            && HELM_INVALID_CHARACTERS.stream().anyMatch(dependency.condition::contains)) {
                        throw new RuntimeException(
                                String.format("Condition of the dependency '%s' is invalid. Can't use '-' characters."
                                        + "You can disable the naming validation using "
                                        + "`<disableNamingValidation>true</disableNamingValidation>`", name));
                    }
                }
            }

            if (chart.values != null) {
                for (ValueReference value : chart.values) {
                    String name = value.property;
                    if (HELM_INVALID_CHARACTERS.stream().anyMatch(name::contains)) {
                        throw new RuntimeException(
                                String.format("Property of the value '%s' is invalid. Can't use '-' characters."
                                        + "You can disable the naming validation using "
                                        + "`<disableNamingValidation>true</disableNamingValidation>`", name));
                    }
                }
            }
        }
    }

    private String getEffectiveName() {
        if (chart == null) {
            return project.getArtifactId();
        }

        return defaultString(chart.name, project.getArtifactId());
    }

    private Path resolveDirectory(String directory) {
        Path path = Paths.get(directory);
        if (!path.isAbsolute()) {
            return project.getBasedir().toPath().resolve(path);
        }

        return path;
    }

    private HelmChartConfig toDekorateHelmChartConfig() {
        HelmChartConfigBuilder builder = new HelmChartConfigBuilder()
                .withEnabled(enabled)
                .withName(getEffectiveName())
                .withCreateTarFile(createTarFile)
                .withCreateValuesSchemaFile(createValuesSchemaFile)
                .withCreateReadmeFile(createReadmeFile)
                .withExtension(extension)
                .withVersion(chart == null ? project.getVersion() : defaultString(chart.version, project.getVersion()))
                .withApiVersion(chart == null ? HELM_CHART_DEFAULT_API_VERSION : chart.apiVersion)
                .withValuesRootAlias(chart == null ? HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS
                        : defaultString(chart.valuesRootAlias, HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS))
                .withNotes(chart == null ? HELM_CHART_DEFAULT_NOTES : defaultString(chart.notes, HELM_CHART_DEFAULT_NOTES));
        if (chart != null) {
            ifNotNull(chart.description).ifPresent(builder::withDescription);
            ifNotNull(chart.keywords).ifPresent(builder::addAllToKeywords);
            ifNotNull(chart.icon).ifPresent(builder::withIcon);
            ifNotNull(chart.condition).ifPresent(builder::withCondition);
            ifNotNull(chart.tags).ifPresent(builder::withTags);
            ifNotNull(chart.appVersion).ifPresent(builder::withAppVersion);
            ifNotNull(chart.deprecated).ifPresent(builder::withDeprecated);
            ifNotNull(chart.annotations).ifPresent(a -> a.forEach(builder::addNewAnnotation));
            ifNotNull(chart.kubeVersion).ifPresent(builder::withKubeVersion);
            ifNotNull(chart.type).ifPresent(builder::withType);
            ifNotNull(chart.home).ifPresent(builder::withHome);
            ifNotNull(chart.sources).ifPresent(builder::addAllToSources);
            ifNotNull(chart.maintainers).ifPresent(l -> l.forEach(m -> builder.addNewMaintainer(
                    m.name,
                    defaultString(m.email),
                    defaultString(m.url))));
            ifNotNull(chart.dependencies)
                    .ifPresent(l -> l.forEach(d -> builder.addToDependencies(toDekorateHelmDependencyConfig(d))));
            ifNotNull(chart.expressions).ifPresent(l -> l.forEach(e -> builder.addNewExpression(e.path, e.expression)));
            ifNotNull(chart.addIfStatement).ifPresent(l -> l.forEach(v -> builder.addNewAddIfStatement(
                    v.property,
                    defaultString(v.onResourceKind),
                    defaultString(v.onResourceName),
                    ifNotNull(v.withDefaultValue).orElse(true),
                    ifNotNull(v.description).orElse("Determine if the resource should be installed or not."))));
        }

        ifNotNull(tarFileClassifier).ifPresent(builder::withTarFileClassifier);
        builder.withValuesSchema(toValuesSchema());

        return builder.build();
    }

    private io.dekorate.helm.config.HelmDependency toDekorateHelmDependencyConfig(HelmDependency dependency) {
        HelmDependencyBuilder builder = new HelmDependencyBuilder()
                .withName(dependency.name)
                .withAlias(defaultString(dependency.alias, dependency.name))
                .withVersion(dependency.version)
                .withRepository(dependency.repository)
                .withCondition(defaultString(dependency.condition))
                .withTags(defaultArray(dependency.tags))
                .withEnabled(ifNotNull(dependency.enabled).orElse(true));

        return builder.build();
    }

    private io.dekorate.helm.config.ValuesSchema toValuesSchema() {
        List<ValuesSchemaProperty> properties = new ArrayList<>();
        if (chart != null && chart.valuesSchema != null) {
            for (io.quarkiverse.helm.maven.ValuesSchemaProperty property : chart.valuesSchema.properties) {

                properties.add(new ValuesSchemaPropertyBuilder()
                        .withName(property.name)
                        .withType(defaultString(property.type, "string"))
                        .withDescription(defaultString(property.description))
                        .withMaximum(ifNotNull(property.maximum).orElse(Integer.MAX_VALUE))
                        .withMinimum(ifNotNull(property.minimum).orElse(Integer.MIN_VALUE))
                        .withRequired(ifNotNull(property.required).orElse(false))
                        .withPattern(defaultString(property.pattern))
                        .build());
            }
        }

        return new ValuesSchemaBuilder()
                .withTitle(chart == null || chart.valuesSchema == null ? HELM_CHART_DEFAULT_VALUES_SCHEMA_TITLE
                        : defaultString(chart.valuesSchema.title, HELM_CHART_DEFAULT_VALUES_SCHEMA_TITLE))
                .withProperties(properties.toArray(new ValuesSchemaProperty[0]))
                .build();
    }

    private List<ConfigReference> toValueReferences() {
        if (chart == null || chart.values == null) {
            return Collections.emptyList();
        }

        return chart.values.stream()
                .map(e -> new ConfigReference.Builder(e.property,
                        defaultArray(e.paths))
                        .withValue(toValue(e))
                        .withDescription(defaultString(e.description, EMPTY))
                        .withExpression(defaultString(e.expression))
                        .withProfile(defaultString(e.profile))
                        .withRequired(ifNotNull(e.required).orElse(false))
                        .withPattern(defaultString(e.pattern))
                        .withMaximum(ifNotNull(e.maximum).orElse(Integer.MAX_VALUE))
                        .withMinimum(ifNotNull(e.minimum).orElse(Integer.MIN_VALUE))
                        .build())
                .collect(Collectors.toList());
    }

    private Object toValue(ValueReference v) {
        if (v.valueAsInt != null) {
            return v.valueAsInt;
        } else if (v.valueAsBool != null) {
            return v.valueAsBool;
        } else if (v.valueAsMap != null && !v.valueAsMap.isEmpty()) {
            return v.valueAsMap;
        } else if (v.valueAsList != null && !v.valueAsList.isEmpty()) {
            return v.valueAsList;
        }

        return v.value;
    }

    private Set<File> readInputResources() throws MojoFailureException {
        Path input = resolveDirectory(inputK8sDirectory);
        Set<File> inputResources = new HashSet<>();
        File inputDirectory = input.toFile();
        if (!inputDirectory.exists()) {
            throw new MojoFailureException(
                    String.format(
                            "Input directory does not exist '%s'. You need to provide the YAML resources to use for the Helm chart generation. ",
                            inputDirectory));
        }

        for (File file : input.toFile().listFiles()) {
            String fileName = file.getName().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                inputResources.add(file);
            }
        }

        if (inputResources.isEmpty()) {
            throw new MojoFailureException(
                    String.format("No YAML resources were found at '%s'. Can't generate the Helm chart.", inputDirectory));
        }

        return inputResources;
    }

    private void deleteOutputHelmFolderIfExists(Path outputFolder) {
        try {
            Files.deleteIfExists(outputFolder);
        } catch (IOException ignored) {

        }
    }

    private Optional<String> ifNotNull(String value) {
        return Optional.ofNullable(value).filter(StringUtils::isNotEmpty);
    }

    private <T> Optional<List<T>> ifNotNull(List<T> value) {
        return Optional.ofNullable(value);
    }

    private <K, V> Optional<Map<K, V>> ifNotNull(Map<K, V> value) {
        return Optional.ofNullable(value);
    }

    private Optional<Boolean> ifNotNull(Boolean value) {
        return Optional.ofNullable(value);
    }

    private Optional<Integer> ifNotNull(Integer value) {
        return Optional.ofNullable(value);
    }

    private static String[] defaultArray(List<String> optional) {
        if (optional == null) {
            return new String[0];
        }

        return optional.toArray(new String[0]);
    }
}
