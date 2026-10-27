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
import java.util.List;

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
                ArknightsApplication.LOGGER.info("[building/sync] 每日重置已落库 event.building={}",
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

    public static boolean advanceBuildingTime(JSONObject userData, long now) {
        loadLayout();
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            return false;
        }
        boolean dirty = false;

        // 1) 无人机回复
        JSONObject status = building.getJSONObject("status");
        JSONObject labor = status == null ? null : status.getJSONObject("labor");
        if (labor != null) {
            int max = labor.getIntValue("maxValue");
            int value = labor.getIntValue("value");
            long last = labor.getLongValue("lastUpdateTime");
            long per = 360L;                       // laborRecoverTime
            if (last <= 0 || last > now) {
                labor.put("lastUpdateTime", now);
                dirty = true;
            } else if (max > 0 && value < max) {
                long gained = (now - last) / per;
                if (gained > 0) {
                    int nv = (int) Math.min(max, value + gained);
                    labor.put("value", nv);
                    // 满了就停表；没满则保留不足一架的余量
                    labor.put("lastUpdateTime", nv >= max ? now : last + gained * per);
                    dirty = true;
                    ArknightsApplication.LOGGER.info("[building] 无人机回复 {} -> {}（+{}）", value, nv, gained);
                }
            }
        }

        // 2) 制造站产出
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
                    // 生产中：最多每 5 秒落一次库，让 lastUpdateTime 别长期停滞
                    dirty = true;
                }
            }
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
        } catch (IOException e) {
            ArknightsApplication.LOGGER.error("[building] 读取 {} 失败: {}", path, e.toString());
            LAYOUT_SLOTS = new JSONObject();
            LAYOUT_CLEAN_COSTS = new JSONObject();
            ROOMS_TABLE = new JSONObject();
            ROOM_UNLOCK_CONDS = new JSONObject();
            MANUFACT_DATA = new JSONObject();
            TRADING_DATA = new JSONObject();
            MANUFACT_FORMULAS = new JSONObject();
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
            ArknightsApplication.LOGGER.info("[manufacture] 停产：{}（剩 {} 件，仓 {}/{}）",
                    newRemain <= 0 ? "配方已完成" : "仓库已满", newRemain, newOutput, capacity);
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
            ArknightsApplication.LOGGER.info("[building] 修复自动房间槽 {} → {} (level 1, state 2)", slotId, autoRoom);
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
                    ArknightsApplication.LOGGER.info("[building] 补建制造站数据 {} (capacity={})", slotId, cap);
                } else if (node.getIntValue("capacity") <= 0) {
                    node.put("capacity", cap);
                    if (node.getIntValue("remainSolutionCnt") <= 0) {
                        node.put("remainSolutionCnt", cap);
                    }
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 修复制造站 {} 仓库容量 → {}", slotId, cap);
                } else if (node.getIntValue("state") == 0
                        && node.getIntValue("remainSolutionCnt") > 0
                        && node.getIntValue("outputSolutionCnt") == 0
                        && node.getLongValue("saveTime") == 0) {
                    node.put("state", 1);           // RUN
                    node.put("lastUpdateTime", now);
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 修复制造站 {} 状态 STOP → RUN", slotId);
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
                    ArknightsApplication.LOGGER.info("[building] 制造站 {} 生产倍速 → {}×（buff.speed={}）",
                            slotId, ps, ps - 1.0);
                }
            } else if ("TRADING".equals(roomType)) {
                int limit = tradingOrderLimit(level);
                if (node == null) {
                    bucket.put(slotId, defaultRoomData(roomType, level, now));
                    fixed++;
                    ArknightsApplication.LOGGER.info("[building] 补建贸易站数据 {} ({} 单)", slotId, limit);
                } else {
                    if (node.getIntValue("stockLimit") <= 0) {
                        node.put("stockLimit", limit);
                        fixed++;
                    }
                    JSONArray stock = node.getJSONArray("stock");
                    if (stock == null || stock.isEmpty()) {
                        node.put("stock", makeTradingOrders(limit));
                        fixed++;
                        ArknightsApplication.LOGGER.info("[building] 修复贸易站 {} 订单 → {} 单", slotId, limit);
                    }
                }
            } else if (node == null) {
                bucket.put(slotId, defaultRoomData(roomType, level, now));
                fixed++;
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
                ArknightsApplication.LOGGER.info("[building] 修复无人机上限 {} → {}（已清理 {} 个槽，当前 {}）",
                        oldMax, sum, slots.size(), labor.getIntValue("value"));
            }
        }
        return fixed;
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
            ArknightsApplication.LOGGER.info("[tutorial] 基建教程材料已充足，跳过发放（progress={}）", progress);
            return "skip";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kitIds.length; i++) {
            if (inv.getIntValue(kitIds[i]) < kitCounts[i]) {
                inv.put(kitIds[i], kitCounts[i]);
                sb.append(kitIds[i]).append("×").append(kitCounts[i]).append(' ');
            }
        }
        ArknightsApplication.LOGGER.info("[tutorial] 基建教程材料已发放（一次性）: {}（progress={}）", sb.toString().trim(), progress);
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
                ArknightsApplication.LOGGER.warn("[cleanRoomSlot] 材料不足但 debug 模式放行: {} need={} have={}",
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
                ArknightsApplication.LOGGER.warn("[cleanRoomSlot] 无人机不足但 debug 放行: need={} have={}", costLabor, have);
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
        ArknightsApplication.LOGGER.info("[cleanRoomSlot] {} 清理完成（{} 消耗 {}，labor {}）",
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
            JSONObject order = new JSONObject(true);
            order.put("instId", i);
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
            stock.add(order);
        }
        return stock;
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
            r.put("buff", new JSONObject());
        }
        return r;
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
            result.put("error", "roomId " + roomId + "(" + roomCategory + ") 不能建在 " + slotCategory + " 槽");
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
                    condFail = "需要 " + needCount + " 个 " + needType + " 等级>=" + needLevel + "，当前 " + have;
                }
            }
        }
        if (condFail != null) {
            if (!lenient) {
                result.put("result", 1);
                result.put("error", condFail);
                return result;
            }
            ArknightsApplication.LOGGER.warn("[buildRoom] 解锁条件未满足但 debug 放行: {}", condFail);
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
                ArknightsApplication.LOGGER.warn("[buildRoom] 材料不足但 debug 放行: {}", it);
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
                ArknightsApplication.LOGGER.warn("[buildRoom] 无人机不足但 debug 放行: need={} have={}", costLabor, have);
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
            slot.put("state", 2);                       // BUILT
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
        ArknightsApplication.LOGGER.info("[buildRoom] {} 建成 {}（消耗 {}，labor {}，第 {} 个）",
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

        userDao.setUserData(account.getUid(), userData);

        JSONObject slotsDelta = new JSONObject(true);
        slotsDelta.put(roomSlotId, slot.clone());

        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("roomSlots", slotsDelta);
        if (!charsDelta.isEmpty()) {
            buildingDelta.put("chars", charsDelta);
        }

        JSONObject modified = new JSONObject(true);
        modified.put("building", buildingDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[assignChar] {} ({}) 上岗名单 → {}（容量 {}）",
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
        ArknightsApplication.LOGGER.info("[changeManufactureSolution] {} 配方 → {}（数量 {}）", roomSlotId, targetFormulaId, solutionCount);
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
                    ArknightsApplication.LOGGER.warn("[deliveryOrder] 材料不足但 debug 放行: {} need={} have={}",
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
        ArknightsApplication.LOGGER.info("[deliveryOrder] {} 交付订单 {}，获得 {}，剩余订单 {}",
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
        ArknightsApplication.LOGGER.info("[deliveryBatchOrder] 交付完成: {}（gold +{}）", delivered, goldDelta);
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

    @PostMapping(
            value = {"/setBuildingAssist"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject setBuildingAssist(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/setBuildingAssist");
        ArknightsApplication.LOGGER.info("setBuildingAssist:" + jsonBody);
        return new  JSONObject();
    }

    @PostMapping(
            value = {"/getAssistReport"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getAssistReport(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /getAssistReport");
        ArknightsApplication.LOGGER.info("getAssistReport:" + jsonBody);
        return new  JSONObject();
    }

    @PostMapping(
            value = {"/getRecentVisitors"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getRecentVisitors(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /getRecentVisitors");
        ArknightsApplication.LOGGER.info("getRecentVisitors:" + jsonBody);
        return new  JSONObject();
    }

    @PostMapping(
            value = {"/getInfoShareVisitorsNum"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getInfoShareVisitorsNum(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /getInfoShareVisitorsNum");
        ArknightsApplication.LOGGER.info("getInfoShareVisitorsNum:" + jsonBody);
        return new  JSONObject();
    }
}
