package io.quarkiverse.helm.deployment;

import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_API_VERSION;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_NOTES;
import static io.quarkiverse.helm.common.utils.Constants.HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.helm")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface HelmChartConfig {
    /**
     * Enabled the generation of Helm files.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Name of the Helm chart.
     * If not set, it will use the application name.
     */
    Optional<String> name();

    /**
     * Project's home page of the Helm chart. It must be a URL.
     */
    Optional<String> home();

    /**
     * The Helm chart list of URLs to source code for this project.
     */
    Optional<List<String>> sources();

    /**
     * Version of the Helm chart.
     * If not set, it will use the application version.
     */
    Optional<String> version();

    /**
     * The Helm chart single-sentence description.
     */
    Optional<String> description();

    /**
     * List of keywords to add to the chart.
     */
    Optional<List<String>> keywords();

    /**
     * The Helm chart list of maintainers.
     */
    Map<String, MaintainerConfig> maintainers();

    /**
     * Icon of the Helm chart. It must be a URL to an SVG or PNG image.
     */
    Optional<String> icon();

    /**
     * The Chart API version. The default value is `v2`.
     */
    @WithDefault(HELM_CHART_DEFAULT_API_VERSION)
    String apiVersion();

    /**
     * The condition to enable this chart.
     */
    Optional<String> condition();

    /**
     * Tags of this chart.
     */
    Optional<String> tags();

    /**
     * The version of the application enclosed of this chart.
     */
    Optional<String> appVersion();

    /**
     * Whether this chart is deprecated.
     */
    Optional<Boolean> deprecated();

    /**
     * Annotations are additional mappings uninterpreted by Helm, made available for inspection by other applications.
     */
    Map<String, String> annotations();

    /**
     * KubeVersion is a SemVer constraint specifying the version of Kubernetes required.
     */
    Optional<String> kubeVersion();

    /**
     * The Helm chart list of dependencies.
     */
    Map<String, HelmDependencyConfig> dependencies();

    /**
     * Specifies the chart type: application or library.
     */
    Optional<String> type();

    /**
     * Alias of the root element in the generated values file.
     */
    @WithDefault(HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS)
    String valuesRootAlias();

    /**
     * Notes template to be generated.
     */
    @WithDefault(HELM_CHART_DEFAULT_NOTES)
    String notes();

    /**
     * Extension of the Helm tarball file. Default is `tar.gz`.
     */
    @WithDefault("tar.gz")
    String extension();

    /**
     * Classifier to be appended into the generated Helm tarball file.
     */
    Optional<String> tarFileClassifier();

    /**
     * If Helm tar file is generated.
     */
    @WithDefault("false")
    boolean createTarFile();

    /**
     * Whether to generate the `values.schema.json` file that is used to validate the Helm Chart input values.
     */
    @WithDefault("true")
    boolean createValuesSchemaFile();

    /**
     * Whether to generate the `README.md` file that includes the Chart description and table with the configurable parameters
     * and their default values.
     */
    @WithDefault("true")
    boolean createReadmeFile();

    /**
     * The configuration references to be mapped into the Helm values file.
     */
    Map<String, ValueReferenceConfig> values();

    /**
     * Helm expressions to be replaced into the generated resources.
     */
    Map<String, ExpressionConfig> expressions();

    /**
     * The if statements to include in the generated resources.
     */
    Map<String, AddIfStatementConfig> addIfStatement();

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
    @WithDefault("src/main/helm")
    String inputDirectory();

    /**
     * The output folder in which to place the Helm generated folder. The folder is relative to the target output directory
     * in Quarkus that is also configurable using the property `quarkus.package.output-directory`.
     *
     * It also supports absolute paths.
     *
     * By default, it will be generated in the folder named "helm".
     */
    @WithDefault("helm")
    String outputDirectory();

    /**
     * The configuration to perform Helm charts uploads to Helm repositories..
     */
    HelmRepository repository();

    /**
     * If enabled, the extension will check whether there are properties using system properties in the form of `${XXX}` and
     * if so, it will expose these properties as env-var values within the generated container resource.
     */
    @WithDefault("true")
    boolean mapSystemProperties();

    /**
     * If true, the naming validation will be disabled.
     * The naming validation rejects property names that contain "-" characters.
     */
    @WithDefault("false")
    boolean disableNamingValidation();

    /**
     * Configuration for the separator string in the filename of profile specific values files i.e. values.profile.yaml,
     * defaults to "."
     */
    @WithDefault(".")
    String valuesProfileSeparator();

    /**
     * Configuration for the `values.schema.json` file.
     */
    ValuesSchemaConfig valuesSchema();

    default List<String> getDependencyNames() {
        return dependencies().entrySet().stream()
                .map(entry -> entry.getValue().alias().orElseGet(() -> entry.getValue().name().orElse(entry.getKey())))
                .collect(Collectors.toList());
    }
}
