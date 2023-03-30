package io.quarkiverse.helm.deployment.utils;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.dekorate.helm.config.HelmChartConfig;
import io.dekorate.utils.Strings;

public final class HelmConfigUtils {
    private HelmConfigUtils() {

    }

    public static String deductProperty(HelmChartConfig helmConfig, String property) {
        if (!startWithDependencyPrefix(property, helmConfig.getDependencies())) {
            String prefix = helmConfig.getValuesRootAlias() + ".";
            if (!property.startsWith(prefix)) {
                property = prefix + property;
            }
        }

        return property;
    }

    private static boolean startWithDependencyPrefix(String property, io.dekorate.helm.config.HelmDependency[] dependencies) {
        if (dependencies == null || dependencies.length == 0) {
            return false;
        }

        String[] parts = property.split(Pattern.quote("."));
        if (parts.length <= 1) {
            return false;
        }

        String name = parts[0];
        return Stream.of(dependencies)
                .map(d -> Strings.defaultIfEmpty(d.getAlias(), d.getName()))
                .anyMatch(d -> Strings.equals(d, name));
    }
}
