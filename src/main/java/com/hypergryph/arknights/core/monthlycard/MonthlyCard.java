package com.hypergryph.arknights.core.monthlycard;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.Admin;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.Calendar;

/**
 * 月卡（每月寻访组合包 / monthlySub）。
 *
 * 数据来源：
 *   data/shop/GPGoodList.json 的 monthlySub[0]：
 *     {"goodId":"GP_mCard_1","displayName":"月卡","cardId":"mCard_1","currencyUnit":"CASH","price":30,
 *      "items":[{"id":"4002","count":6,"type":"DIAMOND"}],              ← 购买立刻给 6 至纯源石
 *      "dailyBonus":[{"id":"4003","count":200,"type":"DIAMOND_SHD"},    ← 每天 200 合成玉
 *                    {"id":"ap_supply_lt_80","count":1,"type":"AP_SUPPLY"}]}  ← 每天 1 个 80 理智药
 *
 *   data/excel/checkin_table.json 的 monthlySubItem（ArknightsApplication.monthlyPass）：
 *     mCard_1 → [ {groupId, startTime, endTime, items[]}, ... ]          ← 按时间段区分的每日奖励
 *
 * 玩家状态字段（status）：
 *   monthlySubscriptionStartTime / monthlySubscriptionEndTime / tipMonthlyCardExpireTs
 *
 * 每日奖励用 userData.localMonthlySub.lastGrantDay（yyyyMMdd）做去重标记，
 * （**不能**写 userData.monthlySub：客户端里 PlayerMonthlySub.lastGrantTs 是
 *   Dictionary<string, PlayerMonthlySubPer>，写成数字会让 /account/syncData 反序列化失败 → 身份同步失败 30000）
 * 因为 status 里没有官方对应的字段；多出来的未知键客户端会忽略。
 */
public final class MonthlyCard {

    /** 月卡时长（天） */
    public static final int DURATION_DAYS = 30;

    private MonthlyCard() {
    }

    /** 在 GPGoodList 里递归找 goodId 对应的商品 */
    public static JSONObject findGood(String goodId) {
        if (goodId == null || goodId.isEmpty()) {
            return null;
        }
        JSONObject list = ArknightsApplication.GPGoodList;
        if (list == null) {
            return null;
        }
        return findGoodIn(list, goodId);
    }

    private static JSONObject findGoodIn(Object node, String goodId) {
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject found = findGoodIn(arr.get(i), goodId);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!(node instanceof JSONObject)) {
            return null;
        }
        JSONObject obj = (JSONObject) node;
        if (goodId.equals(obj.getString("goodId"))) {
            return obj;
        }
        for (String key : obj.keySet()) {
            JSONObject found = findGoodIn(obj.get(key), goodId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 是否月卡类商品（带 cardId） */
    public static boolean isMonthlyCard(JSONObject good) {
        return good != null && good.getString("cardId") != null;
    }

    /**
     * 激活/续期月卡：从 max(现在, 当前到期时间) 起算，加 30 天。
     * @return 新的到期时间
     */
    public static long activate(JSONObject userData, long nowTs) {
        JSONObject status = userData.getJSONObject("status");
        long curEnd = status.getLongValue("monthlySubscriptionEndTime");
        long curStart = status.getLongValue("monthlySubscriptionStartTime");
        long base = Math.max(nowTs, curEnd);
        long newEnd = base + DURATION_DAYS * 86400L;
        if (curStart <= 0) {
            status.put("monthlySubscriptionStartTime", nowTs);
        }
        status.put("monthlySubscriptionEndTime", newEnd);
        status.put("tipMonthlyCardExpireTs", newEnd);
        return newEnd;
    }

    /** 订阅是否生效中 */
    public static boolean isActive(JSONObject userData, long nowTs) {
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        if (status == null) {
            return false;
        }
        return status.getLongValue("monthlySubscriptionStartTime") > 0
                && status.getLongValue("monthlySubscriptionEndTime") > nowTs;
    }

    /**
     * 预测"激活一张月卡 + 发当天奖励"之后 status 会变成什么样（不改动数据）。
     * 供 delta 构造使用：handleModifiedBuild 在真正写入之前就要把增量拼出来。
     */
    public static JSONObject planStatusDelta(JSONObject userData, String cardId, long nowTs) {
        JSONObject delta = new JSONObject(true);
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        if (status == null) {
            return delta;
        }
        long curStart = status.getLongValue("monthlySubscriptionStartTime");
        long curEnd = status.getLongValue("monthlySubscriptionEndTime");
        long newEnd = Math.max(nowTs, curEnd) + DURATION_DAYS * 86400L;
        if (curStart <= 0) {
            delta.put("monthlySubscriptionStartTime", nowTs);
        }
        delta.put("monthlySubscriptionEndTime", newEnd);
        delta.put("tipMonthlyCardExpireTs", newEnd);

        // 激活后一定处于生效中；当天没发过就会有每日奖励
        JSONObject sub = userData.getJSONObject("localMonthlySub");
        boolean grantedToday = sub != null && sub.getIntValue("lastGrantDay") == dayIndex(nowTs);
        if (!grantedToday) {
            JSONArray items = dailyItems(cardId, nowTs);
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String type = item.getString("type");
                    int count = item.getIntValue("count");
                    if ("DIAMOND_SHD".equals(type)) {
                        delta.put("diamondShard", status.getIntValue("diamondShard") + count);
                    } else if ("DIAMOND".equals(type)) {
                        delta.put("androidDiamond", status.getIntValue("androidDiamond") + count);
                        delta.put("iosDiamond", status.getIntValue("iosDiamond") + count);
                    } else if ("GOLD".equals(type)) {
                        delta.put("gold", status.getIntValue("gold") + count);
                    }
                }
            }
        }
        return delta;
    }

    /** 预测激活一张月卡之后 inventory 会变成什么样（目前只有理智药这种堆叠物品） */
    public static JSONObject planInventoryDelta(JSONObject userData, String cardId, long nowTs) {
        JSONObject delta = new JSONObject(true);
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        JSONObject inventory = userData == null ? null : userData.getJSONObject("inventory");
        if (status == null || inventory == null) {
            return delta;
        }
        JSONObject sub = userData.getJSONObject("localMonthlySub");
        if (sub != null && sub.getIntValue("lastGrantDay") == dayIndex(nowTs)) {
            return delta;               // 今天已经发过
        }
        JSONArray items = dailyItems(cardId, nowTs);
        if (items == null) {
            return delta;
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null || !"AP_SUPPLY".equals(item.getString("type"))) {
                continue;
            }
            String id = item.getString("id");
            delta.put(id, inventory.getIntValue(id) + item.getIntValue("count"));
        }
        return delta;
    }

    /** 剩余天数（向上取整，已过期返回 0） */
    public static int remainingDays(JSONObject userData, long nowTs) {
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        if (status == null) {
            return 0;
        }
        long end = status.getLongValue("monthlySubscriptionEndTime");
        if (end <= nowTs) {
            return 0;
        }
        return (int) Math.ceil((end - nowTs) / 86400.0);
    }

    /**
     * 发放当日月卡奖励（每天一次）。
     *
     * 调用点：/user/checkIn —— 客户端 CheckInResponse 的 subscriptionRewards 就是月卡奖励，
     * 所以必须和签到一起发，不能放在登录里（登录不发不会有弹窗）。
     *
     * @return 实际发放的物品列表（ItemModel: {type,id,count}），没发就是空数组
     */
    public static JSONArray grantDailyBonus(JSONObject userData, String cardId) {
        JSONArray sink = new JSONArray();
        if (userData == null) {
            return sink;
        }
        long nowTs = System.currentTimeMillis() / 1000L;
        if (!isActive(userData, nowTs)) {
            return sink;
        }
        int today = dayIndex(nowTs);
        JSONObject sub = userData.getJSONObject("localMonthlySub");
        if (sub == null) {
            sub = new JSONObject(true);
            userData.put("localMonthlySub", sub);
        }
        if (sub.getIntValue("lastGrantDay") == today) {
            return sink;                // 今天已经发过
        }

        JSONArray items = dailyItems(cardId, nowTs);
        if (items == null || items.isEmpty()) {
            ArknightsApplication.LOGGER.warn("[monthlycard] 找不到每日奖励定义（cardId={}）", cardId);
            return sink;
        }

        // 和商店/礼包走同一条入账通道（processArray），不再用 Admin.GM_GiveItem
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject grant = new JSONObject(true);
            grant.put("id", item.getString("id"));
            grant.put("type", item.getString("type"));
            grant.put("count", item.getIntValue("count"));
            com.hypergryph.arknights.core.work.processArray
                    .handleUserDataChange(grant, userData, 0L, 0);
            sink.add(grant);
        }
        sub.put("lastGrantDay", today);
        sub.put("lastGrantTs", nowTs);
        ArknightsApplication.LOGGER.info("[monthlycard] 发放每日奖励 {} 项: {}", sink.size(), sink.toJSONString());
        return sink;
    }

    /**
     * 取当前时间段生效的每日奖励。
     * 优先用 checkin_table.monthlySubItem（按 startTime/endTime 分档），
     * 取不到再退回 GPGoodList 里该商品的 dailyBonus。
     */
    public static JSONArray dailyItems(String cardId, long nowTs) {
        JSONObject monthlyPass = ArknightsApplication.monthlyPass;
        if (monthlyPass != null && cardId != null) {
            JSONArray groups = monthlyPass.getJSONArray(cardId);
            if (groups != null) {
                for (int i = 0; i < groups.size(); i++) {
                    JSONObject group = groups.getJSONObject(i);
                    if (group == null) {
                        continue;
                    }
                    long start = group.getLongValue("startTime");
                    long end = group.getLongValue("endTime");
                    if (nowTs >= start && nowTs <= end) {
                        JSONArray items = group.getJSONArray("items");
                        if (items != null && !items.isEmpty()) {
                            return items;
                        }
                    }
                }
            }
        }
        // 回退：GPGoodList 里的 dailyBonus
        if (cardId != null) {
            JSONObject good = findGoodByCardId(cardId);
            if (good != null) {
                return good.getJSONArray("dailyBonus");
            }
        }
        return null;
    }

    /** 按 cardId 找月卡商品 */
    public static JSONObject findGoodByCardId(String cardId) {
        JSONObject list = ArknightsApplication.GPGoodList;
        JSONArray subs = list == null ? null : list.getJSONArray("localMonthlySub");
        if (subs == null) {
            return null;
        }
        for (int i = 0; i < subs.size(); i++) {
            JSONObject good = subs.getJSONObject(i);
            if (good != null && cardId.equals(good.getString("cardId"))) {
                return good;
            }
        }
        return null;
    }

    /** 当前月卡商品的 cardId（默认 mCard_1） */
    public static String currentCardId() {
        JSONObject list = ArknightsApplication.GPGoodList;
        JSONArray subs = list == null ? null : list.getJSONArray("localMonthlySub");
        if (subs != null && !subs.isEmpty()) {
            JSONObject first = subs.getJSONObject(0);
            if (first != null && first.getString("cardId") != null) {
                return first.getString("cardId");
            }
        }
        // checkin_table.currentMonthlySubId 也可作为兜底
        return "mCard_1";
    }

    /** 本地日期 yyyyMMdd，用于"每天一次"去重 */
    private static int dayIndex(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts * 1000L);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 对比「变更前的 JSON 快照」和当前对象，返回变化的键。
     * 用于构造 playerDataDelta.modified（只下发改动字段）。
     */
    public static JSONObject diff(String beforeJson, JSONObject after) {
        JSONObject delta = new JSONObject(true);
        if (after == null) {
            return delta;
        }
        JSONObject before = null;
        try {
            before = JSONObject.parseObject(beforeJson);
        } catch (Exception ignored) {
        }
        for (String key : after.keySet()) {
            Object now = after.get(key);
            Object old = before == null ? null : before.get(key);
            if (old == null ? now != null : !old.equals(now)) {
                delta.put(key, now);
            }
        }
        return delta;
    }
}
