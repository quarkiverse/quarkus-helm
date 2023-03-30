package io.quarkiverse.helm.deployment.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.dekorate.ConfigReference;
import io.dekorate.utils.Strings;

public class ValuesHolder {
    private final Map<String, Object> prodValues = new HashMap<>();
    private final Map<String, Map<String, Object>> valuesByProfile = new HashMap<>();

    public Map<String, Object> getProdValues() {
        return Collections.unmodifiableMap(prodValues);
    }

    public Map<String, Map<String, Object>> getValuesByProfile() {
        return Collections.unmodifiableMap(valuesByProfile);
    }

    public void put(String property, ConfigReference value) {
        put(property, value.getValue(), value.getProfile());
    }

    public void put(String property, Object value, String profile) {
        get(profile).put(property, value);
    }

    public void putIfAbsent(String property, Object value, String profile) {
        get(profile).putIfAbsent(property, value);
    }

    public void put(String property, Object value) {
        prodValues.put(property, value);
    }

    public Map<String, Object> get(String profile) {
        Map<String, Object> values = prodValues;
        if (Strings.isNotNullOrEmpty(profile)) {
            values = valuesByProfile.get(profile);
            if (values == null) {
                values = new HashMap<>();
                valuesByProfile.put(profile, values);
            }
        }

        return values;
    }
}
