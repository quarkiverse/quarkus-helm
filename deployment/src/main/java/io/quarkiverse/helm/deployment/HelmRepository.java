package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.dekorate.utils.Strings;
import io.smallrye.config.WithDefault;

public interface HelmRepository {
    /**
     * If true, it will perform the upload to a Helm repository.
     */
    @WithDefault("false")
    boolean push();

    /**
     * The deployment target to push. Options are: `kubernetes`, `openshift`, `knative`...
     */
    @WithDefault("${quarkus.kubernetes.deployment-target}")
    Optional<String> deploymentTarget();

    /**
     * The Helm repository type. Options are: `CHARTMUSEUM`, `ARTIFACTORY`, and `NEXUS`.
     */
    Optional<HelmRepositoryType> type();

    /**
     * The Helm repository URL.
     */
    Optional<String> url();

    /**
     * The Helm repository username.
     */
    Optional<String> username();

    /**
     * The Helm repository password.
     */
    Optional<String> password();

    default String getUsername() {
        return username().filter(Strings::isNotNullOrEmpty).orElse(null);
    }

    default String getPassword() {
        return password().filter(Strings::isNotNullOrEmpty).orElse(null);
    }
}
