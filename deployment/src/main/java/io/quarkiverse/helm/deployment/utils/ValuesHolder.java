package io.quarkiverse.helm.deployment.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.dekorate.ConfigReference;

public class ValuesHolder {
    private final Map<String, HelmValueHolder> prodValues = new HashMap<>();
    private final Map<String, Map<String, HelmValueHolder>> valuesByProfile = new HashMap<>();

    public Map<String, HelmValueHolder> getProdValues() {
        return Collections.unmodifiableMap(prodValues);
    }

    public Map<String, Map<String, HelmValueHolder>> getValuesByProfile() {
        return Collections.unmodifiableMap(valuesByProfile);
    }

    public void put(String property, ConfigReference config) {
        put(property, config, config.getValue(), config.getProfile());
    }

    public void put(String property, ConfigReference config, Object value) {
        prodValues.put(property, new HelmValueHolder(value, config));
    }

    public void put(String property, ConfigReference config, Object value, String profile) {
        get(profile).put(property, new HelmValueHolder(value, config));
    }

    public void putIfAbsent(String property, ConfigReference config, Object value, String profile) {
        get(profile).putIfAbsent(property, new HelmValueHolder(value, config));
    }

    public Map<String, HelmValueHolder> get(String profile) {
        Map<String, HelmValueHolder> values = prodValues;
        if (StringUtils.isNotEmpty(profile)) {
            values = valuesByProfile.computeIfAbsent(profile, p -> new HashMap<>());
        }

        return values;
    }

    public static class HelmValueHolder {
        public final Object value;
        public final ConfigReference configReference;

        public HelmValueHolder(Object value, ConfigReference configReference) {
            this.value = value;
            this.configReference = configReference;
        }
    }
}
