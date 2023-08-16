package io.quarkiverse.helm.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class Expression {

    /**
     * The YAMLPath path where to include the template within the resource.
     */
    @Parameter(required = true)
    public String path;

    /**
     * The expression template to include.
     */
    @Parameter(required = true)
    public String expression;
}
