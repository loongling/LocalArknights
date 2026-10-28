package com.hypergryph.arknights.core.shop;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

public final class ShopPurchase {

    private ShopPurchase() {
    }

    public static void deepMerge(JSONObject dst, JSONObject src) {
        for (String key : src.keySet()) {
            Object sv = src.get(key);
            Object dv = dst.get(key);
            if (sv instanceof JSONObject && dv instanceof JSONObject) {
                deepMerge((JSONObject) dv, (JSONObject) sv);
            } else {
                dst.put(key, sv);
            }
        }
    }

    public static void mergeNode(JSONObject modified, String node, JSONObject delta) {
        if (modified == null || delta == null || delta.isEmpty()) {
            return;
        }
        JSONObject target = modified.getJSONObject(node);
        if (target == null) {
            target = JSONObject.parseObject(delta.toJSONString());
        } else {
            deepMerge(target, delta);
        }
        modified.put(node, target);
    }

    public static void recordPurchase(JSONObject modified, JSONObject userData, String[] path,
                                      String goodId, int count, String costField, int costDelta) {
        JSONObject shop = userData == null ? null : userData.getJSONObject("shop");
        if (shop == null || path == null || goodId == null) {
            return;
        }
        JSONObject node = shop;
        for (String seg : path) {
            JSONObject next = node.getJSONObject(seg);
            if (next == null) {
                next = new JSONObject(true);
                node.put(seg, next);
            }
            node = next;
        }
        JSONArray info = node.getJSONArray("info");
        if (info == null) {
            info = new JSONArray();
            node.put("info", info);
        }
        boolean found = false;
        for (int i = 0; i < info.size(); i++) {
            JSONObject entry = info.getJSONObject(i);
            if (entry != null && goodId.equals(entry.getString("id"))) {
                entry.put("count", entry.getIntValue("count") + count);
                found = true;
                break;
            }
        }
        if (!found) {
            JSONObject entry = new JSONObject(true);
            entry.put("id", goodId);
            entry.put("count", count);
            info.add(entry);
        }
        if (costField != null) {
            node.put(costField, node.getIntValue(costField) + costDelta);
        }

        JSONObject leaf = new JSONObject(true);
        leaf.put("info", JSONObject.parseArray(info.toJSONString()));
        if (costField != null) {
            leaf.put(costField, node.getIntValue(costField));
        }
        Object inner = leaf;
        for (int i = path.length - 1; i >= 0; i--) {
            JSONObject wrapper = new JSONObject(true);
            wrapper.put(path[i], inner);
            inner = wrapper;
        }
        mergeNode(modified, "shop", (JSONObject) inner);
    }

    public static int boughtCount(JSONObject userData, String[] path, String goodId) {
        JSONObject shop = userData == null ? null : userData.getJSONObject("shop");
        if (shop == null || path == null || goodId == null) {
            return 0;
        }
        JSONObject node = shop;
        for (String seg : path) {
            if (node == null) {
                return 0;
            }
            node = node.getJSONObject(seg);
        }
        JSONArray info = node == null ? null : node.getJSONArray("info");
        if (info == null) {
            return 0;
        }
        for (int i = 0; i < info.size(); i++) {
            JSONObject entry = info.getJSONObject(i);
            if (entry != null && goodId.equals(entry.getString("id"))) {
                return entry.getIntValue("count");
            }
        }
        return 0;
    }

    public static String checkAvailCount(JSONObject good, JSONObject userData, String[] path,
                                         String goodId, int count) {
        if (good == null || !good.containsKey("availCount")) {
            return null;
        }
        int avail = good.getIntValue("availCount");
        int bought = boughtCount(userData, path, goodId);
        if (bought + count > avail) {
            return "good sold out: " + goodId + " availCount=" + avail + ", already bought=" + bought;
        }
        return null;
    }

    public static String[] gpShopPath(String goodId) {
        JSONObject gp = ArknightsApplication.GPGoodList;
        if (gp == null || goodId == null) {
            return null;
        }
        if (containsGoodId(gp.getJSONArray("oneTimeGP"), goodId)) {
            return new String[]{"GP", "oneTime"};
        }
        if (containsGoodId(gp.getJSONArray("levelGP"), goodId)) {
            return new String[]{"GP", "level"};
        }
        if (packagesContain(gp.getJSONObject("weeklyGroup"), goodId)) {
            return new String[]{"GP", "weekly"};
        }
        if (packagesContain(gp.getJSONObject("monthlyGroup"), goodId)) {
            return new String[]{"GP", "monthly"};
        }
        return null;
    }

    public static boolean containsGoodId(JSONArray arr, String goodId) {
        if (arr == null) {
            return false;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject g = arr.getJSONObject(i);
            if (g != null && goodId.equals(g.getString("goodId"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean packagesContain(JSONObject group, String goodId) {
        JSONObject packages = group == null ? null : group.getJSONObject("packages");
        return packages != null && packages.containsKey(goodId);
    }

    public static void setCurGroupId(JSONObject modified, JSONObject userData, String[] path, String groupId) {
        if (groupId == null || path == null) {
            return;
        }
        JSONObject shop = userData == null ? null : userData.getJSONObject("shop");
        if (shop == null) {
            return;
        }
        JSONObject node = shop;
        for (String seg : path) {
            JSONObject next = node.getJSONObject(seg);
            if (next == null) {
                next = new JSONObject(true);
                node.put(seg, next);
            }
            node = next;
        }
        node.put("curGroupId", groupId);

        JSONObject leaf = new JSONObject(true);
        leaf.put("curGroupId", groupId);
        Object inner = leaf;
        for (int i = path.length - 1; i >= 0; i--) {
            JSONObject wrapper = new JSONObject(true);
            wrapper.put(path[i], inner);
            inner = wrapper;
        }
        mergeNode(modified, "shop", (JSONObject) inner);
    }
}
