package io.quarkiverse.helm.deployment;

import java.util.Optional;

public interface ExpressionConfig {

    /**
     * The YAMLPath path where to include the template within the resource.
     */
    String path();

    /**
     * The expression template to include.
     */
    String expression();

    /**
     * If true, the missing fields of the path will be created in the resources. If false, the expression will only replace
     * existing fields.
     * If not set, the missing fields will only be created when the path starts with a filter expression, for example:
     * `(kind == Deployment).spec.template.spec.restartPolicy`.
     */
    Optional<Boolean> createIfMissing();
}
