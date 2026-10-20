package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONObject;

public class consumableNumberG {
    public static int nextItemNumber(JSONObject consumable) {
        if (consumable == null || consumable.isEmpty()) {
            return 0;
        }
        int max = -1;
        for (Object v : consumable.values()) {
            if (!(v instanceof JSONObject)) {
                continue;
            }
            JSONObject itemObj = (JSONObject) v;
            for (String key : itemObj.keySet()) {
                try {
                    int n = Integer.parseInt(key);
                    if (n > max) {
                        max = n;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字 key 跳过
                }
            }
        }
        return max + 1;
    }

    public static boolean isExist(String itemId, JSONObject consumable) {
        if (consumable == null || itemId == null) {
            return false;
        }
        return consumable.containsKey(itemId);
    }

    public static JSONObject getInnerObject(JSONObject wrapper) {
        if (wrapper == null || wrapper.isEmpty()) {
            return null;
        }
        String key = wrapper.keySet().iterator().next();
        Object v = wrapper.get(key);
        return (v instanceof JSONObject) ? (JSONObject) v : null;
    }

    public static int getInnerNumber(JSONObject wrapper) {
        if (wrapper == null || wrapper.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(wrapper.keySet().iterator().next());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
