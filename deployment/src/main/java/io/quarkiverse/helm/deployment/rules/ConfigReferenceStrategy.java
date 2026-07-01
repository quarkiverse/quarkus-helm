package io.quarkiverse.helm.deployment.rules;

import java.util.Optional;

import io.dekorate.ConfigReference;

public interface ConfigReferenceStrategy {
    default Optional<String> visitPath(ConfigReference configReference, String path) {
        return Optional.empty();
    }
}
