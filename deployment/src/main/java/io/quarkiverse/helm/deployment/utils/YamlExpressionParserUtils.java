package io.quarkiverse.helm.deployment.utils;

import static io.github.yamlpath.utils.PathUtils.DOT;
import static io.github.yamlpath.utils.PathUtils.ESCAPE;
import static io.github.yamlpath.utils.PathUtils.INDEX_OPEN;
import static io.github.yamlpath.utils.PathUtils.PARENTHESIS_CLOSE;
import static io.github.yamlpath.utils.PathUtils.PARENTHESIS_OPEN;
import static io.github.yamlpath.utils.PathUtils.WILDCARD;
import static io.github.yamlpath.utils.PathUtils.normalize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import io.dekorate.ConfigReference;
import io.github.yamlpath.YamlExpressionParser;

public final class YamlExpressionParserUtils {

    public static final String SEPARATOR_TOKEN = ":LINE_SEPARATOR:";
    public static final String SEPARATOR_QUOTES = ":DOUBLE_QUOTES";
    public static final String START_EXPRESSION_TOKEN = ":START:";
    public static final String END_EXPRESSION_TOKEN = ":END:";
    public static final String START_TAG = "{{";
    public static final String END_TAG = "}}";
    public static final String EMPTY = "";
    public static final String VALUES_START_TAG = START_TAG + " .Values.";
    public static final String VALUES_END_TAG = " " + END_TAG;
    public static final String QUOTE_CONVERSION = " | quote";

    private YamlExpressionParserUtils() {

    }

    public static void set(YamlExpressionParser parser, String path, String expression) {
        parser.write(path, adaptExpression(expression));
    }

    public static Object read(YamlExpressionParser parser, String path) {
        Set<Object> found = parser.read(path);
        return found.stream().findFirst().orElse(null);
    }

    public static Object readAndSet(YamlExpressionParser parser, String path, String expression,
            Optional<Boolean> createIfMissing) {
        if (shouldCreatePathIfMissing(path, createIfMissing)) {
            createPathIfMissing(parser, path);
        }

        Set<Object> found = parser.readAndReplace(path, adaptExpression(expression));
        return found.stream().findFirst().orElse(null);
    }

    /**
     * By default, missing nodes are only created when the path starts with a filter expression (for example,
     * `(kind == Deployment).spec`), so they are not created in all the resources. Users can explicitly enable or disable it.
     */
    public static boolean shouldCreatePathIfMissing(String path, Optional<Boolean> createIfMissing) {
        return createIfMissing.orElseGet(() -> path != null && path.trim().startsWith(PARENTHESIS_OPEN));
    }

    /**
     * Create the missing nodes of the given path, so a later write on this path is not silently ignored.
     * Only the trailing plain keys of the path are created: filters, indexes and wildcards must resolve to existing nodes.
     */
    public static void createPathIfMissing(YamlExpressionParser parser, String path) {
        List<String> parts = splitPath(path);
        if (parts.size() < 2) {
            return;
        }

        for (Map<Object, Object> resource : parser.getResources()) {
            YamlExpressionParser resourceParser = new YamlExpressionParser(List.of(resource));
            if (!resourceParser.read(path).isEmpty()) {
                continue;
            }

            for (int index = parts.size() - 1; index > 0; index--) {
                if (!isPlainKey(parts.get(index))) {
                    break;
                }

                Set<Object> found = resourceParser.read(String.join(DOT, parts.subList(0, index)));
                if (!found.isEmpty()) {
                    List<String> missing = parts.subList(index, parts.size());
                    for (Object node : found) {
                        createMissingNodes(node, missing);
                    }
                    break;
                }
            }
        }
    }

    public static String toExpression(String property, Object provided, Object found, ConfigReference valueReference,
            String defaultConversion) {
        Optional<String> expressionProvided = Optional.ofNullable(valueReference.getExpression())
                .filter(StringUtils::isNotEmpty);

        if (expressionProvided.isPresent()) {
            return expressionProvided.get();
        }

        String conversion = defaultConversion;
        // we only need to quote when the found value in the generated resources is a string, but the provided type isn't.
        if (provided != null && !(provided instanceof String) && found instanceof String) {
            // we need conversion
            conversion = QUOTE_CONVERSION;
        }

        return VALUES_START_TAG + property + conversion + VALUES_END_TAG;
    }

    @SuppressWarnings("unchecked")
    private static void createMissingNodes(Object node, List<String> missing) {
        if (node instanceof Collection) {
            for (Object inner : (Collection<Object>) node) {
                createMissingNodes(inner, missing);
            }
        } else if (node instanceof Map) {
            Map<Object, Object> current = (Map<Object, Object>) node;
            for (int index = 0; index < missing.size() - 1; index++) {
                String key = normalize(missing.get(index));
                Object child = current.get(key);
                if (child == null) {
                    child = new LinkedHashMap<>();
                    current.put(key, child);
                } else if (!(child instanceof Map)) {
                    return;
                }

                current = (Map<Object, Object>) child;
            }

            // placeholder value that will be replaced by the expression.
            current.putIfAbsent(normalize(missing.get(missing.size() - 1)), EMPTY);
        }
    }

    /**
     * Split the path in parts following the same rules as YAMLPath: parts are separated by dots, unless they are
     * escaped within single quotes or within parenthesis.
     */
    private static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        String remaining = path.trim();
        while (!remaining.isEmpty()) {
            while (remaining.startsWith(DOT)) {
                remaining = remaining.substring(1);
            }

            if (remaining.isEmpty()) {
                break;
            }

            int end;
            if (remaining.startsWith(ESCAPE)) {
                end = remaining.indexOf(ESCAPE, 1) + 1;
            } else if (remaining.startsWith(PARENTHESIS_OPEN)) {
                end = remaining.indexOf(PARENTHESIS_CLOSE, 1) + 1;
            } else {
                end = remaining.indexOf(DOT);
            }

            if (end <= 0) {
                end = remaining.length();
            }

            parts.add(remaining.substring(0, end));
            remaining = remaining.substring(end);
        }

        return parts;
    }

    private static boolean isPlainKey(String part) {
        if (part.startsWith(ESCAPE)) {
            return part.length() > 1 && part.endsWith(ESCAPE);
        }

        return !part.isBlank()
                && !part.startsWith(PARENTHESIS_OPEN)
                && !part.contains(INDEX_OPEN)
                && !part.equals(WILDCARD);
    }

    private static String adaptExpression(String expression) {
        return START_EXPRESSION_TOKEN +
                expression.replaceAll(Pattern.quote(System.lineSeparator()), SEPARATOR_TOKEN)
                        .replaceAll(Pattern.quote("\""), SEPARATOR_QUOTES)
                + END_EXPRESSION_TOKEN;
    }
}
