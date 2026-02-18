package io.quarkiverse.helm.deployment;

import java.util.Optional;

public interface MaintainerConfig {
    /**
     * Name of the maintainer.
     */
    Optional<String> name();

    /**
     * Email of the maintainer.
     */
    Optional<String> email();

    /**
     * URL profile of the maintainer.
     */
    Optional<String> url();
}
