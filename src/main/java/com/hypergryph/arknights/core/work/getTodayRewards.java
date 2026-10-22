package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

public class getTodayRewards {
    public static JSONObject getTodayReward(int type, int index) {
        if (type == 1) {
            JSONArray chainLogInReward = ArknightsApplication.openServer.getJSONObject("dataMap").getJSONObject("openseverTaskGroup2").getJSONArray("chainLoginData");
            if (chainLogInReward == null || chainLogInReward.isEmpty()) {
                return null;
            }
            int idx = (index >= chainLogInReward.size()) ? 0 : index;   // 只处理越界
            Object obj = chainLogInReward.get(idx);
            return (obj instanceof JSONObject) ? (JSONObject) obj : null;
        } else if (type == 2) {
            JSONArray checkInReward = ArknightsApplication.openServer.getJSONObject("dataMap").getJSONObject("openseverTaskGroup2").getJSONArray("checkInData");
            if (checkInReward == null || checkInReward.isEmpty()) {
                return null;
            }
            int idx = (index >= checkInReward.size()) ? 0 : index;
            Object obj = checkInReward.get(idx);
            return (obj instanceof JSONObject) ? (JSONObject) obj : null;
        }
        return new JSONObject();
    }
}
