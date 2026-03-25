package io.quarkiverse.helm.deployment.utils;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.quarkiverse.helm.deployment.HelmChartConfig;

public final class HelmConfigUtils {

    private static final String ROOTLESS_PROPERTY = "@.";

    private HelmConfigUtils() {

    }

    public static String deductProperty(HelmChartConfig helmConfig, String property) {
        return deductProperty(helmConfig.valuesRootAlias(), helmConfig.dependencies().entrySet().stream()
                .map(entry -> entry.getValue().alias().orElseGet(() -> entry.getValue().name().orElse(entry.getKey())))
                .collect(Collectors.toList()),
                property);
    }

    private static String deductProperty(String valuesRootAlias, List<String> dependencies, String property) {
        if (property.startsWith(ROOTLESS_PROPERTY)) {
            return property.replaceFirst(Pattern.quote(ROOTLESS_PROPERTY), EMPTY);
        }

        if (!startWithDependencyPrefix(property, dependencies)) {
            String prefix = valuesRootAlias + ".";
            if (!property.startsWith(prefix)) {
                property = prefix + property;
            }
        }

        return property;
    }

    private static boolean startWithDependencyPrefix(String property, List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return false;
        }

        String[] parts = property.split(Pattern.quote("."));
        if (parts.length <= 1) {
            return false;
        }

        String name = parts[0];
        return dependencies.stream().anyMatch(d -> Objects.equals(d, name));
    }
}
