package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.dekorate.ConfigReference;

class QuarkusHelmWriterSessionListenerTest {

    @Test
    void shouldTreatPlainEnvValuesAsHelmValues() {
        ConfigReference reference = new ConfigReference.Builder("app.envs.TEST",
                new String[] { "(kind == Deployment).spec.template.spec.containers.(name == app).env.(name == TEST).value" })
                        .withValue("plain-value")
                        .build();

        assertTrue(QuarkusHelmWriterSessionListener.isPlainEnvironmentProperty(reference));
    }

    @Test
    void shouldIgnoreSecretBackedEnvValues() {
        ConfigReference reference = new ConfigReference.Builder("app.envs.TEST",
                new String[] {
                        "(kind == Deployment).spec.template.spec.containers.(name == app).env.(name == TEST).valueFrom.secretKeyRef.key" })
                                .withValue("test-key")
                                .build();

        assertFalse(QuarkusHelmWriterSessionListener.isPlainEnvironmentProperty(reference));
    }

    @Test
    void shouldIgnoreConfigMapBackedEnvValues() {
        ConfigReference reference = new ConfigReference.Builder("app.envs.TEST",
                new String[] {
                        "(kind == Deployment).spec.template.spec.containers.(name == app).env.(name == TEST).valueFrom.configMapKeyRef.name" })
                                .withValue("common-config")
                                .build();

        assertFalse(QuarkusHelmWriterSessionListener.isPlainEnvironmentProperty(reference));
    }
}
