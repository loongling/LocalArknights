package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONArray;
import com.hypergryph.arknights.ArknightsApplication;

import static com.hypergryph.arknights.core.work.checkCanCheckIn.canCheckIn;

public class checkActLogin {

    public static boolean needAddIndex(long lastLoginTs, int index, JSONArray history) {
        long ts = ArknightsApplication.getTimestamp();
        if (canCheckIn(lastLoginTs, ts)) {
            if (history == null || history.isEmpty()) {
                return false;
            }
            return history.size() > index;
        } else{
            return false;
        }
    }

    public static boolean needResetIndex(long lastLoginTs) {
        if (lastLoginTs <= 0) {
            return true;
        }
        long ts = ArknightsApplication.getTimestamp();
        long thirdDayReset = checkCanCheckIn.nextResetAt(lastLoginTs) + 86400L;
        return ts >= thirdDayReset;
    }
}
