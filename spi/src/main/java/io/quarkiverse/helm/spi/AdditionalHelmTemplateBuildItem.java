package io.quarkiverse.helm.spi;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

public final class AdditionalHelmTemplateBuildItem extends MultiBuildItem {

    private final String name;
    private final byte[] content;
    private final String deploymentTarget;
    private final List<ReplacedResource> replacedResources;

    public AdditionalHelmTemplateBuildItem(String name, byte[] content, String deploymentTarget) {
        this(name, content, deploymentTarget, List.of());
    }

    public AdditionalHelmTemplateBuildItem(String name, byte[] content, String deploymentTarget,
            List<ReplacedResource> replacedResources) {
        this.name = name;
        this.content = content;
        this.deploymentTarget = deploymentTarget;
        this.replacedResources = replacedResources;
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

    public List<ReplacedResource> getReplacedResources() {
        return replacedResources;
    }

    /**
     * Identifies a base generated resource (by Kubernetes {@code kind} and {@code metadata.name}) that
     * this additional template supersedes.
     **/
    public record ReplacedResource(String kind, String name) {
    }
}
