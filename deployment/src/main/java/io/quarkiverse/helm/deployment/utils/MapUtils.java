package io.quarkiverse.helm.deployment.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class MapUtils {
    private MapUtils() {

    }

    public static Map<String, Object> toPlainMap(Map<String, Object> map) {
        return toPlainMap(new ArrayList<>(), map);
    }

    public static Map<String, Object> toMultiValueUnsortedMap(Map<String, Object> map) {
        return toMultiValueMap(map, HashMap::new);
    }

    public static Map<String, Object> toMultiValueSortedMap(Map<String, Object> map) {
        return toMultiValueMap(map, TreeMap::new);
    }

    private static Map<String, Object> toMultiValueMap(Map<String, Object> map, Supplier<Map<String, Object>> supplier) {
        Map<String, Object> multiValueMap = supplier.get();
        map.forEach((k, v) -> {

            String[] nodes = k.split(Pattern.quote("."));
            if (nodes.length == 1) {
                multiValueMap.put(k, v);
            } else {
                Map<String, Object> auxKeyValue = multiValueMap;
                for (int index = 0; index < nodes.length - 1; index++) {
                    String nodeName = nodes[index];
                    Object nodeKeyValue = auxKeyValue.get(nodeName);
                    if (nodeKeyValue == null || !(nodeKeyValue instanceof Map)) {
                        nodeKeyValue = supplier.get();
                    }

                    auxKeyValue.put(nodes[index], nodeKeyValue);
                    auxKeyValue = (Map<String, Object>) nodeKeyValue;
                }

                auxKeyValue.put(nodes[nodes.length - 1], v);
            }
        });

        return multiValueMap;
    }

    private static Map<String, Object> toPlainMap(List<String> path, Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            List<String> newPath = new ArrayList<>(path);
            newPath.add(entry.getKey());
            if (entry.getValue() instanceof Map) {
                result.putAll(toPlainMap(newPath, (Map<String, Object>) entry.getValue()));
            } else {
                result.put(String.join(".", newPath), entry.getValue());
            }
        }

        return result;
    }
}
