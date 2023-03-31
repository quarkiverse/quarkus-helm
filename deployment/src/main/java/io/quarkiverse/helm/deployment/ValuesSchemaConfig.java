package io.quarkiverse.helm.deployment;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ValuesSchemaConfig {
    /**
     * Title of the values schema json file.
     */
    @ConfigItem(defaultValue = "Values")
    public String title;

    /**
     * List of properties to add/modify from the values configuration.
     */
    public Map<String, ValuesSchemaPropertyConfig> properties;
}
