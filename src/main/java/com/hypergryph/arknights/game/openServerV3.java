package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.work.processArray;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 新服开启活动 V3（openServerV3）
 *
 * 客户端实测接口（抓包 2026-11-02）：
 *   POST /openServerV3/getCheckInReward      {"index": 1}          累计签到领奖（index 是 0 基天序号）
 *   POST /openServerV3/getMilestoneReward    {"levels": [19,20]}   里程碑领奖（levels 为等级号）
 *
 * 存档结构（user.openServerV3）：
 *   checkIn   { groupId, history:[0/1,...] }              history[i]=1 表示第 i+1 天已领
 *   mission   { milestone: { groupId, point, got:[level...] } }
 *   fullOpen  { today, remain }
 *
 * 数据表：data/excel/open_server_table.json → openServerV3Data
 *   checkInData.<groupId>.checkInItemList [{order, isKeyItem, rewardList:[{id,count,type}]}]
 *   milestoneGroupData.<groupId>.itemList  [{level, reward:{id,count,type}, pointCnt, isBonus}]
 *
 * ITEM_PACK 类型要按 data/excel/item_table.json → itemPackInfos.<packId>.content 展开。
 */
@RestController
@RequestMapping("/openServerV3")
public class openServerV3 {

    private static JSONObject OPEN_SERVER_TABLE;
    private static JSONObject ITEM_PACK_INFOS;

    private static synchronized JSONObject table() {
        if (OPEN_SERVER_TABLE == null) {
            String path = System.getProperty("user.dir") + "/data/excel/open_server_table.json";
            try {
                OPEN_SERVER_TABLE = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[openServerV3] 读取 open_server_table.json 失败", e);
                OPEN_SERVER_TABLE = new JSONObject(true);
            }
        }
        return OPEN_SERVER_TABLE;
    }

    private static synchronized JSONObject itemPacks() {
        if (ITEM_PACK_INFOS == null) {
            String path = System.getProperty("user.dir") + "/data/excel/item_table.json";
            try {
                JSONObject itemTable = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
                ITEM_PACK_INFOS = itemTable.getJSONObject("itemPackInfos");
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[openServerV3] 读取 item_table.json 失败", e);
            }
            if (ITEM_PACK_INFOS == null) {
                ITEM_PACK_INFOS = new JSONObject(true);
            }
        }
        return ITEM_PACK_INFOS;
    }

    /** 发放单个奖励（ITEM_PACK 展开成内容物），返回给客户端的 items 数组 */
    private static void grantReward(JSONObject reward, JSONObject userData, JSONArray items) {
        if (reward == null) {
            return;
        }
        String type = reward.getString("type");
        if ("ITEM_PACK".equals(type)) {
            String packId = reward.getString("id");
            JSONObject pack = itemPacks().getJSONObject(packId);
            if (pack != null && pack.getJSONArray("content") != null) {
                int times = Math.max(1, reward.getIntValue("count"));
                JSONArray content = pack.getJSONArray("content");
                for (int t = 0; t < times; t++) {
                    for (int i = 0; i < content.size(); i++) {
                        JSONObject one = content.getJSONObject(i);
                        grantOne(one.getString("id"), one.getString("type"), one.getIntValue("count"), userData, items);
                    }
                }
                return;
            }
            ArknightsApplication.LOGGER.warn("[openServerV3] 未知礼包 {}", packId);
            return;
        }
        grantOne(reward.getString("id"), type, reward.getIntValue("count"), userData, items);
    }

    private static void grantOne(String id, String type, int count, JSONObject userData, JSONArray items) {
        JSONObject grant = new JSONObject(true);
        grant.put("id", id);
        grant.put("type", type);
        grant.put("count", count);
        // 只有 CHAR 分支会往 itemsOut 里塞 charGet，其它类型要自己补 items，
        // 注意 handleUserDataChange 不能调两次（会重复发货）
        if (ArknightsApplication.checkCheckInItemType(type) == 7) {
            processArray.handleUserDataChange(grant, userData, 0L, -1, items);
        } else {
            processArray.handleUserDataChange(grant, userData, 0L, -1);
            items.add(grant);
        }
    }

    @PostMapping(value = {"/getCheckInReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getCheckInReward(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody(required = false) JSONObject jsonBody,
                                       HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        int index = jsonBody == null ? -1 : jsonBody.getIntValue("index");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /openServerV3/getCheckInReward index=" + index);

        JSONObject result = new JSONObject(true);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            result.put("error", "账户验证失败");
            return result;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject v3 = userData.getJSONObject("openServerV3");
        if (v3 == null) {
            v3 = new JSONObject(true);
            userData.put("openServerV3", v3);
        }
        JSONObject checkIn = v3.getJSONObject("checkIn");
        if (checkIn == null) {
            checkIn = new JSONObject(true);
            checkIn.put("groupId", "new_login_g_1");
            checkIn.put("history", new JSONArray());
            v3.put("checkIn", checkIn);
        }
        String groupId = checkIn.getString("groupId");
        if (groupId == null || groupId.isEmpty()) {
            groupId = "new_login_g_1";
            checkIn.put("groupId", groupId);
        }

        JSONArray history = checkIn.getJSONArray("history");
        if (history == null) {
            history = new JSONArray();
            checkIn.put("history", history);
        }
        // 历史数组补齐到 index+1（补齐的天按「未领取」=1 处理）
        while (history.size() <= index) {
            history.add(1);
        }

        JSONArray items = new JSONArray();
        JSONObject data = table().getJSONObject("openServerV3Data");
        JSONObject group = data == null ? null
                : data.getJSONObject("checkInData") == null ? null
                : data.getJSONObject("checkInData").getJSONObject(groupId);
        if (group == null) {
            result.put("result", 1);
            result.put("items", items);
            result.put("playerDataDelta", emptyDelta());
            return result;
        }
        // history[i]: 0 = 已领取, 1 = 未领取；只有「未领取」才能领
        if (index < 0 || history.getIntValue(index) == 0) {
            // 已领过 / 非法下标
            result.put("items", items);
            result.put("playerDataDelta", emptyDelta());
            return result;
        }
        JSONArray list = group.getJSONArray("checkInItemList");
        JSONObject target = null;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                JSONObject one = list.getJSONObject(i);
                if (one != null && one.getIntValue("order") == index + 1) {   // order 是 1 基
                    target = one;
                    break;
                }
            }
        }
        if (target == null) {
            result.put("items", items);
            result.put("playerDataDelta", emptyDelta());
            return result;
        }
        // 特殊奖励日：第 specialRewardDay 天额外给 specialRewardItem？表里 rewardList 已含，无需额外处理
        JSONArray rewardList = target.getJSONArray("rewardList");
        if (rewardList != null) {
            for (int i = 0; i < rewardList.size(); i++) {
                grantReward(rewardList.getJSONObject(i), userData, items);
            }
        }
        history.set(index, 0);   // 领取后记为「已领取」
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        JSONObject v3Delta = new JSONObject(true);
        JSONObject checkInDelta = new JSONObject(true);
        checkInDelta.put("history", history);
        v3Delta.put("checkIn", checkInDelta);
        modified.put("openServerV3", v3Delta);
        modified.put("inventory", userData.getJSONObject("inventory"));
        modified.put("status", userData.getJSONObject("status"));
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));

        result.put("items", items);
        result.put("playerDataDelta", delta);
        return result;
    }

    @PostMapping(value = {"/getMilestoneReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getMilestoneReward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject jsonBody,
                                         HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        JSONArray levels = jsonBody == null ? null : jsonBody.getJSONArray("levels");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /openServerV3/getMilestoneReward levels=" + levels);

        JSONObject result = new JSONObject(true);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            result.put("error", "账户验证失败");
            return result;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject v3 = userData.getJSONObject("openServerV3");
        if (v3 == null) {
            v3 = new JSONObject(true);
            userData.put("openServerV3", v3);
        }
        JSONObject mission = v3.getJSONObject("mission");
        if (mission == null) {
            mission = new JSONObject(true);
            v3.put("mission", mission);
        }
        JSONObject milestone = mission.getJSONObject("milestone");
        if (milestone == null) {
            milestone = new JSONObject(true);
            milestone.put("groupId", "new_bp_g_1");
            milestone.put("point", 0);
            milestone.put("got", new JSONArray());
            mission.put("milestone", milestone);
        }
        String groupId = milestone.getString("groupId");
        if (groupId == null || groupId.isEmpty()) {
            groupId = "new_bp_g_1";
            milestone.put("groupId", groupId);
        }
        JSONArray got = milestone.getJSONArray("got");
        if (got == null) {
            got = new JSONArray();
            milestone.put("got", got);
        }
        int point = milestone.getIntValue("point");

        JSONArray items = new JSONArray();
        JSONObject data = table().getJSONObject("openServerV3Data");
        JSONObject group = data == null ? null
                : data.getJSONObject("milestoneGroupData") == null ? null
                : data.getJSONObject("milestoneGroupData").getJSONObject(groupId);
        if (group == null || levels == null) {
            result.put("items", items);
            result.put("playerDataDelta", emptyDelta());
            return result;
        }
        JSONArray itemList = group.getJSONArray("itemList");
        java.util.Map<Integer, JSONObject> byLevel = new java.util.HashMap<>();
        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                JSONObject one = itemList.getJSONObject(i);
                if (one != null) {
                    byLevel.put(one.getIntValue("level"), one);
                }
            }
        }
        for (int i = 0; i < levels.size(); i++) {
            int level = levels.getIntValue(i);
            if (got.contains(level)) {
                continue;                                  // 已领
            }
            JSONObject entry = byLevel.get(level);
            if (entry == null || entry.getIntValue("pointCnt") > point) {
                continue;                                  // 等级不存在 / 积分不够
            }
            grantReward(entry.getJSONObject("reward"), userData, items);
            got.add(level);
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        JSONObject v3Delta = new JSONObject(true);
        JSONObject missionDelta = new JSONObject(true);
        JSONObject milestoneDelta = new JSONObject(true);
        milestoneDelta.put("got", got);
        milestoneDelta.put("point", point);
        missionDelta.put("milestone", milestoneDelta);
        v3Delta.put("mission", missionDelta);
        modified.put("openServerV3", v3Delta);
        modified.put("inventory", userData.getJSONObject("inventory"));
        modified.put("status", userData.getJSONObject("status"));
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));

        result.put("items", items);
        result.put("playerDataDelta", delta);
        return result;
    }

    private static JSONObject emptyDelta() {
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        return delta;
    }
}
