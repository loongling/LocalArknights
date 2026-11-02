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

    // 每周合成玉上限的初始值（只用于补老存档缺失字段，正常读 user.campaignsV2.campaignTotalFee）
    private static final int DEFAULT_WEEKLY_CAP = 1200;
    // 每局奖励 / 分档（理智返还、合成玉）现在全部来自 data/battle/campaignRewards.json
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

    /** campaign_table.json 缓存（破关档位 breakLadders 用） */
    private static JSONObject campaignTable() {
        if (CAMPAIGN_TABLE != null) {
            return CAMPAIGN_TABLE;
        }
        synchronized (campaignV2.class) {
            if (CAMPAIGN_TABLE != null) {
                return CAMPAIGN_TABLE;
            }
            JSONObject t = new JSONObject(true);
            try {
                String path = System.getProperty("user.dir") + "/data/excel/campaign_table.json";
                t = JSONObject.parseObject(new String(
                        java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                        java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[campaignV2] 读取 campaign_table.json 失败: {}", e.toString());
            }
            CAMPAIGN_TABLE = t;
            return t;
        }
    }

    /** 每局剿灭奖励配置缓存（data/battle/campaignRewards.json） */
    private static volatile JSONObject REWARDS_CACHE = null;

    private static JSONObject rewardsTable() {
        if (REWARDS_CACHE != null) {
            return REWARDS_CACHE;
        }
        synchronized (campaignV2.class) {
            if (REWARDS_CACHE != null) {
                return REWARDS_CACHE;
            }
            JSONObject t = new JSONObject(true);
            try {
                String path = System.getProperty("user.dir") + "/data/battle/campaignRewards.json";
                JSONObject raw = JSONObject.parseObject(new String(
                        java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                        java.nio.charset.StandardCharsets.UTF_8));
                JSONObject ct = raw == null ? null : raw.getJSONObject("CampaignReward");
                if (ct != null) {
                    t = ct;
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[campaignV2] 读取 campaignRewards.json 失败: {}", e.toString());
            }
            REWARDS_CACHE = t;
            return t;
        }
    }

    /**
     * 取某关的奖励配置，优先级由低到高：
     *   default（每局固定奖励、合成玉道具）
     *   → rotateTemplate（只有 camp_r_* 轮换关才套用）
     *   → <stageId>（该关专属，永久开放的 camp_01/02/03 走这里）
     * 这样永久开放的关卡永远只吃自己那份标准，不会被轮换模板覆盖。
     */
    private static JSONObject rewardConfig(String stageId) {
        JSONObject t = rewardsTable();
        JSONObject merged = new JSONObject(true);
        JSONObject def = t.getJSONObject("default");
        if (def != null) {
            merged.putAll(def);
        }
        if (stageId != null && stageId.startsWith("camp_r_")) {
            JSONObject rotate = t.getJSONObject("rotateTemplate");
            if (rotate != null) {
                merged.putAll(rotate);
            }
        }
        if (stageId != null) {
            JSONObject own = t.getJSONObject(stageId);
            if (own != null) {
                merged.putAll(own);
            }
        }
        return merged;
    }

    public static boolean syncRotationAndWeekly(JSONObject userData) {
        boolean changed = false;
        JSONObject camp = stateOf(userData);
        if (camp == null) {
            camp = new JSONObject(true);
            userData.put("campaignsV2", camp);
            changed = true;
        }
        JSONObject open = camp.getJSONObject("open");
        if (open == null) {
            open = new JSONObject(true);
            camp.put("open", open);
            changed = true;
        }
        // ① 轮换配置
        JSONObject cfg = ArknightsApplication.serverConfig == null ? null
                : ArknightsApplication.serverConfig.getJSONObject("campaign");
        if (cfg != null) {
            for (String key : new String[] { "rotate", "rGroup", "tGroup", "tAllOpen" }) {
                String want = cfg.getString(key);
                if (want != null && !want.isEmpty() && !want.equals(open.getString(key))) {
                    ArknightsApplication.LOGGER.info("[campaignV2] open.{} 按配置改为 {}（原 {}）", key, want,
                            open.getString(key));
                    open.put(key, want);
                    changed = true;
                }
            }
            JSONArray perm = cfg.getJSONArray("permanent");
            if (perm != null) {
                JSONArray cur = open.getJSONArray("permanent");
                if (cur == null || !cur.toJSONString().equals(perm.toJSONString())) {
                    open.put("permanent", perm);
                    changed = true;
                }
            }
        }
        // ② 每周刷新（只清本周进度，不动上限）
        long now = System.currentTimeMillis() / 1000L;
        long week = 7L * 86400L;
        long last = camp.getLongValue("lastRefreshTs");
        if (last <= 0) {
            camp.put("lastRefreshTs", now);
            changed = true;
        } else if (now >= last + week) {
            long weeks = (now - last) / week;
            camp.put("campaignCurrentFee", 0);
            camp.put("lastRefreshTs", last + weeks * week);
            ArknightsApplication.LOGGER.info("[campaignV2] 剿灭周刷新：本周合成玉进度归零（跳过 {} 周），上限保持 {}",
                    weeks, camp.getIntValue("campaignTotalFee"));
            changed = true;
        }
        return changed;
    }

    private static JSONObject settle(JSONObject userData, String stageId, int kills, boolean sweep) {
        syncRotationAndWeekly(userData);
        JSONObject camp = stateOf(userData);
        if (camp == null) {
            camp = new JSONObject(true);
            userData.put("campaignsV2", camp);
        }
        int before = camp.getIntValue("campaignCurrentFee");
        JSONObject cfg = rewardConfig(stageId);
        // 周上限只存在存档里（campaignsV2.campaignTotalFee），
        // 只有老存档缺这个字段时才补一个初始值（官服初始 1200）。
        int total = camp.getIntValue("campaignTotalFee");
        if (total <= 0) {
            total = DEFAULT_WEEKLY_CAP;
            camp.put("campaignTotalFee", total);
        }
        JSONObject inst = instanceOf(userData, stageId);
        if (kills > inst.getIntValue("maxKills")) {
            inst.put("maxKills", kills);
        }

        // 每局奖励表来自 data/battle/campaignRewards.json：
        //   tiers             歼灭数档位门槛（全局）
        //   apReturnByCost    按关卡 apCost 查理智返还表（所有剿灭通用）
        //   <stageId>.shards  该关每档合成玉；没配就是 0（只退理智）
        JSONArray tierCfg = cfg.getJSONArray("tiers");
        if (tierCfg == null) {
            tierCfg = rewardsTable().getJSONArray("tiers");
        }
        int tier = 0;
        if (tierCfg != null) {
            for (int i = 0; i < tierCfg.size(); i++) {
                if (kills >= tierCfg.getIntValue(i)) {
                    tier = i;
                }
            }
        }
        int apReturn = 0;
        JSONObject apByCost = rewardsTable().getJSONObject("apReturnByCost");
        if (apByCost != null) {
            JSONArray apArr = apByCost.getJSONArray(String.valueOf(apCostOf(stageId)));
            if (apArr != null && tier < apArr.size()) {
                apReturn = apArr.getIntValue(tier);
            }
        }
        int tierShards = 0;
        JSONArray shardArr = cfg.getJSONArray("shards");
        if (shardArr != null && tier < shardArr.size()) {
            tierShards = shardArr.getIntValue(tier);
        }
        int shardCount = Math.min(tierShards, Math.max(0, total - before));
        int after = before + shardCount;
        camp.put("campaignCurrentFee", after);

        JSONObject modified = new JSONObject(true);
        JSONArray rewards = new JSONArray();
        JSONArray rewardCfg = cfg.getJSONArray("rewards");
        if (rewardCfg != null) {
            for (int i = 0; i < rewardCfg.size(); i++) {
                JSONObject r = rewardCfg.getJSONObject(i);
                JSONObject one = new JSONObject(true);
                one.put("id", r.getString("id"));
                one.put("type", r.getString("type"));
                one.put("count", r.getIntValue("count"));
                rewards.add(one);
            }
        }
        grantItems(rewards, userData, modified);

        JSONArray diamond = new JSONArray();
        if (shardCount > 0) {
            JSONObject shardCfg = cfg.getJSONObject("shardItem");
            JSONObject shard = new JSONObject(true);
            shard.put("id", shardCfg == null ? "4003" : shardCfg.getString("id"));
            shard.put("type", shardCfg == null ? "DIAMOND_SHD" : shardCfg.getString("type"));
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

        // 官服存档里剿灭关通关后是：{"completeTimes":0,"startTimes":0,"state":2,"hasBattleReplay":1}
        // （剿灭没有三星概念，state 固定记 2；completeTimes/startTimes 不动）
        JSONObject dungeon = userData.getJSONObject("dungeon");
        if (dungeon == null) {
            dungeon = new JSONObject(true);
            userData.put("dungeon", dungeon);
        }
        JSONObject dungeonStages = dungeon.getJSONObject("stages");
        if (dungeonStages == null) {
            dungeonStages = new JSONObject(true);
            dungeon.put("stages", dungeonStages);
        }
        JSONObject entry = dungeonStages.getJSONObject(stageId);
        if (entry == null) {
            entry = new JSONObject(true);
            entry.put("stageId", stageId);
            entry.put("completeTimes", 0);
            entry.put("startTimes", 0);
            entry.put("practiceTimes", 0);
            entry.put("state", 0);
            entry.put("hasBattleReplay", 0);
            dungeonStages.put(stageId, entry);
        }
        if (entry.getIntValue("state") < 2) {
            entry.put("state", 2);
        }
        entry.put("hasBattleReplay", 1);

        // 通关剿灭后按条件解锁其它剿灭关（camp_03 ← 通关 camp_02、camp_r_* ← 通关 main_02-08）
        JSONArray unlocked = new JSONArray();
        com.hypergryph.arknights.game.quest.unlockCampaignByConditions(userData, unlocked, null);
        com.hypergryph.arknights.game.quest.unlockCampaignStages(userData, unlocked, null);

        // 每日/周任务：剿灭也算「通关任意关卡」并计入击杀
        com.hypergryph.arknights.core.function.MissionHelper.addProgress(userData, "CompleteStageAnyType", 1);
        com.hypergryph.arknights.core.function.MissionHelper.addProgress(userData, "EnemyKillInAnyStage", kills);
        // 新服开启活动 V3：剿灭结算后重算见习任务（单次歼灭 100 / 全部进度奖励 都看剿灭数据）
        com.hypergryph.arknights.core.function.MissionHelper.refreshGuideV3(userData);
        if (!unlocked.isEmpty()) {
            JSONObject dungeonDelta = new JSONObject(true);
            dungeonDelta.put("campaigns", new JSONObject(true));
            JSONObject stagesDelta = new JSONObject(true);
            for (int i = 0; i < unlocked.size(); i++) {
                String id = unlocked.getString(i);
                stagesDelta.put(id, dungeonStages.getJSONObject(id));
            }
            dungeonDelta.put("stages", stagesDelta);
            modified.put("dungeon", dungeonDelta);
            ArknightsApplication.LOGGER.info("[campaignV2] 通关 {} 解锁 {} 张剿灭图: {}", stageId, unlocked.size(),
                    unlocked);
        }

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
        result.put("unlockStages", unlocked);
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
        JSONObject stageCfg = null;
        if (stageId != null) {
            JSONObject campaignsNode = campaignTable().getJSONObject("campaigns");
            if (campaignsNode != null) {
                stageCfg = campaignsNode.getJSONObject(stageId);
            }
        }
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
                    // breakFeeAdd：领这一档会给「每周合成玉获取上限」加多少
                    int feeAdd = ladders.getJSONObject(idx).getIntValue("breakFeeAdd");
                    if (feeAdd > 0) {
                        JSONObject campNode = stateOf(userData);
                        if (campNode != null) {
                            int oldTotal = campNode.getIntValue("campaignTotalFee");
                            campNode.put("campaignTotalFee", oldTotal + feeAdd);
                            ArknightsApplication.LOGGER.info("[campaignV2] {} 第 {} 档 breakFeeAdd={} 周上限 {} → {}",
                                    stageId, idx, feeAdd, oldTotal, oldTotal + feeAdd);
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
