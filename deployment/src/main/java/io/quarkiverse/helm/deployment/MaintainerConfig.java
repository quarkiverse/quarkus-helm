package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class MaintainerConfig {
    /**
     * Name of the maintainer.
     */
    @ConfigItem
    String name;

    /**
     * Email of the maintainer.
     */
    @ConfigItem
    Optional<String> email;

    /**
     * URL profile of the maintainer.
     */
    @ConfigItem
    Optional<String> url;
}
