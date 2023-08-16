package io.quarkiverse.helm.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class AddIfStatement {

    /**
     * The property to use in the if statement. If the property starts with `@.`, then the property won't be added under the
     * root element in the generated `values.yaml` file.
     */
    @Parameter(required = true)
    public String property;

    /**
     * The resource kind where to include the if statement.
     */
    @Parameter
    public String onResourceKind;

    /**
     * The resource kind where to include the if statement.
     */
    @Parameter
    public String onResourceName;

    /**
     * The default value of the property
     */
    @Parameter
    public Boolean withDefaultValue;

    /**
     * Provide custom description of the `add-if-statement` property.
     */
    @Parameter
    public String description;
}
