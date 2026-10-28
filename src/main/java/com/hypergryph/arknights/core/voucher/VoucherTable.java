package com.hypergryph.arknights.core.voucher;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.file.IOTools;

public class VoucherTable {

    private static JSONObject vouchers = new JSONObject(true);

    public static void load() {
        JSONObject root = IOTools.ReadJsonFile(
                System.getProperty("user.dir") + "/data/voucher/voucher_detail_table.json");
        JSONObject v = root == null ? null : root.getJSONObject("vouchers");
        vouchers = v == null ? new JSONObject(true) : v;
    }

    public static JSONObject get(String itemId) {
        return vouchers.getJSONObject(itemId);
    }

    public static int size() {
        return vouchers.size();
    }

    public static JSONArray itemList(String itemId) {
        JSONObject v = get(itemId);
        return v == null ? null : v.getJSONArray("itemList");
    }

    public static JSONArray pool(String itemId) {
        JSONObject v = get(itemId);
        return v == null ? null : v.getJSONArray("pool");
    }

    public static boolean isCharVoucher(String itemId) {
        JSONObject v = get(itemId);
        if (v == null) {
            return false;
        }
        if ("CHAR_VOUCHER".equals(v.getString("voucherType"))) {
            return true;
        }
        JSONArray list = v.getJSONArray("itemList");
        if (list == null || list.isEmpty()) {
            return false;
        }
        return "CHAR".equals(list.getJSONObject(0).getString("type"));
    }
}
