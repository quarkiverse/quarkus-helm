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
     * The Helm repository username. For the `OCI` type, if neither the username nor the password are set and the chart
     * is pushed to the same registry as the container image (`quarkus.container-image.registry`), the
     * `quarkus.container-image.username` property is used.
     */
    Optional<String> username();

    /**
     * The Helm repository password. For the `OCI` type, if neither the username nor the password are set and the chart
     * is pushed to the same registry as the container image (`quarkus.container-image.registry`), the
     * `quarkus.container-image.password` property is used.
     */
    Optional<String> password();

    /**
     * If true, it will connect to the OCI registry using plain HTTP. Only used by the `OCI` type, and only meant for
     * local or testing registries. It's also enabled when the chart is pushed to the same registry as the container image
     * and `quarkus.container-image.insecure` is true.
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
