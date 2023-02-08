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
    boolean enabled;

    /**
     * Name of the Helm chart.
     * If not set, it will use the application name.
     */
    @ConfigItem
    Optional<String> name;

    /**
     * Project's home page of the Helm chart. It must be a URL.
     */
    @ConfigItem
    Optional<String> home;

    /**
     * The Helm chart list of URLs to source code for this project.
     */
    @ConfigItem
    Optional<List<String>> sources;

    /**
     * Version of the Helm chart.
     * If not set, it will use the application version.
     */
    @ConfigItem
    Optional<String> version;

    /**
     * The Helm chart single-sentence description.
     */
    @ConfigItem
    Optional<String> description;

    /**
     * List of keywords to add to the chart.
     */
    @ConfigItem
    Optional<List<String>> keywords;

    /**
     * The Helm chart list of maintainers.
     */
    @ConfigItem
    Map<String, MaintainerConfig> maintainers;

    /**
     * Icon of the Helm chart. It must be a URL to an SVG or PNG image.
     */
    @ConfigItem
    Optional<String> icon;

    /**
     * The Chart API version. The default value is `v2`.
     */
    @ConfigItem(defaultValue = "v2")
    String apiVersion;

    /**
     * The condition to enable this chart.
     */
    @ConfigItem
    Optional<String> condition;

    /**
     * Tags of this chart.
     */
    @ConfigItem
    Optional<String> tags;

    /**
     * The version of the application enclosed of this chart.
     */
    @ConfigItem
    Optional<String> appVersion;

    /**
     * Whether this chart is deprecated.
     */
    @ConfigItem
    Optional<Boolean> deprecated;

    /**
     * Annotations are additional mappings uninterpreted by Helm, made available for inspection by other applications.
     */
    @ConfigItem
    Map<String, String> annotations;

    /**
     * KubeVersion is a SemVer constraint specifying the version of Kubernetes required.
     */
    @ConfigItem
    Optional<String> kubeVersion;

    /**
     * The Helm chart list of dependencies.
     */
    @ConfigItem
    Map<String, HelmDependencyConfig> dependencies;

    /**
     * Specifies the chart type: application or library.
     */
    @ConfigItem
    Optional<String> type;

    /**
     * Alias of the root element in the generated values file.
     */
    @ConfigItem(defaultValue = "app")
    String valuesRootAlias;

    /**
     * Notes template to be generated.
     */
    @ConfigItem(defaultValue = "/NOTES.template.txt")
    String notes;

    /**
     * Extension of the Helm tarball file. Default is `tar.gz`.
     */
    @ConfigItem(defaultValue = "tar.gz")
    String extension;

    /**
     * Classifier to be appended into the generated Helm tarball file.
     */
    @ConfigItem
    Optional<String> tarFileClassifier;

    /**
     * If Helm tar file is generated.
     */
    @ConfigItem(defaultValue = "false")
    boolean createTarFile;

    /**
     * The configuration references to be mapped into the Helm values file.
     */
    @ConfigItem
    Map<String, ValueReferenceConfig> values;

    /**
     * Helm expressions to be replaced into the generated resources.
     */
    @ConfigItem
    Map<String, ExpressionConfig> expressions;

    /**
     * The if statements to include in the generated resources.
     */
    @ConfigItem
    Map<String, AddIfStatementConfig> addIfStatement;

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
}
