package io.quarkiverse.helm.deployment;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import io.quarkus.container.image.deployment.ContainerImageConfig;

/**
 * The registry the container image is pushed to (`quarkus.container-image.*`). When the Helm chart is pushed to the same
 * OCI registry, its credentials and `insecure` flag are used as defaults for the Helm repository.
 */
interface ContainerImageRegistry {

    Optional<String> registry();

    Optional<String> username();

    Optional<String> password();

    boolean insecure();

    static ContainerImageRegistry from(ContainerImageConfig config) {
        return new ContainerImageRegistry() {
            @Override
            public Optional<String> registry() {
                return config.registry();
            }

            @Override
            public Optional<String> username() {
                return config.username();
            }

            @Override
            public Optional<String> password() {
                return config.password();
            }

            @Override
            public boolean insecure() {
                return config.insecure();
            }
        };
    }

    default HelmRepository applyDefaultsTo(HelmRepository repository) {
        if (repository.type().orElse(null) != HelmRepositoryType.OCI || !sameRegistryAs(repository)) {
            return repository;
        }

        // credentials are only taken as a pair, and only when none of the Helm repository ones are set
        boolean useCredentials = repository.getUsername() == null && repository.getPassword() == null
                && username().filter(StringUtils::isNotEmpty).isPresent()
                && password().filter(StringUtils::isNotEmpty).isPresent();
        if (useCredentials) {
            Logger.getLogger(ContainerImageRegistry.class).info("Using the container image credentials (the properties "
                    + "`quarkus.container-image.username` and `quarkus.container-image.password`) to push the Helm chart "
                    + "to '" + registry().get() + "'.");
        }
        Optional<String> username = useCredentials ? username() : repository.username();
        Optional<String> password = useCredentials ? password() : repository.password();
        boolean insecure = repository.insecure() || insecure();
        return new HelmRepository() {
            @Override
            public boolean push() {
                return repository.push();
            }

            @Override
            public Optional<String> deploymentTarget() {
                return repository.deploymentTarget();
            }

            @Override
            public Optional<HelmRepositoryType> type() {
                return repository.type();
            }

            @Override
            public Optional<String> url() {
                return repository.url();
            }

            @Override
            public Optional<String> username() {
                return username;
            }

            @Override
            public Optional<String> password() {
                return password;
            }

            @Override
            public boolean insecure() {
                return insecure;
            }
        };
    }

    private boolean sameRegistryAs(HelmRepository repository) {
        if (registry().isEmpty() || repository.url().isEmpty()
                || !repository.url().get().startsWith(OciHelmChartPusher.OCI_SCHEME)) {
            return false;
        }
        String chartRegistry = repository.url().get().substring(OciHelmChartPusher.OCI_SCHEME.length()).split("/", 2)[0];
        return chartRegistry.equalsIgnoreCase(registry().get());
    }
}
