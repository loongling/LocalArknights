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

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mission")
public class mission {
    public mission() {
    }

    @PostMapping(value = {"/**"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject missionFallback(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody(required = false) JSONObject JsonBody,
                                      HttpServletRequest request) {
        ArknightsApplication.LOGGER.warn("[mission][未实现] {} body={}", request.getRequestURI(), JsonBody);
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("items", new com.alibaba.fastjson.JSONArray());
        JSONObject modified = new JSONObject(true);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }
    @PostMapping(
            value = {"/confirmMission"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject confirmMission(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
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

        // 领任务累积点数 + 发放点数里程碑奖励
        String missionType = MissionHelper.typeOf(missionId);
        MissionHelper.addPoint(UserSyncData, missionId);
        if ("DAILY".equals(missionType) || "WEEKLY".equals(missionType)) {
            JSONArray bonus = MissionHelper.grantPointRewards(UserSyncData, missionType);
            for (int i = 0; i < bonus.size(); i++) {
                items.add(bonus.getJSONObject(i));
            }
        }

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
    public JSONObject autoConfirmMissions(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
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
                // 领任务同时累积点数（每日/周），后面按点数发里程碑奖励
                MissionHelper.addPoint(UserSyncData, id);
                ArknightsApplication.LOGGER.info("[MissionHelper] autoConfirm 领取 " + id);
            }

            // 点数里程碑奖励（missionRewards.dailyPoint / weeklyPoint）
            if ("DAILY".equals(type) || "WEEKLY".equals(type)) {
                JSONArray bonus = MissionHelper.grantPointRewards(UserSyncData, type);
                for (int i = 0; i < bonus.size(); i++) {
                    allItems.add(bonus.getJSONObject(i));
                }
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
        modified.put("status", UserSyncData.getJSONObject("status"));

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("items", allItems);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/exchangeMissionRewards"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject exchangeMissionRewards(@RequestHeader(value = "secret", required = false) String secret,
                                             @RequestBody(required = false) JSONObject JsonBody,
                                             HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String type = JsonBody == null ? null : JsonBody.getString("type");
        // 客户端有时不带 type，从奖励 id 前缀推断（reward_weekly_* / reward_daily_*）
        if (type == null && JsonBody != null) {
            String rid = JsonBody.getString("targetRewardsId");
            if (rid == null) {
                rid = JsonBody.getString("rewardId");
            }
            if (rid != null) {
                if (rid.startsWith("reward_weekly")) {
                    type = "WEEKLY";
                } else if (rid.startsWith("reward_daily")) {
                    type = "DAILY";
                }
            }
        }
        if (type == null) {
            type = "DAILY";
        }
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /mission/exchangeMissionRewards type={} body={}", type,
                JsonBody);

        JSONObject result = new JSONObject(true);
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Long uid = ((Account) Accounts.get(0)).getUid();
        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());

        JSONArray wanted = new JSONArray();
        if (JsonBody != null) {
            // 客户端真实请求形态：{"targetRewardsId":"reward_daily_697"}
            if (JsonBody.getString("targetRewardsId") != null) {
                wanted.add(JsonBody.getString("targetRewardsId"));
            }
            if (JsonBody.getString("rewardId") != null) {
                wanted.add(JsonBody.getString("rewardId"));
            }
            if (JsonBody.getJSONArray("rewardIds") != null) {
                wanted.addAll(JsonBody.getJSONArray("rewardIds"));
            }
            if (JsonBody.containsKey("index")) {
                wanted.add(JsonBody.getIntValue("index"));
            }
            if (JsonBody.getJSONArray("indexList") != null) {
                wanted.addAll(JsonBody.getJSONArray("indexList"));
            }
        }

        JSONArray items = wanted.isEmpty()
                ? MissionHelper.grantPointRewards(UserSyncData, type)
                : MissionHelper.exchangePointRewards(UserSyncData, type, wanted);

        userDao.setUserData(uid, UserSyncData);

        JSONObject modified = new JSONObject(true);
        modified.put("mission", UserSyncData.getJSONObject("mission"));
        modified.put("inventory", UserSyncData.getJSONObject("inventory"));
        modified.put("status", UserSyncData.getJSONObject("status"));
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("result", 0);
        result.put("items", items);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
