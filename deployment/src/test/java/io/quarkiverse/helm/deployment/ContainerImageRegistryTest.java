package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ContainerImageRegistryTest {

    private static final ContainerImageRegistry IMAGE_REGISTRY = containerImage(Optional.of("quay.io"),
            Optional.of("image-user"), Optional.of("image-secret"), true);

    @Test
    void usesContainerImageCredentialsAndInsecureForTheSameRegistry() {
        HelmRepository repository = IMAGE_REGISTRY.applyDefaultsTo(
                repository(HelmRepositoryType.OCI, "oci://quay.io/my-org/charts", null, null, false));

        assertEquals("image-user", repository.getUsername());
        assertEquals("image-secret", repository.getPassword());
        assertTrue(repository.insecure());
        assertEquals("oci://quay.io/my-org/charts", repository.url().get());
    }

    @Test
    void keepsHelmCredentialsWhenSet() {
        HelmRepository repository = IMAGE_REGISTRY.applyDefaultsTo(
                repository(HelmRepositoryType.OCI, "oci://quay.io/my-org/charts", "helm-user", "helm-secret", false));

        assertEquals("helm-user", repository.getUsername());
        assertEquals("helm-secret", repository.getPassword());
    }

    @Test
    void doesNotMixHelmAndContainerImageCredentials() {
        // only the username is set: the validation must still fail instead of taking the password from the image
        HelmRepository repository = IMAGE_REGISTRY.applyDefaultsTo(
                repository(HelmRepositoryType.OCI, "oci://quay.io/my-org/charts", "helm-user", null, false));

        assertEquals("helm-user", repository.getUsername());
        assertEquals(null, repository.getPassword());
    }

    @Test
    void ignoresContainerImageForAnotherRegistry() {
        HelmRepository original = repository(HelmRepositoryType.OCI, "oci://ghcr.io/my-org/charts", null, null, false);

        assertSame(original, IMAGE_REGISTRY.applyDefaultsTo(original));
    }

    @Test
    void ignoresContainerImageForNonOciRepositories() {
        HelmRepository original = repository(HelmRepositoryType.CHARTMUSEUM, "http://quay.io/api/charts", null, null,
                false);

        assertSame(original, IMAGE_REGISTRY.applyDefaultsTo(original));
    }

    @Test
    void ignoresContainerImageWithoutRegistry() {
        ContainerImageRegistry noRegistry = containerImage(Optional.empty(), Optional.of("image-user"),
                Optional.of("image-secret"), true);
        HelmRepository original = repository(HelmRepositoryType.OCI, "oci://quay.io/my-org/charts", null, null, false);

        assertSame(original, noRegistry.applyDefaultsTo(original));
    }

    @Test
    void onlyAppliesInsecureWhenContainerImageHasNoCredentials() {
        ContainerImageRegistry insecureOnly = containerImage(Optional.of("localhost:5000"), Optional.empty(),
                Optional.empty(), true);
        HelmRepository repository = insecureOnly.applyDefaultsTo(
                repository(HelmRepositoryType.OCI, "oci://localhost:5000/charts", null, null, false));

        assertTrue(repository.insecure());
        assertEquals(null, repository.getUsername());
        assertFalse(repository.username().isPresent());
    }

    static ContainerImageRegistry containerImage(Optional<String> registry, Optional<String> username,
            Optional<String> password, boolean insecure) {
        return new ContainerImageRegistry() {
            @Override
            public Optional<String> registry() {
                return registry;
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

    private static HelmRepository repository(HelmRepositoryType type, String url, String username, String password,
            boolean insecure) {
        return new HelmRepository() {
            @Override
            public boolean push() {
                return true;
            }

            @Override
            public Optional<String> deploymentTarget() {
                return Optional.empty();
            }

            @Override
            public Optional<HelmRepositoryType> type() {
                return Optional.ofNullable(type);
            }

            @Override
            public Optional<String> url() {
                return Optional.ofNullable(url);
            }

            @Override
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            @Override
            public Optional<String> password() {
                return Optional.ofNullable(password);
            }

            @Override
            public boolean insecure() {
                return insecure;
            }
        };
    }
}
