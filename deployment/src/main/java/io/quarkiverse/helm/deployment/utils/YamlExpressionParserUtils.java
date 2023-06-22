package io.quarkiverse.helm.deployment.utils;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.dekorate.ConfigReference;
import io.dekorate.utils.Strings;
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

    private YamlExpressionParserUtils() {

    }

    public static void set(YamlExpressionParser parser, String path, String expression) {
        parser.write(path, adaptExpression(expression));
    }

    public static Object read(YamlExpressionParser parser, String path) {
        Set<Object> found = parser.read(path);
        return found.stream().findFirst().orElse(null);
    }

    public static Object readAndSet(YamlExpressionParser parser, String path, String expression) {
        Set<Object> found = parser.readAndReplace(path, adaptExpression(expression));
        return found.stream().findFirst().orElse(null);
    }

    public static String toExpression(String property, Object provided, Object found, ConfigReference valueReference) {
        Optional<String> expressionProvided = Optional.ofNullable(valueReference.getExpression())
                .filter(Strings::isNotNullOrEmpty);

        if (expressionProvided.isPresent()) {
            return expressionProvided.get();
        }

        String conversion = EMPTY;
        // we only need to quote when the found value in the generated resources is a string, but the provided type isn't.
        if (provided != null && !(provided instanceof String) && found instanceof String) {
            // we need conversion
            conversion = " | quote";
        }

        return VALUES_START_TAG + property + conversion + VALUES_END_TAG;
    }

    private static String adaptExpression(String expression) {
        return START_EXPRESSION_TOKEN +
                expression.replaceAll(Pattern.quote(System.lineSeparator()), SEPARATOR_TOKEN)
                        .replaceAll(Pattern.quote("\""), SEPARATOR_QUOTES)
                + END_EXPRESSION_TOKEN;
    }
}
