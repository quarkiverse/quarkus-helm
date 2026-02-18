package io.quarkiverse.helm.deployment.utils;

import static io.quarkiverse.helm.deployment.utils.HelmConfigUtils.deductProperty;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;

import io.dekorate.ConfigReference;
import io.dekorate.utils.Serialization;
import io.quarkiverse.helm.deployment.HelmChartConfig;
import io.quarkiverse.helm.deployment.ValuesSchemaPropertyConfig;
import io.quarkiverse.helm.model.ValuesSchema;
import io.quarkiverse.helm.model.ValuesSchemaProperty;

public final class ValuesSchemaUtils {
    private ValuesSchemaUtils() {

    }

    public static Map<String, Object> createSchema(HelmChartConfig helmConfig,
            Map<String, ValuesHolder.HelmValueHolder> prodValues) {
        ValuesSchema schema = new ValuesSchema();
        schema.setTitle(helmConfig.valuesSchema().title());

        // from value references
        for (Map.Entry<String, ValuesHolder.HelmValueHolder> value : prodValues.entrySet()) {
            ConfigReference configReference = value.getValue().configReference;
            String[] tree = deductProperty(helmConfig, value.getKey()).split(Pattern.quote("."));
            ValuesSchemaProperty parent = null;
            Map<String, ValuesSchemaProperty> location = schema.getProperties();
            for (int index = 0; index < tree.length - 1; index++) {
                String part = tree[index];
                ValuesSchemaProperty next = location.get(part);
                if (next == null) {
                    next = new ValuesSchemaProperty();
                    next.setType("object");
                    location.put(part, next);
                }

                parent = next;
                location = next.getProperties();
            }

            String propertyName = tree[tree.length - 1];
            Object propertyValue = value.getValue().value;

            ValuesSchemaProperty property = location.getOrDefault(propertyName, new ValuesSchemaProperty());
            property.setDescription(configReference.getDescription());
            property.setPattern(configReference.getPattern());
            property.setEnumValues(configReference.getEnumValues());
            property.setMaximum(configReference.getMaximum());
            property.setMinimum(configReference.getMinimum());
            if (configReference.isRequired()) {
                if (parent == null) {
                    schema.getRequired().add(propertyName);
                } else {
                    parent.getRequired().add(propertyName);
                }
            }
            if (propertyValue == null) {
                property.setType("null");
            } else if (propertyValue instanceof Integer) {
                property.setType("integer");
            } else if (propertyValue instanceof Number) {
                property.setType("number");
            } else if (propertyValue instanceof Collection) {
                property.setType("array");
            } else if (propertyValue instanceof Boolean) {
                property.setType("boolean");
            } else {
                property.setType("string");
            }

            location.put(propertyName, property);
        }

        // from properties
        for (Map.Entry<String, ValuesSchemaPropertyConfig> propertyFromConfig : helmConfig.valuesSchema().properties()
                .entrySet()) {
            String name = propertyFromConfig.getValue().name().orElse(propertyFromConfig.getKey());

            String[] tree = deductProperty(helmConfig, name).split(Pattern.quote("."));
            ValuesSchemaProperty parent = null;
            Map<String, ValuesSchemaProperty> location = schema.getProperties();
            for (int index = 0; index < tree.length - 1; index++) {
                String part = tree[index];
                ValuesSchemaProperty next = location.get(part);
                if (next == null) {
                    next = new ValuesSchemaProperty();
                    next.setType("object");
                    location.put(part, next);
                }

                parent = next;
                location = next.getProperties();
            }

            String propertyName = tree[tree.length - 1];
            ValuesSchemaProperty property = location.getOrDefault(propertyName, new ValuesSchemaProperty());
            propertyFromConfig.getValue().description().ifPresent(property::setDescription);
            propertyFromConfig.getValue().pattern().ifPresent(property::setPattern);
            propertyFromConfig.getValue().maximum().ifPresent(property::setMaximum);
            propertyFromConfig.getValue().minimum().ifPresent(property::setMinimum);

            if (propertyFromConfig.getValue().required()) {
                if (parent == null) {
                    schema.getRequired().add(propertyName);
                } else {
                    parent.getRequired().add(propertyName);
                }
            }

            if (StringUtils.isNotEmpty(propertyFromConfig.getValue().type())) {
                property.setType(propertyFromConfig.getValue().type());
            }

            location.put(propertyName, property);
        }

        // convert to map
        return Serialization.unmarshal(Serialization.asJson(schema), new TypeReference<Map<String, Object>>() {
        });
    }
}
