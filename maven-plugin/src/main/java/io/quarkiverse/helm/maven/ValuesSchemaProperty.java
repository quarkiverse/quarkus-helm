package io.quarkiverse.helm.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class ValuesSchemaProperty {
    /**
     * Name of the property to add or update. Example: `app.replicas`.
     */
    @Parameter(required = true)
    public String name;

    /**
     * Description of the property.
     */
    @Parameter
    public String description;

    /**
     * Type of the property.
     */
    @Parameter
    public String type;

    /**
     * Minimum value allowed for this property.
     */
    @Parameter
    public Integer minimum;

    /**
     * Maximum value allowed for this property.
     */
    @Parameter
    public Integer maximum;

    /**
     * Pattern to validate the value of this property.
     */
    @Parameter
    public String pattern;

    /**
     * If true, then this property is mandatory.
     */
    @Parameter
    public Boolean required;
}
