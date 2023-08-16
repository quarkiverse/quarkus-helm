package io.quarkiverse.helm.maven;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

public class ValueReference {
    /**
     * The name of the property that will be present in the Helm values file. If the property starts with `@.`, then the
     * property won't be added under the root element in the generated `values.yaml` file.
     */
    @Parameter(required = true)
    public String property;

    /**
     * A comma-separated list of YAMLPath expressions to map the Dekorate auto-generated properties to the final
     * Helm values file.
     */
    @Parameter
    public List<String> paths;

    /**
     * The profile where this value reference will be mapped to.
     * For example, if the profile is `dev`, then a `values.dev.yml` file will be created with the value.
     */
    @Parameter
    public String profile;

    /**
     * The value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @Parameter
    public String value;

    /**
     * The integer value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @Parameter
    public Integer valueAsInt;

    /**
     * The boolean value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @Parameter
    public Boolean valueAsBool;

    /**
     * The map value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @Parameter
    public Map<String, String> valueAsMap;

    /**
     * A list separated by comma that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @Parameter
    public List<String> valueAsList;

    /**
     * If not provided, it will use `{{ .Values.<root alias>.<property> }}`.
     *
     * @return The complete Helm expression to be replaced with.
     */
    @Parameter
    public String expression;

    /**
     * Description of the property.
     */
    @Parameter
    public String description;

    /**
     * Minimum value allowed for this property.
     */
    @Parameter
    public Integer minimum;

    /**
     * Maximum value allowed for this property.
     */
    @Parameter
    public Integer maximum;

    /**
     * Pattern to validate the value of this property.
     */
    @Parameter
    public String pattern;

    /**
     * If true, then this property is mandatory.
     */
    @Parameter
    public Boolean required;
}
