package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface AddIfStatementConfig {

    /**
     * The property to use in the if statement. If the property starts with `@.`, then the property won't be added under the
     * root element in the generated `values.yaml` file.
     */
    Optional<String> property();

    /**
     * The resource kind where to include the if statement.
     */
    Optional<String> onResourceKind();

    /**
     * The resource kind where to include the if statement.
     */
    Optional<String> onResourceName();

    /**
     * The default value of the property
     */
    @WithDefault("true")
    boolean withDefaultValue();

    /**
     * Provide custom description of the `add-if-statement` property.
     */
    @WithDefault("Determine if the resource should be installed or not.")
    String description();
}
