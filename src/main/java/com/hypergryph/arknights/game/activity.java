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
import java.util.List;
import static com.hypergryph.arknights.ArknightsApplication.LOGGER;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
import static com.hypergryph.arknights.core.work.consumableNumberG.*;
import static com.hypergryph.arknights.core.work.getTodayRewards.getTodayReward;

@RestController
@RequestMapping("/activity")
public class activity {
    public  activity() {
    }

    @PostMapping(
            value = {"/getChainLogInReward"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getChainLogInReward(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getChainLogInReward");
        LOGGER.info("getloginchain" + JsonBody);
        int index = JsonBody.getInteger("index");
        if(checkAccount(secret)){
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData;
            UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            JSONObject result = new JSONObject();
            JSONObject reward = new JSONObject();
            JSONObject playerDataDelta = new JSONObject();
            playerDataDelta.put("deleted", new JSONObject());
            JSONObject modified = new JSONObject();
            JSONObject todayRewards = getTodayReward(1, index);
            String itemType = todayRewards.getJSONObject("item").getString("itemType");
            int count = todayRewards.getJSONObject("item").getInteger("count");
            String itemId  = todayRewards.getJSONObject("item").getString("itemId");
            JSONObject openServer = UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin");
            reward.put("type", itemType);
            reward.put("count", count);
            reward.put("id", itemId);
            result.put("reward", reward);
            JSONArray history = openServer.getJSONArray("history");
            history.remove(index);
            history.add(0);
            int T = ArknightsApplication.checkCheckInItemType(itemType);
            JSONObject chainLogin =  new JSONObject();
            JSONObject changedOS = new JSONObject();
            if (index == 6) {
                chainLogin.put("isAvailable", false);
                chainLogin.put("nowIndex", 14);
                chainLogin.put("finalReward", 1);
            } else {
                chainLogin.put("isAvailable", true);
                chainLogin.put("nowIndex", index);
                chainLogin.put("finalReward", 0);
            }
            chainLogin.put("history", history);
            changedOS.put("chainLogin", chainLogin);
            modified.put("openServer", changedOS);
            if (T == 6) {
                JSONObject changedStatus = new JSONObject();
                JSONObject status = UserSyncData.getJSONObject("status");
                int andD = status.getInteger("androidDiamond");
                int iosD = status.getInteger("iosDiamond");
                iosD = iosD + count;
                andD = andD + count;
                status.put("androidDiamond", andD);
                status.put("iosDiamond", iosD);
                UserSyncData.put("status", status);
                changedStatus.put("iosDiamond", iosD);
                changedStatus.put("androidDiamond", andD);
                modified.put("status", changedStatus);
                UserSyncData.put("status", status);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                userDao.setUserData(uid, UserSyncData);
                return result;
            }
            else if(T == 3) {
                int TP = resolveTKT(itemType);
                JSONObject changedStatus = new JSONObject();
                JSONObject status = UserSyncData.getJSONObject("status");
                if(TP == 1){
                    int gachaTicket = status.getInteger("gachaTicket");
                    gachaTicket = gachaTicket + count;
                    changedStatus.put("gachaTicket", gachaTicket);
                    modified.put("status", changedStatus);
                    UserSyncData.put("status", status);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                    userDao.setUserData(uid, UserSyncData);
                    return result;
                } else if(TP == 2){
                    int classicGachaTicket = status.getInteger("classicGachaTicket");
                    classicGachaTicket = classicGachaTicket + count;
                    changedStatus.put("classicGachaTicket", classicGachaTicket);
                    modified.put("status", changedStatus);
                    UserSyncData.put("status", status);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                    userDao.setUserData(uid, UserSyncData);
                    return result;
                }
            }
            else if(T == 5){
                JSONObject consumable = UserSyncData.getJSONObject("consumable");
                if(isExist(itemId, consumable)){
                    JSONObject consumableData = getInnerObject(consumable.getJSONObject(itemId));
                    int num = consumableData.getInteger("count");
                    count = num + count;
                    int me = getInnerNumber(consumable.getJSONObject(itemId));
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    A.put("ts", -1);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    consumable.put(itemId, B);
                    JSONObject Change = new JSONObject();
                    Change.put(itemId, B);
                    UserSyncData.put("consumable", consumable);
                    userDao.setUserData(uid, UserSyncData);
                    modified.put("consumable", Change);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    A.put("ts", -1);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    JSONObject consumableData = new JSONObject();
                    consumable.put(itemId, B);
                    JSONObject Change = new JSONObject();
                    Change.put(itemId, consumableData);
                    UserSyncData.put("consumable", consumable);
                    userDao.setUserData(uid, UserSyncData);
                    modified.put("consumable", Change);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }
        } else {
            return createError(secret, response);
        }
        return new JSONObject();
    }


    private static int resolveTKT(String TKT){
        return switch (TKT) {
            case "TKT_GACHA" -> 1;
            case "CLASSIC_TKT_GACHA_10" -> 2;
            default -> 2;
        };
    }

    @PostMapping(
            value = {"/getOpenServerCheckInReward"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getOpenServerCheckInReward(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getOpenServerCheckInReward");
        LOGGER.info("getloginchain" + JsonBody);
        if (checkAccount(secret)) {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            JSONObject result = new JSONObject();
            JSONObject reward = new JSONObject();
            JSONObject playerDataDelta = new JSONObject();
            playerDataDelta.put("deleted", new JSONObject());
            JSONObject modified = new JSONObject();
            JSONObject openServer = UserSyncData.getJSONObject("openServer").getJSONObject("checkIn");
            JSONArray history = openServer.getJSONArray("history");
            int index = history.size() - 1;
            history.remove(index);
            history.add(0);
            JSONObject todayRewards = getTodayReward(2, index);
            String itemType = todayRewards.getJSONObject("item").getString("itemType");
            int count = todayRewards.getJSONObject("item").getInteger("count");
            String itemId  = todayRewards.getJSONObject("item").getString("itemId");
            reward.put("type", itemType);
            reward.put("count", count);
            reward.put("id", itemId);
            result.put("reward", reward);
            openServer.put("history", history);
            JSONObject CI = new JSONObject();
            CI.put("checkIn", openServer);
            modified.put("openServer", CI);
            UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").put("history", history);
            int T = ArknightsApplication.checkCheckInItemType(itemType);
            if(T == 5){
                JSONObject consumable = UserSyncData.getJSONObject("consumable");
                if(isExist(itemId, consumable)){
                    JSONObject consumableData = getInnerObject(consumable.getJSONObject(itemId));
                    int num = consumableData.getInteger("count");
                    count = num + count;
                    int me = getInnerNumber(consumable.getJSONObject(itemId));
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    A.put("ts", -1);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    consumable.put(itemId, B);
                    JSONObject Change = new JSONObject();
                    Change.put(itemId, B);
                    UserSyncData.put("consumable", consumable);
                    userDao.setUserData(uid, UserSyncData);
                    modified.put("consumable", Change);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    A.put("ts", -1);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    JSONObject consumableData = new JSONObject();
                    consumable.put(itemId, B);
                    JSONObject Change = new JSONObject();
                    Change.put(itemId, consumableData);
                    UserSyncData.put("consumable", consumable);
                    userDao.setUserData(uid, UserSyncData);
                    modified.put("consumable", Change);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }else if (T == 1){
                JSONObject status = UserSyncData.getJSONObject("status");
                JSONObject changedStatus = new JSONObject();
                int num = status.getInteger("gold");
                count = num + count;
                status.put("gold", count);
                changedStatus.put("gold", count);
                UserSyncData.put("status", status);
                userDao.setUserData(uid, UserSyncData);
                modified.put("status", changedStatus);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
            else if (T == 2) {
                JSONObject inventory = UserSyncData.getJSONObject("inventory");
                JSONObject inventoryU = new JSONObject();
                if (inventory.getInteger(itemId) != null) {
                    int counts = inventory.getInteger(itemId);
                    count = counts + count;
                    inventory.put(itemId, count);
                    UserSyncData.put("inventory", inventory);
                    inventoryU.put(itemId, count);
                } else {
                    inventory.put(itemId, count);
                    UserSyncData.put("inventory", inventory);
                    inventoryU.put(itemId, count);
                }
                modified.put("inventory", inventoryU);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                userDao.setUserData(uid, UserSyncData);
                return result;
            }
            else if (T == 3) {
                JSONObject status = UserSyncData.getJSONObject("status");
                JSONObject changedStatus = new JSONObject();
                int num = status.getInteger("instantFinishTicket");
                count = num + count;
                status.put("instantFinishTicket", count);
                changedStatus.put("instantFinishTicket", count);
                UserSyncData.put("status", status);
                userDao.setUserData(uid, UserSyncData);
                modified.put("status", changedStatus);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        } else {
            return createError(secret, response);
        }
        return new JSONObject(true);
    }

    // ─────────────────────── 活动奖励（官服抓包对齐） ───────────────────────
    // 官服状态路径是 user.activity.<活动类型>.<activityId>，
    //   CHECKIN_ONLY  → {lastTs, history:[0/1...]}
    //   CHECKIN_VIDEO → {lastTs, history:[0/1...]}
    //   LOGIN_ONLY    → {reward}
    //   REWARD_ONLY   → {choice, hit}
    //   PRAY_ONLY     → {lastTs, prayDaily, prayMax, prayMaxIndex, prayCount, insureCount, extraCount, praying, prayArray}
    // 奖励配置在 data/excel/activity_table.json：
    //   activity.<类型>.<actId>.checkInList[i].itemList   （签到 / 视频签到）
    //   activity.LOGIN_ONLY.<actId>.itemList              （登录送礼）
    //   dynActs.<actId>.rewardPoolDataDict.<poolId>.charIdList （自选池）

    private static volatile JSONObject ACTIVITY_TABLE = null;
    private static volatile long ACTIVITY_TABLE_TS = -1;

    public static synchronized JSONObject activityTable() {
        long ts = ArknightsApplication.lastReloadTs;
        if (ACTIVITY_TABLE == null || ACTIVITY_TABLE_TS != ts) {
            JSONObject t = com.hypergryph.arknights.core.file.IOTools.ReadJsonFile(
                    System.getProperty("user.dir") + "/data/excel/activity_table.json");
            ACTIVITY_TABLE = t == null ? new JSONObject(true) : t;
            ACTIVITY_TABLE_TS = ts;
            LOGGER.info("[activity] 载入 activity_table.json（{} 个 basicInfo）",
                    ACTIVITY_TABLE.getJSONObject("basicInfo") == null ? 0
                            : ACTIVITY_TABLE.getJSONObject("basicInfo").size());
        }
        return ACTIVITY_TABLE;
    }

    /** user.activity.<type>（没有就建） */
    private static JSONObject actGroup(JSONObject user, String type) {
        JSONObject activity = user.getJSONObject("activity");
        if (activity == null) {
            activity = new JSONObject(true);
            user.put("activity", activity);
        }
        JSONObject group = activity.getJSONObject(type);
        if (group == null) {
            group = new JSONObject(true);
            activity.put(type, group);
        }
        return group;
    }

    /** user.activity.<type>.<actId>（没有就建） */
    private static JSONObject actState(JSONObject user, String type, String actId) {
        JSONObject group = actGroup(user, type);
        JSONObject st = group.getJSONObject(actId);
        if (st == null) {
            st = new JSONObject(true);
            group.put(actId, st);
        }
        return st;
    }

    /** 活动配置：activity.<type>.<actId> */
    private static JSONObject actConfig(String type, String actId) {
        JSONObject act = activityTable().getJSONObject("activity");
        JSONObject byType = act == null ? null : act.getJSONObject(type);
        return byType == null ? null : byType.getJSONObject(actId);
    }

    /** dynActs.<actId> */
    private static JSONObject dynAct(String actId) {
        JSONObject dyn = activityTable().getJSONObject("dynActs");
        return dyn == null ? null : dyn.getJSONObject(actId);
    }

    private static int itemTypeOf(String type) {
        return ArknightsApplication.checkCheckInItemType(type);
    }

    /** 关键节点快照（用于前后 diff，官服只回变化的节点） */
    private static JSONObject snapshot(JSONObject userData) {
        JSONObject snap = new JSONObject(true);
        for (String node : new String[]{"status", "consumable", "inventory"}) {
            JSONObject v = userData.getJSONObject(node);
            snap.put(node, v == null ? new JSONObject(true) : JSONObject.parseObject(v.toJSONString()));
        }
        JSONObject building = userData.getJSONObject("building");
        JSONObject bChars = building == null ? null : building.getJSONObject("chars");
        snap.put("buildingChars", bChars == null ? new JSONObject(true) : JSONObject.parseObject(bChars.toJSONString()));
        JSONObject troop = userData.getJSONObject("troop");
        JSONObject tChars = troop == null ? null : troop.getJSONObject("chars");
        snap.put("troopChars", tChars == null ? new JSONObject(true) : JSONObject.parseObject(tChars.toJSONString()));
        JSONObject dexNav = userData.getJSONObject("dexNav");
        JSONObject dChars = dexNav == null ? null : dexNav.getJSONObject("character");
        snap.put("dexChars", dChars == null ? new JSONObject(true) : JSONObject.parseObject(dChars.toJSONString()));
        return snap;
    }

    /** status 变化（只回变化的键） */
    private static JSONObject statusDelta(JSONObject before, JSONObject userData) {
        JSONObject out = new JSONObject(true);
        if (before == null || userData.getJSONObject("status") == null) return out;
        JSONObject after = userData.getJSONObject("status");
        for (String k : after.keySet()) {
            Object a = after.get(k);
            Object b = before.get(k);
            if (a instanceof Number && b instanceof Number) {
                if (((Number) a).longValue() != ((Number) b).longValue()) out.put(k, a);
            } else if (a != null && !a.equals(b)) {
                out.put(k, a);
            }
        }
        return out;
    }

    /** 某节点里新增/变化的对象（consumable / building.chars / troop.chars / dexNav.character） */
    private static JSONObject objectDelta(JSONObject before, JSONObject after) {
        JSONObject out = new JSONObject(true);
        if (after == null) return out;
        for (String k : after.keySet()) {
            Object a = after.get(k);
            Object b = before == null ? null : before.get(k);
            if (b == null || !String.valueOf(a).equals(String.valueOf(b))) out.put(k, a);
        }
        return out;
    }

    /** 数字表增量（inventory） */
    private static JSONObject numberDelta(JSONObject before, JSONObject after) {
        JSONObject out = new JSONObject(true);
        if (after == null) return out;
        for (String k : after.keySet()) {
            Object a = after.get(k);
            Object b = before == null ? null : before.get(k);
            if (a instanceof Number && b instanceof Number) {
                if (((Number) a).longValue() != ((Number) b).longValue()) out.put(k, a);
            } else if (b == null) {
                out.put(k, a);
            }
        }
        return out;
    }

    /** 未知 itemType 的兜底：塞进 consumable[itemId][instId] */
    private static void addConsumable(JSONObject userData, String itemId, int count) {
        JSONObject consumable = userData.getJSONObject("consumable");
        if (consumable == null) {
            consumable = new JSONObject(true);
            userData.put("consumable", consumable);
        }
        JSONObject stack = consumable.getJSONObject(itemId);
        if (stack == null) {
            stack = new JSONObject(true);
            consumable.put(itemId, stack);
        }
        String target = null;
        for (String k : stack.keySet()) {
            target = k;
            if (stack.getJSONObject(k).getIntValue("count") > 0) break;
        }
        if (target == null) {
            int max = 0;
            for (String itemKey : consumable.keySet()) {
                for (String k : consumable.getJSONObject(itemKey).keySet()) {
                    try { max = Math.max(max, Integer.parseInt(k)); } catch (Exception ignored) { }
                }
            }
            target = String.valueOf(max + 1);
            JSONObject slot = new JSONObject(true);
            slot.put("count", 0);
            slot.put("ts", -1);
            stack.put(target, slot);
        }
        JSONObject slot = stack.getJSONObject(target);
        slot.put("count", slot.getIntValue("count") + count);
    }

    /** 发一批奖励（itemList: [{id,count,type}]），返回发出条目 */
    private static JSONArray grantList(JSONObject userData, JSONArray itemList, long uid) {
        JSONArray out = new JSONArray();
        if (itemList == null) return out;
        for (int i = 0; i < itemList.size(); i++) {
            JSONObject one = itemList.getJSONObject(i);
            if (one == null) continue;
            String type = one.getString("type");
            if (itemTypeOf(type) > 0) {
                com.hypergryph.arknights.core.work.processArray.handleUserDataChange(one, userData, uid, i, out);
                JSONObject echo = new JSONObject(true);
                echo.put("type", type);
                echo.put("id", one.getString("id"));
                echo.put("count", one.getIntValue("count"));
                if (out.isEmpty() || out.getJSONObject(out.size() - 1).getJSONObject("charGet") == null) {
                    out.add(echo);
                }
            } else {
                addConsumable(userData, one.getString("id"), one.getIntValue("count"));
                JSONObject got = new JSONObject(true);
                got.put("type", type);
                got.put("id", one.getString("id"));
                got.put("count", one.getIntValue("count"));
                out.add(got);
            }
        }
        return out;
    }

    /** 用快照前后对比生成 modified */
    private static JSONObject buildDelta(JSONObject userData, JSONObject snap, JSONObject activityPatch) {
        JSONObject modified = new JSONObject(true);
        if (activityPatch != null && !activityPatch.isEmpty()) {
            modified.put("activity", activityPatch);
        }
        JSONObject status = statusDelta(snap.getJSONObject("status"), userData);
        if (!status.isEmpty()) modified.put("status", status);

        JSONObject inv = numberDelta(snap.getJSONObject("inventory"), userData.getJSONObject("inventory"));
        if (!inv.isEmpty()) modified.put("inventory", inv);

        JSONObject cons = objectDelta(snap.getJSONObject("consumable"), userData.getJSONObject("consumable"));
        if (!cons.isEmpty()) modified.put("consumable", cons);

        JSONObject building = userData.getJSONObject("building");
        JSONObject bChars = objectDelta(snap.getJSONObject("buildingChars"),
                building == null ? null : building.getJSONObject("chars"));
        if (!bChars.isEmpty()) {
            JSONObject b = new JSONObject(true);
            b.put("chars", bChars);
            modified.put("building", b);
        }

        JSONObject troop = userData.getJSONObject("troop");
        JSONObject tChars = objectDelta(snap.getJSONObject("troopChars"),
                troop == null ? null : troop.getJSONObject("chars"));
        if (!tChars.isEmpty()) {
            JSONObject t = new JSONObject(true);
            t.put("chars", tChars);
            if (troop.getIntValue("curCharInstId") > 0) t.put("curCharInstId", troop.getIntValue("curCharInstId"));
            modified.put("troop", t);
        }

        JSONObject dexNav = userData.getJSONObject("dexNav");
        JSONObject dChars = objectDelta(snap.getJSONObject("dexChars"),
                dexNav == null ? null : dexNav.getJSONObject("character"));
        if (!dChars.isEmpty()) {
            JSONObject d = new JSONObject(true);
            d.put("character", dChars);
            modified.put("dexNav", d);
        }

        // 干员数量勋章（官服每次发干员都回这 9 个的进度）
        if (!tChars.isEmpty()) {
            int charCount = troop.getJSONObject("chars").size();
            int[] thresholds = {60, 90, 130, 160, 200, 250, 300, 350, 400};
            JSONObject medalsNode = userData.getJSONObject("medal");
            if (medalsNode == null) {
                medalsNode = new JSONObject(true);
                userData.put("medal", medalsNode);
            }
            JSONObject medals = medalsNode.getJSONObject("medals");
            if (medals == null) {
                medals = new JSONObject(true);
                medalsNode.put("medals", medals);
            }
            JSONObject medalsPatch = new JSONObject(true);
            for (int i = 0; i < thresholds.length; i++) {
                String medalId = String.format("medal_player_charnum_%02d", i + 2);
                JSONArray pair = new JSONArray();
                pair.add(charCount);
                pair.add(thresholds[i]);
                JSONArray val = new JSONArray();
                val.add(pair);
                JSONObject entry = new JSONObject(true);
                entry.put("id", medalId);
                entry.put("val", val);
                entry.put("fts", -1);
                entry.put("rts", -1);
                medalsPatch.put(medalId, entry);
                JSONObject stored = medals.getJSONObject(medalId);
                if (stored == null) {
                    medals.put(medalId, JSONObject.parseObject(entry.toJSONString()));
                } else {
                    stored.put("val", val);
                }
            }
            JSONObject medalMod = new JSONObject(true);
            medalMod.put("medals", medalsPatch);
            modified.put("medal", medalMod);
        }
        return modified;
    }

    private static JSONObject wrapDelta(JSONObject modified) {
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        return playerDataDelta;
    }

    /** 签到（CHECKIN_ONLY）：按 activity_table 的 checkInList[index].itemList 发奖，写 history[index]=1 */
    @PostMapping(value = {"/getActivityCheckInReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getActivityCheckInReward(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        int index = jsonBody == null ? 0 : jsonBody.getIntValue("index");
        LOGGER.info("[/" + clientIp + "] /activity/getActivityCheckInReward activityId=" + activityId + " index=" + index);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());

        JSONObject cfg = actConfig("CHECKIN_ONLY", activityId);
        JSONObject slot = cfg == null || cfg.getJSONObject("checkInList") == null ? null
                : cfg.getJSONObject("checkInList").getJSONObject(String.valueOf(index));
        if (slot == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "没有这个签到奖励: " + activityId + "[" + index + "]");
            return bad;
        }
        JSONObject st = actState(userData, "CHECKIN_ONLY", activityId);
        JSONArray hist = st.getJSONArray("history");
        if (hist == null) {
            hist = new JSONArray();
            st.put("history", hist);
        }
        while (hist.size() <= index) {
            hist.add(1);   // 新增格默认「未签到」
        }
        // 官服语义：0 = 已签到（已领），1 = 未签到（可领）
        if (hist.getIntValue(index) == 0) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该签到奖励已领取");
            return bad;
        }
        JSONObject snap = snapshot(userData);
        JSONArray got = grantList(userData, slot.getJSONArray("itemList"), account.getUid());
        hist.set(index, 0);   // 0 = 已签到
        st.put("lastTs", ArknightsApplication.getTimestamp());

        userDao.setUserData(account.getUid(), userData);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        patchGroup.put(activityId, st);
        patch.put("CHECKIN_ONLY", patchGroup);
        JSONObject result = new JSONObject(true);
        result.put("items", got);
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snap, patch)));
        return result;
    }

    /** 视频签到（CHECKIN_VIDEO）：结构同签到 */
    @PostMapping(value = {"/getActivityCheckInVideoReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getActivityCheckInVideoReward(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        int index = jsonBody == null ? 0 : jsonBody.getIntValue("index");
        LOGGER.info("[/" + clientIp + "] /activity/getActivityCheckInVideoReward activityId=" + activityId + " index=" + index);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());

        JSONObject cfg = actConfig("CHECKIN_VIDEO", activityId);
        JSONObject slot = cfg == null || cfg.getJSONObject("checkInList") == null ? null
                : cfg.getJSONObject("checkInList").getJSONObject(String.valueOf(index));
        if (slot == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "没有这个签到奖励: " + activityId + "[" + index + "]");
            return bad;
        }
        JSONObject st = actState(userData, "CHECKIN_VIDEO", activityId);
        JSONArray hist = st.getJSONArray("history");
        if (hist == null) {
            hist = new JSONArray();
            st.put("history", hist);
        }
        while (hist.size() <= index) {
            hist.add(1);   // 新增格默认「未签到」
        }
        // 官服语义：0 = 已签到（已领），1 = 未签到（可领）
        if (hist.getIntValue(index) == 0) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该签到奖励已领取");
            return bad;
        }
        JSONObject snap = snapshot(userData);
        JSONArray got = grantList(userData, slot.getJSONArray("itemList"), account.getUid());
        hist.set(index, 0);   // 0 = 已签到
        st.put("lastTs", ArknightsApplication.getTimestamp());

        userDao.setUserData(account.getUid(), userData);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        patchGroup.put(activityId, st);
        patch.put("CHECKIN_VIDEO", patchGroup);
        JSONObject result = new JSONObject(true);
        result.put("items", got);
        result.put("index", index);
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snap, patch)));
        return result;
    }

    /** 登录送礼（LOGIN_ONLY）：activity.LOGIN_ONLY.<actId>.itemList，领过后 reward 置 0 且记 lastTs 防重领 */
    @PostMapping(value = {"/loginOnly/getReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject loginOnlyGetReward(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        LOGGER.info("[/" + clientIp + "] /activity/loginOnly/getReward activityId=" + activityId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());

        JSONObject cfg = actConfig("LOGIN_ONLY", activityId);
        JSONArray itemList = cfg == null ? null : cfg.getJSONArray("itemList");
        if (itemList == null || itemList.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该活动没有配置奖励: " + activityId);
            return bad;
        }
        JSONObject st = actState(userData, "LOGIN_ONLY", activityId);
        if (st.getLongValue("lastTs") > 0) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该奖励已领取");
            return bad;
        }
        JSONObject snap = snapshot(userData);
        JSONArray got = grantList(userData, itemList, account.getUid());
        st.put("reward", 0);
        st.put("lastTs", ArknightsApplication.getTimestamp());

        userDao.setUserData(account.getUid(), userData);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        JSONObject patchState = new JSONObject(true);
        patchState.put("reward", st.getIntValue("reward"));
        patchGroup.put(activityId, patchState);
        patch.put("LOGIN_ONLY", patchGroup);
        JSONObject result = new JSONObject(true);
        result.put("reward", got);
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snap, patch)));
        return result;
    }

    /** 自选池设置：只存 choice */
    @PostMapping(value = {"/actRewardOnly/setChoice"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject actRewardOnlySetChoice(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        String poolId = jsonBody == null ? null : jsonBody.getString("poolId");
        LOGGER.info("[/" + clientIp + "] /activity/actRewardOnly/setChoice activityId=" + activityId + " poolId=" + poolId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());
        JSONObject st = actState(userData, "REWARD_ONLY", activityId);
        st.put("choice", poolId);
        userDao.setUserData(account.getUid(), userData);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        JSONObject patchState = new JSONObject(true);
        patchState.put("choice", poolId);
        patchGroup.put(activityId, patchState);
        patch.put("REWARD_ONLY", patchGroup);
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snapshot(userData), patch)));
        return result;
    }

    /** 自选池开奖：从 dynActs.<actId>.rewardPoolDataDict.<poolId>.charIdList 随机给一名干员 */
    @PostMapping(value = {"/actRewardOnly/getReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject actRewardOnlyGetReward(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        String poolId = jsonBody == null ? null : jsonBody.getString("poolId");
        LOGGER.info("[/" + clientIp + "] /activity/actRewardOnly/getReward activityId=" + activityId + " poolId=" + poolId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());

        JSONObject st = actState(userData, "REWARD_ONLY", activityId);
        if (st.getString("hit") != null && !st.getString("hit").isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "已经开过奖了: " + st.getString("hit"));
            return bad;
        }
        if (poolId == null) {
            poolId = st.getString("choice");
        }
        JSONObject dyn = dynAct(activityId);
        JSONObject pool = dyn == null || dyn.getJSONObject("rewardPoolDataDict") == null ? null
                : dyn.getJSONObject("rewardPoolDataDict").getJSONObject(String.valueOf(poolId));
        JSONArray charIds = pool == null ? null : pool.getJSONArray("charIdList");
        if (charIds == null || charIds.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "池子没有配置: " + activityId + "/" + poolId);
            return bad;
        }
        String charId = charIds.getString(new java.util.Random().nextInt(charIds.size()));
        JSONObject snap = snapshot(userData);
        JSONArray got = new JSONArray();
        JSONObject one = new JSONObject(true);
        one.put("id", charId);
        one.put("count", 1);
        one.put("type", "CHAR");
        com.hypergryph.arknights.core.work.processArray.handleUserDataChange(one, userData, account.getUid(), 0, got);

        st.put("choice", poolId);
        st.put("hit", charId);
        userDao.setUserData(account.getUid(), userData);

        JSONObject charGet = got.isEmpty() ? null : got.getJSONObject(0);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        patchGroup.put(activityId, st);
        patch.put("REWARD_ONLY", patchGroup);
        JSONObject result = new JSONObject(true);
        if (charGet != null) {
            result.put("charGet", charGet.getJSONObject("charGet") == null ? charGet : charGet.getJSONObject("charGet"));
        }
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snap, patch)));
        return result;
    }

    /** 祈愿墙（PRAY_ONLY）：12 个槽，选 2 张（前一天 <400 则 3 张），取最大值为奖励 */
    @PostMapping(value = {"/prayOnly/getReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject prayOnlyGetReward(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject jsonBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String activityId = jsonBody == null ? null : jsonBody.getString("activityId");
        JSONArray picked = jsonBody == null ? null : jsonBody.getJSONArray("prayArray");
        LOGGER.info("[/" + clientIp + "] /activity/prayOnly/getReward activityId=" + activityId + " prayArray=" + picked);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        Account account = accounts.get(0);
        JSONObject userData = JSON.parseObject(account.getUser());

        int slotCount = 12;
        JSONObject dyn = dynAct(activityId);
        if (dyn != null && dyn.getIntValue("slotCount") > 0) {
            slotCount = dyn.getIntValue("slotCount");
        }
        if (picked == null || picked.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "prayArray 为空");
            return bad;
        }
        JSONObject st = actState(userData, "PRAY_ONLY", activityId);
        long todayStart = todayStartTs();
        st.putIfAbsent("prayDaily", 2);
        st.putIfAbsent("prayMax", 0);
        st.putIfAbsent("prayMaxIndex", 0);
        st.putIfAbsent("prayCount", 0);
        st.putIfAbsent("insureCount", 0);
        st.putIfAbsent("extraCount", 0);
        st.putIfAbsent("praying", 0);
        st.putIfAbsent("prayArray", new JSONArray());
        if (st.getLongValue("lastTs") >= todayStart) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "今日已经许愿过了");
            return bad;
        }
        int allowed = st.getIntValue("prayDaily") + st.getIntValue("extraCount");
        if (picked.size() > allowed) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "今天只能选 " + allowed + " 张");
            return bad;
        }

        // 官服每次揭示的数值范围（实测出现 200/300/400/500/600/800）
        int[] poolCounts = {200, 300, 300, 400, 400, 500, 500, 600, 600, 800};
        java.util.Random rnd = new java.util.Random();
        JSONArray revealed = new JSONArray();
        int max = 0;
        int maxIndex = 0;
        for (int i = 0; i < picked.size(); i++) {
            int idx = picked.getIntValue(i);
            if (idx < 0 || idx >= slotCount) {
                idx = Math.abs(idx) % slotCount;
            }
            int count = poolCounts[rnd.nextInt(poolCounts.length)];
            JSONObject one = new JSONObject(true);
            one.put("index", idx);
            one.put("count", count);
            revealed.add(one);
            if (count > max) {
                max = count;
                maxIndex = idx;
            }
        }

        st.put("lastTs", ArknightsApplication.getTimestamp());
        st.put("praying", 1);
        st.put("prayCount", st.getIntValue("prayCount") + picked.size());
        st.put("prayMax", max);
        st.put("prayMaxIndex", maxIndex);
        st.put("prayArray", revealed);
        // rule2：当日奖励 < 400 → 次日多给一张（extraCount=1）
        if (max < 400) {
            st.put("insureCount", st.getIntValue("insureCount") + 1);
            st.put("extraCount", 1);
        } else {
            st.put("insureCount", 0);
            st.put("extraCount", 0);
        }

        JSONObject snap = snapshot(userData);
        JSONArray got = new JSONArray();
        JSONObject rewardItem = new JSONObject(true);
        rewardItem.put("type", "DIAMOND_SHD");
        rewardItem.put("id", "4003");
        rewardItem.put("count", max);
        com.hypergryph.arknights.core.work.processArray.handleUserDataChange(rewardItem, userData, account.getUid(), 0, got);

        userDao.setUserData(account.getUid(), userData);
        JSONObject patch = new JSONObject(true);
        JSONObject patchGroup = new JSONObject(true);
        patchGroup.put(activityId, st);
        patch.put("PRAY_ONLY", patchGroup);
        JSONObject result = new JSONObject(true);
        JSONArray rewards = new JSONArray();
        JSONObject r = new JSONObject(true);
        r.put("type", "DIAMOND_SHD");
        r.put("id", "4003");
        r.put("count", max);
        rewards.add(r);
        result.put("rewards", rewards);
        result.put("playerDataDelta", wrapDelta(buildDelta(userData, snap, patch)));
        return result;
    }

    private static long todayStartTs() {
        return todayStartTs(System.currentTimeMillis() / 1000L);
    }

    /** 指定时间戳所在的「官服日」起点（Asia/Shanghai 04:00 刷新） */
    private static long todayStartTs(long nowTs) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        c.set(java.util.Calendar.HOUR_OF_DAY, 4); // 官服每日 4 点刷新
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long ts = c.getTimeInMillis() / 1000L;
        if (nowTs < ts) {
            ts -= 86400L;
        }
        return ts;
    }

    /**
     * syncData（canCheck 时）调用：活动签到的 history 里没有 1（未签到）就补一个 1。
     * 官服语义：0 = 已签到，1 = 未签到。只做这一层判断。
     */
    public static void refreshCheckInActivities(JSONObject user) {
        if (user == null) {
            return;
        }
        JSONObject table = activityTable();
        JSONObject actCfg = table.getJSONObject("activity");
        if (actCfg == null) {
            return;
        }
        JSONObject activity = user.getJSONObject("activity");
        if (activity == null) {
            activity = new JSONObject(true);
            user.put("activity", activity);
        }
        for (String type : new String[]{"CHECKIN_ONLY", "CHECKIN_VIDEO"}) {
            JSONObject byType = actCfg.getJSONObject(type);
            if (byType == null) {
                continue;
            }
            JSONObject group = activity.getJSONObject(type);
            if (group == null) {
                group = new JSONObject(true);
                activity.put(type, group);
            }
            for (String actId : byType.keySet()) {
                JSONObject cfg = byType.getJSONObject(actId);
                JSONObject list = cfg == null ? null : cfg.getJSONObject("checkInList");
                if (list == null || list.isEmpty()) {
                    continue;
                }
                JSONObject st = group.getJSONObject(actId);
                if (st == null) {
                    st = new JSONObject(true);
                    group.put(actId, st);
                }
                JSONArray hist = st.getJSONArray("history");
                if (hist == null) {
                    hist = new JSONArray();
                    st.put("history", hist);
                }
                boolean hasOne = false;
                for (int i = 0; i < hist.size(); i++) {
                    Integer v = hist.getInteger(i);
                    if (v != null && v == 1) {
                        hasOne = true;
                        break;
                    }
                }
                if (!hasOne) {
                    hist.add(1);
                }
                if (!st.containsKey("lastTs")) {
                    st.put("lastTs", 0L);
                }
            }
        }
    }
}
