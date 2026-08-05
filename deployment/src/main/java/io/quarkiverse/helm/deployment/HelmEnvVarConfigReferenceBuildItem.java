package io.quarkiverse.helm.deployment;

import java.util.Arrays;
import java.util.List;

import io.dekorate.ConfigReference;
import io.quarkus.builder.item.MultiBuildItem;

// Replaced Dekorate's LowPriorityAddEnvVarDecorator — synthesizes ConfigReferences for Helm values
// mapping without extending Dekorate's AddEnvVarDecorator decorator pipeline
public final class HelmEnvVarConfigReferenceBuildItem extends MultiBuildItem {

    private final String deploymentName;
    private final String containerName;
    private final String envVarName;
    private final String envVarValue;

    public HelmEnvVarConfigReferenceBuildItem(String deploymentName, String containerName, String envVarName,
            String envVarValue) {
        this.deploymentName = deploymentName;
        this.containerName = containerName;
        this.envVarName = envVarName;
        this.envVarValue = envVarValue;
    }

    public HelmEnvVarConfigReferenceBuildItem(String deploymentName, String envVarName, String envVarValue) {
        this(deploymentName, deploymentName, envVarName, envVarValue);
    }

    public List<ConfigReference> toConfigReferences() {
        return Arrays.asList(
                buildConfigReference("containers"),
                buildConfigReference("initContainers"));
    }

    private ConfigReference buildConfigReference(String from) {
        String property = kebabToCamelCase("envs." + envVarName);
        String path = "(metadata.name == " + deploymentName + ").spec.template.spec." + from + "."
                + "(name == " + containerName + ").env.(name == " + envVarName + ").value";
        return new ConfigReference.Builder(property, path).withValue(envVarValue).build();
    }

    private static String kebabToCamelCase(String str) {
        if (str == null || !str.contains("-")) {
            return str;
        }
        String[] parts = str.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
