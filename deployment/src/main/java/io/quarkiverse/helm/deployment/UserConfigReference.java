package io.quarkiverse.helm.deployment;

import io.dekorate.ConfigReference;

/**
 * This class represents a user entered config reference where the value has higher priority that the config references coming
 * from the decorators.
 */
public class UserConfigReference extends ConfigReference {
    public UserConfigReference(String property, String[] paths, Object value, String expression, String profile) {
        super(property, paths, value, expression, profile);
    }
}
