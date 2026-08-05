package io.quarkiverse.helm.deployment.utils;

// Replaced Dekorate's io.dekorate.utils.Strings isEmpty/isNotEmpty
public final class StringUtils {

    // TODO: Code to be moved to: io.quarkus.runtime.util.StringUtil
    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    // TODO: Code to be moved to: io.quarkus.runtime.util.StringUtil
    public static boolean isNotEmpty(final CharSequence cs) {
        return !isEmpty(cs);
    }
}
