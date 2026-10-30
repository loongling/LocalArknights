package com.hypergryph.arknights.game;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hypergryph.arknights.core.work.buildingUpdateLastTs.replaceLastUpdateTime;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.canCheckIn;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.getTomorrow4Am;

@RestController
@RequestMapping("/building")
public class building {

    @PostMapping(
            value = {"/sync"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject sync(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/sync");
        ArknightsApplication.LOGGER.info("BuildingSync:" + jsonBody);
        long ts = ArknightsApplication.getTimestamp();
        JSONObject result = new JSONObject();
        if(checkAccount(secret)){
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            Account account = accounts.get(0);
            JSONObject userData = JSON.parseObject(account.getUser());
            JSONObject playerDataDelta = new JSONObject();
            JSONObject modified = new JSONObject();
            JSONObject deleted = new JSONObject();
            result.put("ts", ts);
            JSONObject building = new JSONObject();
            if (canCheckIn(userData.getJSONObject("event").getLong("building"), ArknightsApplication.getTimestamp())) {
                userData.getJSONObject("event").put("building", getTomorrow4Am());
                JSONObject rlv2 = userData.getJSONObject("rlv2");
                JSONObject mission = userData.getJSONObject("mission");
                JSONObject openServer = userData.getJSONObject("openServer");
                JSONObject gachaNew = userData.getJSONObject("gacha").getJSONObject("newbee");
                JSONObject gacha = new JSONObject();
                gacha.put("gacha", gachaNew);
                JSONObject social = userData.getJSONObject("social");
                JSONObject retro = userData.getJSONObject("retro");
                JSONObject crisisV2 = userData.getJSONObject("crisisV2");
                JSONObject campaignsV2 = userData.getJSONObject("campaignsV2");
                JSONObject sandboxPerm = userData.getJSONObject("sandboxPerm");
                JSONObject dungeon = userData.getJSONObject("dungeon");
                JSONObject mainline = userData.getJSONObject("mainline");
                JSONObject shop = userData.getJSONObject("shop");
                JSONObject nameCardStyle = userData.getJSONObject("nameCardStyle");
                building =userData.getJSONObject("building");
                JSONObject buildingRoom = new JSONObject();
                buildingRoom = userData.getJSONObject("building").getJSONObject("rooms");
                building.put("rooms", buildingRoom);
                playerDataDelta.put("deleted", deleted);
                modified.put("rlv2", rlv2);
                modified.put("mission", mission);
                modified.put("gacha", gacha);
                modified.put("social", social);
                modified.put("openServer", openServer);
                modified.put("campaigns", campaignsV2);
                modified.put("sandboxPerm", sandboxPerm);
                modified.put("dungeon", dungeon);
                modified.put("mainline", mainline);
                modified.put("shop", shop);
                modified.put("nameCardStyle", nameCardStyle);
                modified.put("retro", retro);
                modified.put("crisisV2", crisisV2);
                modified.put("building", building);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                try {
                    userDao.setUserData(account.getUid(), userData);
                } catch (Exception e) {
                    ArknightsApplication.LOGGER.error("[building/sync] 每日重置落库失败", e);
                }
                ArknightsApplication.LOGGER.info("[building/sync] 每日重置已落库event.building={}",
                        userData.getJSONObject("event").getLong("building"));
                return result;
            } else {
                JSONObject eventBuilding = new JSONObject();
                long nowTs = ArknightsApplication.getTimestamp();
                long buildingEvent = userData.getJSONObject("event").getLongValue("building");
                boolean needSave = false;
                if (buildingEvent < nowTs) {
                    buildingEvent = getTomorrow4Am();
                    userData.getJSONObject("event").put("building", buildingEvent);
                    needSave = true;
                }
                eventBuilding.put("building", buildingEvent);
                modified.put("event", eventBuilding);

                if (advanceBuildingTime(userData, nowTs)) {
                    needSave = true;
                }

                JSONObject buildingStatus = userData.getJSONObject("building").getJSONObject("status");
                JSONObject buildingRoom = userData.getJSONObject("building").getJSONObject("rooms");
                JSONObject buildingChar = userData.getJSONObject("building").getJSONObject("chars");
                JSONObject buildingRoomSlot = userData.getJSONObject("building").getJSONObject("roomSlots");
                JSONObject Building = new JSONObject();
                Building.put("rooms", buildingRoom);
                Building.put("roomSlots", buildingRoomSlot);
                Building.put("status", buildingStatus);
                Building.put("chars", buildingChar);
                Building.put("assist", userData.getJSONObject("building").getJSONArray("assist"));
                modified.put("building", Building);
                building.put("status", buildingStatus);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);

                if (needSave) {
                    try {
                        userDao.setUserData(account.getUid(), userData);
                    } catch (Exception e) {
                        ArknightsApplication.LOGGER.error("[building/sync] 落库失败", e);
                    }
                }
                return result;
            }
        }
        else{
            return createError(secret, response);
        }
    }

    private static JSONObject LAYOUT_SLOTS;
    private static JSONObject LAYOUT_CLEAN_COSTS;
    private static JSONObject ROOMS_TABLE;
    private static JSONObject ROOM_UNLOCK_CONDS;
    private static JSONObject MANUFACT_DATA;
    private static JSONObject TRADING_DATA;
    private static JSONObject MANUFACT_FORMULAS;
    private static JSONObject WORKSHOP_FORMULAS;
    private static JSONObject DORM_DATA;
    private static JSONObject FURNITURES_CHAR_TABLE;
    private static JSONObject FURNITURE_GROUPS;
    private static final double HIRE_REFRESH_POINT = 4320000;
    private static final double LABOR_POINT_PER_DRONE = 360;
    private static final double CLUE_EXCHANGE_POINTS = 7200000;
    private static JSONObject BUILDING_CHAR_BUFFS;
    private static JSONObject BUILDING_BUFFS;
    private static double COMFORT_RECOVER_FACTOR = 25;

    public static boolean advanceBuildingTime(JSONObject userData, long now) {
        loadLayout();
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            return false;
        }
        boolean dirty = false;

        refreshLaborBuffSpeed(building, userData);

        JSONObject status = building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        if (labor != null) {
            int max = labor.getIntValue("maxValue");
            int value = labor.getIntValue("value");
            long last = labor.getLongValue("lastUpdateTime");
            if (last <= 0 || last > now) {
                labor.put("lastUpdateTime", now);
                dirty = true;
            } else if (max > 0) {
                double buff = labor.getDoubleValue("buffSpeed");
                double rate = 1.0 + Math.max(0, buff);          // ??
                double pp = labor.getDoubleValue("processPoint") + (now - last) * rate;
                boolean changed = false;
                while (pp >= LABOR_POINT_PER_DRONE && value < max) {
                    pp -= LABOR_POINT_PER_DRONE;
                    value++;
                    changed = true;
                }
                if (value >= max) {
                    pp = 0;
                } else if (pp >= LABOR_POINT_PER_DRONE) {
                    pp = LABOR_POINT_PER_DRONE - 0.01;
                }
                labor.put("value", value);
                labor.put("processPoint", (long) Math.round(pp));
                labor.put("lastUpdateTime", now);
                if (changed) {
                    ArknightsApplication.LOGGER.info("[building] 无人机回??{}/{}（buffSpeed={}?", value, max, buff);
                }
                dirty = true;
            }
        }

        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject manu = rooms == null ? null : rooms.getJSONObject("MANUFACTURE");
        JSONObject slots = building.getJSONObject("roomSlots");
        if (manu != null && !manu.isEmpty()) {
            for (String slotId : new ArrayList<>(manu.keySet())) {
                JSONObject node = manu.getJSONObject(slotId);
                if (node == null) {
                    continue;
                }
                long persistedLast = node.getLongValue("lastUpdateTime");
                int beforeOut = node.getIntValue("outputSolutionCnt");
                int beforeState = node.getIntValue("state");

                int level = 1;
                JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
                if (slot != null) {
                    level = Math.max(1, slot.getIntValue("level"));
                }
                advanceManufacture(node, level, now);

                boolean produced = node.getIntValue("outputSolutionCnt") != beforeOut;
                boolean stateChanged = node.getIntValue("state") != beforeState;
                if (produced || stateChanged) {
                    dirty = true;
                } else if (node.getIntValue("state") == 1 && now - persistedLast >= 5) {
                    dirty = true;
                }
            }
        }

        JSONObject trade = rooms == null ? null : rooms.getJSONObject("TRADING");
        if (trade != null && !trade.isEmpty()) {
            for (String slotId : new ArrayList<>(trade.keySet())) {
                JSONObject node = trade.getJSONObject(slotId);
                if (node == null) {
                    continue;
                }
                int level = 1;
                JSONArray slotChars = null;
                JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
                if (slot != null) {
                    level = Math.max(1, slot.getIntValue("level"));
                    slotChars = slot.getJSONArray("charInstIds");
                }
                if (advanceTrading(node, level, now, slotChars)) {
                    dirty = true;
                }
            }
        }

        if (repairRoomData(userData) > 0) {
            dirty = true;
        }

        JSONObject meetingRooms = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (meetingRooms != null && !meetingRooms.isEmpty()) {
            long dailyStart = dailyResetTs(now);
            for (String slotId : new ArrayList<>(meetingRooms.keySet())) {
                JSONObject node = meetingRooms.getJSONObject(slotId);
                if (node == null) {
                    continue;
                }
                JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
                JSONArray stationed = slot == null ? null : slot.getJSONArray("charInstIds");
                int onDuty = 0;
                if (stationed != null) {
                    for (int i = 0; i < stationed.size(); i++) {
                        Integer v = stationed.getInteger(i);
                        if (v != null && v > 0) {
                            onDuty++;
                        }
                    }
                }
                long produced = node.getLongValue("clueProduceTs");
                Object pendingRaw = node.get("dailyReward");
                boolean hasPending = pendingRaw instanceof JSONObject && !((JSONObject) pendingRaw).isEmpty();
                if (onDuty > 0 && produced < dailyStart && !hasPending) {
                    node.put("dailyReward", makeClueItem(userData, null, missingFaction(node), now, 1));
                    node.put("clueProduceTs", dailyStart);
                    dirty = true;
                    ArknightsApplication.LOGGER.info("[building] meeting {} daily clue ready in dailyReward (onDuty={})",
                            slotId, onDuty);
                }
                JSONObject board = node.getJSONObject("board");
                int factionsOnBoard = board == null ? 0 : board.size();
                long last = node.getLongValue("lastUpdateTime");
                double speed = Math.max(1, node.getDoubleValue("speed"));
                double pp = node.getDoubleValue("processPoint");
                if (factionsOnBoard >= 7) {
                    if (pp <= 0) {
                        pp = CLUE_EXCHANGE_POINTS;
                        node.put("clueExchangeStartedTs", now);
                        dirty = true;
                        ArknightsApplication.LOGGER.info("[building] 会?室线?流开始（7 条齐?");
                    }
                }
                if (pp > 0 && last > 0 && last <= now) {
                    long elapsed = now - last;
                    double left = pp - elapsed * speed;
                    if (left <= 0) {
                        left = 0;
                        JSONObject isNode = node.getJSONObject("infoShare");
                        if (isNode == null) {
                            isNode = new JSONObject(true);
                            isNode.put("ts", 0L);
                            node.put("infoShare", isNode);
                        }
                        isNode.put("reward", 1);
                        node.put("clueExchangeReady", true);
                        ArknightsApplication.LOGGER.info("[building] info share expired, waiting to claim");
                        dirty = true;
                    }
                    node.put("processPoint", (long) Math.round(left));
                    node.put("completeWorkTime", now + (long) Math.ceil(left / speed));
                    dirty = true;
                } else {
                    node.put("processPoint", (long) Math.round(Math.max(0, pp)));
                    node.put("completeWorkTime", now + (long) Math.ceil(Math.max(0, pp) / speed));
                }
                node.put("lastUpdateTime", now);
                JSONObject infoShare = node.getJSONObject("infoShare");
                if (infoShare != null) {
                    long shareTs = infoShare.getLongValue("ts");
                    if (shareTs > 0 && now >= shareTs && infoShare.getIntValue("reward") == 0) {
                        infoShare.put("reward", 1);
                        node.put("clueExchangeReady", true);
                        ArknightsApplication.LOGGER.info("[building] info share expired, reward=1 (claimable)");
                        dirty = true;
                    }
                }
                JSONObject leaveNode = node.getJSONObject("messageLeave");
                if (leaveNode != null) {
                    JSONObject spNode = leaveNode.getJSONObject("sp");
                    if (spNode == null) {
                        spNode = new JSONObject(true);
                        spNode.put("lastWeek", 0);
                        spNode.put("lastWeekSum", 0);
                        spNode.put("thisWeek", 0);
                        spNode.put("thisWeekSum", 0);
                        leaveNode.put("sp", spNode);
                    }
                    long lastSpTs = leaveNode.getLongValue("lastUpdateSpTs");
                    long thisWeekStart = com.hypergryph.arknights.core.dao.visitDao.weekStartTs(now);
                    if (lastSpTs > 0
                            && com.hypergryph.arknights.core.dao.visitDao.weekStartTs(lastSpTs) < thisWeekStart) {
                        spNode.put("lastWeek", spNode.getIntValue("thisWeek"));
                        spNode.put("lastWeekSum", spNode.getIntValue("thisWeekSum"));
                        spNode.put("thisWeek", 0);
                        spNode.put("thisWeekSum", 0);
                        leaveNode.put("lastUpdateSpTs", now);
                        dirty = true;
                        ArknightsApplication.LOGGER.info("[building] 留言板跨周翻转 lastWeek={} lastWeekSum={}",
                                spNode.getIntValue("lastWeek"), spNode.getIntValue("lastWeekSum"));
                    } else if (lastSpTs <= 0) {
                        leaveNode.put("lastUpdateSpTs", now);
                    }
                }
                // 用访客表的真实数据校准留言板计数：本周待领 = 未领访客×30，已领 = 已领访客×30。
                // 这样红点状态永远和「访客列表」自洽，不会因为历史增量操作残留。
                try {
                    long ownerUid = userData.getJSONObject("status") == null ? 0
                            : userData.getJSONObject("status").getLongValue("uid");
                    long ws = com.hypergryph.arknights.core.dao.visitDao.weekStartTs(now);
                    int unclaimed = com.hypergryph.arknights.core.dao.visitDao.countUnclaimed(ownerUid);
                    int claimedCnt = com.hypergryph.arknights.core.dao.visitDao.countClaimedSince(ownerUid, ws);
                    JSONObject lv = node.getJSONObject("messageLeave");
                    if (lv != null) {
                        JSONObject spn = lv.getJSONObject("sp");
                        if (spn == null) {
                            spn = new JSONObject(true);
                            spn.put("lastWeek", 0);
                            spn.put("lastWeekSum", 0);
                            lv.put("sp", spn);
                        }
                        int wantWeek = unclaimed * 30;
                        int wantSum = claimedCnt * 30;
                        if (spn.getIntValue("thisWeek") != wantWeek) {
                            spn.put("thisWeek", wantWeek);
                            dirty = true;
                        }
                        if (spn.getIntValue("thisWeekSum") != wantSum) {
                            spn.put("thisWeekSum", wantSum);
                            dirty = true;
                        }
                    }
                } catch (Exception e) {
                    ArknightsApplication.LOGGER.warn("[building] 校准留言板计数失败: {}", e.toString());
                }
                normalizeClues(userData, null, node);
            }
        }

        JSONObject hire = rooms == null ? null : rooms.getJSONObject("HIRE");
        if (hire != null && !hire.isEmpty()) {
            for (String slotId : new ArrayList<>(hire.keySet())) {
                JSONObject node = hire.getJSONObject(slotId);
                if (node == null) {
                    continue;
                }
                JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
                int level = slot == null ? 1 : Math.max(1, slot.getIntValue("level"));
                int cap = level >= 3 ? 3 : (level >= 2 ? 2 : 1);
                long last = node.getLongValue("lastUpdateTime");
                if (last <= 0 || last > now) {
                    node.put("lastUpdateTime", now);
                    continue;
                }
                long elapsed = Math.min(now - last, 3600);
                double speed = node.getDoubleValue("speed");
                if (speed <= 0) {
                    speed = 100;
                }
                double pp = node.getDoubleValue("processPoint") + elapsed * speed;
                int count = node.getIntValue("refreshCount");
                boolean changed = false;
                while (pp >= HIRE_REFRESH_POINT && count < cap) {
                    pp -= HIRE_REFRESH_POINT;
                    count++;
                    changed = true;
                }
                if (pp >= HIRE_REFRESH_POINT) {
                    pp = HIRE_REFRESH_POINT;
                }
                node.put("processPoint", (long) Math.round(pp));
                node.put("refreshCount", count);
                node.put("lastUpdateTime", now);
                if (changed) {
                    ArknightsApplication.LOGGER.info("[building] 办公?{} 联络点数恢? ?{}/{}", slotId, count, cap);
                    dirty = true;
                }
            }
        }
        JSONObject training = rooms == null ? null : rooms.getJSONObject("TRAINING");
        if (training != null && !training.isEmpty()) {
            for (String slotId : new ArrayList<>(training.keySet())) {
                JSONObject node = training.getJSONObject(slotId);
                if (node != null && advanceTraining(userData, node, now)) {
                    dirty = true;
                }
            }
        }

        // 5) 干员心情（building.chars[].ap）：派驻消??空闲回?
        if (advanceBuildingCharsAp(building, now)) {
            dirty = true;
        }
        return dirty;
    }

    private static final int CHAR_AP_MAX = 8640000;
    private static final double CHAR_AP_PER_SECOND = 360000.0 / 3600.0;

    private static double dormRecoverPerSecond(int comfort, int level) {
        double base = 160;
        try {
            base = DORM_DATA.getJSONArray("phases")
                    .getJSONObject(Math.max(0, level - 1)).getDoubleValue("manpowerRecover");
        } catch (Exception ignored) {
            // 数据缺失?1 ?160
        }
        double factor = COMFORT_RECOVER_FACTOR > 0 ? COMFORT_RECOVER_FACTOR : 25;
        return base + comfort / factor;
    }

    private static double charApRate(JSONObject room, String roomType, int level) {
        if (roomType == null || roomType.isEmpty()) {
            return 0.0;
        }
        if ("DORMITORY".equals(roomType)) {
            int comfort = room == null ? 0 : room.getIntValue("comfort");
            return dormRecoverPerSecond(comfort, level);
        }
        if ("MANUFACTURE".equals(roomType) || "TRADING".equals(roomType)) {
            return room != null && room.getIntValue("state") == 1 ? -CHAR_AP_PER_SECOND : 0.0;
        }
        return -CHAR_AP_PER_SECOND;
    }

    private static boolean advanceBuildingCharsAp(JSONObject building, long now) {
        JSONObject chars = building.getJSONObject("chars");
        if (chars == null || chars.isEmpty()) {
            return false;
        }
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject rooms = building.getJSONObject("rooms");

        java.util.Map<String, String> dutySlot = new java.util.HashMap<>();
        if (slots != null) {
            for (String slotId : slots.keySet()) {
                JSONObject slot = slots.getJSONObject(slotId);
                if (slot == null || slot.getIntValue("state") != 2) {
                    continue;
                }
                JSONArray ids = slot.getJSONArray("charInstIds");
                if (ids == null) {
                    continue;
                }
                for (int i = 0; i < ids.size(); i++) {
                    dutySlot.put(String.valueOf(ids.get(i)), slotId);
                }
            }
        }

        boolean dirty = false;
        for (String key : new ArrayList<>(chars.keySet())) {
            JSONObject c = chars.getJSONObject(key);
            if (c == null) {
                continue;
            }
            long lastAp = c.getLongValue("lastApAddTime");
            if (lastAp <= 0 || lastAp > now) {
                c.put("lastApAddTime", now);
                dirty = true;
                continue;
            }
            long elapsed = now - lastAp;
            if (elapsed <= 0) {
                continue;
            }
            if (elapsed > 600) {
                elapsed = 600;
            }

            String slotId = dutySlot.get(key);
            if (slotId == null) {
                String own = c.getString("roomSlotId");
                if (own != null && !own.isEmpty() && slots != null && slots.containsKey(own)) {
                    slotId = own;
                }
            }

            String roomType = null;
            int level = 1;
            JSONObject roomNode = null;
            if (slotId != null) {
                JSONObject slot = slots.getJSONObject(slotId);
                if (slot != null) {
                    roomType = slot.getString("roomId");
                    level = Math.max(1, slot.getIntValue("level"));
                    if (rooms != null && roomType != null) {
                        JSONObject bucket = rooms.getJSONObject(roomType);
                        if (bucket != null) {
                            roomNode = bucket.getJSONObject(slotId);
                        }
                    }
                }
            }

            double rate = charApRate(roomNode, roomType, level);
            int ap = c.containsKey("ap") ? c.getIntValue("ap") : CHAR_AP_MAX;
            if (rate == 0.0) {
                c.put("changeScale", 0);
                c.put("lastApAddTime", now);
                if (ap != CHAR_AP_MAX && !"DORMITORY".equals(roomType)) {
                    // 空闲/停摆：既不消耗也不回?
                }
                continue;
            }

            long deltaUnits = (long) Math.abs(elapsed * rate);
            if (deltaUnits <= 0) {
                c.put("lastApAddTime", now);
                continue;
            }
            int newAp;
            if (rate < 0) {
                newAp = (int) Math.max(0, ap - deltaUnits);
                c.put("workTime", c.getLongValue("workTime") + elapsed);
            } else {
                newAp = (int) Math.min(CHAR_AP_MAX, ap + deltaUnits);
            }
            c.put("changeScale", (int) Math.round(rate));
            if (newAp != ap) {
                c.put("ap", newAp);
                dirty = true;
            }
            c.put("lastApAddTime", now);
        }
        return dirty;
    }

    private static synchronized void loadLayout() {
        if (LAYOUT_SLOTS != null) {
            return;
        }
        String path = System.getProperty("user.dir") + "/data/excel/building_data.json";
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            JSONObject data = JSON.parseObject(content);
            JSONObject v0 = data.getJSONObject("layouts").getJSONObject("v0");
            LAYOUT_SLOTS = v0.getJSONObject("slots");
            LAYOUT_CLEAN_COSTS = v0.getJSONObject("cleanCosts");
            ROOMS_TABLE = data.getJSONObject("rooms");
            ROOM_UNLOCK_CONDS = data.getJSONObject("roomUnlockConds");
            MANUFACT_DATA = data.getJSONObject("manufactData");
            TRADING_DATA = data.getJSONObject("tradingData");
            MANUFACT_FORMULAS = data.getJSONObject("manufactFormulas");
            WORKSHOP_FORMULAS = data.getJSONObject("workshopFormulas");
            DORM_DATA = data.getJSONObject("dormData");
            double factor = data.getDoubleValue("comfortManpowerRecoverFactor");
            COMFORT_RECOVER_FACTOR = factor > 0 ? factor : 25;
            try {
                JSONObject custom = data.getJSONObject("customData");
                FURNITURES_CHAR_TABLE = custom.getJSONObject("furnitures");
                FURNITURE_GROUPS = custom.getJSONObject("groups");
            } catch (Exception ignored) {
                FURNITURES_CHAR_TABLE = new JSONObject();
                FURNITURE_GROUPS = new JSONObject();
            }
            try {
                BUILDING_CHAR_BUFFS = data.getJSONObject("chars");
                BUILDING_BUFFS = data.getJSONObject("buffs");
            } catch (Exception ignored) {
                BUILDING_CHAR_BUFFS = new JSONObject();
                BUILDING_BUFFS = new JSONObject();
            }
        } catch (IOException e) {
            ArknightsApplication.LOGGER.error("[building] 读取 {} 失败: {}", path, e.toString());
            LAYOUT_SLOTS = new JSONObject();
            LAYOUT_CLEAN_COSTS = new JSONObject();
            ROOMS_TABLE = new JSONObject();
            ROOM_UNLOCK_CONDS = new JSONObject();
            MANUFACT_DATA = new JSONObject();
            TRADING_DATA = new JSONObject();
            MANUFACT_FORMULAS = new JSONObject();
            WORKSHOP_FORMULAS = new JSONObject();
            DORM_DATA = new JSONObject();
            FURNITURES_CHAR_TABLE = new JSONObject();
            FURNITURE_GROUPS = new JSONObject();
            BUILDING_CHAR_BUFFS = new JSONObject();
            BUILDING_BUFFS = new JSONObject();
        }
    }

    private static double manufactBaseSpeed(int level) {
        try {
            JSONArray phases = MANUFACT_DATA.getJSONArray("phases");
            return phases.getJSONObject(Math.max(0, level - 1)).getDoubleValue("speed");
        } catch (Exception e) {
            return 1.0;
        }
    }

    private static double manufactCostPoint(String formulaId) {
        try {
            JSONObject f = MANUFACT_FORMULAS.getJSONObject(formulaId);
            double v = f == null ? 0 : f.getDoubleValue("costPoint");
            return v > 0 ? v : 2700.0;
        } catch (Exception e) {
            return 2700.0;
        }
    }

    private static double basementProduceSpeed() {
        try {
            double v = ArknightsApplication.serverConfig.getJSONObject("basement").getDoubleValue("produceSpeed");
            return v > 0 ? v : 1.0;
        } catch (Exception e) {
            return 1.0;
        }
    }

    private static void advanceManufacture(JSONObject node, int level, long now) {
        if (node == null) {
            return;
        }
        long last = node.getLongValue("lastUpdateTime");
        if (last <= 0 || last > now) {
            node.put("lastUpdateTime", now);
            node.put("processPoint", 0.0);
            return;
        }

        if (node.getIntValue("state") != 1) {
            node.put("lastUpdateTime", now);
            return;
        }

        int capacity = node.getIntValue("capacity");
        if (capacity <= 0) {
            capacity = manufactOutputCapacity(level);
        }
        int remain = node.getIntValue("remainSolutionCnt");
        int output = node.getIntValue("outputSolutionCnt");

        double bonus = 0;
        JSONObject buff = node.getJSONObject("buff");
        if (buff == null) {
            buff = new JSONObject(true);
            node.put("buff", buff);
        }

        double produceSpeed = basementProduceSpeed();
        buff.put("speed", produceSpeed - 1.0);
        bonus = buff.getDoubleValue("speed");
        double speed = manufactBaseSpeed(level) * (1.0 + bonus);
        double costPoint = manufactCostPoint(node.getString("formulaId"));

        double save = node.getDoubleValue("saveTime") + (now - last) * speed;
        int canMake = Math.max(0, Math.min(remain, capacity - output));
        int produced = (int) (save / costPoint);
        if (produced > canMake) {
            produced = canMake;
            save = canMake * costPoint;
        }
        if (produced > 0) {
            save -= produced * costPoint;
        }
        if (save < 0) {
            save = 0;
        }

        int newOutput = output + produced;
        int newRemain = remain - produced;
        node.put("saveTime", (long) save);
        node.put("outputSolutionCnt", newOutput);
        node.put("remainSolutionCnt", newRemain);
        node.put("processPoint", save / costPoint);
        node.put("lastUpdateTime", now);

        if (newRemain <= 0 || newOutput >= capacity) {
            node.put("state", 0);
            node.put("saveTime", 0L);
            node.put("processPoint", 0.0);
            ArknightsApplication.LOGGER.info("[manufacture] 停产：{}（剩 {} 件，?{}/{}?",
                    newRemain <= 0 ? "配方已完?" : "仓库已满", newRemain, newOutput, capacity);
        }
    }

    private static int manufactOutputCapacity(int level) {
        try {
            JSONArray phases = MANUFACT_DATA.getJSONArray("phases");
            return phases.getJSONObject(Math.max(0, level - 1)).getIntValue("outputCapacity");
        } catch (Exception e) {
            return 24;
        }
    }

    private static int tradingOrderLimit(int level) {
        try {
            JSONArray phases = TRADING_DATA.getJSONArray("phases");
            return phases.getJSONObject(Math.max(0, level - 1)).getIntValue("orderLimit");
        } catch (Exception e) {
            return 6;
        }
    }

    private static final java.util.Map<String, String> AUTO_ROOM = new java.util.HashMap<>();

    static {
        AUTO_ROOM.put("ELEVATOR", "ELEVATOR");
        AUTO_ROOM.put("CORRIDOR", "CORRIDOR");
        AUTO_ROOM.put("SPECIAL", "CONTROL");
        AUTO_ROOM.put("CUSTOM", "DORMITORY");
        AUTO_ROOM.put("CUSTOM_P", "PRIVATE");
    }

    public static int repairAutoRooms(JSONObject userData) {
        loadLayout();
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            return 0;
        }
        JSONObject slots = building.getJSONObject("roomSlots");
        if (slots == null) {
            return 0;
        }
        int fixed = 0;
        for (String slotId : new ArrayList<>(slots.keySet())) {
            JSONObject node = slots.getJSONObject(slotId);
            JSONObject def = LAYOUT_SLOTS.getJSONObject(slotId);
            if (node == null || def == null) {
                continue;
            }
            if (node.getIntValue("state") != 0) {
                continue;
            }
            String autoRoom = AUTO_ROOM.get(def.getString("category"));
            if (autoRoom == null) {
                continue;
            }
            node.put("level", 1);
            node.put("state", 2);
            node.put("roomId", autoRoom);
            if (node.getJSONArray("charInstIds") == null) {
                node.put("charInstIds", new JSONArray());
            }
            if (!node.containsKey("completeConstructTime")) {
                node.put("completeConstructTime", -1);
            }
            fixed++;
            ArknightsApplication.LOGGER.info("[building] ???房间?{} ?{} (level 1, state 2)", slotId, autoRoom);
        }

        fixed += repairRoomData(userData);
        return fixed;
    }

    public static int repairRoomData(JSONObject userData) {
        loadLayout();
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            return 0;
        }
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject rooms = building.getJSONObject("rooms");
        if (slots == null) {
            return 0;
        }
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }

        long now = ArknightsApplication.getTimestamp();
        int fixed = 0;

        // 基建?列表：官服是 building.assist = [id,-1,-1,-1,-1]
        JSONArray assistArr = building.getJSONArray("assist");
        if (assistArr == null || assistArr.size() < 5) {
            JSONArray fresh = new JSONArray();
            for (int i = 0; i < 5; i++) {
                fresh.add(-1);
            }
            building.put("assist", fresh);
            fixed++;
            ArknightsApplication.LOGGER.info("[building] 补建基建?列表 assist=[-1×5]");
        }

        if (building.getJSONObject("furniture") == null) {
            building.put("furniture", new JSONObject(true));
            fixed++;
        }
        JSONObject sol = building.getJSONObject("solution");
        if (sol == null) {
            sol = new JSONObject(true);
            building.put("solution", sol);
            fixed++;
        }
        if (sol.getJSONObject("furnitureTs") == null) {
            sol.put("furnitureTs", new JSONObject(true));
            fixed++;
        }

        for (String slotId : new ArrayList<>(slots.keySet())) {
            JSONObject slot = slots.getJSONObject(slotId);
            if (slot == null || slot.getIntValue("state") != 2) {
                continue;
            }
            String roomType = slot.getString("roomId");
            if (roomType == null || "NONE".equals(roomType) || "ELEVATOR".equals(roomType) || "CORRIDOR".equals(roomType)) {
                continue;
            }
            int level = Math.max(1, slot.getIntValue("level"));
            JSONObject bucket = rooms.getJSONObject(roomType);
            if (bucket == null) {
                bucket = new JSONObject(true);
                rooms.put(roomType, bucket);
            }
            JSONObject node = bucket.getJSONObject(slotId);

            if ("MANUFACTURE".equals(roomType)) {
                int cap = manufactOutputCapacity(level);
                if (node == null) {
                    bucket.put(slotId, defaultRoomData(roomType, level, now));
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 补建制?站数据 {} (capacity={})", slotId, cap);
                } else if (node.getIntValue("capacity") <= 0) {
                    node.put("capacity", cap);
                    if (node.getIntValue("remainSolutionCnt") <= 0) {
                        node.put("remainSolutionCnt", cap);
                    }
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] ??制?站 {} 仓库容量 ?{}", slotId, cap);
                } else if (node.getIntValue("state") == 0
                        && node.getIntValue("remainSolutionCnt") > 0
                        && node.getIntValue("outputSolutionCnt") == 0
                        && node.getLongValue("saveTime") == 0) {
                    node.put("state", 1);           // RUN
                    node.put("lastUpdateTime", now);
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] ??制?站 {} 状??STOP ?RUN", slotId);
                }

                JSONObject manuBuff = node.getJSONObject("buff");
                if (manuBuff == null) {
                    manuBuff = new JSONObject(true);
                    node.put("buff", manuBuff);
                }
                double ps = basementProduceSpeed();
                if (Math.abs(manuBuff.getDoubleValue("speed") - (ps - 1.0)) > 1e-9) {
                    manuBuff.put("speed", ps - 1.0);
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 制?站 {} 生产倍???{}×（buff.speed={}?",
                            slotId, ps, ps - 1.0);
                }
            } else if ("TRADING".equals(roomType)) {
                int limit = tradingOrderLimit(level);
                if (node == null) {
                    bucket.put(slotId, defaultRoomData(roomType, level, now));
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 补建贸易站数?{} ({} ?", slotId, limit);
                } else {
                    if (node.getIntValue("stockLimit") <= 0) {
                        node.put("stockLimit", limit);
                        fixed++;
                    }
                    JSONArray stock = node.getJSONArray("stock");
                    if (stock == null) {
                        node.put("stock", new JSONArray());
                        fixed++;
                        ArknightsApplication.LOGGER.info("[building] 贸易?{} 补建 stock 数组（等按时间出单）", slotId);
                    }
                }
            } else if ("DORMITORY".equals(roomType)) {
                if (node == null) {
                    bucket.put(slotId, defaultRoomData(roomType, level, now));
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 补建宿舍数据 {}", slotId);
                } else {
                    int comfort = dormComfort(node);
                    if (node.getIntValue("comfort") != comfort) {
                        node.put("comfort", comfort);
                        fixed++;
                        ArknightsApplication.LOGGER.info("[building] 宿舍 {} 舒?度 ?{}（心?+{}/s?",
                                slotId, comfort, dormRecoverPerSecond(comfort, level));
                    }
                }
            } else if ("MEETING".equals(roomType)) {
                if (node == null) {
                    bucket.put(slotId, defaultRoomData(roomType, level, now));
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 补建会?室数?{}", slotId);
                } else if (repairMeetingNode(node, now)) {
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 会??{} 补全字?", slotId);
                }
            } else if (node == null) {
                bucket.put(slotId, defaultRoomData(roomType, level, now));
                fixed++;
            } else if ("POWER".equals(roomType)
                    && (node.getJSONObject("buff") == null || !node.getJSONObject("buff").containsKey("global"))) {
                JSONObject fresh = defaultRoomData(roomType, level, now);
                node.put("buff", fresh.getJSONObject("buff"));
                fixed++;
                ArknightsApplication.LOGGER.info("[building] 发电?{} 补建 buff（laborSpeed?", slotId);
            }
        }

        JSONObject status = building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        if (labor != null) {
            int sum = 0;
            for (String slotId : slots.keySet()) {
                JSONObject def = LAYOUT_SLOTS.getJSONObject(slotId);
                if (def != null) {
                    sum += def.getIntValue("provideLabor");
                }
            }
            if (sum != labor.getIntValue("maxValue")) {
                int oldMax = labor.getIntValue("maxValue");
                labor.put("maxValue", sum);
                if (labor.getIntValue("value") > sum) {
                    labor.put("value", sum);
                }
                fixed++;
                ArknightsApplication.LOGGER.info("[building] ??无人机上?{} ?{}（已清理 {} ?，当?{}?",
                        oldMax, sum, slots.size(), labor.getIntValue("value"));
            }
            double laborSpeed = applyPowerLaborSpeed(building, userData, slots);
            if (Math.abs(laborSpeed - labor.getDoubleValue("buffSpeed")) > 0.0001) {
                labor.put("buffSpeed", Math.round(laborSpeed * 100) / 100.0);
                fixed++;
                ArknightsApplication.LOGGER.info("[building] 无人机恢复加?buffSpeed ?{}", labor.getDoubleValue("buffSpeed"));
            }
        }
        // 干员重?占位（?户?会崩）自?
        fixed += dedupeAssignedChars(building);
        return fixed;
    }

    /** 把一?员从?室节点（trainee/trainer）里摘掉 */
    private static void clearTrainingChar(JSONObject building, String slotId, List<Integer> wanted) {
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("TRAINING");
        JSONObject node = bucket == null ? null : bucket.getJSONObject(slotId);
        if (node == null) {
            return;
        }
        JSONObject trainee = node.getJSONObject("trainee");
        if (trainee != null && wanted.contains(trainee.getIntValue("charInstId"))) {
            trainee.put("charInstId", -1);
            trainee.put("state", 0);
            trainee.put("targetSkill", -1);
            trainee.put("processPoint", 0);
            node.put("state", 0);
        }
        JSONObject trainer = node.getJSONObject("trainer");
        if (trainer != null && wanted.contains(trainer.getIntValue("charInstId"))) {
            trainer.put("charInstId", -1);
            trainer.put("state", 0);
        }
    }

    private static int dedupeAssignedChars(JSONObject building) {
        JSONObject slots = building.getJSONObject("roomSlots");
        if (slots == null) {
            return 0;
        }
        java.util.LinkedHashMap<Integer, String> owner = new java.util.LinkedHashMap<>();
        int fixed = 0;
        for (String slotId : new ArrayList<>(slots.keySet())) {
            JSONObject slot = slots.getJSONObject(slotId);
            JSONArray ids = slot == null ? null : slot.getJSONArray("charInstIds");
            if (ids == null) {
                continue;
            }
            for (int i = 0; i < ids.size(); i++) {
                Integer v = ids.getInteger(i);
                if (v == null || v <= 0) {
                    continue;
                }
                if (!owner.containsKey(v)) {
                    owner.put(v, slotId);
                    continue;
                }
                // 重?：保留先出现的槽位，清掉这里
                ids.set(i, -1);
                fixed++;
                if ("TRAINING".equals(slot.getString("roomId"))) {
                    List<Integer> one = new ArrayList<>();
                    one.add(v);
                    clearTrainingChar(building, slotId, one);
                }
                ArknightsApplication.LOGGER.warn("[building] 干员 {} 同时出现?{} ?{}，已?{} 撤下",
                        v, owner.get(v), slotId, slotId);
            }
        }
        // 顺带?building.chars ?roomSlotId 同?成真实槽?
        JSONObject bchars = building.getJSONObject("chars");
        if (bchars != null) {
            for (String key : new ArrayList<>(bchars.keySet())) {
                JSONObject c = bchars.getJSONObject(key);
                if (c == null) {
                    continue;
                }
                String real = owner.get(Integer.valueOf(key));
                if (real == null) {
                    if (!"".equals(c.getString("roomSlotId"))) {
                        c.put("roomSlotId", "");
                        c.put("index", -1);
                        c.put("changeScale", 0);
                        fixed++;
                    }
                } else if (!real.equals(c.getString("roomSlotId"))) {
                    c.put("roomSlotId", real);
                    fixed++;
                }
            }
        }
        return fixed;
    }

    private static double applyPowerLaborSpeed(JSONObject building, JSONObject userData, JSONObject slots) {
        JSONObject rooms = building == null ? null : building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("POWER");
        if (bucket == null) {
            return 0;
        }
        JSONObject troop = userData == null ? null : userData.getJSONObject("troop");
        JSONObject troopChars = troop == null ? null : troop.getJSONObject("chars");
        double total = 0;
        for (String pid : new ArrayList<>(bucket.keySet())) {
            JSONObject node = bucket.getJSONObject(pid);
            if (node == null) {
                continue;
            }
            JSONObject buff = node.getJSONObject("buff");
            if (buff == null) {
                buff = new JSONObject(true);
                node.put("buff", buff);
            }
            double slotSpeed = powerLaborSpeed(slots == null ? null : slots.getJSONObject(pid), troopChars);
            buff.put("laborSpeed", Math.round(slotSpeed * 100) / 100.0);
            total += slotSpeed;
        }
        return total;
    }

    private static void refreshLaborBuffSpeed(JSONObject building, JSONObject userData) {
        JSONObject slots = building == null ? null : building.getJSONObject("roomSlots");
        double total = applyPowerLaborSpeed(building, userData, slots);
        JSONObject status = building == null ? null : building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        if (labor != null && Math.abs(total - labor.getDoubleValue("buffSpeed")) > 0.0001) {
            labor.put("buffSpeed", Math.round(total * 100) / 100.0);
        }
    }

    private static double powerLaborSpeed(JSONObject slot, JSONObject troopChars) {
        if (slot == null || troopChars == null) {
            return 0;
        }
        JSONArray ids = slot.getJSONArray("charInstIds");
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int stationed = 0;
        for (int i = 0; i < ids.size(); i++) {
            int instId = ids.getIntValue(i);
            if (instId <= 0) {
                continue;
            }
            stationed++;
            JSONObject c = troopChars.getJSONObject(String.valueOf(instId));
            if (c == null) {
                continue;
            }
            int phase = c.getIntValue("evolvePhase");
            int level = c.getIntValue("level");
            String charId = c.getString("charId");
            JSONObject def = BUILDING_CHAR_BUFFS == null ? null : BUILDING_CHAR_BUFFS.getJSONObject(charId);
            JSONArray groups = def == null ? null : def.getJSONArray("buffChar");
            if (groups == null) {
                continue;
            }
            for (int g = 0; g < groups.size(); g++) {
                JSONObject grp = groups.getJSONObject(g);
                JSONArray buffData = grp == null ? null : grp.getJSONArray("buffData");
                if (buffData == null) {
                    continue;
                }
                for (int k = 0; k < buffData.size(); k++) {
                    JSONObject bd = buffData.getJSONObject(k);
                    String buffId = bd.getString("buffId");
                    JSONObject cond = bd.getJSONObject("cond");
                    if (cond != null) {
                        int needPhase = phaseOf(cond.getString("phase"));
                        if (phase < needPhase || level < cond.getIntValue("level")) {
                            continue;                       // ?能未解锁
                        }
                    }
                    JSONObject buff = BUILDING_BUFFS == null ? null : BUILDING_BUFFS.getJSONObject(buffId);
                    if (buff == null || !"POWER".equals(buff.getString("roomType"))) {
                        continue;
                    }
                    String desc = buff.getString("description");
                    if (desc == null || !desc.contains("无人?")) {
                        continue;
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\+(\\d+(?:\\.\\d+)?)%")
                            .matcher(desc.replaceAll("<[^>]+>", ""));
                    if (m.find()) {
                        sum += Double.parseDouble(m.group(1)) / 100.0;
                    }
                }
            }
        }
        return stationed > 0 ? 0.05 + sum : 0;
    }

    private static int phaseOf(String phase) {
        if (phase == null) {
            return 0;
        }
        try {
            return Integer.parseInt(phase.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String grantTutorialKit(JSONObject userData) {
        JSONObject st = userData.getJSONObject("status");
        if (st == null) {
            return null;
        }
        int progress = st.getIntValue("progress");
        if (progress < 1100 || progress >= 10100) {
            return null;
        }
        JSONObject flags = st.getJSONObject("flags");
        if (flags == null) {
            flags = new JSONObject(true);
            st.put("flags", flags);
        }
        final String kitFlag = "obt/tutorial/building_kit";
        if (flags.containsKey(kitFlag)) {
            return null;
        }

        JSONObject inv = userData.getJSONObject("inventory");
        if (inv == null) {
            inv = new JSONObject(true);
            userData.put("inventory", inv);
        }
        String[] kitIds = {"3131", "3132", "3133", "3003"};
        int[] kitCounts = {10, 10, 5, 20};

        boolean enough = true;
        for (int i = 0; i < kitIds.length; i++) {
            if (inv.getIntValue(kitIds[i]) < kitCounts[i]) {
                enough = false;
                break;
            }
        }

        flags.put(kitFlag, 1);
        if (enough) {
            ArknightsApplication.LOGGER.info("[tutorial] 基建教程材料已充足，跳过发放（progress={}?", progress);
            return "skip";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kitIds.length; i++) {
            if (inv.getIntValue(kitIds[i]) < kitCounts[i]) {
                inv.put(kitIds[i], kitCounts[i]);
                sb.append(kitIds[i]).append("×").append(kitCounts[i]).append(' ');
            }
        }
        ArknightsApplication.LOGGER.info("[tutorial] 基建教程材料已发放: {}（progress={}?", sb.toString().trim(), progress);
        return sb.toString().trim();
    }

    private static int slotNum(String slotId) {        int us = slotId.lastIndexOf('_');
        if (us < 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(slotId.substring(us + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int cleanCostIndex(String roomSlotId, String cleanCostId) {
        if (cleanCostId == null || LAYOUT_CLEAN_COSTS == null || !LAYOUT_CLEAN_COSTS.containsKey(cleanCostId)) {
            return -1;
        }
        List<String> ids = new ArrayList<>();
        for (String id : LAYOUT_SLOTS.keySet()) {
            JSONObject s = LAYOUT_SLOTS.getJSONObject(id);
            if (s != null && cleanCostId.equals(s.getString("cleanCostId"))) {
                ids.add(id);
            }
        }
        ids.sort((a, b) -> {
            int na = slotNum(a);
            int nb = slotNum(b);
            return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
        });
        return ids.indexOf(roomSlotId);
    }

    @PostMapping(
            value = {"/cleanRoomSlot"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject cleanRoomSlot(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String roomSlotId = jsonBody.getString("roomSlotId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/cleanRoomSlot roomSlotId=" + roomSlotId);

        JSONObject result = new JSONObject(true);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            result.put("message", "server is close");
            return result;
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            result.put("error", "account check failed");
            return result;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            result.put("result", 403);
            result.put("error", "banned");
            return result;
        }

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject roomSlots = building.getJSONObject("roomSlots");

        loadLayout();

        JSONObject slotDef = LAYOUT_SLOTS.getJSONObject(roomSlotId);
        if (slotDef == null) {
            result.put("result", 1);
            result.put("error", "unknown roomSlotId: " + roomSlotId);
            return result;
        }

        if (roomSlots.containsKey(roomSlotId)) {
            result.put("result", 1);
            result.put("error", "roomSlot already cleaned: " + roomSlotId);
            return result;
        }

        String cleanCostId = slotDef.getString("cleanCostId");
        int index = cleanCostIndex(roomSlotId, cleanCostId);
        List<JSONObject> costItems = new ArrayList<>();
        if (index >= 0) {
            JSONObject costDef = LAYOUT_CLEAN_COSTS.getJSONObject(cleanCostId);
            JSONObject byNum = costDef == null ? null : costDef.getJSONObject("number");
            JSONObject one = byNum == null ? null : byNum.getJSONObject(String.valueOf(index));
            JSONArray items = one == null ? null : one.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    costItems.add(items.getJSONObject(i));
                }
            }
        }

        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }

        boolean lenient = true;
        try {
            lenient = ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug");
        } catch (Exception ignored) {
        }

        for (JSONObject it : costItems) {
            String id = it.getString("id");
            int need = it.getIntValue("count");
            if (inventory.getIntValue(id) < need) {
                if (!lenient) {
                    result.put("result", 1);
                    result.put("error", "not enough material: " + id);
                    return result;
                }
                ArknightsApplication.LOGGER.warn("[cleanRoomSlot] 材料不足debug 模式: {} need={} have={}",
                        id, need, inventory.getIntValue(id));
            }
        }

        int costLabor = slotDef.getIntValue("costLabor");
        int provideLabor = slotDef.getIntValue("provideLabor");
        JSONObject laborDelta = null;
        JSONObject labor = building.getJSONObject("status").getJSONObject("labor");
        if (labor != null && (costLabor > 0 || provideLabor > 0)) {
            int have = labor.getIntValue("value");
            int maxV = labor.getIntValue("maxValue");
            if (have < costLabor) {
                if (!lenient) {
                    result.put("result", 1);
                    result.put("error", "not enough drone");
                    return result;
                }
                ArknightsApplication.LOGGER.warn("[cleanRoomSlot] 无人机不足但 debug 放?: need={} have={}", costLabor, have);
            }
            maxV = maxV + provideLabor;
            int val = Math.min(have - costLabor + provideLabor, maxV);
            if (val < 0) {
                val = 0;
            }
            labor.put("maxValue", maxV);
            labor.put("value", val);
            laborDelta = new JSONObject(true);
            laborDelta.put("value", val);
            laborDelta.put("maxValue", maxV);
        }

        JSONObject inventoryDelta = new JSONObject(true);
        for (JSONObject it : costItems) {
            String id = it.getString("id");
            int left = Math.max(0, inventory.getIntValue(id) - it.getIntValue("count"));
            inventory.put(id, left);
            inventoryDelta.put(id, left);
        }

        String slotCategory = slotDef.getString("category");
        String autoRoom = AUTO_ROOM.get(slotCategory);
        JSONObject newSlot = new JSONObject(true);
        if (autoRoom != null) {
            newSlot.put("level", 1);
            newSlot.put("state", 2);
            newSlot.put("roomId", autoRoom);
        } else {
            newSlot.put("level", 0);
            newSlot.put("state", 0);
            newSlot.put("roomId", "NONE");
        }
        newSlot.put("charInstIds", new JSONArray());
        newSlot.put("completeConstructTime", -1);
        roomSlots.put(roomSlotId, newSlot);

        userDao.setUserData(account.getUid(), userData);

        JSONObject slotsDelta = new JSONObject(true);
        slotsDelta.put(roomSlotId, newSlot);

        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("roomSlots", slotsDelta);
        if (laborDelta != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("labor", laborDelta);
            buildingDelta.put("status", statusDelta);
        }

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        if (!inventoryDelta.isEmpty()) {
            modified.put("inventory", inventoryDelta);
        }

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[cleanRoomSlot] {} 清理完成（{} 消{}，labor {}?",
                roomSlotId, cleanCostId, costItems, costLabor);
        return result;
    }

    private static int roomCountOfType(JSONObject rooms, String roomId) {
        JSONObject bucket = rooms.getJSONObject(roomId);
        return bucket == null ? 0 : bucket.size();
    }

    private static int countRoomAtLeastLevel(JSONObject roomSlots, JSONObject rooms, String roomType, int level) {
        JSONObject bucket = rooms.getJSONObject(roomType);
        if (bucket == null) {
            return 0;
        }
        int n = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject slot = roomSlots.getJSONObject(slotId);
            if (slot != null && slot.getIntValue("level") >= level) {
                n++;
            }
        }
        return n;
    }

    private static JSONArray makeTradingOrders(int count) {
        JSONArray stock = new JSONArray();
        for (int i = 1; i <= count; i++) {
            stock.add(makeOneTradingOrder(i));
        }
        return stock;
    }

    private static JSONObject makeOneTradingOrder(int instId) {
        JSONObject order = new JSONObject(true);
        order.put("instId", instId);
        JSONArray delivery = new JSONArray();
        JSONObject d = new JSONObject(true);
        d.put("id", "3003");
        d.put("type", "MATERIAL");
        d.put("count", 2);
        delivery.add(d);
        order.put("delivery", delivery);
        order.put("type", "O_GOLD");
        JSONObject gain = new JSONObject(true);
        gain.put("id", "4001");
        gain.put("type", "GOLD");
        gain.put("count", 1000);
        order.put("gain", gain);
        order.put("buff", new JSONArray());
        return order;
    }

    private static double tradingOrderBaseSeconds() {
        try {
            JSONObject cfg = ArknightsApplication.serverConfig.getJSONObject("building");
            if (cfg != null) {
                double v = cfg.getDoubleValue("tradingOrderBaseSeconds");
                if (v > 0) {
                    return v;
                }
            }
        } catch (Exception ignored) {
            // 配置缺失用默?
        }
        return 3600.0;
    }

    private static boolean setTradingState(JSONObject node, int want) {
        if (node.getIntValue("state") == want) {
            return false;
        }
        node.put("state", want);
        return true;
    }

    private static boolean advanceTrading(JSONObject node, int level, long now, JSONArray charInstIds) {
        JSONObject buff = node.getJSONObject("buff");
        double charSpeed = buff == null ? 0.0 : buff.getDoubleValue("speed");
        double orderSpeed = 1.0;
        try {
            orderSpeed = TRADING_DATA.getJSONArray("phases")
                    .getJSONObject(Math.max(0, level - 1)).getDoubleValue("orderSpeed");
        } catch (Exception ignored) {
        }
        if (orderSpeed <= 0) {
            orderSpeed = 1.0;
        }

        int limit = node.getIntValue("stockLimit") > 0 ? node.getIntValue("stockLimit") : tradingOrderLimit(level);
        JSONArray stock = node.getJSONArray("stock");
        boolean changed = false;
        if (stock == null) {
            stock = new JSONArray();
            node.put("stock", stock);
            changed = true;
        }

        long last = node.getLongValue("lastUpdateTime");
        boolean dirty = changed;

        if (stock.size() >= limit) {
            dirty |= setTradingState(node, 0);
            for (String k : new String[]{"next", "display"}) {
                if (node.containsKey(k)) {
                    node.remove(k);
                    dirty = true;
                }
            }
            if (node.getLongValue("completeWorkTime") != 0) {
                node.put("completeWorkTime", 0);
                dirty = true;
            }
            if (last != now) {
                node.put("lastUpdateTime", now);
                dirty = true;
            }
            return dirty;
        }
        dirty |= setTradingState(node, 1);

        double secondsPerOrder = tradingOrderBaseSeconds() / (orderSpeed * (1.0 + charSpeed));
        if (secondsPerOrder <= 0) {
            secondsPerOrder = 3600.0;
        }
        long need = Math.max(1L, (long) Math.ceil(secondsPerOrder));

        long complete = node.getLongValue("completeWorkTime");
        if (complete <= 0 || complete > now + need * 2) {
            complete = now + need;
        }

        int maxInst = 0;
        for (int i = 0; i < stock.size(); i++) {
            JSONObject o = stock.getJSONObject(i);
            if (o != null) {
                maxInst = Math.max(maxInst, o.getIntValue("instId"));
            }
        }

        int produced = 0;
        while (complete <= now && stock.size() < limit) {
            stock.add(makeOneTradingOrder(++maxInst));
            complete += need;
            produced++;
            dirty = true;
        }

        if (stock.size() >= limit) {
            setTradingState(node, 0);
            node.remove("next");
            node.remove("display");
            node.put("completeWorkTime", 0);
            node.put("lastUpdateTime", now);
            ArknightsApplication.LOGGER.info("[building] 贸易站出?+{}，已满仓 {}/{}（停产）",
                    produced, stock.size(), limit);
            return true;
        }

        node.put("completeWorkTime", complete);
        node.put("lastUpdateTime", now);

        double speed = orderSpeed * (1.0 + charSpeed);
        double maxPoint = Math.max(1.0, need * speed);
        double remaining = Math.max(0.0, complete - now);
        double processPoint = Math.min(maxPoint, remaining * speed);

        JSONObject next = new JSONObject(true);
        next.put("order", maxInst + 1);
        next.put("speed", speed);
        next.put("processPoint", (long) Math.round(processPoint));
        next.put("maxPoint", Math.round(maxPoint));
        node.put("next", next);

        JSONObject display = new JSONObject(true);
        display.put("base", -100);
        display.put("buff", 0);
        node.put("display", display);

        if (buff != null) {
            JSONObject apCost = buff.getJSONObject("apCost");
            if (apCost == null) {
                apCost = new JSONObject(true);
                buff.put("apCost", apCost);
            }
            JSONObject self = apCost.getJSONObject("self");
            if (self == null) {
                self = new JSONObject(true);
                apCost.put("self", self);
            }
            if (charInstIds != null) {
                for (int i = 0; i < charInstIds.size(); i++) {
                    String id = String.valueOf(charInstIds.get(i));
                    if (!self.containsKey(id)) {
                        self.put(id, 25);
                    }
                }
            }
        }

        if (produced > 0) {
            ArknightsApplication.LOGGER.info("[building] 贸易站出?+{}（每?{}s，现?{}/{}，加?{}?",
                    produced, need, stock.size(), limit, charSpeed);
        }
        return true;
    }

    private static JSONObject tradingBuff() {
        JSONObject buff = new JSONObject(true);
        buff.put("speed", 0.31);
        buff.put("limit", 0);
        JSONObject apCost = new JSONObject(true);
        apCost.put("all", 0);
        apCost.put("single", new JSONObject());
        apCost.put("self", new JSONObject());
        buff.put("apCost", apCost);
        buff.put("rate", new JSONObject());
        buff.put("tgw", new JSONArray());
        buff.put("point", new JSONObject());
        buff.put("manuLines", new JSONObject());
        buff.put("orderWtBuff", new JSONArray());
        buff.put("orderBuff", new JSONArray());
        JSONObject violated = new JSONObject(true);
        violated.put("orderChecker", new JSONArray());
        violated.put("cntBuff", new JSONArray());
        buff.put("violatedInfo", violated);
        JSONObject speGoldOrder = new JSONObject(true);
        speGoldOrder.put("activated", false);
        buff.put("speGoldOrder", speGoldOrder);
        return buff;
    }

    private static JSONObject manufactBuff() {
        JSONObject buff = new JSONObject(true);
        JSONObject apCost = new JSONObject(true);
        apCost.put("self", new JSONObject());
        apCost.put("all", 0);
        buff.put("apCost", apCost);
        buff.put("speed", 0);
        buff.put("capacity", 0);
        buff.put("sSpeed", 0);
        buff.put("tSpeed", new JSONObject());
        buff.put("cSpeed", 0);
        buff.put("capFrom", new JSONObject());
        buff.put("maxSpeed", -1);
        buff.put("point", new JSONObject());
        buff.put("flag", new JSONObject());
        buff.put("skillExtend", new JSONObject());
        return buff;
    }

    public static JSONObject createRoomNode(String roomType, int level) {
        loadLayout();
        return defaultRoomData(roomType, level, ArknightsApplication.getTimestamp());
    }

    private static JSONObject defaultRoomData(String roomType, int level, long now) {
        JSONObject r = new JSONObject(true);
        r.put("state", 0);
        r.put("presetQueue", new JSONArray());
        r.put("lastUpdateTime", now);
        r.put("completeWorkTime", -1);

        if ("MANUFACTURE".equals(roomType)) {
            r.put("formulaId", "1");
            r.put("remainSolutionCnt", 0);
            r.put("outputSolutionCnt", 0);
            r.put("saveTime", 0);
            r.put("tailTime", 0);
            r.put("capacity", manufactOutputCapacity(level));
            r.put("processPoint", 0);
            r.put("apCost", -100);
            r.put("buff", manufactBuff());
            JSONObject display = new JSONObject(true);
            display.put("base", -100);
            display.put("buff", 0);
            r.put("display", display);
        } else if ("TRADING".equals(roomType)) {
            int limit = tradingOrderLimit(level);
            r.put("strategy", "O_GOLD");
            r.put("stockLimit", limit);
            r.put("apCost", 0);
            r.put("stock", makeTradingOrders(limit));
            r.put("buff", tradingBuff());
        } else if ("POWER".equals(roomType)) {
            JSONObject gbuff = new JSONObject(true);
            JSONObject global = new JSONObject(true);
            global.put("roomCnt", new JSONObject(true));
            gbuff.put("global", global);
            JSONObject manuc = new JSONObject(true);
            manuc.put("charSpeed", new JSONObject(true));
            gbuff.put("manufacture", manuc);
            gbuff.put("laborSpeed", 0);
            JSONObject gapCost = new JSONObject(true);
            gapCost.put("self", new JSONObject(true));
            gbuff.put("apCost", gapCost);
            r.put("buff", gbuff);
        } else if ("DORMITORY".equals(roomType)) {
            JSONObject buff = new JSONObject(true);
            JSONObject apCost = new JSONObject(true);
            apCost.put("all", 0);
            JSONObject single = new JSONObject(true);
            single.put("target", null);
            single.put("value", 0);
            apCost.put("single", single);
            apCost.put("self", new JSONObject(true));
            apCost.put("exclude", new JSONObject(true));
            buff.put("apCost", apCost);
            buff.put("point", new JSONObject(true));
            r.put("buff", buff);
            JSONObject diy = new JSONObject(true);
            diy.put("wallPaper", null);
            diy.put("floor", null);
            diy.put("carpet", new JSONArray());
            diy.put("other", new JSONArray());
            r.put("diySolution", diy);
            r.put("comfort", 0);
            JSONArray lock = new JSONArray();
            for (int i = 0; i < 5; i++) {
                lock.add(0);
            }
            r.put("lockQueue", lock);
        } else if ("TRAINING".equals(roomType)) {
            JSONObject buff = new JSONObject(true);
            buff.put("speed", 0.15);
            buff.put("lvEx", new JSONObject(true));
            buff.put("lvCost", new JSONObject(true));
            buff.put("apCost", -100);
            JSONObject reduce = new JSONObject(true);
            reduce.put("target", null);
            reduce.put("progress", 0);
            reduce.put("cut", 1);
            buff.put("reduce", reduce);
            JSONObject rtb = new JSONObject(true);
            rtb.put("fulltime", false);
            rtb.put("activated", false);
            rtb.put("cnt", 0);
            rtb.put("reset", false);
            buff.put("reduceTimeBd", rtb);
            r.put("buff", buff);
            r.put("state", 0);
            JSONObject trainee = new JSONObject(true);
            trainee.put("charInstId", -1);
            trainee.put("state", 0);
            trainee.put("targetSkill", -1);
            trainee.put("processPoint", 0);
            trainee.put("speed", 1.0);
            r.put("trainee", trainee);
            JSONObject trainer = new JSONObject(true);
            trainer.put("charInstId", -1);
            trainer.put("state", 0);
            r.put("trainer", trainer);
        } else if ("MEETING".equals(roomType)) {
            JSONObject buff = new JSONObject(true);
            buff.put("speed", 0);
            JSONObject weight = new JSONObject(true);
            for (String f : new String[]{"RHINE", "PENGUIN", "BLACKSTEEL", "URSUS", "GLASGOW", "KJERAG", "RHODES"}) {
                weight.put(f, 0);
            }
            buff.put("weight", weight);
            buff.put("flag", new JSONObject(true));
            JSONObject mapCost = new JSONObject(true);
            mapCost.put("self", new JSONObject(true));
            buff.put("apCost", mapCost);
            buff.put("notOwned", 0);
            buff.put("owned", 0);
            r.put("buff", buff);
            r.put("startApCounter", new JSONObject(true));
            r.put("mustgetClue", new JSONArray());
            r.put("state", 1);
            r.put("speed", 115);
            r.put("processPoint", 0);
            r.put("ownStock", new JSONArray());
            r.put("receiveStock", new JSONArray());
            r.put("board", new JSONObject(true));
            JSONObject socialReward = new JSONObject(true);
            socialReward.put("daily", 0);
            socialReward.put("search", 0);
            r.put("socialReward", socialReward);
            r.put("dailyReward", null);
            r.put("expiredReward", 0);
            r.put("received", 0);
            JSONObject infoShare = new JSONObject(true);
            infoShare.put("ts", 0);
            infoShare.put("reward", 0);
            r.put("infoShare", infoShare);
            r.put("mfc", new JSONObject(true));
            JSONObject leave = new JSONObject(true);
            leave.put("inUse", true);
            leave.put("lastVisitTs", 0);
            leave.put("lastShowTs", 0);
            leave.put("lastUpdateSpTs", now);
            JSONObject sp = new JSONObject(true);
            sp.put("lastWeek", 0);
            sp.put("lastWeekSum", 0);
            sp.put("thisWeek", 0);
            sp.put("thisWeekSum", 0);
            leave.put("sp", sp);
            r.put("messageLeave", leave);
            JSONObject diy = new JSONObject(true);
            diy.put("wallPaper", null);
            diy.put("floor", null);
            diy.put("carpet", new JSONArray());
            diy.put("other", defaultMeetingFurniture());
            r.put("diySolution", diy);
        } else if ("HIRE".equals(roomType)) {
            JSONObject buff = new JSONObject(true);
            buff.put("speed", 0.45);
            buff.put("point", new JSONObject(true));
            JSONObject apCostH = new JSONObject(true);
            apCostH.put("self", new JSONObject(true));
            buff.put("apCost", apCostH);
            JSONObject meetingH = new JSONObject(true);
            meetingH.put("speedUp", 0);
            buff.put("meeting", meetingH);
            JSONObject stack = new JSONObject(true);
            stack.put("clueWeight", new JSONObject(true));
            stack.put("char", new JSONArray());
            buff.put("stack", stack);
            r.put("buff", buff);
            r.put("state", 1);
            r.put("refreshCount", level >= 3 ? 3 : (level >= 2 ? 2 : 1));
            r.put("processPoint", 0);
            r.put("speed", 100 + Math.max(0, level - 1) * 15);
        }
        return r;
    }

    private static JSONArray defaultMeetingFurniture() {
        Object[][] defs = {
                {"furniM_basicRoom_chandelier_01", 2, 4},
                {"furniM_basicRoom_plant_01", 12, 0},
                {"furniM_basicRoom_cupboard_01", 0, 0},
                {"furniM_basicRoom_cupboard_02", 3, 0},
                {"furniM_basicRoom_cutoff_01", 11, 8},
                {"furniM_basicRoom_domelight_01", 16, 4},
                {"furniM_basicRoom_sofa_L_01", 16, 7},
                {"furniM_basicRoom_sofa_01", 19, 4},
                {"furniM_basicRoom_sofa_R_01", 27, 7},
                {"furniM_basicRoom_teatable_01", 20, 8},
                {"furniM_basicRoom_shelf_01", 31, 1},
                {"furniM_basicRoom_plant_02", 38, 5},
                {"furniM_basicRoom_desk_01", 40, 3},
                {"furniM_basicRoom_musicplayer_01", 12, 8},
                {"furniM_basicRoom_messageboard_01", 33, 1}
        };
        JSONArray arr = new JSONArray();
        for (Object[] d : defs) {
            JSONObject coord = new JSONObject(true);
            coord.put("x", (Integer) d[1]);
            coord.put("y", (Integer) d[2]);
            JSONObject item = new JSONObject(true);
            item.put("id", (String) d[0]);
            item.put("coordinate", coord);
            arr.add(item);
        }
        return arr;
    }

    private static boolean repairMeetingNode(JSONObject node, long now) {
        if (node == null) {
            return false;
        }
        boolean fixed = false;
        JSONObject tpl = defaultRoomData("MEETING", 1, now);
        for (String key : new ArrayList<>(tpl.keySet())) {
            if (key.equals("lastUpdateTime") || key.equals("completeWorkTime") || key.equals("presetQueue")) {
                continue;
            }
            if (!node.containsKey(key)) {
                node.put(key, tpl.get(key));
                fixed = true;
            }
        }
        if (!node.containsKey("lastUpdateTime")) {
            node.put("lastUpdateTime", now);
            fixed = true;
        }
        if (!node.containsKey("completeWorkTime")) {
            node.put("completeWorkTime", now + 3600);
            fixed = true;
        }
        return fixed;
    }

    private static int dormComfort(JSONObject roomNode) {
        if (roomNode == null) {
            return 0;
        }
        JSONObject diy = roomNode.getJSONObject("diySolution");
        if (diy == null) {
            return roomNode.getIntValue("comfort");
        }
        Set<String> placed = new HashSet<>();
        int sum = 0;
        for (String key : new String[]{"carpet", "other"}) {
            JSONArray list = diy.getJSONArray(key);
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.getString("id");
                if (id == null) {
                    continue;
                }
                placed.add(id);
                JSONObject furni = FURNITURES_CHAR_TABLE == null ? null : FURNITURES_CHAR_TABLE.getJSONObject(id);
                if (furni != null) {
                    sum += furni.getIntValue("comfort");
                }
            }
        }

        if (FURNITURE_GROUPS != null && !placed.isEmpty()) {
            for (String gid : new ArrayList<>(FURNITURE_GROUPS.keySet())) {
                JSONObject g = FURNITURE_GROUPS.getJSONObject(gid);
                if (g == null) {
                    continue;
                }
                JSONArray members = g.getJSONArray("furniture");
                if (members == null || members.isEmpty()) {
                    continue;
                }
                boolean complete = true;
                for (int i = 0; i < members.size(); i++) {
                    if (!placed.contains(members.getString(i))) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    sum += g.getIntValue("comfort");
                }
            }
        }
        return Math.min(5000, sum);
    }

    @PostMapping(value = {"/upgradeRoom"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject upgradeRoom(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody JSONObject jsonBody,
                                  HttpServletRequest request, HttpServletResponse response) {
        String roomSlotId = jsonBody.getString("roomSlotId");
        int targetLevel = jsonBody.getIntValue("targetLevel");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/upgradeRoom slot=" + roomSlotId + " target=" + targetLevel);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(roomSlotId);
        JSONObject bad = new JSONObject(true);
        if (slot == null) {
            bad.put("result", 1);
            bad.put("error", "?房间? " + roomSlotId);
            return bad;
        }
        if (slot.getIntValue("state") == 1) {
            bad.put("result", 1);
            bad.put("error", "房间正在升级? " + roomSlotId);
            return bad;
        }
        int cur = Math.max(1, slot.getIntValue("level"));
        String roomId = slot.getString("roomId");
        JSONObject def = ROOMS_TABLE.getJSONObject(roomId);
        JSONArray phases = def == null ? null : def.getJSONArray("phases");
        if (phases == null || targetLevel <= cur || targetLevel > phases.size()) {
            bad.put("result", 1);
            bad.put("error", "非法?等级: " + targetLevel + "（当?" + cur + "，上?" + (phases == null ? 0 : phases.size()) + "?");
            return bad;
        }
        JSONObject cost = phases.getJSONObject(targetLevel - 1).getJSONObject("buildCost");
        JSONArray items = cost.getJSONArray("items");
        int laborCost = cost.getIntValue("labor");
        long buildTime = cost.getLongValue("time");

        JSONObject inventory = userData.getJSONObject("inventory");
        JSONObject status = building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        int haveLabor = labor == null ? 0 : labor.getIntValue("value");
        if (haveLabor < laborCost) {
            bad.put("result", 1);
            bad.put("error", "无人机不? ??" + laborCost + "，只?" + haveLabor);
            return bad;
        }
        List<String> lack = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                if (inventory.getIntValue(it.getString("id")) < it.getIntValue("count")) {
                    lack.add(it.getString("id") + " ??" + it.getIntValue("count")
                            + " ? " + inventory.getIntValue(it.getString("id")));
                }
            }
        }
        if (!lack.isEmpty()) {
            bad.put("result", 1);
            bad.put("error", "材料不足: " + String.join("?", lack));
            return bad;
        }
        JSONObject inventoryDelta = new JSONObject(true);
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                String id = it.getString("id");
                int left = inventory.getIntValue(id) - it.getIntValue("count");
                inventory.put(id, left);
                inventoryDelta.put(id, left);
            }
        }
        if (labor != null) {
            labor.put("value", haveLabor - laborCost);
        }

        long now = ArknightsApplication.getTimestamp();
        slot.put("state", 1);
        slot.put("completeConstructTime", now + Math.max(1, buildTime));
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        modified.put("inventory", inventoryDelta);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] {} {} ?始升?Lv{} ?Lv{}（无人机 -{}?",
                roomSlotId, roomId, cur, targetLevel, laborCost);
        return result;
    }

    @PostMapping(value = {"/completeUpgradeRoom"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject completeUpgradeRoom(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody JSONObject jsonBody,
                                          HttpServletRequest request, HttpServletResponse response) {
        String roomSlotId = jsonBody.getString("roomSlotId");
        int targetLevel = jsonBody.getIntValue("targetLevel");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/completeUpgradeRoom slot=" + roomSlotId + " target=" + targetLevel);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(roomSlotId);
        if (slot == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "?房间? " + roomSlotId);
            return bad;
        }
        if (slot.getIntValue("state") != 1) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "房间不在升级? " + roomSlotId);
            return bad;
        }
        slot.put("level", targetLevel);
        slot.put("state", 2);
        String roomId = slot.getString("roomId");
        // 升级后房间数?新等级重建（官服 rooms 里?房间节点也跟?变）
        repairRoomData(userData);
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] {} {} 升级完成 ?Lv{}", roomSlotId, roomId, targetLevel);
        return result;
    }

    @PostMapping(value = {"/degradeRoom"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject degradeRoom(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody JSONObject jsonBody,
                                  HttpServletRequest request, HttpServletResponse response) {
        String roomSlotId = jsonBody.getString("roomSlotId");
        int targetLevel = jsonBody.getIntValue("targetLevel");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/degradeRoom slot=" + roomSlotId + " target=" + targetLevel);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(roomSlotId);
        JSONObject bad = new JSONObject(true);
        if (slot == null) {
            bad.put("result", 1);
            bad.put("error", "?房间? " + roomSlotId);
            return bad;
        }
        int cur = Math.max(1, slot.getIntValue("level"));
        if (targetLevel <= 0 || targetLevel >= cur) {
            bad.put("result", 1);
            bad.put("error", "非法?等级: " + targetLevel + "（当?" + cur + "?");
            return bad;
        }
        String roomId = slot.getString("roomId");
        JSONObject def = ROOMS_TABLE.getJSONObject(roomId);
        JSONArray phases = def == null ? null : def.getJSONArray("phases");

        JSONObject inventory = userData.getJSONObject("inventory");
        JSONObject inventoryDelta = new JSONObject(true);
        if (phases != null) {
            for (int lv = targetLevel + 1; lv <= cur && lv <= phases.size(); lv++) {
                JSONObject cost = phases.getJSONObject(lv - 1).getJSONObject("buildCost");
                JSONArray items = cost.getJSONArray("items");
                if (items == null) {
                    continue;
                }
                for (int i = 0; i < items.size(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String id = it.getString("id");
                    int after = inventory.getIntValue(id) + it.getIntValue("count");
                    inventory.put(id, after);
                    inventoryDelta.put(id, after);
                }
            }
        }

        slot.put("level", targetLevel);
        slot.put("state", 2);
        // 降级后按?等级重建房间节点
        repairRoomData(userData);
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        modified.put("inventory", inventoryDelta);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] {} {} 降级 Lv{} ?Lv{}，返?{}", roomSlotId, roomId, cur, targetLevel, inventoryDelta);
        return result;
    }

    @PostMapping(value = {"/buyLabor"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject buyLabor(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody JSONObject jsonBody,
                               HttpServletRequest request, HttpServletResponse response) {
        int costAp = Math.max(1, jsonBody.getIntValue("costAp"));
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/buyLabor costAp=" + costAp);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject status = userData.getJSONObject("status");
        JSONObject labor = building.getJSONObject("status") == null ? null
                : building.getJSONObject("status").getJSONObject("labor");
        JSONObject bad = new JSONObject(true);
        if (labor == null) {
            bad.put("result", 1);
            bad.put("error", "没有无人机数?");
            return bad;
        }
        int ap = status.getIntValue("ap");
        if (ap < costAp) {
            bad.put("result", 1);
            bad.put("error", "理智不足: ??" + costAp + "，只?" + ap);
            return bad;
        }
        int maxLabor = labor.getIntValue("maxValue");
        int curLabor = labor.getIntValue("value");
        if (curLabor >= maxLabor) {
            bad.put("result", 1);
            bad.put("error", "无人机已满（" + curLabor + "/" + maxLabor + "?");
            return bad;
        }
        long now = ArknightsApplication.getTimestamp();
        int newAp = ap - costAp;
        status.put("ap", newAp);
        status.put("lastApAddTime", now);
        int newLabor = Math.min(maxLabor, curLabor + costAp);
        labor.put("value", newLabor);
        labor.put("lastUpdateTime", now);
        userDao.setUserData(account.getUid(), userData);

        JSONObject statusDelta = new JSONObject(true);
        statusDelta.put("ap", newAp);
        statusDelta.put("lastApAddTime", now);
        JSONObject laborDelta = new JSONObject(true);
        laborDelta.put("value", newLabor);
        laborDelta.put("maxValue", maxLabor);
        laborDelta.put("lastUpdateTime", now);
        laborDelta.put("buffSpeed", labor.getDoubleValue("buffSpeed"));
        laborDelta.put("processPoint", labor.getDoubleValue("processPoint"));
        JSONObject buildingStatusDelta = new JSONObject(true);
        buildingStatusDelta.put("labor", laborDelta);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("status", buildingStatusDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        modified.put("status", statusDelta);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] 买无人机 理智 {}→{}，无人机 {}→{}/{}",
                ap, newAp, curLabor, newLabor, maxLabor);
        return result;
    }

    @PostMapping(value = {"/accelerateOrder"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject accelerateOrder(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject jsonBody,
                                      HttpServletRequest request, HttpServletResponse response) {
        String roomSlotId = jsonBody.getString("slotId");
        int orderId = jsonBody.getIntValue("orderId");
        int cost = Math.max(1, jsonBody.getIntValue("cost"));
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/accelerateOrder slot=" + roomSlotId + " orderId=" + orderId + " cost=" + cost);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(roomSlotId);
        JSONObject bad = new JSONObject(true);
        if (slot == null || !"TRADING".equals(slot.getString("roomId"))) {
            bad.put("result", 1);
            bad.put("error", "不是贸易站房间槽: " + roomSlotId);
            return bad;
        }
        JSONObject rooms2 = building.getJSONObject("rooms");
        JSONObject tradeBucket = rooms2 == null ? null : rooms2.getJSONObject("TRADING");
        JSONObject node = tradeBucket == null ? null : tradeBucket.getJSONObject(roomSlotId);
        JSONObject next = node == null ? null : node.getJSONObject("next");
        if (next == null || next.getIntValue("order") != orderId) {
            bad.put("result", 1);
            bad.put("error", "订单不存在或已完? " + orderId);
            return bad;
        }
        JSONObject status = building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        int haveLabor = labor == null ? 0 : labor.getIntValue("value");
        if (haveLabor < cost) {
            bad.put("result", 1);
            bad.put("error", "无人机不足" + cost + "，只?" + haveLabor);
            return bad;
        }

        long now = ArknightsApplication.getTimestamp();
        long complete = node.getLongValue("completeWorkTime");
        long newComplete = complete - (long) cost * 180L;      // 1 ?= 3 分钟
        labor.put("value", haveLabor - cost);

        int level = Math.max(1, slot.getIntValue("level"));
        JSONArray slotChars = slot.getJSONArray("charInstIds");
        node.put("completeWorkTime", newComplete);
        node.put("lastUpdateTime", now);
        advanceTrading(node, level, now, slotChars);

        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        JSONObject nextAfter = node.getJSONObject("next");
        ArknightsApplication.LOGGER.info("[building] 加速{} 消耗 {} 架无人机 {} 分钟），剩余进度 {}",
                orderId, cost, cost * 3, nextAfter == null ? "已完?" : nextAfter.getDoubleValue("processPoint"));
        return result;
    }

    @PostMapping(value = {"/setPrivateDormOwner"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject setPrivateDormOwner(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody JSONObject jsonBody,
                                          HttpServletRequest request, HttpServletResponse response) {
        String slotId = jsonBody.getString("slotId");
        if (slotId == null) {
            slotId = jsonBody.getString("roomSlotId");
        }
        int charInstId = jsonBody.getIntValue("charInsId");
        if (charInstId == 0) {
            charInstId = jsonBody.getIntValue("charInstId");
        }
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/setPrivateDormOwner slot=" + slotId + " charInstId=" + charInstId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slot = building.getJSONObject("roomSlots") == null ? null
                : building.getJSONObject("roomSlots").getJSONObject(slotId);
        JSONObject bad = new JSONObject(true);
        if (slot == null) {
            bad.put("result", 1);
            bad.put("error", "?房间? " + slotId);
            return bad;
        }
        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }
        String roomType = slot.getString("roomId");
        JSONObject bucket = rooms.getJSONObject(roomType);
        if (bucket == null) {
            bucket = new JSONObject(true);
            rooms.put(roomType, bucket);
        }
        JSONObject node = bucket.getJSONObject(slotId);
        if (node == null) {
            node = defaultRoomData(roomType, Math.max(1, slot.getIntValue("level")),
                    ArknightsApplication.getTimestamp());
            bucket.put(slotId, node);
        }
        JSONArray owners = new JSONArray();
        if (charInstId > 0) {
            owners.add(charInstId);
        }
        node.put("owners", owners);
        userDao.setUserData(account.getUid(), userData);

        JSONObject nodeDelta = new JSONObject(true);
        nodeDelta.put("owners", owners);
        JSONObject roomDelta = new JSONObject(true);
        roomDelta.put(slotId, nodeDelta);
        JSONObject roomsDelta = new JSONObject(true);
        roomsDelta.put(roomType, roomDelta);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", roomsDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] 活动?{} 室长 ?{}", slotId, owners);
        return result;
    }

    @PostMapping(value = {"/getClueBox"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getClueBox(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody JSONObject jsonBody,
                                 HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getClueBox");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONArray box = new JSONArray();
        JSONObject rooms = building == null ? null : building.getJSONObject("rooms");
        JSONObject meeting = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (meeting != null) {
            for (String slotId : new ArrayList<>(meeting.keySet())) {
                JSONObject node = meeting.getJSONObject(slotId);
                JSONArray stock = node == null ? null : node.getJSONArray("receiveStock");
                if (stock != null) {
                    for (int i = 0; i < stock.size(); i++) {
                        box.add(stock.getJSONObject(i));
                    }
                }
            }
        }
        JSONObject modified = new JSONObject(true);
        modified.put("pushFlags", new JSONObject(true));
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("box", box);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/getClueFriendList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getClueFriendList(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getClueFriendList");
        JSONObject result = new JSONObject(true);
        result.put("result", new JSONArray());
        result.put("starFriendList", new JSONArray());
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/getMessageBoardContent"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getMessageBoardContent(@RequestHeader(value = "secret", required = false) String secret,
                                             @RequestBody JSONObject jsonBody,
                                             HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getMessageBoardContent");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        long now = ArknightsApplication.getTimestamp();
        // 自然周清空：每周一 04:00 起只保留本周的访客记录
        try {
            int purged = com.hypergryph.arknights.core.dao.visitDao.purgeOldWeeks();
            if (purged > 0) {
                ArknightsApplication.LOGGER.info("[building] 跨周清理访客记录 {} 条", purged);
            }
        } catch (Exception ignored) {
        }
        long weekStart = com.hypergryph.arknights.core.dao.visitDao.weekStartTs(now);
        long lastWeekStart = weekStart - 7L * 86400;
        JSONArray thisWeek = new JSONArray();
        JSONArray lastWeek = new JSONArray();
        int todayVisit = 0;
        int weeklyVisit = 0;
        int lastWeekVisit = 0;
        try {
            long dayStart = dailyResetTs(now);
            // 官服按「访客」聚合：同一个好友多条记录合并成一条，visitCount = 次数，lastVisitTs 取最新
            java.util.LinkedHashMap<String, JSONObject> byUid = new java.util.LinkedHashMap<>();
            for (java.util.Map<String, Object> v : com.hypergryph.arknights.core.dao.visitDao.listVisits(account.getUid(), lastWeekStart)) {
                String uidKey = String.valueOf(v.get("uid"));
                long ts = ((Number) v.get("ts")).longValue();
                JSONObject exist = byUid.get(uidKey);
                if (exist != null) {
                    exist.put("visitCount", exist.getIntValue("visitCount") + 1);
                    if (ts > exist.getLongValue("lastVisitTs")) {
                        exist.put("lastVisitTs", ts);
                    }
                    continue;
                }
                JSONObject item = new JSONObject(true);
                item.put("uid", uidKey);
                item.put("nickName", v.get("nickName"));
                item.put("nickNumber", v.get("nickNumber"));
                item.put("avatar", new JSONObject(true));
                ((JSONObject) item.get("avatar")).put("type", "ICON");
                ((JSONObject) item.get("avatar")).put("id", "avatar_def_10");
                item.put("lastVisitTs", ts);
                item.put("nameCardSkin", "nc_rhodes_default");
                item.put("nameCardSkinTmpl", 0);
                item.put("level", v.get("level"));
                item.put("visitCount", 1);
                // 官服访客条目还带这几个字段，缺了客户端会把访客一直当成"新访客"（红点不消）
                item.put("infoShare", 0);
                item.put("recentVisited", 0);
                String secChar = null, secSkin = null;
                try {
                    List<Account> vs = userDao.queryAccountByUid(Long.parseLong(uidKey));
                    if (vs.size() == 1) {
                        JSONObject vUser = JSON.parseObject(vs.get(0).getUser());
                        JSONObject vTroop = vUser.getJSONObject("troop");
                        JSONObject vChars = vTroop == null ? null : vTroop.getJSONObject("chars");
                        if (vChars != null && !vChars.isEmpty()) {
                            JSONObject c0 = vChars.getJSONObject(new ArrayList<>(vChars.keySet()).get(0));
                            if (c0 != null) {
                                secChar = c0.getString("charId");
                                secSkin = c0.getString("skin");
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                item.put("secretary", secChar);
                item.put("secretarySkinId", secSkin);
                item.put("secretarySkinSp", false);
                item.put("emoji", "building_basic_04");
                byUid.put(uidKey, item);
            }
            for (JSONObject item : byUid.values()) {
                long ts = item.getLongValue("lastVisitTs");
                int visits = item.getIntValue("visitCount");
                if (ts >= weekStart) {
                    thisWeek.add(item);
                    // 官服 weeklyVisit 统计的是本周访问次数（含同一好友重复来访），不是人数
                    weeklyVisit += visits;
                    if (ts >= dayStart) {
                        todayVisit++;
                    }
                } else {
                    lastWeek.add(item);
                    lastWeekVisit += visits;
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[building] 读取访客失败: {}", e.toString());
        }
        JSONObject result = new JSONObject(true);
        result.put("thisWeekVisitors", thisWeek);
        result.put("lastWeekVisitors", lastWeek);
        result.put("todayVisit", todayVisit);
        result.put("weeklyVisit", weeklyVisit);
        result.put("lastWeekVisit", lastWeekVisit);
        result.put("lastWeekSpReward", 0);
        long lastShowTs = 0;
        Object[] hitMb = meetingSlot(building);
        if (hitMb != null) {
            JSONObject leave = ((JSONObject) hitMb[1]).getJSONObject("messageLeave");
            if (leave == null) {
                leave = new JSONObject(true);
                leave.put("inUse", true);
                leave.put("lastVisitTs", 0);
                leave.put("lastShowTs", 0);
                ((JSONObject) hitMb[1]).put("messageLeave", leave);
            }
            // 官服抓包（002_10 / 002_16）：**打开留言板本身**就会把 lastShowTs / lastUpdateSpTs 设为 now，
            // 客户端靠「lastVisitTs > lastShowTs」判断"有新访客"，不更新这个红点就永远消不掉。
            lastShowTs = leave.getLongValue("lastShowTs");
            leave.put("lastShowTs", now);
            leave.put("lastUpdateSpTs", now);
            userDao.setUserData(account.getUid(), userData);
        }
        result.put("lastShowTs", lastShowTs);
        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static long dailyResetTs(long now) {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        java.time.ZonedDateTime t = java.time.Instant.ofEpochSecond(now).atZone(zone);
        java.time.ZonedDateTime reset = t.withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (t.isBefore(reset)) {
            reset = reset.minusDays(1);
        }
        return reset.toEpochSecond();
    }

    @PostMapping(value = {"/startInfoShare"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject startInfoShare(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody JSONObject jsonBody,
                                     HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/startInfoShare");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        // 官服实测：这次调用就??提交线索开始线?流???板子上的线索?消??清空，不返还库存??
        // 抓包对照：提交前 board=6 / ownStock=7，提交后 board=0 / ownStock 仍为 7?
        JSONObject board = node.getJSONObject("board");
        int consumed = 0;
        JSONArray clearedFactions = new JSONArray();
        if (board != null && !board.isEmpty()) {
            java.util.Set<String> usedIds = new java.util.HashSet<>();
            for (String faction : new ArrayList<>(board.keySet())) {
                Object v = board.get(faction);
                if (v != null) {
                    usedIds.add(String.valueOf(v));
                }
                clearedFactions.add(faction);
            }
            consumed = usedIds.size();
            // 板上线索被消耗：从 ownStock 里删掉这些（它们在板上时 inUse=1）
            JSONArray ownStockList = node.getJSONArray("ownStock");
            if (ownStockList != null) {
                for (int i = ownStockList.size() - 1; i >= 0; i--) {
                    JSONObject c = ownStockList.getJSONObject(i);
                    if (c != null && usedIds.contains(c.getString("id"))) {
                        ownStockList.remove(i);
                    }
                }
            }
            board.clear();
        }
        JSONObject infoShare = node.getJSONObject("infoShare");
        if (infoShare == null) {
            infoShare = new JSONObject(true);
            node.put("infoShare", infoShare);
        }
        long now = ArknightsApplication.getTimestamp();
        infoShare.put("ts", now + 86400L);          // 交流持续 24 小时
        infoShare.put("reward", 0);
        node.put("clueExchangeStartedTs", now);
        node.put("clueExchangeReady", false);
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[building] startInfoShare: consumed {} board clues", consumed);
        JSONObject result = clueResult(userData, building, true, true);
        addDeletedBoard(result, (String) hit[0], clearedFactions);
        return result;
    }

    @PostMapping(value = {"/getInfoShareReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getInfoShareReward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody JSONObject jsonBody,
                                         HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getInfoShareReward");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        if (hit == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "没有会客室");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONObject infoShare = node.getJSONObject("infoShare");
        if (infoShare == null) {
            infoShare = new JSONObject(true);
            infoShare.put("ts", 0);
            infoShare.put("reward", 0);
            node.put("infoShare", infoShare);
        }
        long ts = infoShare.getLongValue("ts");
        long now = ArknightsApplication.getTimestamp();
        boolean claimable = infoShare.getIntValue("reward") == 1 || (ts > 0 && now >= ts);
        JSONArray visitors = infoShareVisitors(userData);
        int num = visitors.isEmpty() ? 1 : visitors.size();
        JSONObject modified = new JSONObject(true);
        JSONArray reward = new JSONArray();
        if (claimable) {
            int gain = 210;
            JSONObject status = userData.getJSONObject("status");
            if (status != null) {
                status.put("socialPoint", status.getIntValue("socialPoint") + gain);
                JSONObject statusDelta = new JSONObject(true);
                statusDelta.put("socialPoint", status.getIntValue("socialPoint"));
                modified.put("status", statusDelta);
            }
            JSONObject rw = new JSONObject(true);
            rw.put("id", "SOCIAL_PT");
            rw.put("type", "SOCIAL_PT");
            rw.put("count", gain);
            reward.add(rw);
            infoShare.put("reward", 0);
            infoShare.put("ts", 0L);
            ArknightsApplication.LOGGER.info("[building] 情报分享奖励领取：社交点数 +{}（访客 {} 人仅展示）", gain, num);
        } else {
            ArknightsApplication.LOGGER.info("[building] 情报分享尚未到期（ts={}），不发放", ts);
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = clueResult(userData, building, true, true);
        JSONObject mod = result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (modified.containsKey("status")) {
            mod.put("status", modified.getJSONObject("status"));
        }
        result.put("list", visitors);
        result.put("reward", reward);
        return result;
    }

    private static JSONArray infoShareVisitors(JSONObject userData) {
        JSONArray out = new JSONArray();
        JSONObject social = userData == null ? null : userData.getJSONObject("social");
        JSONObject friends = social == null ? null : social.getJSONObject("assistUserList");
        if (friends == null || friends.isEmpty()) {
            return out;
        }
        for (String key : new ArrayList<>(friends.keySet())) {
            JSONObject f = friends.getJSONObject(key);
            if (f == null) {
                continue;
            }
            JSONObject v = new JSONObject(true);
            v.put("uid", key);
            v.put("nickName", f.getString("nickName"));
            v.put("nickNumber", f.getString("nickNumber"));
            v.put("level", f.getIntValue("level"));
            v.put("avatar", f.getJSONObject("avatar"));
            v.put("ts", ArknightsApplication.getTimestamp());
            v.put("alias", f.getString("alias"));
            v.put("secretary", f.getString("secretary"));
            v.put("secretarySkinId", f.getString("secretarySkinId"));
            v.put("secretarySkinSp", false);
            out.add(v);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    @PostMapping(value = {"/visitBuilding"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject visitBuilding(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject jsonBody,
                                    HttpServletRequest request, HttpServletResponse response) {
        String friendId = jsonBody.getString("friendId");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/visitBuilding friendId=" + friendId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject status = userData.getJSONObject("status");

        // 官服契约（003_07 抓包）：snapshot{owner,chars,roomSlots,rooms,music} + visitorList + rewards + notice
        JSONObject snapshot = new JSONObject(true);
        JSONObject owner = new JSONObject(true);
        JSONObject charsOut = new JSONObject(true);
        JSONObject roomSlotsOut = new JSONObject(true);
        JSONObject roomsOut = new JSONObject(true);
        JSONObject musicOut = new JSONObject(true);
        musicOut.put("selected", "");
        try {
            List<Account> friends = userDao.queryAccountByUid(Long.parseLong(friendId));
            if (friends.size() == 1) {
                JSONObject fu = JSON.parseObject(friends.get(0).getUser());
                JSONObject fs = fu.getJSONObject("status");
                owner.put("uid", friendId);
                owner.put("nickName", fs == null ? "" : fs.getString("nickName"));
                owner.put("nickNumber", fs == null ? "" : fs.getString("nickNumber"));
                owner.put("level", fs == null ? 1 : fs.getIntValue("level"));
                owner.put("avatar", fu.getJSONObject("avatar") == null ? new JSONObject(true) : fu.getJSONObject("avatar"));
                owner.put("nameCardSkinId", "nc_rhodes_default");
                owner.put("nameCardSkinTmpl", 0);
                JSONObject troop = fu.getJSONObject("troop");
                JSONObject tchars = troop == null ? null : troop.getJSONObject("chars");
                String secretary = null;
                String secretarySkin = null;
                if (tchars != null && !tchars.isEmpty()) {
                    JSONObject c0 = tchars.getJSONObject(new ArrayList<>(tchars.keySet()).get(0));
                    if (c0 != null) {
                        secretary = c0.getString("charId");
                        secretarySkin = c0.getString("skin");
                    }
                    for (String k : tchars.keySet()) {
                        JSONObject c = tchars.getJSONObject(k);
                        if (c == null) {
                            continue;
                        }
                        JSONObject brief = new JSONObject(true);
                        brief.put("charId", c.getString("charId"));
                        brief.put("skin", c.getString("skin"));
                        charsOut.put(k, brief);
                    }
                }
                owner.put("secretary", secretary);
                owner.put("secretarySkinId", secretarySkin);
                owner.put("secretarySkinSp", false);

                JSONObject fb = fu.getJSONObject("building");
                if (fb != null && fb.getJSONObject("roomSlots") != null) {
                    roomSlotsOut = fb.getJSONObject("roomSlots");
                }
                JSONObject frooms = fb == null ? null : fb.getJSONObject("rooms");
                if (frooms != null) {
                    for (String rt : new String[]{"DORMITORY", "MEETING"}) {
                        JSONObject b = frooms.getJSONObject(rt);
                        if (b != null) {
                            for (String slotKey : new ArrayList<>(b.keySet())) {
                                coerceIntFields(b.getJSONObject(slotKey));
                            }
                            roomsOut.put(rt, b);
                        }
                    }
                }
            } else {
                owner.put("uid", friendId);
                owner.put("nickName", "");
                owner.put("nickNumber", "");
                owner.put("level", 1);
                owner.put("avatar", new JSONObject(true));
                owner.put("nameCardSkinId", "nc_rhodes_default");
                owner.put("nameCardSkinTmpl", 0);
                owner.put("secretary", null);
                owner.put("secretarySkinId", null);
                owner.put("secretarySkinSp", false);
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[building] visitBuilding snapshot 构造失败: {}", e.toString());
        }
        snapshot.put("owner", owner);
        snapshot.put("chars", charsOut);
        snapshot.put("roomSlots", roomSlotsOut);
        snapshot.put("rooms", roomsOut);
        snapshot.put("music", musicOut);

        int gain = 30;
        if (status != null) {
            status.put("socialPoint", status.getIntValue("socialPoint") + gain);
        }
        userDao.setUserData(account.getUid(), userData);

        JSONArray rewards = new JSONArray();
        JSONObject rw = new JSONObject(true);
        rw.put("id", "SOCIAL_PT");
        rw.put("type", "SOCIAL_PT");
        rw.put("count", gain);
        rewards.add(rw);

        JSONObject modified = new JSONObject(true);
        if (status != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("socialPoint", status.getIntValue("socialPoint"));
            modified.put("status", statusDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("snapshot", snapshot);
        result.put("visitorList", new JSONArray());
        result.put("rewards", rewards);
        result.put("notice", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] visitBuilding {} → 社交点数 +{}", friendId, gain);
        try {
            String nick = null, nickNum = null, avatarId = "";
            int lv = 1;
            JSONObject st = status;
            if (st != null) {
                nick = st.getString("nickName");
                nickNum = st.getString("nickNumber");
                lv = st.getIntValue("level");
            }
            long now = ArknightsApplication.getTimestamp();
            long dayStart = dailyResetTs(now);
            boolean repeatToday = com.hypergryph.arknights.core.dao.visitDao
                    .visitedToday(Long.parseLong(friendId), account.getUid(), dayStart);
            if (repeatToday) {
                com.hypergryph.arknights.core.dao.visitDao.touchToday(
                        Long.parseLong(friendId), account.getUid(), dayStart, now);
            } else {
                com.hypergryph.arknights.core.dao.visitDao.addVisit(
                        Long.parseLong(friendId), account.getUid(), nick, nickNum, lv, avatarId, now);
            }
            List<Account> hosts = userDao.queryAccountByUid(Long.parseLong(friendId));
            if (hosts.size() == 1) {
                Account host = hosts.get(0);
                JSONObject hostData = JSON.parseObject(host.getUser());
                Object[] hh = meetingSlot(hostData.getJSONObject("building"));
                if (hh != null) {
                    JSONObject node = (JSONObject) hh[1];
                    JSONObject leave = node.getJSONObject("messageLeave");
                    if (leave == null) {
                        leave = new JSONObject(true);
                        leave.put("inUse", true);
                        leave.put("lastShowTs", 0);
                        leave.put("lastUpdateSpTs", now);
                        node.put("messageLeave", leave);
                    }
                    // 同一访客当天重复来访不再加待领点数（否则红点一直消不掉）
                    if (repeatToday) {
                        userDao.setUserData(host.getUid(), hostData);
                        ArknightsApplication.LOGGER.info("[building] 访客 {} 今天已经来过 {}，不重复计点",
                                account.getUid(), friendId);
                        return result;
                    }
                    leave.put("lastVisitTs", now);
                    int visitors;
                    try {
                        visitors = com.hypergryph.arknights.core.dao.visitDao.countUnclaimed(host.getUid());
                    } catch (Exception e) {
                        visitors = 1;
                    }
                    JSONObject sp = leave.getJSONObject("sp");
                    if (sp == null) {
                        sp = new JSONObject(true);
                        sp.put("lastWeek", 0);
                        sp.put("lastWeekSum", 0);
                        sp.put("thisWeek", 0);
                        sp.put("thisWeekSum", 0);
                        leave.put("sp", sp);
                    }
                    sp.put("thisWeek", sp.getIntValue("thisWeek") + 30);
                    userDao.setUserData(host.getUid(), hostData);
                    ArknightsApplication.LOGGER.info("[building] 访客 {} 访问了 {}，对方待领点数={}（lastVisitTs={}）",
                            account.getUid(), friendId, sp.getIntValue("thisWeek"), now);
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[building] 记录访客失败: {}", e.toString());
        }
        return result;
    }

    private static Object[] meetingSlot(JSONObject building) {
        JSONObject rooms = building == null ? null : building.getJSONObject("rooms");
        JSONObject meeting = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (meeting == null || meeting.isEmpty()) {
            return null;
        }
        String slotId = new ArrayList<>(meeting.keySet()).get(0);
        JSONObject node = meeting.getJSONObject(slotId);
        return node == null ? null : new Object[]{slotId, node};
    }

    private static JSONArray clueChars(JSONObject userData) {
        JSONArray out = new JSONArray();
        JSONObject troop = userData == null ? null : userData.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        if (chars == null || chars.isEmpty()) {
            return out;
        }
        int added = 0;
        for (String key : new ArrayList<>(chars.keySet())) {
            JSONObject c = chars.getJSONObject(key);
            if (c == null) {
                continue;
            }
            JSONObject brief = new JSONObject(true);
            brief.put("charId", c.getString("charId"));
            brief.put("level", c.getIntValue("level"));
            brief.put("skin", c.getString("skin"));
            brief.put("evolvePhase", c.getIntValue("evolvePhase"));
            out.add(brief);
            if (++added >= 2) {
                break;
            }
        }
        return out;
    }

    private static void addDeletedBoard(JSONObject result, String slotId, JSONArray factions) {
        if (result == null || factions == null || factions.isEmpty()) {
            return;
        }
        JSONObject slot = new JSONObject(true);
        slot.put("board", factions);
        JSONObject meeting = new JSONObject(true);
        meeting.put(slotId, slot);
        JSONObject rooms = new JSONObject(true);
        rooms.put("MEETING", meeting);
        JSONObject building = new JSONObject(true);
        building.put("rooms", rooms);
        JSONObject deleted = new JSONObject(true);
        deleted.put("building", building);
        JSONObject delta = result.getJSONObject("playerDataDelta");
        if (delta != null) {
            delta.put("deleted", deleted);
        }
    }

    private static void coerceIntFields(JSONObject node) {
        if (node == null) {
            return;
        }
        for (String key : new String[]{"processPoint", "speed", "completeWorkTime", "lastUpdateTime",
                "received", "expiredReward", "clueProduceTs"}) {
            Object v = node.get(key);
            if (v instanceof Double || v instanceof Float) {
                node.put(key, (long) Math.round(((Number) v).doubleValue()));
            } else if (v instanceof java.math.BigDecimal) {
                node.put(key, ((java.math.BigDecimal) v).longValue());
            }
        }
        JSONObject labor = node.getJSONObject("labor");
        if (labor != null) {
            for (String key : new String[]{"processPoint", "value", "maxValue", "buffSpeed"}) {
                Object v = labor.get(key);
                if (v instanceof Double || v instanceof Float) {
                    labor.put(key, (long) Math.round(((Number) v).doubleValue()));
                }
            }
        }
    }

    private static String missingFaction(JSONObject node) {
        String[] all = {"RHINE", "PENGUIN", "BLACKSTEEL", "URSUS", "GLASGOW", "KJERAG", "RHODES"};
        JSONObject board = node == null ? null : node.getJSONObject("board");
        java.util.List<String> free = new ArrayList<>();
        for (String f : all) {
            if (board == null || !board.containsKey(f)) {
                free.add(f);
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        return free.get((int) (Math.random() * free.size()));
    }

    private static JSONObject makeClueItem(JSONObject userData, String accountUid, String faction, long now, int seq) {
        String uid = accountUid;
        if (uid == null || uid.isEmpty()) {
            uid = userData == null ? null : userData.getString("uid");
        }
        if (uid == null || uid.isEmpty()) {
            uid = "10000001";
        }
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        String[] factions = {"RHINE", "PENGUIN", "BLACKSTEEL", "URSUS", "GLASGOW", "KJERAG"};
        String type = faction != null ? faction : factions[(int) (Math.random() * factions.length)];
        JSONObject clue = new JSONObject(true);
        clue.put("id", uid + "#" + seq + "#" + now);
        clue.put("type", type);
        clue.put("number", (int) (Math.random() * 7) + 1);
        clue.put("uid", uid);
        clue.put("name", status == null ? "" : status.getString("nickName"));
        clue.put("nickNum", status == null ? "" : status.getString("nickNumber"));
        clue.put("chars", clueChars(userData));
        clue.put("inUse", 0);
        clue.put("ts", now);
        return clue;
    }

    private static void normalizeClues(JSONObject userData, String accountUid, JSONObject node) {
        if (node == null) {
            return;
        }
        String uid = accountUid == null || accountUid.isEmpty()
                ? (userData == null ? "10000001" : userData.getString("uid")) : accountUid;
        if (uid == null || uid.isEmpty()) {
            uid = "10000001";
        }
        JSONObject status = userData == null ? null : userData.getJSONObject("status");
        String nickName = status == null ? "" : status.getString("nickName");
        String nickNum = status == null ? "" : status.getString("nickNumber");
        for (String key : new String[]{"ownStock", "receiveStock"}) {
            JSONArray stock = node.getJSONArray(key);
            if (stock == null) {
                continue;
            }
            for (int i = 0; i < stock.size(); i++) {
                JSONObject c = stock.getJSONObject(i);
                if (c == null) {
                    continue;
                }
                String id = c.getString("id");
                if (id != null && id.startsWith("0#")) {
                    c.put("id", uid + id.substring(1));
                }
                if (!c.containsKey("uid") || c.getString("uid") == null
                        || c.getString("uid").isEmpty() || "0".equals(c.getString("uid"))) {
                    c.put("uid", uid);
                }
                if (!c.containsKey("name")) {
                    c.put("name", nickName);
                }
                if (!c.containsKey("nickNum") || c.getString("nickNum") == null || c.getString("nickNum").isEmpty()) {
                    c.put("nickNum", nickNum);
                }
                if (!c.containsKey("number")) {
                    c.put("number", (int) (Math.random() * 7) + 1);
                }
                JSONArray chars = c.getJSONArray("chars");
                if (chars == null || chars.isEmpty()) {
                    c.put("chars", clueChars(userData));
                }
                if (!c.containsKey("inUse")) {
                    c.put("inUse", 0);
                }
                if (!c.containsKey("ts")) {
                    c.put("ts", ArknightsApplication.getTimestamp());
                }
            }
        }
        JSONObject board = node.getJSONObject("board");
        if (board != null && !board.isEmpty()) {
            for (String faction : new ArrayList<>(board.keySet())) {
                Object v = board.get(faction);
                if (v instanceof String && ((String) v).startsWith("0#")) {
                    board.put(faction, uid + ((String) v).substring(1));
                }
            }
        }
        java.util.Set<String> onBoard = new java.util.HashSet<>();
        if (board != null) {
            for (String faction : board.keySet()) {
                Object v = board.get(faction);
                if (v != null) {
                    onBoard.add(String.valueOf(v));
                }
            }
        }
        JSONArray stock = node.getJSONArray("ownStock");
        if (stock != null) {
            for (int i = 0; i < stock.size(); i++) {
                JSONObject c = stock.getJSONObject(i);
                if (c == null) {
                    continue;
                }
                int want = onBoard.contains(c.getString("id")) ? 1 : 0;
                if (c.getIntValue("inUse") != want) {
                    c.put("inUse", want);
                }
            }
        }
    }

    private static JSONObject clueBuildingDelta(JSONObject building, boolean withSlots) {
        JSONObject b = new JSONObject(true);
        b.put("rooms", building.getJSONObject("rooms"));
        if (withSlots) {
            b.put("roomSlots", building.getJSONObject("roomSlots"));
            b.put("chars", building.getJSONObject("chars"));
            b.put("status", building.getJSONObject("status"));
        }
        return b;
    }

    private JSONObject clueResult(JSONObject userData, JSONObject building, boolean withSlots, boolean withEvent) {
        JSONObject modified = new JSONObject(true);
        modified.put("building", clueBuildingDelta(building, withSlots));
        if (withEvent) {
            JSONObject eventDelta = new JSONObject(true);
            JSONObject ev = userData.getJSONObject("event");
            eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
            modified.put("event", eventDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/putClueToTheBoard", "/putClueToTheBoardAuto"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject putClueToTheBoard(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clueId = jsonBody.getString("clueId");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/putClueToTheBoard clueId=" + clueId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONArray ownStock = node.getJSONArray("ownStock");
        JSONObject board = node.getJSONObject("board");
        if (board == null) {
            board = new JSONObject(true);
            node.put("board", board);
        }
        JSONObject clue = null;
        if (ownStock != null && clueId != null) {
            for (int i = 0; i < ownStock.size(); i++) {
                JSONObject c = ownStock.getJSONObject(i);
                if (c != null && clueId.equals(c.getString("id"))) {
                    clue = c;
                    break;
                }
            }
        }
        JSONArray wanted = new JSONArray();
        for (String key : new String[]{"clues", "clueIds", "ids"}) {
            JSONArray arr = jsonBody.getJSONArray(key);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    Object v = arr.get(i);
                    if (v != null) {
                        wanted.add(String.valueOf(v));
                    }
                }
            }
        }
        boolean auto = (clueId == null || clueId.isEmpty()) && wanted.isEmpty();
        if (auto && ownStock != null) {
            int placed = 0;
            for (int i = 0; i < ownStock.size(); i++) {
                JSONObject c = ownStock.getJSONObject(i);
                if (c == null || c.getIntValue("inUse") != 0) {
                    continue;
                }
                String f = c.getString("type");
                if (f == null || board.containsKey(f)) {
                    continue;
                }
                c.put("inUse", 1);
                board.put(f, c.getString("id"));
                placed++;
            }
            if (placed == 0) {
                ArknightsApplication.LOGGER.info("[building] 快捷放置：没有可放的线索（板上 {} 条）", board.size());
                return clueResult(userData, building, true, true);
            }
            userDao.setUserData(account.getUid(), userData);
            ArknightsApplication.LOGGER.info("[building] 快捷放置：一次放上 {} 条，板上共 {} 条", placed, board.size());
            JSONObject result = clueResult(userData, building, true, true);
            result.put("count", placed);
            result.put("putCount", placed);
            return result;
        }
        if (!wanted.isEmpty() && ownStock != null) {
            int placed = 0;
            for (int w = 0; w < wanted.size(); w++) {
                String want = wanted.getString(w);
                for (int i = 0; i < ownStock.size(); i++) {
                    JSONObject c = ownStock.getJSONObject(i);
                    if (c == null || c.getIntValue("inUse") != 0 || !want.equals(c.getString("id"))) {
                        continue;
                    }
                    String f = c.getString("type");
                    if (f == null || board.containsKey(f)) {
                        break;
                    }
                    c.put("inUse", 1);
                    board.put(f, want);
                    placed++;
                    break;
                }
            }
            userDao.setUserData(account.getUid(), userData);
            JSONObject result = clueResult(userData, building, true, true);
            result.put("count", placed);
            result.put("putCount", placed);
            ArknightsApplication.LOGGER.info("[building] 快捷放置（按客户端列表）：请求 {} 条，放上 {} 条", wanted.size(), placed);
            return result;
        }
        if (clue == null && ownStock != null) {
            for (int i = 0; i < ownStock.size(); i++) {
                JSONObject c = ownStock.getJSONObject(i);
                if (c != null && c.getIntValue("inUse") == 0 && !board.containsKey(c.getString("type"))) {
                    clue = c;
                    break;
                }
            }
        }
        if (clue == null) {
            bad.put("result", 1);
            bad.put("error", "没有可放置的线索（板上缺的阵营库存里没有）");
            return bad;
        }
        String type = clue.getString("type");
        if (board.containsKey(type)) {
            bad.put("result", 1);
            bad.put("error", "板子上已经有 " + type + " 线索");
            return bad;
        }
        // 官服语义：线索放在板上时**仍留在 ownStock**，只是 inUse 变成 1
        clue.put("inUse", 1);
        board.put(type, clue.getString("id"));
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[building] 线索上板 {} → {}（板上 {} 条，inUse=1）",
                clue.getString("id"), type, board.size());
        return clueResult(userData, building, true, true);
    }

    @PostMapping(value = {"/takeClueFromBoard"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject takeClueFromBoard(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String type = jsonBody.getString("type");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/takeClueFromBoard type=" + type);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONObject board = node.getJSONObject("board");
        if (board == null || !board.containsKey(type)) {
            bad.put("result", 1);
            bad.put("error", "板子上没?" + type + " 线索");
            return bad;
        }
        String clueId = board.getString(type);
        board.remove(type);
        JSONArray ownStock = node.getJSONArray("ownStock");
        if (ownStock == null) {
            ownStock = new JSONArray();
            node.put("ownStock", ownStock);
        }
        // 线索本来就在 ownStock 里，拿下板子只需把 inUse 改回 0
        boolean found = false;
        for (int i = 0; i < ownStock.size(); i++) {
            JSONObject c = ownStock.getJSONObject(i);
            if (c != null && clueId.equals(c.getString("id"))) {
                c.put("inUse", 0);
                found = true;
                break;
            }
        }
        if (!found) {
            JSONObject clue = new JSONObject(true);
            clue.put("id", clueId);
            clue.put("type", type);
            clue.put("inUse", 0);
            ownStock.add(clue);
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = clueResult(userData, building, false, false);
        // 官服抓包（007_45）：必须用 deleted 告诉客户端板上这个阵营的线索被移除了，
        // 否则客户端深合并后还会一直显示。
        JSONArray removed = new JSONArray();
        removed.add(type);
        addDeletedBoard(result, (String) hit[0], removed);
        return result;
    }

    @PostMapping(value = {"/sendClue", "/sendClueAuto"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject sendClue(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody JSONObject jsonBody,
                               HttpServletRequest request, HttpServletResponse response) {
        String friendId = jsonBody.getString("friendId");
        String clueId = jsonBody.getString("clueId");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/sendClue friendId=" + friendId + " clueId=" + clueId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONArray ownStock = node.getJSONArray("ownStock");
        boolean removed = false;
        if (ownStock != null && clueId != null) {
            for (int i = 0; i < ownStock.size(); i++) {
                JSONObject c = ownStock.getJSONObject(i);
                if (c != null && clueId.equals(c.getString("id"))) {
                    ownStock.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (!removed) {
            bad.put("result", 1);
            bad.put("error", "线索不在库存? " + clueId);
            return bad;
        }
        JSONObject socialReward = node.getJSONObject("socialReward");
        if (socialReward == null) {
            socialReward = new JSONObject(true);
            socialReward.put("daily", 0);
            socialReward.put("search", 0);
            node.put("socialReward", socialReward);
        }
        socialReward.put("search", socialReward.getIntValue("search") + 20);
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = clueResult(userData, building, true, true);
        JSONObject modified = result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (userData.getJSONObject("status") != null) {
            modified.put("status", userData.getJSONObject("status"));
        }
        return result;
    }

    @PostMapping(value = {"/receiveClueToStock"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject receiveClueToStock(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody JSONObject jsonBody,
                                         HttpServletRequest request, HttpServletResponse response) {
        JSONArray clues = jsonBody.getJSONArray("clues");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/receiveClueToStock");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONArray receiveStock = node.getJSONArray("receiveStock");
        JSONArray ownStock = node.getJSONArray("ownStock");
        if (ownStock == null) {
            ownStock = new JSONArray();
            node.put("ownStock", ownStock);
        }
        if (receiveStock != null && clues != null) {
            for (int i = 0; i < clues.size(); i++) {
                String id = clues.getString(i);
                for (int k = 0; k < receiveStock.size(); k++) {
                    JSONObject c = receiveStock.getJSONObject(k);
                    if (c != null && id != null && id.equals(c.getString("id"))) {
                        receiveStock.remove(k);
                        ownStock.add(c);
                        break;
                    }
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = clueResult(userData, building, true, true);
        JSONObject modified = result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (userData.getJSONObject("status") != null) {
            modified.put("status", userData.getJSONObject("status"));
        }
        return result;
    }

    @PostMapping(value = {"/getDailyClue"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getDailyClue(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getDailyClue");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONArray ownStock = node.getJSONArray("ownStock");
        if (ownStock == null) {
            ownStock = new JSONArray();
            node.put("ownStock", ownStock);
        }
        // 手动领取每日线索：把 dailyReward 里待领的那条移进库存
        Object pendingRaw = node.get("dailyReward");
        JSONObject pending = pendingRaw instanceof JSONObject ? (JSONObject) pendingRaw : null;
        if (pending == null || pending.isEmpty()) {
            JSONObject no = new JSONObject(true);
            no.put("result", 1);
            no.put("error", "今日会客室线索还没产出或已领取");
            return no;
        }
        ownStock.add(pending);
        node.put("dailyReward", null);
        long now = ArknightsApplication.getTimestamp();
        node.put("clueProduceTs", dailyResetTs(now));
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[building] 领取每日线索 {} {} -> ownStock {}",
                pending.getString("type"), pending.getString("id"), ownStock.size());
        return clueResult(userData, building, true, true);
    }

    @PostMapping(value = {"/getMeetingroomReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getMeetingroomReward(@RequestHeader(value = "secret", required = false) String secret,
                                           @RequestBody JSONObject jsonBody,
                                           HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/getMeetingroomReward");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONArray types = jsonBody.getJSONArray("type");
        if (types == null && jsonBody.containsKey("type")) {
            types = new JSONArray();
            types.add(jsonBody.getIntValue("type"));
        }
        int n = types == null || types.isEmpty() ? 1 : types.size();
        JSONObject status = userData.getJSONObject("status");
        int gain = 20 * n;
        if (status != null) {
            status.put("socialPoint", status.getIntValue("socialPoint") + gain);
        }
        JSONObject socialReward = node.getJSONObject("socialReward");
        if (socialReward != null) {
            socialReward.put("search", 0);
            socialReward.put("daily", 0);
        }
        JSONArray arr = new JSONArray();
        for (int i = 0; i < n; i++) {
            JSONObject rw = new JSONObject(true);
            rw.put("id", "SOCIAL_PT");
            rw.put("type", "SOCIAL_PT");
            rw.put("count", 20);
            arr.add(rw);
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = clueResult(userData, building, true, true);
        JSONObject modified = result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (status != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("socialPoint", status.getIntValue("socialPoint"));
            modified.put("status", statusDelta);
        }
        result.put("rewards", arr);
        return result;
    }

    @PostMapping(value = {"/confirmMessageBoardReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject confirmMessageBoardReward(@RequestHeader(value = "secret", required = false) String secret,
                                                @RequestBody JSONObject jsonBody,
                                                HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /building/confirmMessageBoardReward");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject status = userData.getJSONObject("status");
        // 留言板奖励：按「已来访但还没领过奖励」的访客数 ×30 社交点数（不是线索板）
        int visitors;
        try {
            visitors = com.hypergryph.arknights.core.dao.visitDao.countUnclaimed(account.getUid());
        } catch (Exception e) {
            visitors = messageBoardVisitors(userData);
        }
        int gain = 30 * Math.max(1, visitors);
        if (status != null) {
            status.put("socialPoint", status.getIntValue("socialPoint") + gain);
        }
        try {
            com.hypergryph.arknights.core.dao.visitDao.markClaimed(account.getUid());
        } catch (Exception ignored) {
        }
        Object[] hit = meetingSlot(building);
        if (hit != null) {
            JSONObject node = (JSONObject) hit[1];
            JSONObject leave = node.getJSONObject("messageLeave");
            if (leave != null) {
                JSONObject sp = leave.getJSONObject("sp");
                if (sp == null) {
                    sp = new JSONObject(true);
                    sp.put("lastWeek", 0);
                    sp.put("lastWeekSum", 0);
                    sp.put("thisWeek", 0);
                    sp.put("thisWeekSum", 0);
                    leave.put("sp", sp);
                }
                sp.put("thisWeek", 0);
                sp.put("lastWeek", 0);
                sp.put("thisWeekSum", sp.getIntValue("thisWeekSum") + gain);
                ((JSONObject) hit[1]).put("received", 1);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        JSONArray reward = new JSONArray();
        JSONObject rw = new JSONObject(true);
        rw.put("type", "SOCIAL_PT");
        rw.put("id", "SOCIAL_PT");
        rw.put("count", gain);
        reward.add(rw);
        JSONObject result = clueResult(userData, building, true, true);
        JSONObject modified = result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (status != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("socialPoint", status.getIntValue("socialPoint"));
            modified.put("status", statusDelta);
        }
        result.put("reward", reward);
        ArknightsApplication.LOGGER.info("[building] 留言板奖励领取：访客 {} 人 → 社交点数 +{}", visitors, gain);
        return result;
    }

    private static int messageBoardVisitors(JSONObject userData) {
        if (userData == null) {
            return 1;
        }
        Object[] hit = meetingSlot(userData.getJSONObject("building"));
        if (hit == null) {
            return 1;
        }
        JSONObject node = (JSONObject) hit[1];
        JSONObject leave = node.getJSONObject("messageLeave");
        JSONObject sp = leave == null ? null : leave.getJSONObject("sp");
        int n = sp == null ? 0 : sp.getIntValue("thisWeek");
        return n <= 0 ? 1 : n;
    }

    @PostMapping(value = {"/deleteOwnClue", "/deleteReceiveClue"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject deleteClue(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody JSONObject jsonBody,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clueId = jsonBody.getString("clueId");
        if (clueId == null) {
            clueId = jsonBody.getString("id");
        }
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/deleteClue clueId=" + clueId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = meetingSlot(building);
        JSONObject bad = new JSONObject(true);
        if (hit == null) {
            bad.put("result", 1);
            bad.put("error", "没有会??");
            return bad;
        }
        JSONObject node = (JSONObject) hit[1];
        for (String key : new String[]{"ownStock", "receiveStock"}) {
            JSONArray stock = node.getJSONArray(key);
            if (stock == null) {
                continue;
            }
            for (int i = stock.size() - 1; i >= 0; i--) {
                JSONObject c = stock.getJSONObject(i);
                if (c != null && clueId != null && clueId.equals(c.getString("id"))) {
                    stock.remove(i);
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);
        return clueResult(userData, building, true, true);
    }

    private static JSONObject buildingForDelta(JSONObject building) {
        JSONObject out = new JSONObject(true);
        out.put("roomSlots", building.getJSONObject("roomSlots"));
        out.put("rooms", building.getJSONObject("rooms"));
        out.put("chars", building.getJSONObject("chars"));
        out.put("status", building.getJSONObject("status"));
        return out;
    }

    @PostMapping(
            value = {"/buildRoom"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buildRoom(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String roomSlotId = jsonBody.getString("roomSlotId");
        String roomId = jsonBody.getString("roomId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/buildRoom slot={} room={}", roomSlotId, roomId);

        JSONObject result = new JSONObject(true);
        result.put("alert", new JSONArray());

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            return result;
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            result.put("error", "account check failed");
            return result;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            result.put("result", 403);
            return result;
        }

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject roomSlots = building.getJSONObject("roomSlots");
        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }

        loadLayout();

        JSONObject slotDef = LAYOUT_SLOTS.getJSONObject(roomSlotId);
        if (slotDef == null) {
            result.put("result", 1);
            result.put("error", "unknown roomSlotId: " + roomSlotId);
            return result;
        }
        JSONObject slot = roomSlots.getJSONObject(roomSlotId);
        if (slot == null) {
            result.put("result", 1);
            result.put("error", "roomSlot not cleaned yet: " + roomSlotId);
            return result;
        }
        if (slot.getIntValue("state") != 0) {
            result.put("result", 1);
            result.put("error", "roomSlot is not empty: " + roomSlotId);
            return result;
        }

        JSONObject roomDef = ROOMS_TABLE.getJSONObject(roomId);
        if (roomDef == null) {
            result.put("result", 1);
            result.put("error", "unknown roomId: " + roomId);
            return result;
        }
        String slotCategory = slotDef.getString("category");
        String roomCategory = roomDef.getString("category");
        if (slotCategory == null || !slotCategory.equals(roomCategory)) {
            result.put("result", 1);
            result.put("error", "roomId " + roomId + "(" + roomCategory + ") 不能建在 " + slotCategory + " ?");
            return result;
        }

        boolean lenient = true;
        try {
            lenient = ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug");
        } catch (Exception ignored) {
        }

        int maxCount = roomDef.getIntValue("maxCount");
        int existing = roomCountOfType(rooms, roomId);
        if (maxCount >= 0 && existing >= maxCount) {
            result.put("result", 1);
            result.put("error", "room count limit reached: " + roomId + " max=" + maxCount);
            return result;
        }

        JSONArray phases = roomDef.getJSONArray("phases");
        JSONObject phase0 = (phases == null || phases.isEmpty()) ? null : phases.getJSONObject(0);
        if (phase0 == null) {
            result.put("result", 1);
            result.put("error", "room has no phases: " + roomId);
            return result;
        }

        String unlockCondId = phase0.getString("unlockCondId");
        JSONObject condDef = (unlockCondId == null) ? null : ROOM_UNLOCK_CONDS.getJSONObject(unlockCondId);
        String condFail = null;
        if (condDef != null) {
            JSONObject byNum = condDef.getJSONObject("number");
            JSONObject cond = byNum == null ? null : byNum.getJSONObject(String.valueOf(existing + 1));
            if (cond != null && cond.getIntValue("count") > 0) {
                String needType = cond.getString("type");
                int needLevel = cond.getIntValue("level");
                int needCount = cond.getIntValue("count");
                int have = countRoomAtLeastLevel(roomSlots, rooms, needType, needLevel);
                if (have < needCount) {
                    condFail = "??" + needCount + " ?" + needType + " 等级>=" + needLevel + "，当?" + have;
                }
            }
        }
        if (condFail != null) {
            if (!lenient) {
                result.put("result", 1);
                result.put("error", condFail);
                return result;
            }
            ArknightsApplication.LOGGER.warn("[buildRoom] 解锁条件?足但 debug 放?: {}", condFail);
        }

        JSONObject buildCost = phase0.getJSONObject("buildCost");
        JSONArray costItems = new JSONArray();
        int costLabor = 0;
        if (buildCost != null) {
            JSONArray items = buildCost.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    costItems.add(items.getJSONObject(i));
                }
            }
            costLabor = buildCost.getIntValue("labor");
        }

        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }
        for (int i = 0; i < costItems.size(); i++) {
            JSONObject it = costItems.getJSONObject(i);
            if (inventory.getIntValue(it.getString("id")) < it.getIntValue("count")) {
                if (!lenient) {
                    result.put("result", 1);
                    result.put("error", "not enough material: " + it.getString("id"));
                    return result;
                }
                ArknightsApplication.LOGGER.warn("[buildRoom] 材料不足?debug 放?: {}", it);
            }
        }

        long now = ArknightsApplication.getTimestamp();
        JSONObject laborDelta = null;
        if (costLabor > 0) {
            JSONObject labor = building.getJSONObject("status").getJSONObject("labor");
            int have = labor.getIntValue("value");
            if (have < costLabor) {
                if (!lenient) {
                    result.put("result", 1);
                    result.put("error", "not enough drone");
                    return result;
                }
                ArknightsApplication.LOGGER.warn("[buildRoom] 无人机不足但 debug 放?: need={} have={}", costLabor, have);
            }
            int left = Math.max(0, have - costLabor);
            labor.put("value", left);
            laborDelta = new JSONObject(true);
            laborDelta.put("value", left);
        }

        JSONObject inventoryDelta = new JSONObject(true);
        for (int i = 0; i < costItems.size(); i++) {
            JSONObject it = costItems.getJSONObject(i);
            String id = it.getString("id");
            int left = Math.max(0, inventory.getIntValue(id) - it.getIntValue("count"));
            inventory.put(id, left);
            inventoryDelta.put(id, left);
        }

        int buildTime = buildCost == null ? 0 : buildCost.getIntValue("time");
        if (buildTime > 0) {
            slot.put("level", 0);
            slot.put("state", 1);
            slot.put("completeConstructTime", now + buildTime);
        } else {
            slot.put("level", 1);
            slot.put("state", 2);
            slot.put("completeConstructTime", -1);
        }
        slot.put("roomId", roomId);
        if (slot.getJSONArray("charInstIds") == null) {
            slot.put("charInstIds", new JSONArray());
        }

        JSONObject typeBucket = rooms.getJSONObject(roomId);
        if (typeBucket == null) {
            typeBucket = new JSONObject(true);
            rooms.put(roomId, typeBucket);
        }
        JSONObject roomData = defaultRoomData(roomId, Math.max(1, slot.getIntValue("level")), now);
        typeBucket.put(roomSlotId, roomData);

        userDao.setUserData(account.getUid(), userData);

        JSONObject slotsDelta = new JSONObject(true);
        slotsDelta.put(roomSlotId, slot.clone());

        JSONObject roomsDelta = new JSONObject(true);
        JSONObject typeDelta = new JSONObject(true);
        typeDelta.put(roomSlotId, roomData.clone());
        roomsDelta.put(roomId, typeDelta);

        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("roomSlots", slotsDelta);
        buildingDelta.put("rooms", roomsDelta);
        if (laborDelta != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("labor", laborDelta);
            buildingDelta.put("status", statusDelta);
        }

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        if (!inventoryDelta.isEmpty()) {
            modified.put("inventory", inventoryDelta);
        }

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[buildRoom] {} 建成 {}（消?{}，labor {}，? {} ?",
                roomSlotId, roomId, costItems, costLabor, existing + 1);
        return result;
    }

    private static JSONObject bubbleNode(int add, long ts) {
        JSONObject b = new JSONObject(true);
        b.put("add", add);
        b.put("ts", ts);
        return b;
    }

    private static JSONObject defaultBuildingChar(String charId, long now) {
        JSONObject c = new JSONObject(true);
        c.put("charId", charId);
        c.put("lastApAddTime", now);
        c.put("ap", 8640000L);
        c.put("roomSlotId", "");
        c.put("index", -1);
        c.put("changeScale", 0);
        JSONObject bubble = new JSONObject(true);
        bubble.put("normal", bubbleNode(-1, 0));
        bubble.put("assist", bubbleNode(-1, 0));
        bubble.put("private", bubbleNode(-1, 0));
        c.put("bubble", bubble);
        c.put("workTime", 0);
        c.put("privateRooms", new JSONArray());
        return c;
    }

    @PostMapping(
            value = {"/assignChar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject assignChar(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String roomSlotId = jsonBody.getString("roomSlotId");
        JSONArray rawList = jsonBody.getJSONArray("charInstIdList");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/assignChar slot={} list={}", roomSlotId, rawList);

        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            return result;
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            result.put("error", "account check failed");
            return result;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            result.put("result", 403);
            return result;
        }

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject roomSlots = building.getJSONObject("roomSlots");
        JSONObject buildingChars = building.getJSONObject("chars");
        if (buildingChars == null) {
            buildingChars = new JSONObject(true);
            building.put("chars", buildingChars);
        }
        JSONObject troopChars = userData.getJSONObject("troop").getJSONObject("chars");

        loadLayout();

        if (roomSlotId == null || !roomSlots.containsKey(roomSlotId)) {
            result.put("result", 1);
            result.put("error", "unknown roomSlotId: " + roomSlotId);
            return result;
        }
        JSONObject slot = roomSlots.getJSONObject(roomSlotId);
        if (slot.getIntValue("state") != 2) {
            result.put("result", 1);
            result.put("error", "roomSlot is not built: " + roomSlotId);
            return result;
        }
        String roomId = slot.getString("roomId");
        JSONObject roomDef = ROOMS_TABLE.getJSONObject(roomId);
        if (roomDef == null) {
            result.put("result", 1);
            result.put("error", "unknown roomId: " + roomId);
            return result;
        }

        int level = Math.max(1, slot.getIntValue("level"));
        JSONArray phases = roomDef.getJSONArray("phases");
        int capacity = 0;
        if (phases != null && phases.size() >= level) {
            capacity = phases.getJSONObject(level - 1).getIntValue("maxStationedNum");
        }

        List<Integer> wanted = new ArrayList<>();
        if (rawList != null) {
            for (int i = 0; i < rawList.size(); i++) {
                Integer v = rawList.getInteger(i);
                if (v != null && v > 0 && !wanted.contains(v)) {
                    wanted.add(v);
                }
            }
        }
        if (capacity > 0 && wanted.size() > capacity) {
            result.put("result", 1);
            result.put("error", "too many chars: " + wanted.size() + " > " + capacity);
            return result;
        }

        for (Integer instId : wanted) {
            if (!troopChars.containsKey(String.valueOf(instId))
                    && !buildingChars.containsKey(String.valueOf(instId))) {
                result.put("result", 1);
                result.put("error", "unknown charInstId: " + instId);
                return result;
            }
        }

        long now = ArknightsApplication.getTimestamp();
        JSONObject charsDelta = new JSONObject(true);

        JSONArray oldIds = slot.getJSONArray("charInstIds");
        
        if (!wanted.isEmpty()) {
            for (String otherId : new ArrayList<>(roomSlots.keySet())) {
                if (otherId.equals(roomSlotId)) {
                    continue;
                }
                JSONObject other = roomSlots.getJSONObject(otherId);
                JSONArray otherChars = other == null ? null : other.getJSONArray("charInstIds");
                if (otherChars == null) {
                    continue;
                }
                boolean touched = false;
                for (int i = 0; i < otherChars.size(); i++) {
                    Integer v = otherChars.getInteger(i);
                    if (v != null && v > 0 && wanted.contains(v)) {
                        otherChars.set(i, -1);
                        touched = true;
                    }
                }
                if (touched && "TRAINING".equals(other.getString("roomId"))) {
                    clearTrainingChar(building, otherId, wanted);
                }
            }
        }
        if (oldIds != null) {
            for (int i = 0; i < oldIds.size(); i++) {
                Integer old = oldIds.getInteger(i);
                if (old == null || old <= 0 || wanted.contains(old)) {
                    continue;
                }
                JSONObject c = buildingChars.getJSONObject(String.valueOf(old));
                if (c != null) {
                    c.put("roomSlotId", "");
                    c.put("index", -1);
                    c.put("changeScale", 0);
                    charsDelta.put(String.valueOf(old), c.clone());
                }
            }
        }

        for (int i = 0; i < wanted.size(); i++) {
            Integer instId = wanted.get(i);
            String key = String.valueOf(instId);
            JSONObject c = buildingChars.getJSONObject(key);
            if (c == null) {
                JSONObject troopChar = troopChars.getJSONObject(key);
                c = defaultBuildingChar(troopChar.getString("charId"), now);
                buildingChars.put(key, c);
            }
            c.put("roomSlotId", roomSlotId);
            c.put("index", i);
            c.put("changeScale", 0);
            if (!c.containsKey("lastApAddTime")) {
                c.put("lastApAddTime", now);
            }
            charsDelta.put(key, c.clone());
        }

        JSONArray padded = new JSONArray();
        int size = capacity > 0 ? capacity : wanted.size();
        for (int i = 0; i < size; i++) {
            padded.add(i < wanted.size() ? wanted.get(i) : -1);
        }
        slot.put("charInstIds", padded);

        JSONObject trainingRoomsDelta = null;
        if ("TRAINING".equals(roomId)) {
            JSONObject rooms = building.getJSONObject("rooms");
            if (rooms == null) {
                rooms = new JSONObject(true);
                building.put("rooms", rooms);
            }
            JSONObject bucket = rooms.getJSONObject("TRAINING");
            if (bucket == null) {
                bucket = new JSONObject(true);
                rooms.put("TRAINING", bucket);
            }
            JSONObject node = bucket.getJSONObject(roomSlotId);
            if (node == null) {
                node = defaultRoomData("TRAINING", level, now);
                bucket.put(roomSlotId, node);
            }
            JSONObject buff = node.getJSONObject("buff");
            if (buff == null) {
                buff = new JSONObject(true);
                node.put("buff", buff);
            }
            int trainerId = padded.size() > 0 ? padded.getIntValue(0) : -1;
            int traineeId = padded.size() > 1 ? padded.getIntValue(1) : -1;

            String trainerCharId = null;
            if (trainerId > 0) {
                JSONObject tc = troopChars.getJSONObject(String.valueOf(trainerId));
                if (tc != null) {
                    trainerCharId = tc.getString("charId");
                }
            }
            double buffSpeed = trainingBuffSpeed(trainerCharId);
            buff.put("speed", buffSpeed);

            JSONObject trainee = node.getJSONObject("trainee");
            if (trainee == null) {
                trainee = new JSONObject(true);
                node.put("trainee", trainee);
            }
            int oldTrainee = trainee.getIntValue("charInstId");
            boolean trainingRunning = trainee.getIntValue("state") == 1;
            if (!trainingRunning || traineeId != oldTrainee || traineeId <= 0) {
                trainee.put("charInstId", traineeId);
                trainee.put("targetSkill", -1);
                trainee.put("processPoint", 0);
                trainee.put("state", traineeId > 0 ? 3 : 0);
                node.put("state", 0);
            }
            trainee.put("speed", 1.0 + buffSpeed);

            JSONObject trainer = node.getJSONObject("trainer");
            if (trainer == null) {
                trainer = new JSONObject(true);
                node.put("trainer", trainer);
            }
            trainer.put("charInstId", trainerId);
            trainer.put("state", trainerId > 0 ? (trainee.getIntValue("state") == 1 ? 1 : 3) : 0);
            node.put("lastUpdateTime", now);

            JSONObject roomsDelta = new JSONObject(true);
            JSONObject trainingDelta = new JSONObject(true);
            trainingDelta.put(roomSlotId, node.clone());
            roomsDelta.put("TRAINING", trainingDelta);
            trainingRoomsDelta = roomsDelta;
            ArknightsApplication.LOGGER.info("[assignChar] ??{} 教练={} 学员={}",
                    roomSlotId, trainerId, traineeId);
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject slotsDelta = new JSONObject(true);
        slotsDelta.put(roomSlotId, slot.clone());

        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("roomSlots", slotsDelta);
        if (trainingRoomsDelta != null) {
            buildingDelta.put("rooms", trainingRoomsDelta);
        }
        if (!charsDelta.isEmpty()) {
            buildingDelta.put("chars", charsDelta);
        }

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[assignChar] {} ({}) 上岗名单 ?{}（??{}?",
                roomSlotId, roomId, padded, capacity);
        return result;
    }

    @PostMapping(
            value = {"/changeManufactureSolution", "/changeManufact"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeManufactureSolution(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String roomSlotId = jsonBody.getString("roomSlotId");
        String targetFormulaId = jsonBody.getString("targetFormulaId");
        int solutionCount = jsonBody.getIntValue("solutionCount");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/changeManufactureSolution slot={} formula={} count={}",
                roomSlotId, targetFormulaId, solutionCount);

        JSONObject result = new JSONObject(true);
        result.put("change", false);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return result;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return result;
        }
        Account account = accounts.get(0);

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }
        JSONObject bucket = rooms.getJSONObject("MANUFACTURE");
        JSONObject node = bucket == null ? null : bucket.getJSONObject(roomSlotId);
        if (node == null) {
            result.put("error", "no MANUFACTURE room at " + roomSlotId);
            return result;
        }

        long now = ArknightsApplication.getTimestamp();
        node.put("formulaId", targetFormulaId);
        int cnt = solutionCount;
        if (cnt <= 0) {
            cnt = node.getIntValue("capacity");
            if (cnt <= 0) {
                cnt = manufactOutputCapacity(1);
                node.put("capacity", cnt);
            }
        }
        node.put("remainSolutionCnt", cnt);
        node.put("state", 1);
        node.put("lastUpdateTime", now);

        JSONObject manuBuff = node.getJSONObject("buff");
        if (manuBuff == null) {
            manuBuff = new JSONObject(true);
            node.put("buff", manuBuff);
        }
        double ps = basementProduceSpeed();
        manuBuff.put("speed", ps - 1.0);

        userDao.setUserData(account.getUid(), userData);

        JSONObject typeDelta = new JSONObject(true);
        typeDelta.put(roomSlotId, node.clone());
        JSONObject roomsDelta = new JSONObject(true);
        roomsDelta.put("MANUFACTURE", typeDelta);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", roomsDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("change", true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[changeManufactureSolution] {} 配方 ?{}（数?{}?", roomSlotId, targetFormulaId, solutionCount);
        return result;
    }
    
    @PostMapping(
            value = {"/deliveryOrder"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject deliveryOrder(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String slotId = jsonBody.getString("slotId");
        if (slotId == null) {
            slotId = jsonBody.getString("roomSlotId");
        }
        long orderId = jsonBody.getLongValue("orderId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/deliveryOrder slot={} order={}", slotId, orderId);

        JSONObject result = new JSONObject(true);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            return result;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            return result;
        }
        Account account = accounts.get(0);

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("TRADING");
        JSONObject node = bucket == null ? null : bucket.getJSONObject(slotId);
        if (node == null) {
            result.put("result", 1);
            result.put("error", "no TRADING room at " + slotId);
            return result;
        }

        JSONArray stock = node.getJSONArray("stock");
        if (stock == null) {
            stock = new JSONArray();
            node.put("stock", stock);
        }

        int orderIndex = -1;
        JSONObject order = null;
        for (int i = 0; i < stock.size(); i++) {
            JSONObject o = stock.getJSONObject(i);
            if (o != null && o.getLongValue("instId") == orderId) {
                order = o;
                orderIndex = i;
                break;
            }
        }
        if (order == null) {
            result.put("result", 1);
            result.put("error", "order not found: " + orderId);
            return result;
        }

        boolean lenient = true;
        try {
            lenient = ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug");
        } catch (Exception ignored) {
        }

        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }
        JSONObject inventoryDelta = new JSONObject(true);
        JSONArray delivery = order.getJSONArray("delivery");
        if (delivery != null) {
            for (int i = 0; i < delivery.size(); i++) {
                JSONObject d = delivery.getJSONObject(i);
                String id = d.getString("id");
                int need = d.getIntValue("count");
                if (inventory.getIntValue(id) < need) {
                    if (!lenient) {
                        result.put("result", 1);
                        result.put("error", "not enough material: " + id);
                        return result;
                    }
                    ArknightsApplication.LOGGER.warn("[deliveryOrder] 材料不足?debug 放?: {} need={} have={}",
                            id, need, inventory.getIntValue(id));
                }
                int left = Math.max(0, inventory.getIntValue(id) - need);
                inventory.put(id, left);
                inventoryDelta.put(id, left);
            }
        }

        JSONObject status = userData.getJSONObject("status");
        JSONObject statusDelta = new JSONObject(true);
        JSONObject gain = order.getJSONObject("gain");
        if (gain != null) {
            String type = gain.getString("type");
            String id = gain.getString("id");
            int count = gain.getIntValue("count");
            if ("GOLD".equals(type)) {
                long gold = status.getLongValue("gold") + count;
                status.put("gold", gold);
                statusDelta.put("gold", gold);
            } else {
                inventory.put(id, inventory.getIntValue(id) + count);
                inventoryDelta.put(id, inventory.getIntValue(id));
            }
        }

        stock.remove(orderIndex);
        node.put("lastUpdateTime", ArknightsApplication.getTimestamp());

        userDao.setUserData(account.getUid(), userData);

        JSONObject typeDelta = new JSONObject(true);
        typeDelta.put(slotId, node.clone());
        JSONObject roomsDelta = new JSONObject(true);
        roomsDelta.put("TRADING", typeDelta);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", roomsDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        if (!statusDelta.isEmpty()) {
            modified.put("status", statusDelta);
        }
        if (!inventoryDelta.isEmpty()) {
            modified.put("inventory", inventoryDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[deliveryOrder] {} 交付订单 {}，获?{}，剩余??{}",
                slotId, orderId, gain, stock.size());
        return result;
    }

    @PostMapping(
            value = {"/deliveryBatchOrder", "/tradingDeliveryBatch"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject deliveryBatchOrder(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        JSONArray slotList = jsonBody.getJSONArray("slotList");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/deliveryBatchOrder slots={}", slotList);

        JSONObject result = new JSONObject(true);
        result.put("delivered", new JSONObject(true));

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            return result;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            return result;
        }
        Account account = accounts.get(0);

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject trading = rooms == null ? null : rooms.getJSONObject("TRADING");
        if (trading == null || trading.isEmpty()) {
            result.put("error", "no TRADING room");
            return result;
        }

        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }
        JSONObject status = userData.getJSONObject("status");

        boolean lenient = false;
        try {
            lenient = ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug");
        } catch (Exception ignored) {
        }

        List<String> targets = new ArrayList<>();
        if (slotList == null || slotList.isEmpty()) {
            targets.addAll(trading.keySet());
        } else {
            for (int i = 0; i < slotList.size(); i++) {
                targets.add(slotList.getString(i));
            }
        }

        JSONObject delivered = new JSONObject(true);
        JSONObject inventoryDelta = new JSONObject(true);
        JSONObject statusDelta = new JSONObject(true);
        long goldDelta = 0;

        for (String slotId : targets) {
            JSONObject node = trading.getJSONObject(slotId);
            if (node == null) {
                continue;
            }
            JSONArray stock = node.getJSONArray("stock");
            if (stock == null || stock.isEmpty()) {
                continue;
            }

            JSONArray remain = new JSONArray();
            JSONArray gains = new JSONArray();

            for (int i = 0; i < stock.size(); i++) {
                JSONObject order = stock.getJSONObject(i);
                if (order == null) {
                    continue;
                }
                JSONArray delivery = order.getJSONArray("delivery");
                boolean affordable = true;
                if (delivery != null) {
                    for (int j = 0; j < delivery.size(); j++) {
                        JSONObject d = delivery.getJSONObject(j);
                        if (inventory.getIntValue(d.getString("id")) < d.getIntValue("count")) {
                            affordable = false;
                            break;
                        }
                    }
                }
                if (!affordable && !lenient) {
                    remain.add(order);
                    continue;
                }

                if (delivery != null) {
                    for (int j = 0; j < delivery.size(); j++) {
                        JSONObject d = delivery.getJSONObject(j);
                        String id = d.getString("id");
                        int left = Math.max(0, inventory.getIntValue(id) - d.getIntValue("count"));
                        inventory.put(id, left);
                        inventoryDelta.put(id, left);
                    }
                }
                JSONObject gain = order.getJSONObject("gain");
                if (gain != null) {
                    String type = gain.getString("type");
                    String id = gain.getString("id");
                    int count = gain.getIntValue("count");
                    if ("GOLD".equals(type)) {
                        goldDelta += count;
                    } else {
                        inventory.put(id, inventory.getIntValue(id) + count);
                        inventoryDelta.put(id, inventory.getIntValue(id));
                    }
                    JSONObject bundle = new JSONObject(true);
                    bundle.put("id", id);
                    bundle.put("type", type);
                    bundle.put("count", count);
                    gains.add(bundle);
                }
            }

            node.put("stock", remain);
            if (remain.isEmpty()) {
                node.remove("completeWorkTime");
            }
            node.put("lastUpdateTime", ArknightsApplication.getTimestamp());
            if (!gains.isEmpty()) {
                delivered.put(slotId, gains);
            }
        }

        if (goldDelta > 0) {
            long gold = status.getLongValue("gold") + goldDelta;
            status.put("gold", gold);
            statusDelta.put("gold", gold);
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject roomsDelta = new JSONObject(true);
        JSONObject tradingDelta = new JSONObject(true);
        for (String slotId : delivered.keySet()) {
            tradingDelta.put(slotId, trading.getJSONObject(slotId).clone());
        }
        if (!tradingDelta.isEmpty()) {
            roomsDelta.put("TRADING", tradingDelta);
        }
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", roomsDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        if (!statusDelta.isEmpty()) {
            modified.put("status", statusDelta);
        }
        if (!inventoryDelta.isEmpty()) {
            modified.put("inventory", inventoryDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("delivered", delivered);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[deliveryBatchOrder] 交付完成: {}（gold +{}?", delivered, goldDelta);
        return result;
    }

    @PostMapping(
            value = {"/settleManufacture", "/settleManufact"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject settleManufacture(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        JSONArray slotList = jsonBody.getJSONArray("roomSlotIdList");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/settleManufacture slots={}", slotList);

        JSONObject result = new JSONObject(true);
        result.put("supplement", 0);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return result;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return result;
        }
        Account account = accounts.get(0);

        loadLayout();

        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject roomSlots = building.getJSONObject("roomSlots");
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MANUFACTURE");
        if (bucket == null || bucket.isEmpty()) {
            return result;
        }

        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }

        List<String> targets = new ArrayList<>();
        if (slotList == null || slotList.isEmpty()) {
            targets.addAll(bucket.keySet());
        } else {
            for (int i = 0; i < slotList.size(); i++) {
                targets.add(slotList.getString(i));
            }
        }

        long now = ArknightsApplication.getTimestamp();
        JSONObject inventoryDelta = new JSONObject(true);
        JSONObject slotsDelta = new JSONObject(true);

        for (String slotId : targets) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) {
                continue;
            }
            JSONObject slot = roomSlots.getJSONObject(slotId);
            int level = slot == null ? 1 : Math.max(1, slot.getIntValue("level"));

            advanceManufacture(node, level, now);

            int out = node.getIntValue("outputSolutionCnt");
            if (out > 0) {
                String itemId = null;
                int perUnit = 1;
                JSONObject formula = MANUFACT_FORMULAS.getJSONObject(node.getString("formulaId"));
                if (formula != null) {
                    itemId = formula.getString("itemId");
                    perUnit = Math.max(1, formula.getIntValue("count"));
                }
                if (itemId != null) {
                    int add = out * perUnit;
                    inventory.put(itemId, inventory.getIntValue(itemId) + add);
                    inventoryDelta.put(itemId, inventory.getIntValue(itemId));
                    ArknightsApplication.LOGGER.info("[settleManufacture] {} 收取 {} ×{}", slotId, itemId, add);
                }
                node.put("outputSolutionCnt", 0);
            }

            if (node.getIntValue("remainSolutionCnt") > 0 && node.getIntValue("state") == 0) {
                node.put("state", 1);           // RUN
                node.put("lastUpdateTime", now);
            }
            slotsDelta.put(slotId, node.clone());
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject typeDelta = new JSONObject(true);
        typeDelta.put("MANUFACTURE", slotsDelta);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", typeDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);
        if (!inventoryDelta.isEmpty()) {
            modified.put("inventory", inventoryDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/workshopSynthesis"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject workshopSynthesis(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/workshopSynthesis formulaId="
                + jsonBody.getString("formulaId") + " times=" + jsonBody.getIntValue("times"));
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        String formulaId = jsonBody.getString("formulaId");
        int times = Math.max(1, jsonBody.getIntValue("times"));
        JSONObject formula = WORKSHOP_FORMULAS.getJSONObject(formulaId);
        if (formula == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "?加工配方: " + formulaId);
            return bad;
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject inventory = userData.getJSONObject("inventory");
        JSONObject status = userData.getJSONObject("status");

        JSONArray costs = formula.getJSONArray("costs");
        List<String> lack = new ArrayList<>();
        if (costs != null) {
            for (int i = 0; i < costs.size(); i++) {
                JSONObject c = costs.getJSONObject(i);
                String id = c.getString("id");
                int need = c.getIntValue("count") * times;
                int have = inventory.getIntValue(id);
                if (have < need) {
                    lack.add("材料 " + id + " ??" + need + " ? " + have);
                }
            }
        }
        long goldCost = (long) formula.getIntValue("goldCost") * times;
        if (status.getLongValue("gold") < goldCost) {
            lack.add("龙门币需?" + goldCost + " ? " + status.getLongValue("gold"));
        }
        if (!lack.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "材料不足: " + String.join("?", lack));
            return bad;
        }

        JSONObject inventoryDelta = new JSONObject(true);
        if (costs != null) {
            for (int i = 0; i < costs.size(); i++) {
                JSONObject c = costs.getJSONObject(i);
                String id = c.getString("id");
                int left = Math.max(0, inventory.getIntValue(id) - c.getIntValue("count") * times);
                inventory.put(id, left);
                inventoryDelta.put(id, left);
            }
        }
        long goldLeft = status.getLongValue("gold") - goldCost;
        status.put("gold", goldLeft);

        String itemId = formula.getString("itemId");
        int outCount = Math.max(1, formula.getIntValue("count")) * times;
        int newOut = inventory.getIntValue(itemId) + outCount;
        inventory.put(itemId, newOut);
        inventoryDelta.put(itemId, newOut);

        // 官服契约：results 是单个 ItemBundle 对象（不是数组），额外产出才放 additional[]
        JSONObject main = new JSONObject(true);
        main.put("type", "MATERIAL");
        main.put("id", itemId);
        main.put("count", outCount);

        JSONArray additional = new JSONArray();
        double rate = formula.getDoubleValue("extraOutcomeRate");
        JSONArray groups = formula.getJSONArray("extraOutcomeGroup");
        if (groups != null && !groups.isEmpty() && rate > 0) {
            for (int t = 0; t < times; t++) {
                if (Math.random() >= rate) {
                    continue;
                }
                JSONObject pick = groups.getJSONObject((int) (Math.random() * groups.size()));
                String extraId = pick.getString("itemId");
                int extraCount = Math.max(1, pick.getIntValue("itemCount"));
                int after = inventory.getIntValue(extraId) + extraCount;
                inventory.put(extraId, after);
                inventoryDelta.put(extraId, after);
                JSONObject bundle = new JSONObject(true);
                bundle.put("id", extraId);
                bundle.put("type", "MATERIAL");
                bundle.put("count", extraCount);
                additional.add(bundle);
            }
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("inventory", inventoryDelta);
        JSONObject statusDelta = new JSONObject(true);
        statusDelta.put("gold", goldLeft);
        modified.put("status", statusDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("results", main);
        result.put("additional", additional);
        result.put("recoverCost", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] 加工 {} ×{} ?{}×{}，?外产?{} ?龙门?-{}",
                formulaId, times, itemId, outCount, additional.size(), goldCost);
        return result;
    }

    @PostMapping(value = {"/changeDiySolution"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject changeDiySolution(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String roomSlotId = jsonBody.getString("roomSlotId");
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/changeDiySolution slot=" + roomSlotId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(roomSlotId);
        if (slot == null || slot.getIntValue("state") != 2) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "房间槽不?: " + roomSlotId);
            return bad;
        }
        String roomType = slot.getString("roomId");
        int level = Math.max(1, slot.getIntValue("level"));
        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }
        JSONObject bucket = rooms.getJSONObject(roomType);
        if (bucket == null) {
            bucket = new JSONObject(true);
            rooms.put(roomType, bucket);
        }
        JSONObject node = bucket.getJSONObject(roomSlotId);
        if (node == null) {
            node = defaultRoomData(roomType, level, ArknightsApplication.getTimestamp());
            bucket.put(roomSlotId, node);
        }

        JSONObject req = jsonBody.getJSONObject("solution");
        JSONObject solution = new JSONObject(true);
        solution.put("wallPaper", req == null ? null : req.get("wallPaper"));
        solution.put("floor", req == null ? null : req.get("floor"));
        solution.put("carpet", req == null || req.getJSONArray("carpet") == null ? new JSONArray() : req.getJSONArray("carpet"));
        solution.put("other", req == null || req.getJSONArray("other") == null ? new JSONArray() : req.getJSONArray("other"));

        JSONObject furniture = building.getJSONObject("furniture");
        if (furniture == null) {
            furniture = new JSONObject(true);
            building.put("furniture", furniture);
        }
        java.util.Map<String, Integer> need = new java.util.HashMap<>();
        for (String key : new String[]{"carpet", "other"}) {
            JSONArray list = solution.getJSONArray(key);
            for (int i = 0; i < list.size(); i++) {
                JSONObject it = list.getJSONObject(i);
                String id = it == null ? null : it.getString("id");
                if (id != null) {
                    need.merge(id, 1, Integer::sum);
                }
            }
        }
        for (java.util.Map.Entry<String, Integer> e : need.entrySet()) {
            JSONObject own = furniture.getJSONObject(e.getKey());
            int have = own == null ? 0 : own.getIntValue("count");
            if (have < e.getValue()) {
                JSONObject bad = new JSONObject(true);
                bad.put("result", 1);
                bad.put("error", "家具不足: " + e.getKey() + " ??" + e.getValue() + " 件，? " + have);
                return bad;
            }
        }

        node.put("diySolution", solution);
        node.put("comfort", dormComfort(node));
        if (node.getJSONArray("lockQueue") == null) {
            JSONArray lock = new JSONArray();
            for (int i = 0; i < 5; i++) {
                lock.add(0);
            }
            node.put("lockQueue", lock);
        }
        int inUseTotal = recomputeFurnitureInUse(building);

        userDao.setUserData(account.getUid(), userData);

        JSONObject Building = new JSONObject(true);
        Building.put("roomSlots", building.getJSONObject("roomSlots"));
        Building.put("rooms", building.getJSONObject("rooms"));
        Building.put("chars", building.getJSONObject("chars"));
        Building.put("status", building.getJSONObject("status"));
        Building.put("furniture", building.getJSONObject("furniture"));

        JSONObject modified = new JSONObject(true);
        modified.put("building", Building);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] {} 布置完成: {} 种?具，comfort={}，占?{} ?",
                roomSlotId, need.size(), node.getIntValue("comfort"), inUseTotal);
        return result;
    }

    @PostMapping(value = {"/editLockQueue"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject editLockQueue(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject jsonBody,
                                    HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        JSONObject lockPos = jsonBody.getJSONObject("lockPos");
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/editLockQueue lockPos=" + lockPos);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }
        if (lockPos != null) {
            for (String slotId : new ArrayList<>(lockPos.keySet())) {
                JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
                if (slot == null) {
                    ArknightsApplication.LOGGER.warn("[building] editLockQueue ?槽位 {}", slotId);
                    continue;
                }
                String roomType = slot.getString("roomId");
                int level = Math.max(1, slot.getIntValue("level"));
                JSONObject bucket = rooms.getJSONObject(roomType);
                if (bucket == null) {
                    bucket = new JSONObject(true);
                    rooms.put(roomType, bucket);
                }
                JSONObject node = bucket.getJSONObject(slotId);
                if (node == null) {
                    node = defaultRoomData(roomType, level, ArknightsApplication.getTimestamp());
                    bucket.put(slotId, node);
                }
                node.put("lockQueue", lockPos.getJSONArray(slotId));
            }
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject Building = new JSONObject(true);
        Building.put("roomSlots", building.getJSONObject("roomSlots"));
        Building.put("rooms", building.getJSONObject("rooms"));
        Building.put("chars", building.getJSONObject("chars"));
        Building.put("status", building.getJSONObject("status"));

        JSONObject modified = new JSONObject(true);
        modified.put("building", Building);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static double trainingBuffSpeed(String trainerCharId) {
        double base = 0.05;
        if (trainerCharId == null || BUILDING_CHAR_BUFFS == null) {
            return base;
        }
        JSONObject def = BUILDING_CHAR_BUFFS.getJSONObject(trainerCharId);
        JSONArray groups = def == null ? null : def.getJSONArray("buffChar");
        if (groups == null) {
            return base;
        }
        for (int i = 0; i < groups.size(); i++) {
            JSONObject g = groups.getJSONObject(i);
            JSONArray buffData = g == null ? null : g.getJSONArray("buffData");
            if (buffData == null) {
                continue;
            }
            for (int k = 0; k < buffData.size(); k++) {
                String buffId = buffData.getJSONObject(k).getString("buffId");
                JSONObject b = BUILDING_BUFFS == null ? null : BUILDING_BUFFS.getJSONObject(buffId);
                if (b != null && "TRAINING".equals(b.getString("roomType"))) {
                    return base + 0.10;
                }
            }
        }
        return base;
    }

    private static boolean advanceTraining(JSONObject userData, JSONObject node, long now) {
        JSONObject trainee = node.getJSONObject("trainee");
        if (trainee == null || trainee.getIntValue("state") != 1) {
            return false;
        }
        long last = node.getLongValue("lastUpdateTime");
        if (last <= 0 || last > now) {
            node.put("lastUpdateTime", now);
            return true;
        }
        long elapsed = now - last;
        if (elapsed > 600) {
            elapsed = 600;              // 离线补算上限 10 分钟
        }
        double speed = Math.max(0.01, trainee.getDoubleValue("speed"));
        double left = trainee.getDoubleValue("processPoint") - elapsed * speed;
        node.put("lastUpdateTime", now);
        if (left > 0) {
            trainee.put("processPoint", (long) Math.round(left));
            return true;
        }
        return finishTraining(userData, node);
    }

    private static boolean finishTraining(JSONObject userData, JSONObject node) {
        JSONObject trainee = node.getJSONObject("trainee");
        if (trainee == null) {
            return false;
        }
        int charInstId = trainee.getIntValue("charInstId");
        int skillIndex = trainee.getIntValue("targetSkill");
        JSONObject troop = userData.getJSONObject("troop");
        JSONObject troopChars = troop == null ? null : troop.getJSONObject("chars");
        JSONObject tc = troopChars == null || charInstId <= 0 ? null : troopChars.getJSONObject(String.valueOf(charInstId));
        if (tc != null) {
            JSONArray skills = tc.getJSONArray("skills");
            if (skills != null && skillIndex >= 0 && skillIndex < skills.size()) {
                JSONObject sk = skills.getJSONObject(skillIndex);
                int lvl = sk.getIntValue("specializeLevel") + 1;
                sk.put("specializeLevel", lvl);
                sk.put("completeUpgradeTime", -1);
                ArknightsApplication.LOGGER.info("[building] ?完成 charInstId={} skillIndex={} ?专精 {}",
                        charInstId, skillIndex, lvl);
            }
        }
        trainee.put("processPoint", 0);
        trainee.put("targetSkill", -1);
        trainee.put("state", 3);
        node.put("state", 0);
        return true;
    }

    private static Object[] findIdleTrainee(JSONObject building) {
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("TRAINING");
        if (bucket == null) {
            return null;
        }
        for (String slotId : new ArrayList<>(bucket.keySet())) {
            JSONObject node = bucket.getJSONObject(slotId);
            JSONObject trainee = node == null ? null : node.getJSONObject("trainee");
            if (trainee != null && trainee.getIntValue("state") == 3 && trainee.getIntValue("charInstId") > 0) {
                return new Object[]{slotId, node};
            }
        }
        return null;
    }

    @PostMapping(value = {"/upgradeSpecialization"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject upgradeSpecialization(@RequestHeader(value = "secret", required = false) String secret,
                                            @RequestBody JSONObject jsonBody,
                                            HttpServletRequest request, HttpServletResponse response) {
        int skillIndex = jsonBody.getIntValue("skillIndex");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/upgradeSpecialization skillIndex=" + skillIndex);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        loadLayout();
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        Object[] hit = findIdleTrainee(building);
        if (hit == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "训练室里没有待练（已指派且空闲）的学员");
            return bad;
        }
        String slotId = (String) hit[0];
        JSONObject node = (JSONObject) hit[1];
        JSONObject trainee = node.getJSONObject("trainee");
        int charInstId = trainee.getIntValue("charInstId");
        JSONObject troopChars = userData.getJSONObject("troop").getJSONObject("chars");
        JSONObject tc = troopChars.getJSONObject(String.valueOf(charInstId));
        if (tc == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "学员不存? " + charInstId);
            return bad;
        }
        String charId = tc.getString("charId");
        JSONObject charDef = ArknightsApplication.characterJson.getJSONObject(charId);
        JSONArray skillsDef = charDef == null ? null : charDef.getJSONArray("skills");
        if (skillsDef == null || skillIndex < 0 || skillIndex >= skillsDef.size() - 1) {
        }
        if (skillsDef == null || skillIndex < 0 || skillIndex >= skillsDef.size()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "？？？ " + skillIndex);
            return bad;
        }
        JSONArray conds = skillsDef.getJSONObject(skillIndex).getJSONArray("levelUpCostCond");
        JSONArray charSkills = tc.getJSONArray("skills");
        int curLevel = 0;
        if (charSkills != null && skillIndex < charSkills.size()) {
            curLevel = charSkills.getJSONObject(skillIndex).getIntValue("specializeLevel");
        }
        if (conds == null || curLevel >= conds.size()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该技能已经满专精（专精等级 " + curLevel + "）");
            return bad;
        }
        JSONObject cond = conds.getJSONObject(curLevel);
        int lvlUpTime = cond.getIntValue("lvlUpTime");
        JSONArray costs = cond.getJSONArray("levelUpCost");

        JSONObject inventory = userData.getJSONObject("inventory");
        List<String> lack = new ArrayList<>();
        if (costs != null) {
            for (int i = 0; i < costs.size(); i++) {
                JSONObject c = costs.getJSONObject(i);
                String id = c.getString("id");
                int need = c.getIntValue("count");
                if (inventory.getIntValue(id) < need) {
                    lack.add(id + " ??" + need + " ? " + inventory.getIntValue(id));
                }
            }
        }
        if (!lack.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "材料不足: " + String.join("?", lack));
            return bad;
        }
        JSONObject inventoryDelta = new JSONObject(true);
        if (costs != null) {
            for (int i = 0; i < costs.size(); i++) {
                JSONObject c = costs.getJSONObject(i);
                String id = c.getString("id");
                int left = inventory.getIntValue(id) - c.getIntValue("count");
                inventory.put(id, left);
                inventoryDelta.put(id, left);
            }
        }

        JSONObject buff = node.getJSONObject("buff");
        double buffSpeed = buff == null ? 0 : buff.getDoubleValue("speed");
        long now = ArknightsApplication.getTimestamp();
        trainee.put("state", 1);
        trainee.put("targetSkill", skillIndex);
        trainee.put("processPoint", lvlUpTime);
        trainee.put("speed", 1.0 + buffSpeed);
        node.put("state", 1);
        node.put("lastUpdateTime", now);

        userDao.setUserData(account.getUid(), userData);

        JSONObject Building = new JSONObject(true);
        Building.put("roomSlots", building.getJSONObject("roomSlots"));
        Building.put("rooms", building.getJSONObject("rooms"));
        Building.put("chars", building.getJSONObject("chars"));
        Building.put("status", building.getJSONObject("status"));
        JSONObject troopDelta = new JSONObject(true);
        JSONObject charsDelta = new JSONObject(true);
        charsDelta.put(String.valueOf(charInstId), tc);
        troopDelta.put("chars", charsDelta);

        JSONObject modified = new JSONObject(true);
        modified.put("building", Building);
        modified.put("inventory", inventoryDelta);
        modified.put("troop", troopDelta);
        JSONObject eventDelta = new JSONObject(true);
        JSONObject ev = userData.getJSONObject("event");
        eventDelta.put("building", ev == null ? 0 : ev.getLongValue("building"));
        modified.put("event", eventDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] ?始??{} skillIndex={} 点数={} speed={}",
                slotId, skillIndex, lvlUpTime, 1.0 + buffSpeed);
        return result;
    }

    @PostMapping(value = {"/completeUpgradeSpecialization"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject completeUpgradeSpecialization(@RequestHeader(value = "secret", required = false) String secret,
                                                    @RequestBody JSONObject jsonBody,
                                                    HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /building/completeUpgradeSpecialization");
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("TRAINING");
        JSONObject troopChars = userData.getJSONObject("troop").getJSONObject("chars");
        JSONObject charsDelta = new JSONObject(true);
        if (bucket != null) {
            for (String slotId : new ArrayList<>(bucket.keySet())) {
                JSONObject node = bucket.getJSONObject(slotId);
                JSONObject trainee = node == null ? null : node.getJSONObject("trainee");
                if (trainee == null || trainee.getIntValue("state") != 1) {
                    continue;
                }
                if (trainee.getDoubleValue("processPoint") > 0) {
                    continue;                       // 还没到点，忽?
                }
                int charInstId = trainee.getIntValue("charInstId");
                finishTraining(userData, node);
                JSONObject tc = troopChars.getJSONObject(String.valueOf(charInstId));
                if (tc != null) {
                    charsDelta.put(String.valueOf(charInstId), tc);
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("rooms", building.getJSONObject("rooms"));
        modified.put("building", buildingDelta);
        if (!charsDelta.isEmpty()) {
            JSONObject troopDelta = new JSONObject(true);
            troopDelta.put("chars", charsDelta);
            modified.put("troop", troopDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static int recomputeFurnitureInUse(JSONObject building) {
        JSONObject furniture = building.getJSONObject("furniture");
        if (furniture == null) {
            return 0;
        }
        for (String id : new ArrayList<>(furniture.keySet())) {
            JSONObject own = furniture.getJSONObject(id);
            if (own != null) {
                own.put("inUse", 0);
            }
        }
        JSONObject rooms = building.getJSONObject("rooms");
        int total = 0;
        if (rooms != null) {
            for (String roomType : new ArrayList<>(rooms.keySet())) {
                JSONObject bucket = rooms.getJSONObject(roomType);
                if (bucket == null) {
                    continue;
                }
                for (String slotId : new ArrayList<>(bucket.keySet())) {
                    JSONObject node = bucket.getJSONObject(slotId);
                    JSONObject diy = node == null ? null : node.getJSONObject("diySolution");
                    if (diy == null) {
                        continue;
                    }
                    for (String key : new String[]{"carpet", "other"}) {
                        JSONArray list = diy.getJSONArray(key);
                        if (list == null) {
                            continue;
                        }
                        for (int i = 0; i < list.size(); i++) {
                            JSONObject it = list.getJSONObject(i);
                            String id = it == null ? null : it.getString("id");
                            if (id == null) {
                                continue;
                            }
                            JSONObject own = furniture.getJSONObject(id);
                            if (own != null) {
                                own.put("inUse", own.getIntValue("inUse") + 1);
                            }
                            total++;
                        }
                    }
                }
            }
        }
        return total;
    }

    @PostMapping(value = {"/setBuildingAssist"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject setBuildingAssist(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        int type = jsonBody.getIntValue("type");
        int charInstId = jsonBody.getIntValue("charInstId");
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/setBuildingAssist type=" + type
                + " charInstId=" + charInstId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "no building data");
            return bad;
        }
        if (type < 0 || type > 4) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "type 必须?0..4: " + type);
            return bad;
        }

        JSONArray assist = building.getJSONArray("assist");
        if (assist == null || assist.size() < 5) {
            assist = new JSONArray();
            for (int i = 0; i < 5; i++) {
                assist.add(-1);
            }
            building.put("assist", assist);
        }

        // 同一干员不能占两个槽位
        if (charInstId > 0) {
            for (int i = 0; i < 5; i++) {
                if (assist.getIntValue(i) == charInstId && i != type) {
                    assist.set(i, -1);
                }
            }
        }
        assist.set(type, charInstId > 0 ? charInstId : -1);

        JSONObject buildingChars = building.getJSONObject("chars");
        if (buildingChars == null) {
            buildingChars = new JSONObject(true);
            building.put("chars", buildingChars);
        }
        if (charInstId > 0 && !buildingChars.containsKey(String.valueOf(charInstId))) {
            JSONObject entry = new JSONObject(true);
            entry.put("lastApAddTime", ArknightsApplication.getTimestamp());
            buildingChars.put(String.valueOf(charInstId), entry);
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject Building = new JSONObject(true);
        Building.put("roomSlots", building.getJSONObject("roomSlots"));
        Building.put("rooms", building.getJSONObject("rooms"));
        Building.put("chars", building.getJSONObject("chars"));
        Building.put("status", building.getJSONObject("status"));
        Building.put("assist", assist);

        JSONObject modified = new JSONObject(true);
        modified.put("building", Building);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[building] ??{} ?{}（assist={}?", type, charInstId, assist);
        return result;
    }

    @PostMapping(
            value = {"/getAssistReport"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getAssistReport(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/getAssistReport");
        JSONObject result = new JSONObject(true);
        result.put("reports", new JSONArray());
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }

    @PostMapping(
            value = {"/getRecentVisitors"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getRecentVisitors(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/getRecentVisitors");
        // 官服返回 {"visitors":[]}
        JSONObject result = new JSONObject(true);
        result.put("visitors", new JSONArray());
        return result;
    }

    @PostMapping(
            value = {"/getInfoShareVisitorsNum"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getInfoShareVisitorsNum(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/getInfoShareVisitorsNum");
        // 官服返回 {"num":N}：情报分享期间来访问基地的人数
        int num = 0;
        try {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            if (accounts.size() == 1) {
                Account account = accounts.get(0);
                JSONObject userData = JSON.parseObject(account.getUser());
                Object[] hit = meetingSlot(userData.getJSONObject("building"));
                long since = 0;
                if (hit != null) {
                    JSONObject node = (JSONObject) hit[1];
                    JSONObject infoShare = node.getJSONObject("infoShare");
                    if (infoShare != null) {
                        long ts = infoShare.getLongValue("ts");
                        since = ts > 0 ? ts - 86400L : 0;
                    }
                }
                num = com.hypergryph.arknights.core.dao.visitDao.countSince(account.getUid(), since);
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[building] getInfoShareVisitorsNum 失败: {}", e.toString());
        }
        JSONObject result = new JSONObject(true);
        result.put("num", num);
        return result;
    }

    @PostMapping(value = {"/gainIntimacy"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gainIntimacy(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/gainIntimacy charInstId="
                + jsonBody.getString("charInstId"));
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        int instId = jsonBody.getIntValue("charInstId");
        JSONObject troop = userData.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        JSONObject charDelta = new JSONObject(true);
        if (chars != null && chars.getJSONObject(String.valueOf(instId)) != null) {
            JSONObject c = chars.getJSONObject(String.valueOf(instId));
            int favor = c.getIntValue("favorPoint") + 1;
            c.put("favorPoint", favor);
            charDelta.put(String.valueOf(instId), c);
            userDao.setUserData(account.getUid(), userData);
        }
        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(userData.getJSONObject("building")));
        JSONObject troopDelta = new JSONObject(true);
        troopDelta.put("chars", charDelta);
        modified.put("troop", troopDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping(value = {"/gainAssistIntimacy"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gainAssistIntimacy(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody JSONObject jsonBody,
                                         HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/gainAssistIntimacy charInstId="
                + jsonBody.getString("charInstId"));
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        if (jsonBody.get("charInstId") == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("statusCode", 400);
            bad.put("error", "Bad Request");
            bad.put("message", "\"charInstId\" is required");
            return bad;
        }
        return gainIntimacy(secret, jsonBody, request, response);
    }

    @PostMapping(value = {"/changeBGM"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject changeBGM(@RequestHeader(value = "secret", required = false) String secret,
                                @RequestBody JSONObject jsonBody,
                                HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String musicId = jsonBody.getString("musicId");
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/changeBGM musicId=" + musicId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        JSONObject music = building == null ? null : building.getJSONObject("music");
        if (music == null) {
            music = new JSONObject(true);
            music.put("inUse", true);
            music.put("state", new JSONObject(true));
            building.put("music", music);
        }
        music.put("selected", musicId);
        music.put("inUse", true);
        userDao.setUserData(account.getUid(), userData);
        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingForDelta(building));
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
    
    @PostMapping(value = {"/sendEmoji"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject sendEmoji(@RequestHeader(value = "secret", required = false) String secret,
                                @RequestBody JSONObject jsonBody,
                                HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String friendId = jsonBody.getString("friendId");
        String emoji = jsonBody.getString("emoji");
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /building/sendEmoji friendId=" + friendId
                + " emoji=" + emoji);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        JSONObject result = new JSONObject(true);
        result.put("nextTs", ArknightsApplication.getTimestamp() + 60);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
