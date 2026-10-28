package com.hypergryph.arknights.core.shop;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.monthlycard.MonthlyCard;
import com.hypergryph.arknights.core.work.processArray;

public final class GPGoodGrant {

    private GPGoodGrant() {
    }

    private static java.util.Map<String, String> snapshot(JSONObject userData) {
        java.util.Map<String, String> snapshot = new java.util.HashMap<>();
        for (String node : new String[]{"status", "inventory", "troop", "skin", "building", "dexNav"}) {
            JSONObject obj = userData.getJSONObject(node);
            if (obj != null) {
                snapshot.put(node, obj.toJSONString());
            }
        }
        return snapshot;
    }

    private static void mergeChangedNodes(JSONObject modified, JSONObject userData,
                                          java.util.Map<String, String> before) {
        for (java.util.Map.Entry<String, String> e : before.entrySet()) {
            JSONObject now = userData.getJSONObject(e.getKey());
            if (now == null) {
                continue;
            }
            if (!now.equals(JSONObject.parseObject(e.getValue()))) {
                modified.put(e.getKey(), now);
            }
        }
    }

    public static JSONArray grant(JSONObject userData, JSONObject good, long uid, JSONObject modified) {
        String goodId = good == null ? null : good.getString("goodId");
        JSONArray items = good == null ? null : good.getJSONArray("items");
        JSONArray echo = items == null ? new JSONArray() : JSONObject.parseArray(items.toJSONString());

        if (items != null) {
            // 干员会联动改 troop/building/dexNav 等，先快照再 diff（handleModifiedBuild 无法预测这些）
            boolean hasChar = false;
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                if (it != null && "CHAR".equals(it.getString("type"))) {
                    hasChar = true;
                    break;
                }
            }
            java.util.Map<String, String> before = hasChar ? snapshot(userData) : null;

            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                int kind = ArknightsApplication.checkCheckInItemType(item.getString("type"));
                if (kind < 0) {
                    ArknightsApplication.LOGGER.warn(
                            "[shop] 类型 {} 未实现入账（goodId={} id={} count={}）",
                            item.getString("type"), goodId, item.getString("id"),
                            item.getIntValue("count"));
                }
                processArray.handleModifiedBuild(item, modified, userData, i);
                // 第五个参数收集需要回给客户端的物品（干员的 charGet 就在里面）
                processArray.handleUserDataChange(item, userData, uid, i, echo);
            }

            if (before != null) {
                mergeChangedNodes(modified, userData, before);
            }
        }

        long nowTs = System.currentTimeMillis() / 1000L;

        // 月卡本体（带 cardId 的）：订阅字段 + 当天每日奖励
        if (MonthlyCard.isMonthlyCard(good)) {
            String cardId = good.getString("cardId");
            ShopPurchase.mergeNode(modified, "status",
                    MonthlyCard.planStatusDelta(userData, cardId, nowTs));
            ShopPurchase.mergeNode(modified, "inventory",
                    MonthlyCard.planInventoryDelta(userData, cardId, nowTs));
            long newEnd = MonthlyCard.activate(userData, nowTs);
            MonthlyCard.grantDailyBonus(userData, cardId);
            ArknightsApplication.LOGGER.info("[shop] uid={} 购买月卡 {}，到期 {}（剩余 {} 天）",
                    uid, goodId, newEnd, MonthlyCard.remainingDays(userData, nowTs));
        }

        // 采购记录（月卡可重复购买，gpShopPath 返回 null）
        String[] path = ShopPurchase.gpShopPath(goodId);
        if (path != null) {
            ShopPurchase.recordPurchase(modified, userData, path, goodId, 1, null, 0);
            if ("weekly".equals(path[1])) {
                JSONObject weekly = ArknightsApplication.GPGoodList.getJSONObject("weeklyGroup");
                ShopPurchase.setCurGroupId(modified, userData, path, weekly == null ? null : weekly.getString("groupId"));
            } else if ("monthly".equals(path[1])) {
                JSONObject monthly = ArknightsApplication.GPGoodList.getJSONObject("monthlyGroup");
                ShopPurchase.setCurGroupId(modified, userData, path, monthly == null ? null : monthly.getString("groupId"));
            }
        }

        return echo;
    }
}
