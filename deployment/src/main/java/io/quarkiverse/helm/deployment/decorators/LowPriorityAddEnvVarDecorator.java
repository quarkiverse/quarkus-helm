package io.quarkiverse.helm.deployment.decorators;

import static io.dekorate.ConfigReference.joinProperties;

import java.util.Arrays;
import java.util.List;

import io.dekorate.ConfigReference;
import io.dekorate.kubernetes.config.EnvBuilder;
import io.dekorate.kubernetes.decorator.AddEnvVarDecorator;
import io.dekorate.kubernetes.decorator.AddInitContainerDecorator;
import io.dekorate.kubernetes.decorator.AddSidecarDecorator;
import io.dekorate.kubernetes.decorator.ApplyApplicationContainerDecorator;
import io.dekorate.kubernetes.decorator.Decorator;
import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;

public class LowPriorityAddEnvVarDecorator extends AddEnvVarDecorator {

    private final String name;
    private final String value;

    public LowPriorityAddEnvVarDecorator(String deploymentName, String name, String value) {
        this(deploymentName, deploymentName, name, value);
    }

    public LowPriorityAddEnvVarDecorator(String deployment, String container, String name, String value) {
        super(deployment, container, new EnvBuilder().withName(name).withValue(value).build());
        this.name = name;
        this.value = value;
    }

    /**
     * It must be executed before standard AddEnvVarDecorator, so these values got overwritten.
     *
     * @return
     */
    @Override
    public Class<? extends Decorator>[] before() {
        return new Class[] { AddEnvVarDecorator.class };
    }

    @Override
    public Class<? extends Decorator>[] after() {
        return new Class[] { ResourceProvidingDecorator.class, ApplyApplicationContainerDecorator.class,
                AddSidecarDecorator.class, AddInitContainerDecorator.class };
    }

    @Override
    public List<ConfigReference> getConfigReferences() {
        return Arrays.asList(buildConfigReferenceForContainers(), buildConfigReferenceForInitContainers());
    }

    private ConfigReference buildConfigReferenceForContainers() {
        return buildConfigReferenceForEnvVarValue("containers");
    }

    private ConfigReference buildConfigReferenceForInitContainers() {
        return buildConfigReferenceForEnvVarValue("initContainers");
    }

    private ConfigReference buildConfigReferenceForEnvVarValue(String from) {
        String property = joinProperties("envs." + name);
        String path = "(metadata.name == " + getDeploymentName() + ").spec.template.spec." + from + "."
                + "(name == " + getContainerName() + ").env.(name == " + name + ").value";

        return new ConfigReference.Builder(property, path).withValue(value).build();
    }
}
