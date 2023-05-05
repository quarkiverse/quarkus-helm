package io.quarkiverse.helm.deployment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME, name = "helm")
public class HelmChartConfig {
    /**
     * Enabled the generation of Helm files.
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;

    /**
     * Name of the Helm chart.
     * If not set, it will use the application name.
     */
    @ConfigItem
    public Optional<String> name;

    /**
     * Project's home page of the Helm chart. It must be a URL.
     */
    @ConfigItem
    public Optional<String> home;

    /**
     * The Helm chart list of URLs to source code for this project.
     */
    @ConfigItem
    public Optional<List<String>> sources;

    /**
     * Version of the Helm chart.
     * If not set, it will use the application version.
     */
    @ConfigItem
    public Optional<String> version;

    /**
     * The Helm chart single-sentence description.
     */
    @ConfigItem
    public Optional<String> description;

    /**
     * List of keywords to add to the chart.
     */
    @ConfigItem
    public Optional<List<String>> keywords;

    /**
     * The Helm chart list of maintainers.
     */
    @ConfigItem
    Map<String, MaintainerConfig> maintainers;

    /**
     * Icon of the Helm chart. It must be a URL to an SVG or PNG image.
     */
    @ConfigItem
    public Optional<String> icon;

    /**
     * The Chart API version. The default value is `v2`.
     */
    @ConfigItem(defaultValue = "v2")
    public String apiVersion;

    /**
     * The condition to enable this chart.
     */
    @ConfigItem
    public Optional<String> condition;

    /**
     * Tags of this chart.
     */
    @ConfigItem
    public Optional<String> tags;

    /**
     * The version of the application enclosed of this chart.
     */
    @ConfigItem
    public Optional<String> appVersion;

    /**
     * Whether this chart is deprecated.
     */
    @ConfigItem
    public Optional<Boolean> deprecated;

    /**
     * Annotations are additional mappings uninterpreted by Helm, made available for inspection by other applications.
     */
    @ConfigItem
    public Map<String, String> annotations;

    /**
     * KubeVersion is a SemVer constraint specifying the version of Kubernetes required.
     */
    @ConfigItem
    public Optional<String> kubeVersion;

    /**
     * The Helm chart list of dependencies.
     */
    @ConfigItem
    public Map<String, HelmDependencyConfig> dependencies;

    /**
     * Specifies the chart type: application or library.
     */
    @ConfigItem
    public Optional<String> type;

    /**
     * Alias of the root element in the generated values file.
     */
    @ConfigItem(defaultValue = "app")
    public String valuesRootAlias;

    /**
     * Notes template to be generated.
     */
    @ConfigItem(defaultValue = "/NOTES.template.txt")
    public String notes;

    /**
     * Extension of the Helm tarball file. Default is `tar.gz`.
     */
    @ConfigItem(defaultValue = "tar.gz")
    public String extension;

    /**
     * Classifier to be appended into the generated Helm tarball file.
     */
    @ConfigItem
    public Optional<String> tarFileClassifier;

    /**
     * If Helm tar file is generated.
     */
    @ConfigItem(defaultValue = "false")
    public boolean createTarFile;

    /**
     * Whether to generate the `values.schema.json` file that is used to validate the Helm Chart input values.
     */
    @ConfigItem(defaultValue = "true")
    public boolean createValuesSchemaFile;

    /**
     * Whether to generate the `README.md` file that includes the Chart description and table with the configurable parameters
     * and their default values.
     */
    @ConfigItem(defaultValue = "true")
    public boolean createReadmeFile;

    /**
     * The configuration references to be mapped into the Helm values file.
     */
    @ConfigItem
    public Map<String, ValueReferenceConfig> values;

    /**
     * Helm expressions to be replaced into the generated resources.
     */
    @ConfigItem
    public Map<String, ExpressionConfig> expressions;

    /**
     * The if statements to include in the generated resources.
     */
    @ConfigItem
    public Map<String, AddIfStatementConfig> addIfStatement;

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
    @ConfigItem(defaultValue = "src/main/helm")
    public String inputDirectory;

    /**
     * The output folder in which to place the Helm generated folder. The folder is relative to the target output directory
     * in Quarkus that is also configurable using the property `quarkus.package.output-directory`.
     *
     * It also supports absolute paths.
     *
     * By default, it will be generated in the folder named "helm".
     */
    @ConfigItem(defaultValue = "helm")
    public String outputDirectory;

    /**
     * The configuration to perform Helm charts uploads to Helm repositories..
     */
    @ConfigItem
    public HelmRepository repository;

    /**
     * If enabled, the extension will check whether there are properties using system properties in the form of `${XXX}` and
     * if so, it will expose these properties as env-var values within the generated container resource.
     */
    @ConfigItem(defaultValue = "true")
    public boolean mapSystemProperties;

    /**
     * If true, the naming validation will be disabled.
     * The naming validation rejects property names that contain "-" characters.
     */
    @ConfigItem(defaultValue = "false")
    public boolean disableNamingValidation;

    /**
     * Configuration for the `values.schema.json` file.
     */
    public ValuesSchemaConfig valuesSchema;
}
