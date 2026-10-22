package io.quarkiverse.helm.deployment.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.yamlpath.YamlExpressionParser;
import io.github.yamlpath.YamlPath;

class YamlExpressionParserUtilsTest {

    private static final String RESOURCES = "---\n" +
            "apiVersion: apps/v1\n" +
            "kind: Deployment\n" +
            "metadata:\n" +
            "  name: app\n" +
            "spec:\n" +
            "  template:\n" +
            "    spec:\n" +
            "      containers:\n" +
            "        - name: app\n" +
            "          image: app:latest\n" +
            "---\n" +
            "apiVersion: v1\n" +
            "kind: Service\n" +
            "metadata:\n" +
            "  name: app\n" +
            "spec:\n" +
            "  type: ClusterIP\n";

    @Test
    void shouldCreateMissingFieldWhenPathStartsWithFilter() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser, "(kind == Deployment).spec.template.spec.restartPolicy",
                "{{ .Values.app.restartPolicy }}", Optional.empty());

        assertTrue(dump(parser, "(kind == Deployment).spec.template.spec.restartPolicy").contains(".Values.app.restartPolicy"));
        assertNull(YamlExpressionParserUtils.read(parser, "(kind == Service).spec.template"));
    }

    @Test
    void shouldCreateMissingNestedFieldsWithEscapedKeys() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser,
                "(kind == Deployment).spec.template.metadata.annotations.'checksum/config'",
                "{{ include \"foo\" . | sha256sum }}", Optional.empty());

        Object annotations = YamlExpressionParserUtils.read(parser, "(kind == Deployment).spec.template.metadata.annotations");
        assertTrue(annotations instanceof Map);
        assertTrue(((Map<?, ?>) annotations).containsKey("checksum/config"));
    }

    @Test
    void shouldCreateMissingFieldInsideFilteredListItem() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser,
                "(kind == Deployment).spec.template.spec.containers.(name == app).imagePullPolicy",
                "{{ .Values.app.pullPolicy }}", Optional.empty());

        assertTrue(dump(parser, "(kind == Deployment).spec.template.spec.containers.(name == app).imagePullPolicy")
                .contains(".Values.app.pullPolicy"));
    }

    @Test
    void shouldNotCreateMissingFieldWhenPathDoesNotStartWithFilter() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser, "metadata.annotations.team", "{{ .Values.team }}",
                Optional.empty());

        assertNull(YamlExpressionParserUtils.read(parser, "metadata.annotations"));
    }

    @Test
    void shouldCreateMissingFieldWhenExplicitlyEnabled() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser, "metadata.annotations.team", "{{ .Values.team }}",
                Optional.of(true));

        assertTrue(dump(parser, "(kind == Deployment).metadata.annotations.team").contains(".Values.team"));
        assertTrue(dump(parser, "(kind == Service).metadata.annotations.team").contains(".Values.team"));
    }

    @Test
    void shouldNotCreateMissingFieldWhenExplicitlyDisabled() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser, "(kind == Deployment).spec.template.spec.restartPolicy",
                "{{ .Values.app.restartPolicy }}", Optional.of(false));

        assertNull(YamlExpressionParserUtils.read(parser, "(kind == Deployment).spec.template.spec.restartPolicy"));
    }

    @Test
    void shouldNotCreateFieldsWhenFilterDoesNotMatch() {
        YamlExpressionParser parser = parser();

        YamlExpressionParserUtils.readAndSet(parser, "(kind == StatefulSet).spec.template.spec.restartPolicy",
                "{{ .Values.app.restartPolicy }}", Optional.empty());

        assertFalse(parser.dumpAsString().contains("restartPolicy"));
    }

    private static YamlExpressionParser parser() {
        try {
            return YamlPath.from(new ByteArrayInputStream(RESOURCES.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String dump(YamlExpressionParser parser, String path) {
        return String.valueOf(YamlExpressionParserUtils.read(parser, path));
    }
}
