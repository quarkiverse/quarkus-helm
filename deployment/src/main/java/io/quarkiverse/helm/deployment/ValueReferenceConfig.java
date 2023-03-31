package io.quarkiverse.helm.deployment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ValueReferenceConfig {
    /**
     * The name of the property that will be present in the Helm values file. If the property starts with `@.`, then the
     * property won't be added under the root element in the generated `values.yaml` file.
     */
    @ConfigItem
    Optional<String> property;

    /**
     * A comma-separated list of YAMLPath expressions to map the Dekorate auto-generated properties to the final
     * Helm values file.
     */
    @ConfigItem
    Optional<List<String>> paths;

    /**
     * The profile where this value reference will be mapped to.
     * For example, if the profile is `dev`, then a `values-dev.yml` file will be created with the value.
     */
    @ConfigItem
    Optional<String> profile;

    /**
     * The value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @ConfigItem
    Optional<String> value;

    /**
     * The integer value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @ConfigItem
    Optional<Integer> valueAsInt;

    /**
     * The boolean value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @ConfigItem
    Optional<Boolean> valueAsBool;

    /**
     * The map value that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @ConfigItem
    Map<String, String> valueAsMap;

    /**
     * A list separated by comma that the property will have in the Helm values file.
     * If not set, the extension will resolve it from the generated artifacts.
     */
    @ConfigItem
    Optional<List<String>> valueAsList;

    /**
     * If not provided, it will use `{{ .Values.<root alias>.<property> }}`.
     *
     * @return The complete Helm expression to be replaced with.
     */
    @ConfigItem
    Optional<String> expression;

    /**
     * Description of the property.
     */
    @ConfigItem
    Optional<String> description;

    /**
     * Minimum value allowed for this property.
     */
    @ConfigItem
    Optional<Integer> minimum;

    /**
     * Maximum value allowed for this property.
     */
    @ConfigItem
    Optional<Integer> maximum;

    /**
     * Pattern to validate the value of this property.
     */
    @ConfigItem
    Optional<String> pattern;

    /**
     * If true, then this property is mandatory.
     */
    @ConfigItem(defaultValue = "false")
    boolean required;
}
