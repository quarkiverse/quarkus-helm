package io.quarkiverse.helm.deployment.rules;

import java.util.List;
import java.util.Optional;

import io.dekorate.ConfigReference;

public final class ConfigReferenceStrategyManager {

    private static final List<ConfigReferenceStrategy> CONFIG_REFERENCE_STRATEGIES = List.of(
            new HttpGetPortConfigReferenceStrategy());

    private ConfigReferenceStrategyManager() {

    }

    public static ConfigReference visit(ConfigReference configReference) {
        for (ConfigReferenceStrategy strategy : CONFIG_REFERENCE_STRATEGIES) {
            for (int i = 0; i < configReference.getPaths().length; i++) {
                Optional<String> newPath = strategy.visitPath(configReference, configReference.getPaths()[i]);
                if (newPath.isPresent()) {
                    configReference.getPaths()[i] = newPath.get();
                }
            }
        }

        return configReference;
    }
}
