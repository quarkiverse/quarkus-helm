package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ValuesSchemaPropertyConfig {
    /**
     * Name of the property to add or update. Example: `app.replicas`.
     */
    @ConfigItem
    public Optional<String> name;

    /**
     * Description of the property.
     */
    @ConfigItem
    public Optional<String> description;

    /**
     * Type of the property.
     */
    @ConfigItem(defaultValue = "string")
    public String type;

    /**
     * Minimum value allowed for this property.
     */
    @ConfigItem
    public Optional<Integer> minimum;

    /**
     * Maximum value allowed for this property.
     */
    @ConfigItem
    public Optional<Integer> maximum;

    /**
     * Pattern to validate the value of this property.
     */
    @ConfigItem
    public Optional<String> pattern;

    /**
     * If true, then this property is mandatory.
     */
    @ConfigItem(defaultValue = "false")
    public boolean required;
}
