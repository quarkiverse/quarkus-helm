package io.quarkiverse.helm.deployment;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

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
     * The Helm repository type. Options are: `CHARTMUSEUM`, `ARTIFACTORY`, `NEXUS`, and `OCI`.
     */
    Optional<HelmRepositoryType> type();

    /**
     * The Helm repository URL. For the `OCI` type, it must start with `oci://` (for example, `oci://ghcr.io/my-org/charts`)
     * and the chart is pushed to `<url>/<chart name>:<chart version>`.
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

    /**
     * If true, it will connect to the OCI registry using plain HTTP. Only used by the `OCI` type, and only meant for
     * local or testing registries.
     */
    @WithDefault("false")
    boolean insecure();

    default String getUsername() {
        return username().filter(StringUtils::isNotEmpty).orElse(null);
    }

    default String getPassword() {
        return password().filter(StringUtils::isNotEmpty).orElse(null);
    }
}
