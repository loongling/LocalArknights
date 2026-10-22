package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.Objects;

public class getProduct {
    public static JSONObject getProduct(int storeId) {
        JSONObject obj = ArknightsApplication.AllProductList;
        if (obj == null) {
            return null;
        }

        JSONArray list = obj.getJSONArray("productList");
        if (list == null) {
            return null;
        }

        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            if (item.getIntValue("store_id") == storeId) {
                JSONObject result = new JSONObject();
                result.put("name", item.getString("name"));
                result.put("price", item.getInteger("price"));
                result.put("status", item.getInteger("status"));
                result.put("product_id", item.getString("product_id"));
                result.put("desc", item.getString("desc"));
                return result;
            }
        }
        return null;
    }

    public static JSONObject getCashGood(String productId) {
        JSONArray list = ArknightsApplication.CashGoodList;
        if (list == null || productId == null) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            String goodId = item.getString("goodId");
            if (goodId != null && (goodId.equals(productId) || goodId.startsWith(productId + "_"))) {
                return item;
            }
        }
        return null;
    }

    public static JSONObject getLevelGood(String id) {
        JSONArray array = ArknightsApplication.GPGoodList.getJSONArray("levelGP");
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (Objects.equals(id, item.getString("goodId"))) {
                return item;
            }
        }
        return null;
    }

    public static JSONObject oneTimeGood(String id) {
        JSONArray array = ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP");
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (Objects.equals(id, item.getString("goodId"))) {
                return item;
            }
        }
        return null;
    }
}