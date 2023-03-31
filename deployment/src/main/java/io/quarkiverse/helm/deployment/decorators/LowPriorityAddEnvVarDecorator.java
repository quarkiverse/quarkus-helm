package io.quarkiverse.helm.deployment.decorators;

import static io.dekorate.ConfigReference.joinProperties;

import java.util.Arrays;
import java.util.List;

import io.dekorate.ConfigReference;
import io.dekorate.kubernetes.config.Env;
import io.dekorate.kubernetes.config.EnvBuilder;
import io.dekorate.kubernetes.decorator.AddEnvVarDecorator;
import io.dekorate.kubernetes.decorator.Decorator;

public class LowPriorityAddEnvVarDecorator extends AddEnvVarDecorator {

    private final String name;
    private final String value;

    /**
     * This constructor should never be used.
     *
     * @param env
     */
    public LowPriorityAddEnvVarDecorator(Env env) {
        this(ANY, env.getName(), env.getValue());
    }

    public LowPriorityAddEnvVarDecorator(String deploymentName, String name, String value) {
        super(deploymentName, deploymentName, new EnvBuilder().withName(name).withValue(value).build());
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
    public List<ConfigReference> getConfigReferences() {
        return Arrays.asList(buildConfigReferenceForEnvValue());
    }

    private ConfigReference buildConfigReferenceForEnvValue() {
        String property = joinProperties("envs." + name);
        String envFilter = ".env.(name == " + name + ").value";
        String path = "(metadata.name == " + getDeploymentName() + ").spec.template.spec.containers.(name == "
                + getContainerName() + ")" + envFilter;

        return new ConfigReference.Builder(property, path).withValue(value).build();
    }
}
