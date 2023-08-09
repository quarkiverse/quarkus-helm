package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface ValuesSchemaPropertyConfig {
    /**
     * Name of the property to add or update. Example: `app.replicas`.
     */
    Optional<String> name();

    /**
     * Description of the property.
     */
    Optional<String> description();

    /**
     * Type of the property.
     */
    @WithDefault("string")
    String type();

    /**
     * Minimum value allowed for this property.
     */
    Optional<Integer> minimum();

    /**
     * Maximum value allowed for this property.
     */
    Optional<Integer> maximum();

    /**
     * Pattern to validate the value of this property.
     */
    Optional<String> pattern();

    /**
     * If true, then this property is mandatory.
     */
    @WithDefault("false")
    boolean required();
}
