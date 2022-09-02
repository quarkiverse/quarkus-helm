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
     * The Chart API version. The default value is `v2`.
     */
    @ConfigItem(defaultValue = "v2")
    String apiVersion;

    /**
     * Name of the Helm chart.
     * If not set, it will use the application name.
     */
    @ConfigItem
    Optional<String> name;

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
     * Icon of the Helm chart. It must be a URL to an SVG or PNG image.
     */
    @ConfigItem
    Optional<String> icon;

    /**
     * Project's home page of the Helm chart. It must be a URL.
     */
    @ConfigItem
    Optional<String> home;

    /**
     * List of keywords to add to the chart.
     */
    @ConfigItem
    Optional<List<String>> keywords;

    /**
     * The Helm chart list of URLs to source code for this project.
     */
    @ConfigItem
    Optional<List<String>> sources;

    /**
     * Extension of the Helm tarball file. Default is `tar.gz`.
     */
    @ConfigItem(defaultValue = "tar.gz")
    String extension;

    /**
     * If Helm tar file is generated.
     */
    @ConfigItem(defaultValue = "false")
    boolean createTarFile;

    /**
     * The Helm chart list of maintainers.
     */
    @ConfigItem
    Map<String, MaintainerConfig> maintainers;

    /**
     * The Helm chart list of dependencies.
     */
    @ConfigItem
    Map<String, HelmDependencyConfig> dependencies;

    /**
     * The configuration references to be mapped into the Helm values file.
     */
    @ConfigItem
    Map<String, ValueReferenceConfig> values;

    /**
     * The output folder in which to place the Helm generated folder. By default, it will be generated in the folder named
     * "helm".
     */
    @ConfigItem(defaultValue = "helm")
    public String outputDirectory;
}
