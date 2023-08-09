package io.quarkiverse.helm.deployment;

import java.util.Map;

import io.smallrye.config.WithDefault;

public interface ValuesSchemaConfig {
    /**
     * Title of the values schema json file.
     */
    @WithDefault("Values")
    String title();

    /**
     * List of properties to add/modify from the values configuration.
     */
    Map<String, ValuesSchemaPropertyConfig> properties();
}
