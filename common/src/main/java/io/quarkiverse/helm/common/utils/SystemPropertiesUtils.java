package io.quarkiverse.helm.common.utils;

import static io.dekorate.utils.Strings.defaultIfEmpty;
import static io.github.yamlpath.utils.StringUtils.isNullOrEmpty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.dekorate.utils.Strings;

public final class SystemPropertiesUtils {

    private static final String SYSTEM_PROPERTY_START = "${";
    private static final String SYSTEM_PROPERTY_END = "}";

    private SystemPropertiesUtils() {

    }

    public static boolean hasSystemProperties(String rawValue) {
        return Strings.isNotNullOrEmpty(rawValue) && rawValue.contains(SYSTEM_PROPERTY_START);
    }

    public static List<String> getSystemProperties(String str) {
        return substringsBetween(str, SYSTEM_PROPERTY_START, SYSTEM_PROPERTY_END);
    }

    public static String getPropertyFromSystem(String propertyName, String defaultValue) {
        String value = Optional.ofNullable(System.getProperty(propertyName))
                .orElseGet(() -> System.getenv(propertyName));

        return defaultIfEmpty(value, defaultValue);
    }

    private static List<String> substringsBetween(String str, String open, String close) {
        if (Strings.isNullOrEmpty(str) || Strings.isNullOrEmpty(open) || Strings.isNullOrEmpty(close)) {
            return Collections.emptyList();
        }

        int closeLen = close.length();
        int openLen = open.length();
        List<String> list = new ArrayList();
        int end;
        for (int pos = 0; pos < str.length() - closeLen; pos = end + closeLen) {
            int start = str.indexOf(open, pos);
            if (start < 0) {
                break;
            }

            start += openLen;
            end = str.indexOf(close, start);
            if (end < 0) {
                break;
            }

            String currentStr = str.substring(start);
            String tentative = currentStr.substring(0, end - start);
            while (countMatches(tentative, open) != countMatches(tentative, close)) {
                end++;
                if (end >= str.length()) {
                    break;
                }

                tentative = currentStr.substring(0, end - start);
            }

            list.add(tentative);
        }

        return list;
    }

    private static int countMatches(String str, String sub) {
        if (!isNullOrEmpty(str) && !isNullOrEmpty(sub)) {
            int count = 0;

            for (int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sub.length()) {
                ++count;
            }

            return count;
        } else {
            return 0;
        }
    }
}
