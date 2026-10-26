package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.function.MissionHelper;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mission")
public class mission {
    public mission() {
    }
    @PostMapping(
            value = {"/confirmMission"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject confirmMission(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        String missionId = JsonBody.getString("missionId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /mission/confirmMission " + missionId);

        JSONObject result = new JSONObject(true);

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }

        Long uid = ((Account) Accounts.get(0)).getUid();
        if (((Account) Accounts.get(0)).getBan() == 1L) {
            response.setStatus(500);
            JSONObject err = new JSONObject(true);
            err.put("statusCode", 403);
            err.put("error", "Bad Request");
            err.put("message", "error");
            return err;
        }

        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());

        if (MissionHelper.entry(missionId) == null) {
            result.put("result", 1);
            result.put("error", "unknown mission: " + missionId);
            return result;
        }

        JSONArray items = MissionHelper.grantRewards(UserSyncData, missionId);
        MissionHelper.setClaimed(UserSyncData, missionId);

        String next = MissionHelper.nextMissionId(UserSyncData, missionId);
        if (next != null) {
            MissionHelper.setInProgress(UserSyncData, next);
            MissionHelper.refreshMission(UserSyncData, next);
        }

        JSONObject claimedEntry = MissionHelper.entry(missionId);
        if (claimedEntry != null && claimedEntry.getBooleanValue("haveSubMissionToUnlock")) {
            MissionHelper.syncSubMissions(UserSyncData);
        }

        userDao.setUserData(uid, UserSyncData);

        JSONObject modified = new JSONObject(true);
        modified.put("mission", UserSyncData.getJSONObject("mission"));
        modified.put("inventory", UserSyncData.getJSONObject("inventory"));
        modified.put("status", UserSyncData.getJSONObject("status"));

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("items", items);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[MissionHelper] confirm " + missionId + " → next=" + next + " items=" + items.toJSONString());
        return result;
    }

    @PostMapping(
            value = {"/autoConfirmMissions"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject autoConfirmMissions(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        String type = JsonBody.getString("type");
        if (type == null) {
            type = "MAIN";
        }
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /mission/autoConfirmMissions " + type);

        JSONObject result = new JSONObject(true);

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }

        Long uid = ((Account) Accounts.get(0)).getUid();
        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());

        JSONArray allItems = new JSONArray();
        JSONObject missionRoot = UserSyncData.getJSONObject("mission");
        JSONObject bucket = missionRoot.getJSONObject("missions").getJSONObject(type);

        if (bucket != null) {
            // 先收集再改，避免边遍历边改 map
            List<String> claimable = new ArrayList<>();
            for (String id : bucket.keySet()) {
                JSONObject node = bucket.getJSONObject(id);
                if (node == null || node.getIntValue("state") != 2) {
                    continue;
                }
                JSONArray progress = node.getJSONArray("progress");
                if (progress == null || progress.isEmpty()) {
                    continue;
                }
                boolean done = true;
                for (int i = 0; i < progress.size(); i++) {
                    JSONObject p = progress.getJSONObject(i);
                    if (p.getIntValue("value") < p.getIntValue("target")) {
                        done = false;
                        break;
                    }
                }
                if (done) {
                    claimable.add(id);
                }
            }

            for (String id : claimable) {
                JSONArray items = MissionHelper.grantRewards(UserSyncData, id);
                for (int i = 0; i < items.size(); i++) {
                    allItems.add(items.getJSONObject(i));
                }
                MissionHelper.setClaimed(UserSyncData, id);
                ArknightsApplication.LOGGER.info("[MissionHelper] autoConfirm 领取 " + id);
            }

            // 一键领取后按官服逻辑激活下一个：变「进行中」；条件已满足则直接可领取
            if (!claimable.isEmpty()) {
                String next = MissionHelper.nextMissionId(UserSyncData, claimable.get(claimable.size() - 1));
                if (next == null) {
                    next = MissionHelper.firstUnlockedUnclaimed(UserSyncData, type);
                }
                if (next != null) {
                    MissionHelper.setInProgress(UserSyncData, next);
                    MissionHelper.refreshMission(UserSyncData, next);
                }
            }
        }

        userDao.setUserData(uid, UserSyncData);

        JSONObject modified = new JSONObject(true);
        modified.put("mission", UserSyncData.getJSONObject("mission"));
        modified.put("inventory", UserSyncData.getJSONObject("inventory"));

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("items", allItems);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
