package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.work.processArray;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/campaignV2"})
public class campaignV2 {

    // 每场剿灭给多少合成玉进度（旧常量，保留给日志/兼容）
    private static final int FEE_PER_BATTLE = 60;
    // 官服结算档位：歼灭数门槛
    private static final int[] KILL_TIERS = {0, 100, 200, 250, 300, 325, 350, 375, 400};
    // 对应理智返还
    private static final int[] AP_RETURN_BY_TIER = {20, 14, 9, 6, 4, 3, 2, 1, 0};
    // 对应合成玉（游戏内显示为「约」，这里取整档值）
    private static final int[] SHARD_BY_TIER = {0, 60, 110, 145, 170, 185, 205, 230, 260};
    // uid → 正在打的关卡（battleFinish 只有 data，靠它找 stageId）
    private static final Map<Long, String> LAST_BATTLE = new ConcurrentHashMap<>();
    private static volatile JSONObject CAMPAIGN_TABLE = null;


    private static JSONObject emptyDelta() {
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        return delta;
    }

    private static JSONObject stateOf(JSONObject userData) {
        return userData.getJSONObject("campaignsV2");
    }

    private static JSONObject instanceOf(JSONObject userData, String stageId) {
        JSONObject camp = stateOf(userData);
        if (camp == null) {
            camp = new JSONObject(true);
            userData.put("campaignsV2", camp);
        }
        JSONObject inst = camp.getJSONObject("instances");
        if (inst == null) {
            inst = new JSONObject(true);
            camp.put("instances", inst);
        }
        JSONObject one = inst.getJSONObject(stageId);
        if (one == null) {
            one = new JSONObject(true);
            one.put("maxKills", 0);
            JSONArray rs = new JSONArray();
            for (int i = 0; i < 8; i++) {
                rs.add(0);
            }
            one.put("rewardStatus", rs);
            inst.put(stageId, one);
        }
        return one;
    }

    private static boolean stageUnlocked(JSONObject userData, String stageId) {
        JSONObject dungeon = userData.getJSONObject("dungeon");
        JSONObject stages = dungeon == null ? null : dungeon.getJSONObject("stages");
        return stages != null && stages.containsKey(stageId);
    }

    private static int apCostOf(String stageId) {
        try {
            JSONObject st = ArknightsApplication.stageTable.getJSONObject(stageId);
            if (st != null && st.getIntValue("apCost") > 0) {
                return st.getIntValue("apCost");
            }
        } catch (Exception ignored) {
        }
        return 20;
    }

    private static JSONArray grantItems(JSONArray rewards, JSONObject userData, JSONObject modified) {
        JSONObject statusBefore = userData.getJSONObject("status") == null ? new JSONObject(true)
                : com.alibaba.fastjson.JSON.parseObject(userData.getJSONObject("status").toJSONString());
        JSONObject invBefore = userData.getJSONObject("inventory") == null ? new JSONObject(true)
                : com.alibaba.fastjson.JSON.parseObject(userData.getJSONObject("inventory").toJSONString());
        JSONArray granted = new JSONArray();
        for (int i = 0; i < rewards.size(); i++) {
            JSONObject it = rewards.getJSONObject(i);
            JSONObject grant = new JSONObject(true);
            grant.put("id", it.getString("id"));
            grant.put("type", it.getString("type"));
            grant.put("count", it.getIntValue("count"));
            processArray.handleUserDataChange(grant, userData, 0L, -1, null);
            granted.add(grant);
        }
        JSONObject statusNow = userData.getJSONObject("status");
        if (statusNow != null && !statusNow.toJSONString().equals(statusBefore.toJSONString())) {
            modified.put("status", statusNow);
        }
        JSONObject invNow = userData.getJSONObject("inventory");
        if (invNow != null && !invNow.toJSONString().equals(invBefore.toJSONString())) {
            modified.put("inventory", invNow);
        }
        return granted;
    }

    private static JSONObject decryptBattleData(String hex, String status) {
        try {
            String LOG_TOKEN_KEY = "pM6Umv*^hVQuB6t&";
            String md5 = org.springframework.util.DigestUtils
                    .md5DigestAsHex((LOG_TOKEN_KEY + status).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] key = hexToBytes(md5);
            byte[] iv = hexToBytes(hex.substring(hex.length() - 32));
            byte[] body = hexToBytes(hex.substring(0, hex.length() - 32));
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(iv));
            byte[] dec = cipher.doFinal(body);
            int last = -1;
            for (int i = dec.length - 1; i >= 0; i--) {
                if (dec[i] == '}') {
                    last = i;
                    break;
                }
            }
            if (last < 0) {
                return null;
            }
            return JSONObject.parseObject(new String(dec, 0, last + 1, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[campaignV2] 解密对战数据失败: {}", e.toString());
            return null;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static JSONObject settle(JSONObject userData, String stageId, int kills, boolean sweep) {
        JSONObject camp = stateOf(userData);
        if (camp == null) {
            camp = new JSONObject(true);
            userData.put("campaignsV2", camp);
        }
        int before = camp.getIntValue("campaignCurrentFee");
        int total = camp.getIntValue("campaignTotalFee");
        if (total <= 0) {
            total = 1200;
            camp.put("campaignTotalFee", total);
        }
        JSONObject inst = instanceOf(userData, stageId);
        if (kills > inst.getIntValue("maxKills")) {
            inst.put("maxKills", kills);
        }

        //   歼灭数    +0  +100  +200  +250  +300  +325  +350  +375  +400
        //   理智返还  +20  +14   +9    +6    +4    +3    +2    +1    +0
        //   合成玉    +0  +60  +110  +145  +170  +185  +205  +230  +260
        int tier = 0;
        for (int i = 0; i < KILL_TIERS.length; i++) {
            if (kills >= KILL_TIERS[i]) {
                tier = i;
            }
        }
        int apReturn = AP_RETURN_BY_TIER[tier];
        int shardCount = Math.min(SHARD_BY_TIER[tier], Math.max(0, total - before));
        int after = before + shardCount;
        camp.put("campaignCurrentFee", after);

        JSONObject modified = new JSONObject(true);
        JSONArray rewards = new JSONArray();
        JSONObject gold = new JSONObject(true);
        gold.put("id", "4001");
        gold.put("type", "GOLD");
        gold.put("count", 60);
        rewards.add(gold);
        JSONObject exp = new JSONObject(true);
        exp.put("id", "2001");
        exp.put("type", "CARD_EXP");
        exp.put("count", 5);
        rewards.add(exp);
        grantItems(rewards, userData, modified);

        JSONArray diamond = new JSONArray();
        if (shardCount > 0) {
            JSONObject shard = new JSONObject(true);
            shard.put("id", "4003");
            shard.put("type", "DIAMOND_SHD");
            shard.put("count", shardCount);
            diamond.add(shard);
            grantItems(diamond, userData, modified);
        }
        // 理智返还
        if (apReturn > 0) {
            JSONObject statusNode = userData.getJSONObject("status");
            int maxAp = statusNode.getIntValue("maxAp");
            int curAp = statusNode.getIntValue("ap");
            int newAp = maxAp > 0 ? Math.min(maxAp, curAp + apReturn) : curAp + apReturn;
            statusNode.put("ap", newAp);
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("ap", newAp);
            statusDelta.put("lastApAddTime", statusNode.getLongValue("lastApAddTime"));
            modified.put("status", statusDelta);
        }

        modified.put("campaignsV2", camp);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("apFailReturn", apReturn);
        result.put("rewards", rewards);
        result.put("unusualRewards", new JSONArray());
        result.put("additionalRewards", new JSONArray());
        result.put("diamondMaterialRewards", diamond);
        result.put("furnitureRewards", new JSONArray());
        result.put("currentFeeBefore", before);
        result.put("currentFeeAfter", after);
        result.put("unlockStages", new JSONArray());
        result.put("playerDataDelta", delta);
        ArknightsApplication.LOGGER.info("[campaignV2] {} 结算 {} kills={} 档位={} 理智返还={} 合成玉 {}->{}",
                sweep ? "扫荡" : "作战", stageId, kills, tier, apReturn, before, after);
        return result;
    }

    @PostMapping(value = {"/battleStart"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleStart(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts == null || accounts.isEmpty()) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        String stageId = body == null ? null : body.getString("stageId");
        ArknightsApplication.LOGGER.info("[campaignV2] battleStart uid={} stage={}", account.getUid(), stageId);
        if (stageId == null || !stageUnlocked(userData, stageId)) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        int cost = apCostOf(stageId);
        JSONObject status = userData.getJSONObject("status");
        int ap = status.getIntValue("ap");
        int newAp = Math.max(0, ap - cost);
        status.put("ap", newAp);

        JSONObject stages = userData.getJSONObject("dungeon").getJSONObject("stages");
        JSONObject entry = stages.getJSONObject(stageId);
        if (entry != null) {
            entry.put("startTimes", entry.getIntValue("startTimes") + 1);
        }
        LAST_BATTLE.put(account.getUid(), stageId);
        userDao.setUserData(account.getUid(), userData);

        JSONObject statusDelta = new JSONObject(true);
        statusDelta.put("ap", newAp);
        statusDelta.put("lastApAddTime", status.getLongValue("lastApAddTime"));
        JSONObject modified = new JSONObject(true);
        modified.put("status", statusDelta);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("battleId", java.util.UUID.randomUUID().toString());
        result.put("playerDataDelta", delta);
        return result;
    }

    @PostMapping(value = {"/battleFinish"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleFinish(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts == null || accounts.isEmpty()) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        String stageId = LAST_BATTLE.remove(account.getUid());
        int kills = 0;
        int completeState = 0;
        String dataHex = body == null ? null : body.getString("data");
        if (dataHex != null && !dataHex.isEmpty()) {
            // 解开对战数据，拿真实 stageId / killCnt / completeState
            try {
                JSONObject bd = decryptBattleData(dataHex,
                        userData.getJSONObject("pushFlags").getString("status"));
                if (bd != null) {
                    if (bd.getString("stageId") != null) {
                        stageId = bd.getString("stageId");
                    }
                    kills = bd.getIntValue("killCnt");
                    completeState = bd.getIntValue("completeState");
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[campaignV2] battleFinish 解密失败: {}", e.toString());
            }
        }
        if (stageId == null && body != null) {
            stageId = body.getString("stageId");
        }
        ArknightsApplication.LOGGER.info("[campaignV2] battleFinish uid={} stage={} kills={} state={}", account.getUid(),
                stageId, kills, completeState);
        if (stageId == null || !stageUnlocked(userData, stageId)) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        JSONObject result = settle(userData, stageId, kills, false);
        userDao.setUserData(account.getUid(), userData);
        return result;
    }

    @PostMapping(value = {"/battleSweep"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleSweep(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts == null || accounts.isEmpty()) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        String stageId = body == null ? null : body.getString("stageId");
        String itemId = body == null ? null : body.getString("itemId");
        int instId = body == null ? -1 : body.getIntValue("instId");
        ArknightsApplication.LOGGER.info("[campaignV2] battleSweep uid={} stage={} item={} inst={}",
                account.getUid(), stageId, itemId, instId);
        if (stageId == null || !stageUnlocked(userData, stageId)) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        // 扣剿灭代理卡
        JSONObject consumable = userData.getJSONObject("consumable");
        JSONObject itemNode = null;
        if (consumable != null && itemId != null) {
            JSONObject byId = consumable.getJSONObject(itemId);
            if (byId != null) {
                itemNode = byId.getJSONObject(String.valueOf(instId));
                if (itemNode != null) {
                    int left = itemNode.getIntValue("count") - 1;
                    itemNode.put("count", Math.max(0, left));
                }
            }
        }
        JSONObject result = settle(userData, stageId, 0, true);
        if (itemNode != null) {
            JSONObject consDelta = new JSONObject(true);
            JSONObject one = new JSONObject(true);
            JSONObject slot = new JSONObject(true);
            slot.put("ts", itemNode.getLongValue("ts"));
            slot.put("count", itemNode.getIntValue("count"));
            one.put(String.valueOf(instId), slot);
            consDelta.put(itemId, one);
            ((JSONObject) result.getJSONObject("playerDataDelta").getJSONObject("modified"))
                    .put("consumable", consDelta);
        }
        userDao.setUserData(account.getUid(), userData);
        return result;
    }

    @PostMapping(value = {"/getBreakReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getBreakReward(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody(required = false) JSONObject body,
                                     HttpServletRequest request) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts == null || accounts.isEmpty()) {
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("items", new JSONArray());
            err.put("playerDataDelta", emptyDelta());
            return err;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        String stageId = body == null ? null : body.getString("stageId");
        JSONArray indexList = body == null ? null : body.getJSONArray("indexList");
        ArknightsApplication.LOGGER.info("[campaignV2] getBreakReward uid={} stage={} index={}", account.getUid(),
                stageId, indexList);
        JSONObject inst = stageId == null ? null : instanceOf(userData, stageId);
        JSONArray rewardStatus = inst == null ? null : inst.getJSONArray("rewardStatus");
        JSONObject stageCfg = stageId == null ? null
                : CAMPAIGN_TABLE.getJSONObject("campaigns") == null ? null
                : CAMPAIGN_TABLE.getJSONObject("campaigns").getJSONObject(stageId);
        JSONArray ladders = stageCfg == null ? null : stageCfg.getJSONArray("breakLadders");
        int maxKills = inst == null ? 0 : inst.getIntValue("maxKills");
        JSONObject modified = new JSONObject(true);
        JSONArray items = new JSONArray();
        if (indexList != null) {
            for (int i = 0; i < indexList.size(); i++) {
                int idx = indexList.getIntValue(i);
                if (rewardStatus == null || idx < 0 || idx >= rewardStatus.size()) {
                    continue;
                }
                if (rewardStatus.getIntValue(idx) == 1) {
                    continue;
                }
                if (ladders != null && idx < ladders.size()) {
                    int need = ladders.getJSONObject(idx).getIntValue("killCnt");
                    if (maxKills < need) {
                        ArknightsApplication.LOGGER.info("[campaignV2] getBreakReward 拒绝：{} 第 {} 档需要 {} 击杀，当前最高 {}",
                                stageId, idx, need, maxKills);
                        continue;
                    }
                }
                rewardStatus.set(idx, 1);
                JSONArray got = new JSONArray();
                if (ladders != null && idx < ladders.size()) {
                    JSONArray rs = ladders.getJSONObject(idx).getJSONArray("rewards");
                    if (rs != null) {
                        for (int k = 0; k < rs.size(); k++) {
                            JSONObject r = rs.getJSONObject(k);
                            JSONObject one = new JSONObject(true);
                            one.put("id", r.getString("id"));
                            one.put("type", r.getString("type"));
                            one.put("count", r.getIntValue("count"));
                            got.add(one);
                        }
                    }
                }
                grantItems(got, userData, modified);
                for (int k = 0; k < got.size(); k++) {
                    items.add(got.getJSONObject(k));
                }
            }
        }
        JSONObject camp = stateOf(userData);
        if (camp != null) {
            modified.put("campaignsV2", camp);
        }
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("items", items);
        result.put("playerDataDelta", delta);
        userDao.setUserData(account.getUid(), userData);
        return result;
    }

    @PostMapping(value = {"/getExMissionReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getExMissionReward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("[campaignV2] getExMissionReward body={}",
                body == null ? "{}" : body.toJSONString());
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("items", new JSONArray());
        result.put("playerDataDelta", emptyDelta());
        return result;
    }

    @PostMapping(value = {"/**"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject fallback(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject body,
                               HttpServletRequest request) {
        ArknightsApplication.LOGGER.warn("[campaignV2][未实现] {} body={}", request.getRequestURI(),
                body == null ? "{}" : body.toJSONString());
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("playerDataDelta", emptyDelta());
        return result;
    }
}
