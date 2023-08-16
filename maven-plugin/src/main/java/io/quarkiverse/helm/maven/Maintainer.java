package io.quarkiverse.helm.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class Maintainer {
    /**
     * Name of the maintainer.
     */
    @Parameter(required = true)
    public String name;

    /**
     * Email of the maintainer.
     */
    @Parameter
    public String email;

    /**
     * URL profile of the maintainer.
     */
    @Parameter
    public String url;
}
