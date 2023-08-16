package io.quarkiverse.helm.maven;

import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

public class HelmDependency {
    /**
     * Name of the dependency.
     */
    @Parameter(required = true)
    public String name;

    /**
     * Version of the dependency.
     */
    @Parameter
    public String version;

    /**
     * Repository of the dependency.
     */
    @Parameter
    public String repository;

    /**
     * Dependency condition. If the property starts with `@.`, then the property won't be added under the root element in the
     * generated `values.yaml` file.
     */
    @Parameter
    public String condition;

    /**
     * Dependency tags.
     */
    @Parameter
    public List<String> tags;

    /**
     * Whether this dependency should be loaded.
     */
    @Parameter
    public Boolean enabled;

    /**
     * Alias of the dependency.
     */
    @Parameter
    public String alias;
}
