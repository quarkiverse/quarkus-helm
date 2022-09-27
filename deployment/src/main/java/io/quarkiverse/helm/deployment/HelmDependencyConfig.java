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

    /**
     * Instruct the application to wait for the service with this name that should be installed as part of this Helm dependency.
     */
    @ConfigItem
    Optional<String> waitForService;

    /**
     * If wait for service is set, it will use this image to configure the init-containers within the deployment resource.
     */
    @ConfigItem(defaultValue = "alpine:3.16.2")
    String waitForServiceImage;

    /**
     * If wait for service is set, it will use this command to run the init-containers within the deployment resource.
     */
    @ConfigItem(defaultValue = "for i in $(seq 1 200); do nc -z -w3 ::service-name && exit 0 || sleep 3; done; exit 1")
    String waitForServiceCommandTemplate;
}
