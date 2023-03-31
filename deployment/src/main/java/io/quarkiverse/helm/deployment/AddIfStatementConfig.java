package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class AddIfStatementConfig {

    /**
     * The property to use in the if statement. If the property starts with `@.`, then the property won't be added under the
     * root element in the generated `values.yaml` file.
     */
    @ConfigItem
    Optional<String> property;

    /**
     * The resource kind where to include the if statement.
     */
    @ConfigItem
    Optional<String> onResourceKind;

    /**
     * The resource kind where to include the if statement.
     */
    @ConfigItem
    Optional<String> onResourceName;

    /**
     * The default value of the property
     */
    @ConfigItem(defaultValue = "true")
    boolean withDefaultValue;

    /**
     * Provide custom description of the `add-if-statement` property.
     */
    @ConfigItem(defaultValue = "Determine if the resource should be installed or not.")
    String description;
}
