package io.quarkiverse.helm.maven;

import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

public class ValuesSchema {

    /**
     * Title of the values schema json file.
     */
    @Parameter
    public String title;

    /**
     * List of properties to add/modify from the values configuration.
     */
    @Parameter
    public List<ValuesSchemaProperty> properties;
}
