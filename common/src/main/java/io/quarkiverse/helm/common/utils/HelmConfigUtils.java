package io.quarkiverse.helm.common.utils;

import static io.github.yamlpath.utils.StringUtils.EMPTY;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.dekorate.helm.config.HelmChartConfig;
import io.dekorate.utils.Strings;

public final class HelmConfigUtils {

    private static final String ROOTLESS_PROPERTY = "@.";

    private HelmConfigUtils() {

    }

    public static String deductProperty(HelmChartConfig helmConfig, String property) {
        return deductProperty(helmConfig.getValuesRootAlias(), Stream.of(helmConfig.getDependencies())
                .map(d -> Strings.defaultIfEmpty(d.getAlias(), d.getName()))
                .collect(Collectors.toList()),
                property);
    }

    public static String deductProperty(String valuesRootAlias, List<String> dependencies, String property) {
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
        if (dependencies == null || dependencies.size() == 0) {
            return false;
        }

        String[] parts = property.split(Pattern.quote("."));
        if (parts.length <= 1) {
            return false;
        }

        String name = parts[0];
        return dependencies.stream().anyMatch(d -> Strings.equals(d, name));
    }
}
