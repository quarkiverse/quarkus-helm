package io.quarkiverse.helm.spi;

import io.quarkus.builder.item.MultiBuildItem;

public final class AdditionalHelmTemplateBuildItem extends MultiBuildItem {

    private final String name;
    private final byte[] content;
    private final String deploymentTarget;

    public AdditionalHelmTemplateBuildItem(String name, byte[] content, String deploymentTarget) {
        this.name = name;
        this.content = content;
        this.deploymentTarget = deploymentTarget;
    }

    public String getName() {
        return name;
    }

    public byte[] getContent() {
        return content;
    }

    public String getDeploymentTarget() {
        return deploymentTarget;
    }
}
