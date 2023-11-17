package io.quarkiverse.helm.deployment.rules;

import java.util.Optional;

import io.dekorate.ConfigReference;

/**
 * Strategy to select the correct port in the HTTP probes.
 * For example, having the config reference with property `ports.custom` with port `8888`, and the probe is
 * configured to use:
 *
 * <code>
 *         livenessProbe:
 *             failureThreshold: 3
 *             httpGet:
 *               path: /q/health/live
 *               port: 8888
 *               scheme: HTTP
 * </code>
 *
 * Then, the probe should use the `ports.custom`, otherwise it should not be applied.
 */
public class HttpGetPortConfigReferenceStrategy implements ConfigReferenceStrategy {

    private static final String HTTP_GET_PORT = "httpGet.port";
    private static final String HTTP_GET_PORT_REPLACEMENT = "httpGet.(port == %s).port";

    @Override
    public Optional<String> visitPath(ConfigReference configReference, String path) {
        if (path.endsWith(HTTP_GET_PORT)) {
            return Optional.of(path.replace(HTTP_GET_PORT,
                    String.format(HTTP_GET_PORT_REPLACEMENT, configReference.getValue())));
        }

        return Optional.empty();
    }
}
