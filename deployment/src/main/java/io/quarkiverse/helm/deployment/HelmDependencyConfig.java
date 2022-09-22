package io.quarkiverse.helm.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class HelmDependencyConfig {
    /**
     * Name of the dependency.
     */
    @ConfigItem
    String name;

    /**
     * Version of the dependency.
     */
    @ConfigItem
    String version;

    /**
     * Repository of the dependency.
     */
    @ConfigItem
    String repository;

    /**
     * Alias of the dependency.
     */
    @ConfigItem
    Optional<String> alias;

    /**
     * Dependency condition.
     */
    @ConfigItem
    Optional<String> condition;

    /**
     * Dependency tags.
     */
    @ConfigItem
    Optional<List<String>> tags;
}
