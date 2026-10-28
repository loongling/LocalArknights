package com.hypergryph.arknights.core.shop;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.Calendar;
import java.util.TimeZone;

public final class PeriodicShopReset {

    private static final int RESET_HOUR = 4;

    private PeriodicShopReset() {
    }

    private static Calendar calendar(long epochSecond, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(epochSecond * 1000L);
        return c;
    }

    private static long shiftByResetHour(long epochSecond) {
        return epochSecond - RESET_HOUR * 3600L;
    }

    public static long weekIndex(long epochSecond) {
        TimeZone tz = TimeZone.getDefault();
        Calendar c = calendar(shiftByResetHour(epochSecond), tz);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK);          // 1=周日 … 7=周六
        int sinceMonday = (dow + 5) % 7;                // 周一=0 … 周日=6
        c.add(Calendar.DAY_OF_YEAR, -sinceMonday);
        return c.getTimeInMillis() / 1000L;
    }

    public static long monthIndex(long epochSecond) {
        Calendar c = calendar(shiftByResetHour(epochSecond), TimeZone.getDefault());
        return (long) c.get(Calendar.YEAR) * 100L + (c.get(Calendar.MONTH) + 1);
    }

    public static long secondsToNextWeeklyReset(long epochSecond) {
        return weekIndex(epochSecond) + 7 * 86400L + RESET_HOUR * 3600L - epochSecond;
    }

    public static long secondsToNextMonthlyReset(long epochSecond) {
        TimeZone tz = TimeZone.getDefault();
        Calendar c = calendar(shiftByResetHour(epochSecond), tz);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis() / 1000L + RESET_HOUR * 3600L - epochSecond;
    }

    public static boolean resetIfNeeded(JSONObject userData) {
        if (userData == null) {
            return false;
        }
        JSONObject shop = userData.getJSONObject("shop");
        JSONObject gp = shop == null ? null : shop.getJSONObject("GP");
        if (gp == null) {
            return false;
        }

        JSONObject periodic = userData.getJSONObject("periodicShop");
        if (periodic == null) {
            periodic = new JSONObject(true);
            userData.put("periodicShop", periodic);
        }

        long now = System.currentTimeMillis() / 1000L;
        long week = weekIndex(now);
        long month = monthIndex(now);
        boolean changed = false;

        // 周礼包
        JSONObject weekly = gp.getJSONObject("weekly");
        if (weekly != null && periodic.getLongValue("weeklyResetAt") != week) {
            weekly.put("info", new JSONArray());
            String groupId = currentGroupId("weeklyGroup");
            if (groupId != null) {
                weekly.put("curGroupId", groupId);
            }
            periodic.put("weeklyResetAt", week);
            changed = true;
            ArknightsApplication.LOGGER.info("[shop] 周礼包商店已重置（week={}，下次 {} 秒后）",
                    week, secondsToNextWeeklyReset(now));
        }

        // 月礼包
        JSONObject monthly = gp.getJSONObject("monthly");
        if (monthly != null && periodic.getLongValue("monthlyResetAt") != month) {
            monthly.put("info", new JSONArray());
            String groupId = currentGroupId("monthlyGroup");
            if (groupId != null) {
                monthly.put("curGroupId", groupId);
            }
            periodic.put("monthlyResetAt", month);
            changed = true;
            ArknightsApplication.LOGGER.info("[shop] 月礼包商店已重置（month={}，下次 {} 秒后）",
                    month, secondsToNextMonthlyReset(now));
        }

        return changed;
    }

    private static String currentGroupId(String node) {
        try {
            JSONObject gp = ArknightsApplication.GPGoodList;
            JSONObject group = gp == null ? null : gp.getJSONObject(node);
            return group == null ? null : group.getString("groupId");
        } catch (Exception e) {
            return null;
        }
    }
}
