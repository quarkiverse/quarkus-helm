package io.quarkiverse.helm.deployment;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ExpressionConfig {

    /**
     * The YAMLPath path where to include the template within the resource.
     */
    @ConfigItem
    String path;

    /**
     * The expression template to include.
     */
    @ConfigItem
    String expression;
}
