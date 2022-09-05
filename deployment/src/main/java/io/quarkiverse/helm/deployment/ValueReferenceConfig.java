package io.quarkiverse.helm.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ValueReferenceConfig {
    /**
     * The name of the property that will be present in the Helm values file.
     */
    @ConfigItem
    String property;

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
}
