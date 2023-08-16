package io.quarkiverse.helm.common.utils;

import java.util.List;

public final class Constants {

    public static final String NAME_FORMAT_REG_EXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";
    public static final List<String> HELM_INVALID_CHARACTERS = List.of("-");

    public static final String HELM_CHART_DEFAULT_API_VERSION = "v2";
    public static final String HELM_CHART_DEFAULT_VALUES_ROOT_ALIAS = "app";
    public static final String HELM_CHART_DEFAULT_NOTES = "/NOTES.template.txt";

    public static final String HELM_CHART_DEFAULT_VALUES_SCHEMA_TITLE = "Values";

    private Constants() {

    }
}
