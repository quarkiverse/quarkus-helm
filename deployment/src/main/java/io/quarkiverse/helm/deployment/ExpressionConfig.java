package io.quarkiverse.helm.deployment;

public interface ExpressionConfig {

    /**
     * The YAMLPath path where to include the template within the resource.
     */
    String path();

    /**
     * The expression template to include.
     */
    String expression();
}
