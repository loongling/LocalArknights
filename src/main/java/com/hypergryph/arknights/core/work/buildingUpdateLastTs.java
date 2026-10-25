package com.hypergryph.arknights.core.work;

import java.util.Map;

public class buildingUpdateLastTs {

    @SuppressWarnings("unchecked")
    public static <T> T replaceLastUpdateTime(T node, long ts) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;

            if (map.containsKey("lastUpdateTime")) {
                map.put("lastUpdateTime", ts);
            }

            for (Object value : map.values()) {
                replaceLastUpdateTime(value, ts);
            }
        } else if (node instanceof Iterable) {
            for (Object item : (Iterable<?>) node) {
                replaceLastUpdateTime(item, ts);
            }
        }
        return node;
    }
}