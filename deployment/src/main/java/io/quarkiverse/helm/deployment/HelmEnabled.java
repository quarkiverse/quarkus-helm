package io.quarkiverse.helm.deployment;

import java.util.function.BooleanSupplier;

public class HelmEnabled implements BooleanSupplier {

    private final HelmChartConfig config;

    public HelmEnabled(HelmChartConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.enabled;
    }
}
