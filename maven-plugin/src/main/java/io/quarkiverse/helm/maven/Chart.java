package io.quarkiverse.helm.maven;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

public class Chart {
    /**
     * Name of the Helm chart. If not set, it will use the project name.
     */
    @Parameter
    public String name;

    /**
     * Project's home page of the Helm chart. It must be a URL.
     */
    @Parameter
    public String home;

    /**
     * The Helm chart list of URLs to source code for this project.
     */
    @Parameter
    public List<String> sources;

    /**
     * Version of the Helm chart.
     * If not set, it will use the application version.
     */
    @Parameter
    public String version;

    /**
     * The Helm chart single-sentence description.
     */
    @Parameter
    public String description;

    /**
     * List of keywords to add to the chart.
     */
    @Parameter
    public List<String> keywords;

    /**
     * The Helm chart list of maintainers.
     */
    @Parameter
    public List<Maintainer> maintainers;

    /**
     * Icon of the Helm chart. It must be a URL to an SVG or PNG image.
     */
    @Parameter
    public String icon;

    /**
     * The Chart API version. The default value is `v2`.
     */
    @Parameter
    public String apiVersion;

    /**
     * The condition to enable this chart.
     */
    @Parameter
    public String condition;

    /**
     * Tags of this chart.
     */
    @Parameter
    public String tags;

    /**
     * The version of the application enclosed of this chart.
     */
    @Parameter
    public String appVersion;

    /**
     * Whether this chart is deprecated.
     */
    @Parameter
    public Boolean deprecated;

    /**
     * Annotations are additional mappings uninterpreted by Helm, made available for inspection by other applications.
     */
    @Parameter
    public Map<String, String> annotations;

    /**
     * KubeVersion is a SemVer constraint specifying the version of Kubernetes required.
     */
    @Parameter
    public String kubeVersion;

    /**
     * The Helm chart list of dependencies.
     */
    @Parameter
    public List<HelmDependency> dependencies;

    /**
     * Specifies the chart type: application or library.
     */
    @Parameter
    public String type;

    /**
     * Alias of the root element in the generated values file.
     */
    @Parameter
    public String valuesRootAlias;

    /**
     * Notes template to be generated.
     */
    @Parameter
    public String notes;

    /**
     * The configuration references to be mapped into the Helm values file.
     */
    @Parameter
    public List<ValueReference> values;

    /**
     * Helm expressions to be replaced into the generated resources.
     */
    @Parameter
    public List<Expression> expressions;

    /**
     * The if statements to include in the generated resources.
     */
    @Parameter
    public List<AddIfStatement> addIfStatement;

    /**
     * Configuration for the `values.schema.json` file.
     */
    @Parameter
    public ValuesSchema valuesSchema;
}
