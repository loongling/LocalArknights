package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.decrypt.Utils;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tower")
public class tower {

    private static final java.util.Random RANDOM = new java.util.Random();

    private static final String[] PROFESSIONS = {
            "PIONEER", "WARRIOR", "TANK", "SNIPER", "CASTER", "SUPPORT", "MEDIC", "SPECIAL"
    };

    private static JSONObject TOWER_TABLE;

    private static synchronized JSONObject table() {
        if (TOWER_TABLE == null) {
            String path = System.getProperty("user.dir") + "/data/excel/climb_tower_table.json";
            try {
                TOWER_TABLE = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[tower] 读取 climb_tower_table.json 失败", e);
                TOWER_TABLE = new JSONObject(true);
            }
        }
        return TOWER_TABLE;
    }

    private static JSONObject emptyTactical() {
        JSONObject out = new JSONObject(true);
        for (String p : PROFESSIONS) {
            out.put(p, "");
        }
        return out;
    }

    private static JSONObject defaultTactical() {
        JSONObject out = emptyTactical();
        JSONObject buffs = table().getJSONObject("tacticalBuffs");
        if (buffs == null) {
            return out;
        }
        for (String id : buffs.keySet()) {
            JSONObject b = buffs.getJSONObject(id);
            if (b == null || !b.getBooleanValue("isDefaultActive")) {
                continue;
            }
            String prof = b.getString("profession");
            if (prof != null && out.containsKey(prof)
                    && (out.getString(prof) == null || out.getString(prof).isEmpty())) {
                out.put(prof, id);
            }
        }
        return out;
    }

    private static JSONObject towerNode(JSONObject userData) {
        JSONObject node = userData.getJSONObject("tower");
        if (node == null) {
            node = new JSONObject(true);
            userData.put("tower", node);
        }
        if (node.getJSONObject("current") == null) {
            node.put("current", emptyCurrent());
        }
        if (node.getJSONObject("outer") == null) {
            JSONObject outer = new JSONObject(true);
            outer.put("training", new JSONObject(true));
            outer.put("towers", new JSONObject(true));
            outer.put("hasTowerPass", 0);
            outer.put("pickedGodCard", new JSONObject(true));
            outer.put("tactical", emptyTactical());
            outer.put("strategy", "OPTIMIZE");
            outer.put("squad", null);
            node.put("outer", outer);
        }
        return node;
    }

    private static JSONObject emptyCurrent() {
        JSONObject cur = new JSONObject(true);
        JSONObject st = new JSONObject(true);
        st.put("state", "NONE");
        st.put("tower", "");
        st.put("coord", 0);
        st.put("tactical", emptyTactical());
        st.put("strategy", "OPTIMIZE");
        st.put("start", 0);
        st.put("isHard", false);
        cur.put("status", st);
        cur.put("layer", new JSONArray());
        cur.put("cards", new JSONObject(true));
        JSONObject gc = new JSONObject(true);
        gc.put("id", "");
        gc.put("subGodCardId", "");
        cur.put("godCard", gc);
        JSONObject ht = new JSONObject(true);
        ht.put("count", 0);
        ht.put("candidate", new JSONArray());
        ht.put("canGiveUp", false);
        cur.put("halftime", ht);
        cur.put("trap", new JSONArray());
        return cur;
    }

    private static boolean towerCleared(JSONObject userData, String towerId) {
        JSONObject towerNode = userData.getJSONObject("tower");
        if (towerNode == null) {
            return false;
        }
        JSONObject outer = towerNode.getJSONObject("outer");
        if (outer == null) {
            return false;
        }
        JSONObject training = outer.getJSONObject("training");
        if (training != null && training.getIntValue(towerId) > 0) {
            return true;
        }
        JSONObject towers = outer.getJSONObject("towers");
        return towers != null && towers.getJSONObject(towerId) != null;
    }

    private static int subcardStageSort() {
        JSONObject detail = table().getJSONObject("detailConst");
        int v = detail == null ? 0 : detail.getIntValue("subcardStageSort");
        return v > 0 ? v : 4;
    }

    private static int recruitChoiceCount() {
        JSONObject detail = table().getJSONObject("detailConst");
        int v = detail == null ? 0 : detail.getIntValue("charRecruitChoiceCount");
        return v > 0 ? v : 5;
    }

    private static JSONArray buildRecruitCandidates(JSONObject userData, JSONObject cur, int max) {
        JSONArray out = new JSONArray();
        JSONObject troopChars = userData.getJSONObject("troop") == null ? null
                : userData.getJSONObject("troop").getJSONObject("chars");
        if (troopChars == null) {
            return out;
        }
        JSONObject cards = cur.getJSONObject("cards");
        java.util.Set<String> used = new java.util.HashSet<>();
        if (cards != null) {
            for (String k : cards.keySet()) {
                String rel = cards.getJSONObject(k).getString("relation");
                if (rel != null) {
                    used.add(rel);
                }
            }
        }
        // 官服是从可用干员里随机抽 5 个（charRecruitChoiceCount）
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String k : troopChars.keySet()) {
            JSONObject c = troopChars.getJSONObject(k);
            if (c == null) {
                continue;
            }
            String instId = String.valueOf(c.getIntValue("instId"));
            if (!used.contains(instId)) {
                pool.add(k);
            }
        }
        java.util.Collections.shuffle(pool, RANDOM);
        int choice = Math.min(max, recruitChoiceCount());
        for (String k : pool) {
            if (out.size() >= choice) {
                break;
            }
            JSONObject c = troopChars.getJSONObject(k);
            JSONObject group = new JSONObject(true);
            group.put("groupId", c.getString("charId"));
            group.put("type", "CHAR");
            JSONArray one = new JSONArray();
            one.add(buildPlayerCard(0, c, null));
            group.put("cards", one);
            out.add(group);
        }
        return out;
    }

    private static JSONObject okDelta(JSONObject towerDelta) {
        JSONObject modified = new JSONObject(true);
        modified.put("tower", towerDelta);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", delta);
        return result;
    }

    private static JSONObject bad(String msg, int code) {
        JSONObject bad = new JSONObject(true);
        bad.put("result", code);
        bad.put("error", msg);
        return bad;
    }

    // ───────────────────────────── createGame ─────────────────────────────

    @PostMapping(value = {"/createGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject createGame(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String towerId = body == null ? null : body.getString("tower");
        boolean isHard = body != null && body.getIntValue("isHard") == 1;
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/createGame tower=" + towerId + " isHard=" + isHard);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject towers = table().getJSONObject("towers");
        JSONObject def = towers == null || towerId == null ? null : towers.getJSONObject(towerId);
        if (def == null) {
            return bad("未知的派驻区域: " + towerId, 1);
        }
        JSONArray levelIds = isHard && def.getJSONArray("hardLevels") != null
                ? def.getJSONArray("hardLevels") : def.getJSONArray("levels");
        // 前置区域解锁：towers[].preTowerId 必须先通关（演练是 tr_01 → tr_02 → tr_03 链）
        String preTower = def.getString("preTowerId");
        if (preTower != null && !preTower.isEmpty() && !towerCleared(userData, preTower)) {
            return bad("需要先通关前置区域: " + preTower, 1);
        }

        if (levelIds == null || levelIds.isEmpty()) {
            return bad("该区域没有关卡: " + towerId, 1);
        }

        JSONObject node = towerNode(userData);
        JSONObject cur = new JSONObject(true);
        JSONObject st = new JSONObject(true);
        st.put("state", "STANDBY");
        st.put("tower", towerId);
        st.put("coord", 0);
        st.put("tactical", defaultTactical());
        st.put("strategy", "NONE");
        st.put("start", ArknightsApplication.getTimestamp());
        st.put("isHard", isHard);
        cur.put("status", st);

        JSONArray layer = new JSONArray();
        for (int i = 0; i < levelIds.size(); i++) {
            JSONObject one = new JSONObject(true);
            one.put("id", levelIds.getString(i));
            one.put("try", 0);
            one.put("pass", false);
            layer.add(one);
        }
        cur.put("layer", layer);
        cur.put("cards", new JSONObject(true));
        JSONObject gc = new JSONObject(true);
        gc.put("id", "");
        gc.put("subGodCardId", "");
        cur.put("godCard", gc);
        JSONObject ht = new JSONObject(true);
        ht.put("count", 0);
        ht.put("candidate", new JSONArray());
        ht.put("canGiveUp", false);
        cur.put("halftime", ht);
        cur.put("trap", new JSONArray());

        node.put("current", cur);
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[tower] 创建派驻 {} 共 {} 层", towerId, layer.size());
        return okDelta(node);
    }

    // ───────────────────────────── battleStart ────────────────────────────

    @PostMapping(value = {"/battleStart"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleStart(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String stageId = body == null ? null : body.getString("stageId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/battleStart stage=" + stageId);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONArray layer = cur.getJSONArray("layer");
        JSONObject target = null;
        if (layer != null) {
            for (int i = 0; i < layer.size(); i++) {
                if (stageId != null && stageId.equals(layer.getJSONObject(i).getString("id"))) {
                    target = layer.getJSONObject(i);
                    break;
                }
            }
        }
        if (target == null) {
            return bad("当前派驻没有这一层: " + stageId, 1);
        }
        target.put("try", target.getIntValue("try") + 1);
        userDao.setUserData(account.getUid(), userData);

        JSONObject result = okDelta(node);
        result.put("battleId", UUID.randomUUID().toString());
        return result;
    }

    // ───────────────────────────── battleFinish ───────────────────────────

    @PostMapping(value = {"/battleFinish"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleFinish(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/battleFinish");

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject pushFlags = userData.getJSONObject("pushFlags");
        String status = pushFlags == null ? "0" : pushFlags.getString("status");

        int completeState = 0;
        int killCnt = 0;
        String dataHex = body == null ? null : body.getString("data");
        if (dataHex != null && !dataHex.isEmpty()) {
            try {
                JSONObject bd = Utils.BattleData_decrypt(dataHex, status);
                if (bd != null) {
                    completeState = bd.getIntValue("completeState");
                    killCnt = bd.getIntValue("killCnt");
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[tower] battleFinish 解密失败: {}", e.toString());
            }
        }
        if (completeState == 0 && body != null && body.getJSONObject("battleData") != null) {
            completeState = body.getJSONObject("battleData").getIntValue("completeState");
        }
        // 只有拿不到真实对战数据时才用 debug 兜底，避免真打输了也被判成通关
        boolean hasRealState = completeState > 0;
        if (!hasRealState && ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug")) {
            completeState = 3;
        }
        boolean pass = completeState >= 2;
        ArknightsApplication.LOGGER.info("[tower] battleFinish state={} kill={} pass={}", completeState, killCnt, pass);

        // 失败局：官服不返回 tower 节点（current 原样保留，layer.try 已在 battleStart 计过）
        if (!pass) {
            JSONObject failDelta = new JSONObject(true);
            failDelta.put("modified", new JSONObject(true));
            failDelta.put("deleted", new JSONObject(true));
            JSONObject failResult = new JSONObject(true);
            failResult.put("isNewRecord", false);
            JSONObject zero = new JSONObject(true);
            JSONObject zLow = new JSONObject(true);
            zLow.put("from", 0);
            zLow.put("to", 0);
            JSONObject zHigh = new JSONObject(true);
            zHigh.put("from", 0);
            zHigh.put("to", 0);
            zero.put("low", zLow);
            zero.put("high", zHigh);
            failResult.put("reward", zero);
            failResult.put("show", String.valueOf(killCnt));
            failResult.put("trap", new JSONArray());
            failResult.put("playerDataDelta", failDelta);
            return failResult;
        }

        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        String towerId = cur.getJSONObject("status").getString("tower");
        JSONArray layer = cur.getJSONArray("layer");
        if (layer != null) {
            for (int i = 0; i < layer.size(); i++) {
                JSONObject one = layer.getJSONObject(i);
                if (one.getBooleanValue("pass")) {
                    continue;
                }
                if (pass) {
                    one.put("pass", true);
                }
                break;                                  // 只推进当前层
            }
        }
        int passed = 0;
        if (layer != null) {
            for (int i = 0; i < layer.size(); i++) {
                if (layer.getJSONObject(i).getBooleanValue("pass")) {
                    passed++;
                }
            }
        }
        JSONObject st = cur.getJSONObject("status");
        int totalLayers = layer == null ? 0 : layer.size();
        boolean allPass = totalLayers > 0 && passed >= totalLayers;
        // 官服状态机：全通 END；第 (subcardStageSort-1) 层后进 SUB_GOD_CARD_RECRUIT；其余 RECRUIT
        String nextState;
        if (allPass) {
            nextState = "END";
        } else if (passed == subcardStageSort() - 1) {
            nextState = "SUB_GOD_CARD_RECRUIT";
        } else {
            nextState = "RECRUIT";
        }
        st.put("state", nextState);
        st.put("coord", passed);

        // 中场招募：只有 RECRUIT 状态下给候选（官服样本：count=1、5 个候选、canGiveUp=true）
        JSONObject halftime = cur.getJSONObject("halftime");
        if (halftime == null) {
            halftime = new JSONObject(true);
            cur.put("halftime", halftime);
        }
        if ("RECRUIT".equals(nextState)) {
            halftime.put("count", 1);
            halftime.put("candidate", buildRecruitCandidates(userData, cur, 5));
            halftime.put("canGiveUp", true);
        } else {
            halftime.put("count", 0);
            halftime.put("candidate", new JSONArray());
            halftime.put("canGiveUp", false);
        }

        JSONObject outer = node.getJSONObject("outer");
        if (pass && towerId != null && !towerId.isEmpty() && outer != null) {
            JSONObject towersNode = outer.getJSONObject("towers");
            if (towersNode == null) {
                towersNode = new JSONObject(true);
                outer.put("towers", towersNode);
            }
            JSONObject rec = towersNode.getJSONObject(towerId);
            if (rec == null) {
                rec = new JSONObject(true);
                rec.put("best", passed);
                rec.put("reward", new JSONArray());
                rec.put("unlockHard", false);
                rec.put("hardBest", 0);
                rec.put("canSweep", false);
                rec.put("canSweepHard", false);
                towersNode.put(towerId, rec);
            } else if (rec.getIntValue("best") < passed) {
                rec.put("best", passed);
            }
            // 官服：全通后 unlockHard/canSweep 置 true，outer.hasTowerPass=1（battleFinish 就写，不等 settle）
            if (allPass) {
                boolean isHardRun = st.getBooleanValue("isHard");
                if (!isHardRun) {
                    rec.put("best", Math.max(rec.getIntValue("best"), totalLayers));
                    rec.put("unlockHard", true);
                    rec.put("canSweep", true);
                    outer.put("hasTowerPass", 1);
                } else {
                    rec.put("hardBest", Math.max(rec.getIntValue("hardBest"), totalLayers));
                    rec.put("canSweepHard", true);
                    outer.put("hasTowerPass", 1);
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject result = okDelta(node);
        result.put("isNewRecord", pass);
        JSONObject reward = new JSONObject(true);
        JSONObject low = new JSONObject(true);
        low.put("from", 0);
        low.put("to", 0);
        JSONObject high = new JSONObject(true);
        high.put("from", 0);
        high.put("to", 0);
        reward.put("low", low);
        reward.put("high", high);
        result.put("reward", reward);
        result.put("show", "0");
        result.put("trap", new JSONArray());
        return result;
    }

    // ───────────────────────────── initGodCard ────────────────────────────

    @PostMapping(value = {"/initGodCard"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject initGodCard(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String godCardId = body == null ? null : body.getString("godCardId");
        String subGodCardId = body == null ? null : body.getString("subGodCardId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/initGodCard " + godCardId + " sub=" + subGodCardId);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONObject gc = cur.getJSONObject("godCard");
        if (gc == null) {
            gc = new JSONObject(true);
            cur.put("godCard", gc);
        }
        gc.put("id", godCardId == null ? "" : godCardId);
        if (subGodCardId != null) {
            gc.put("subGodCardId", subGodCardId);
        }
        JSONObject st = cur.getJSONObject("status");
        st.put("state", "INIT_BUFF");

        JSONObject outer = node.getJSONObject("outer");
        if (outer != null && godCardId != null && !godCardId.isEmpty()) {
            JSONObject picked = outer.getJSONObject("pickedGodCard");
            if (picked == null) {
                picked = new JSONObject(true);
                outer.put("pickedGodCard", picked);
            }
            // pickedGodCard 是「历史所有选过的导能元件」累加记录：
            //   {"card_05":[], "card_07":["subcard_07_01"], …}
            // 已存在的条目要保留子卡列表，只有新元件才建空数组
            JSONArray subs = picked.getJSONArray(godCardId);
            if (subs == null) {
                subs = new JSONArray();
                picked.put(godCardId, subs);
            }
            if (subGodCardId != null && !subGodCardId.isEmpty() && !subs.contains(subGodCardId)) {
                subs.add(subGodCardId);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        return okDelta(node);
    }

    // ───────────────────────────── initGame ───────────────────────────────

    @PostMapping(value = {"/initGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject initGame(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject body,
                               HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/initGame");

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONObject st = cur.getJSONObject("status");

        JSONObject tactical = body == null ? null : body.getJSONObject("tactical");
        if (tactical != null) {
            JSONObject merged = st.getJSONObject("tactical");
            if (merged == null) {
                merged = emptyTactical();
            }
            for (String p : PROFESSIONS) {
                String v = tactical.getString(p);
                if (v != null) {
                    merged.put(p, v);
                }
            }
            st.put("tactical", merged);
        }
        String strategy = body == null ? null : body.getString("strategy");
        if (strategy != null && !strategy.isEmpty()) {
            st.put("strategy", strategy);
        }
        st.put("state", "INIT_CARD");

        JSONObject outer = node.getJSONObject("outer");
        if (outer != null) {
            outer.put("tactical", st.getJSONObject("tactical"));
            outer.put("strategy", st.getString("strategy"));
        }
        userDao.setUserData(account.getUid(), userData);
        return okDelta(node);
    }

    // ───────────────────────────── initCard ───────────────────────────────

    @PostMapping(value = {"/initCard"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject initCard(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject body,
                               HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/initCard");

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONArray slots = body == null ? null : body.getJSONArray("slots");
        JSONObject troopChars = userData.getJSONObject("troop") == null ? null
                : userData.getJSONObject("troop").getJSONObject("chars");

        JSONObject cards = new JSONObject(true);
        JSONArray squad = new JSONArray();
        int seq = 0;
        if (slots != null) {
            for (int i = 0; i < slots.size(); i++) {
                JSONObject slot = slots.getJSONObject(i);
                int instId = slot.getIntValue("charInstId");
                if (instId <= 0) {
                    continue;
                }
                JSONObject src = troopChars == null ? null : troopChars.getJSONObject(String.valueOf(instId));
                if (src == null) {
                    ArknightsApplication.LOGGER.warn("[tower] initCard 找不到干员实例 {}", instId);
                    continue;
                }
                seq++;
                cards.put(String.valueOf(seq), buildPlayerCard(seq, src, slot));
                JSONObject sq = new JSONObject(true);
                sq.put("charInstId", instId);
                sq.put("skillIndex", slot.getIntValue("skillIndex"));
                sq.put("currentEquip", slot.getString("currentEquip"));
                squad.add(sq);
            }
        }
        cur.put("cards", cards);
        JSONObject st = cur.getJSONObject("status");
        st.put("state", "STANDBY");
        st.put("coord", 0);

        JSONObject outer = node.getJSONObject("outer");
        if (outer != null) {
            outer.put("squad", squad);
        }
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[tower] initCard 生成 {} 张卡", cards.size());
        return okDelta(node);
    }

    private static JSONObject buildPlayerCard(int seq, JSONObject src, JSONObject slot) {
        JSONObject card = new JSONObject(true);
        card.put("instId", String.valueOf(seq));
        card.put("type", "CHAR");
        card.put("charId", src.getString("charId"));
        card.put("relation", String.valueOf(src.getIntValue("instId")));
        card.put("evolvePhase", src.getIntValue("evolvePhase"));
        card.put("level", src.getIntValue("level"));
        card.put("favorPoint", src.getIntValue("favorPoint"));
        card.put("potentialRank", src.getIntValue("potentialRank"));
        card.put("mainSkillLvl", src.getIntValue("mainSkillLvl"));
        card.put("skills", src.getJSONArray("skills"));
        card.put("defaultSkillIndex", src.getIntValue("defaultSkillIndex"));
        String equip = slot == null ? null : slot.getString("currentEquip");
        if (equip == null || equip.isEmpty()) {
            equip = src.getString("currentEquip");
        }
        card.put("currentEquip", equip == null || equip.isEmpty() ? null : equip);
        card.put("equip", src.getJSONObject("equip"));
        card.put("skin", src.getString("skin"));
        return card;
    }

    // ───────────────────────────── recruit ────────────────────────────────

    @PostMapping(value = {"/recruit"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject recruit(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject body,
                              HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String charId = body == null ? null : body.getString("charId");
        boolean giveUp = body != null && body.getIntValue("giveUp") == 1;
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/recruit char=" + charId + " giveUp=" + giveUp);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONObject cards = cur.getJSONObject("cards");
        if (cards == null) {
            cards = new JSONObject(true);
            cur.put("cards", cards);
        }
        if (!giveUp && charId != null && !charId.isEmpty()) {
            int seq = cards.size() + 1;
            cards.put(String.valueOf(seq), buildNpcCard(seq, charId));
        }
        JSONArray layer = cur.getJSONArray("layer");
        int passed = 0;
        if (layer != null) {
            for (int i = 0; i < layer.size(); i++) {
                if (layer.getJSONObject(i).getBooleanValue("pass")) {
                    passed++;
                }
            }
        }
        JSONObject st = cur.getJSONObject("status");
        st.put("state", "RECRUIT");
        st.put("coord", passed);
        userDao.setUserData(account.getUid(), userData);
        return okDelta(node);
    }

    private static JSONObject buildNpcCard(int seq, String charId) {
        JSONObject card = new JSONObject(true);
        card.put("instId", String.valueOf(seq));
        card.put("type", "NPC");
        card.put("charId", charId);
        card.put("relation", "0");
        card.put("evolvePhase", 2);
        card.put("level", 80);
        card.put("favorPoint", 25570);
        card.put("potentialRank", 0);
        card.put("mainSkillLvl", 7);
        JSONArray skills = new JSONArray();
        JSONObject meta = ArknightsApplication.characterJson == null ? null
                : ArknightsApplication.characterJson.getJSONObject(charId);
        JSONArray defSkills = meta == null ? null : meta.getJSONArray("skills");
        if (defSkills != null && !defSkills.isEmpty()) {
            JSONObject one = new JSONObject(true);
            one.put("skillId", defSkills.getJSONObject(0).getString("skillId"));
            one.put("unlock", 1);
            one.put("state", 0);
            one.put("specializeLevel", 3);
            one.put("completeUpgradeTime", -1);
            skills.add(one);
        }
        card.put("skills", skills);
        card.put("defaultSkillIndex", 0);
        card.put("skin", charId + "#1");
        return card;
    }

    // ───────────────────────────── layerReward ────────────────────────────

    @PostMapping(value = {"/layerReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject layerReward(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String towerId = body == null ? null : body.getString("tower");
        JSONArray layers = body == null ? null : body.getJSONArray("layers");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/layerReward " + towerId + " layers=" + layers);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);

        JSONObject table = table();
        // 层奖励来自关卡定义：levels[<层关卡>].dropInfo.displayDropInfo（minCount=maxCount）
        JSONObject levels = table.getJSONObject("levels");
        JSONObject defOfTower = table.getJSONObject("towers") == null ? null
                : table.getJSONObject("towers").getJSONObject(towerId);
        JSONArray defLevels = defOfTower == null ? null
                : (defOfTower.getBooleanValue("isHardOf") ? null : defOfTower.getJSONArray("levels"));

        JSONObject outer = node.getJSONObject("outer");
        JSONObject towersNode = outer == null ? null : outer.getJSONObject("towers");
        if (towersNode == null) {
            towersNode = new JSONObject(true);
            outer.put("towers", towersNode);
        }
        JSONObject rec = towerId == null ? null : towersNode.getJSONObject(towerId);
        if (rec == null) {
            rec = new JSONObject(true);
            rec.put("best", 0);
            rec.put("reward", new JSONArray());
            rec.put("unlockHard", false);
            rec.put("hardBest", 0);
            rec.put("canSweep", false);
            rec.put("canSweepHard", false);
            towersNode.put(towerId, rec);
        }
        JSONArray got = rec.getJSONArray("reward");
        if (got == null) {
            got = new JSONArray();
            rec.put("reward", got);
        }
        JSONArray items = new JSONArray();
        JSONObject inventory = userData.getJSONObject("inventory");
        int best = rec.getIntValue("best");
        if (layers != null) {
            for (int i = 0; i < layers.size(); i++) {
                int layer = layers.getIntValue(i);
                if (got.contains(layer)) {
                    continue;                       // 已领
                }
                String levelId = defLevels != null && layer >= 1 && layer <= defLevels.size()
                        ? defLevels.getString(layer - 1) : null;
                JSONObject levelDef = levelId == null || levels == null ? null : levels.getJSONObject(levelId);
                JSONObject drop = levelDef == null ? null
                        : (levelDef.getJSONObject("dropInfo") == null ? null
                        : levelDef.getJSONObject("dropInfo").getJSONObject("displayDropInfo"));
                if (drop != null) {
                    for (String itemId : drop.keySet()) {
                        JSONObject d = drop.getJSONObject(itemId);
                        int cnt = Math.max(d.getIntValue("minCount"), d.getIntValue("maxCount"));
                        if (cnt > 0) {
                            grantItem(items, inventory, itemId, cnt);
                        }
                    }
                }
                got.add(layer);
                if (layer > best) {
                    best = layer;
                }
            }
        }
        rec.put("best", best);
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("tower", node);
        modified.put("inventory", inventory);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", delta);
        return result;
    }

    private static void grantItem(JSONArray items, JSONObject inventory, String itemId, int count) {
        JSONObject item = new JSONObject(true);
        item.put("type", "MATERIAL");
        item.put("id", itemId);
        item.put("count", count);
        items.add(item);
        if (inventory != null) {
            inventory.put(itemId, inventory.getIntValue(itemId) + count);
        }
    }

    // ───────────────────────────── sweepGame ──────────────────────────────

    @PostMapping(value = {"/sweepGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject sweepGame(@RequestHeader(value = "secret", required = false) String secret,
                                @RequestBody(required = false) JSONObject body,
                                HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String towerId = body == null ? null : body.getString("tower");
        boolean isHard = body != null && body.getIntValue("isHard") == 1;
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/sweepGame tower=" + towerId + " isHard=" + isHard);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject outer = node.getJSONObject("outer");
        JSONObject towersNode = outer == null ? null : outer.getJSONObject("towers");
        JSONObject rec = towersNode == null || towerId == null ? null : towersNode.getJSONObject(towerId);
        if (rec == null) {
            return bad("该区域还没有清理记录: " + towerId, 1);
        }
        JSONObject table = table();
        JSONObject detail = table.getJSONObject("detailConst");
        JSONObject towersDef = table.getJSONObject("towers");
        JSONObject def = towerId == null ? null : towersDef.getJSONObject(towerId);
        if (def == null) {
            return bad("未知的派驻区域: " + towerId, 1);
        }
        int needLayer = detail == null ? 1
                : detail.getIntValue(isHard ? "sweepOpenDifficultLayer" : "sweepOpenOrdinaryLayer");
        int best = isHard ? rec.getIntValue("hardBest") : rec.getIntValue("best");
        boolean canSweep = isHard ? rec.getBooleanValue("canSweepHard") : rec.getBooleanValue("canSweep");
        if (!canSweep || best < Math.max(1, needLayer)) {
            return bad("该区域还不能扫荡（需要先通关）", 1);
        }

        // 扣常态事务代理卡
        int cost = detail == null ? 2 : detail.getIntValue("sweepCostCount");
        if (cost <= 0) {
            cost = 2;
        }
        JSONObject inventory = userData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            userData.put("inventory", inventory);
        }
        String ticketId = "EXTERMINATION_AGENT";
        int owned = inventory.getIntValue(ticketId);
        if (owned < cost) {
            return bad("常态事务代理卡不足（需要 " + cost + " 张）", 1);
        }
        inventory.put(ticketId, owned - cost);

        // 逐层结算阶段酬劳；困难模式（应急补给）翻倍
        JSONObject levels = table.getJSONObject("levels");
        JSONArray defLevels = isHard && def.getJSONArray("hardLevels") != null
                ? def.getJSONArray("hardLevels") : def.getJSONArray("levels");
        JSONArray items = new JSONArray();
        int multiplier = isHard ? 2 : 1;
        if (defLevels != null) {
            int layerCount = Math.min(best, defLevels.size());
            for (int i = 0; i < layerCount; i++) {
                JSONObject levelDef = levels == null ? null : levels.getJSONObject(defLevels.getString(i));
                JSONObject drop = levelDef == null ? null
                        : (levelDef.getJSONObject("dropInfo") == null ? null
                        : levelDef.getJSONObject("dropInfo").getJSONObject("displayDropInfo"));
                if (drop == null) {
                    continue;
                }
                for (String itemId : drop.keySet()) {
                    JSONObject d = drop.getJSONObject(itemId);
                    int cnt = Math.max(d.getIntValue("minCount"), d.getIntValue("maxCount")) * multiplier;
                    if (cnt > 0) {
                        grantItem(items, inventory, itemId, cnt);
                    }
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("tower", node);
        modified.put("inventory", inventory);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", delta);
        return result;
    }

    // ───────────────────────────── seasonMissonsAward ─────────────────────

    @PostMapping(value = {"/seasonMissonsAward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject seasonMissonsAward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        JSONArray ids = body == null ? null : body.getJSONArray("missionIds");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/seasonMissonsAward " + ids);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject season = node.getJSONObject("season");
        if (season == null) {
            return bad("当前没有派驻周期", 1);
        }
        JSONObject missions = season.getJSONObject("missions");
        if (missions == null) {
            missions = new JSONObject(true);
            season.put("missions", missions);
        }
        JSONObject missionData = table().getJSONObject("missionData");
        JSONArray items = new JSONArray();
        JSONObject inventory = userData.getJSONObject("inventory");
        JSONObject status = userData.getJSONObject("status");
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                String mid = ids.getString(i);
                JSONObject m = missions.getJSONObject(mid);
                JSONObject defM = missionData == null ? null : missionData.getJSONObject(mid);
                if (m == null || defM == null || m.getBooleanValue("hasRecv")) {
                    continue;
                }
                if (m.getIntValue("value") < m.getIntValue("target")) {
                    continue;
                }
                JSONArray rewards = defM.getJSONArray("rewards");
                if (rewards != null) {
                    for (int k = 0; k < rewards.size(); k++) {
                        JSONObject rw = rewards.getJSONObject(k);
                        String type = rw.getString("type");
                        String id = rw.getString("id");
                        int count = rw.getIntValue("count");
                        if (count <= 0) {
                            continue;
                        }
                        if ("GOLD".equals(type) && status != null) {
                            status.put("gold", status.getIntValue("gold") + count);
                            JSONObject it = new JSONObject(true);
                            it.put("type", type);
                            it.put("id", id);
                            it.put("count", count);
                            items.add(it);
                        } else if (inventory != null) {
                            grantItem(items, inventory, id, count);
                        }
                    }
                }
                m.put("hasRecv", true);
            }
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("tower", node);
        if (inventory != null) {
            modified.put("inventory", inventory);
        }
        if (status != null) {
            modified.put("status", status);
        }
        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", delta);
        return result;
    }

    // ───────────────────────────── chooseSubGodCard ───────────────────────

    @PostMapping(value = {"/chooseSubGodCard"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject chooseSubGodCard(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody(required = false) JSONObject body,
                                       HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String subGodCardId = body == null ? null : body.getString("subGodCardId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/chooseSubGodCard " + subGodCardId);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        JSONObject gc = cur.getJSONObject("godCard");
        if (gc == null) {
            gc = new JSONObject(true);
            gc.put("id", "");
            cur.put("godCard", gc);
        }
        gc.put("subGodCardId", subGodCardId == null ? "" : subGodCardId);
        JSONObject st = cur.getJSONObject("status");
        st.put("state", "STANDBY");

        String godCardId = gc.getString("id");
        JSONObject outer = node.getJSONObject("outer");
        if (outer != null && godCardId != null && !godCardId.isEmpty()
                && subGodCardId != null && !subGodCardId.isEmpty()) {
            JSONObject picked = outer.getJSONObject("pickedGodCard");
            if (picked == null) {
                picked = new JSONObject(true);
                outer.put("pickedGodCard", picked);
            }
            JSONArray subs = picked.getJSONArray(godCardId);
            if (subs == null) {
                subs = new JSONArray();
                picked.put(godCardId, subs);
            }
            if (!subs.contains(subGodCardId)) {
                subs.add(subGodCardId);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        return okDelta(node);
    }

    // ───────────────────────────── settleGame ─────────────────────────────

    @PostMapping(value = {"/settleGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject settleGame(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /tower/settleGame");

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject node = towerNode(userData);
        JSONObject cur = node.getJSONObject("current");
        String towerId = cur.getJSONObject("status").getString("tower");
        JSONArray layer = cur.getJSONArray("layer");
        int passed = 0;
        boolean allPass = layer != null && !layer.isEmpty();
        if (layer != null) {
            for (int i = 0; i < layer.size(); i++) {
                if (layer.getJSONObject(i).getBooleanValue("pass")) {
                    passed++;
                } else {
                    allPass = false;
                }
            }
        }

        JSONObject outer = node.getJSONObject("outer");
        JSONObject towersDef = table().getJSONObject("towers");
        JSONObject def = towerId == null ? null : towersDef.getJSONObject(towerId);
        boolean training = def != null && "TRAINING".equals(def.getString("towerType"));
        if (allPass && towerId != null && !towerId.isEmpty() && outer != null) {
            if (training) {
                JSONObject tr = outer.getJSONObject("training");
                if (tr == null) {
                    tr = new JSONObject(true);
                    outer.put("training", tr);
                }
                tr.put(towerId, 1);
                ArknightsApplication.LOGGER.info("[tower] 保全演练 {} 通关，正式玩法已解锁", towerId);
            }
            // 正式区域的 outer.towers / hasTowerPass 已在 battleFinish 写好，settle 不再重复写
        }

        // 官服结算会把 cards 的每个 key 列进 deleted（不是给空对象）
        JSONArray deletedCards = new JSONArray();
        JSONObject oldCards = cur.getJSONObject("cards");
        if (oldCards != null) {
            for (String k : oldCards.keySet()) {
                deletedCards.add(k);
            }
        }
        node.put("current", emptyCurrent());
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("tower", node);
        JSONObject deleted = new JSONObject(true);
        if (!deletedCards.isEmpty()) {
            JSONObject curDeleted = new JSONObject(true);
            curDeleted.put("cards", deletedCards);
            JSONObject towerDeleted = new JSONObject(true);
            towerDeleted.put("current", curDeleted);
            deleted.put("tower", towerDeleted);
        }
        // pushMessage：客户端靠它弹「扫荡解锁 / 奖章 / 赛季任务完成」
        JSONArray push = new JSONArray();
        if (allPass && towerId != null && !towerId.isEmpty()) {
            JSONObject towersNode2 = outer == null ? null : outer.getJSONObject("towers");
            JSONObject rec = towersNode2 == null || towerId == null ? null : towersNode2.getJSONObject(towerId);
            if (rec != null && rec.getBooleanValue("canSweep")) {
                JSONObject p1 = new JSONObject(true);
                p1.put("path", "towerSweepUnlock");
                JSONObject payload1 = new JSONObject(true);
                payload1.put("towerId", towerId);
                payload1.put("isHard", false);
                p1.put("payload", payload1);
                push.add(p1);
            }
            String medalId = def == null ? null : def.getString("medalId");
            if (medalId != null && !medalId.isEmpty()) {
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
                if (!medals.containsKey(medalId)) {
                    JSONObject m = new JSONObject(true);
                    m.put("id", medalId);
                    JSONArray val = new JSONArray();
                    JSONArray one = new JSONArray();
                    one.add(1);
                    one.add(1);
                    val.add(one);
                    m.put("val", val);
                    m.put("fts", ArknightsApplication.getTimestamp());
                    m.put("rts", -1);
                    medals.put(medalId, m);
                    modified.put("medal", medalsNode);
                    JSONObject p2 = new JSONObject(true);
                    p2.put("path", "medalFinish");
                    JSONObject payload2 = new JSONObject(true);
                    JSONArray idList = new JSONArray();
                    idList.add(medalId);
                    payload2.put("idList", idList);
                    p2.put("payload", payload2);
                    push.add(p2);
                }
            }
            // 赛季任务：TowerSettlePass / TowerSettleLayer
            JSONArray doneTasks = advanceSeasonMissions(node, towerId, passed, def);
            if (!doneTasks.isEmpty()) {
                JSONObject p3 = new JSONObject(true);
                p3.put("path", "towerSeasonTask");
                JSONObject payload3 = new JSONObject(true);
                payload3.put("idList", doneTasks);
                p3.put("payload", payload3);
                push.add(p3);
            }
            userDao.setUserData(account.getUid(), userData);
        }

        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", deleted);
        JSONObject result = new JSONObject(true);
        result.put("ts", ArknightsApplication.getTimestamp());
        result.put("playerDataDelta", delta);
        if (!push.isEmpty()) {
            result.put("pushMessage", push);
        }
        return result;
    }

    private static JSONArray advanceSeasonMissions(JSONObject node, String towerId, int passed, JSONObject towerDef) {
        JSONArray finished = new JSONArray();
        JSONObject season = node.getJSONObject("season");
        if (season == null) {
            return finished;
        }
        String seasonId = season.getString("id");
        JSONObject missions = season.getJSONObject("missions");
        if (missions == null) {
            missions = new JSONObject(true);
            season.put("missions", missions);
        }
        JSONObject missionData = table().getJSONObject("missionData");
        if (missionData == null) {
            return finished;
        }
        for (String mid : new java.util.ArrayList<>(missions.keySet())) {
            JSONObject nodeM = missions.getJSONObject(mid);
            JSONObject defM = missionData.getJSONObject(mid);
            if (nodeM == null || defM == null || nodeM.getBooleanValue("hasRecv")) {
                continue;
            }
            String group = defM.getString("missionGroup");
            if (group != null && seasonId != null && !group.equals(seasonId)) {
                continue;
            }
            String template = defM.getString("template");
            JSONArray param = defM.getJSONArray("param");
            int target = nodeM.getIntValue("target");
            int value = nodeM.getIntValue("value");
            if ("TowerSettlePass".equals(template)) {
                String towerParam = param != null && param.size() > 1 ? param.getString(1) : null;
                if (towerId.equals(towerParam) && value < target) {
                    nodeM.put("value", target);
                    finished.add(mid);
                }
            } else if ("TowerSettleLayer".equals(template)) {
                int needLayer = param != null && param.size() > 2 ? param.getIntValue(2) : 0;
                if (passed >= needLayer && value < target) {
                    int next = Math.min(target, value + 1);
                    nodeM.put("value", next);
                    if (next >= target) {
                        finished.add(mid);
                    }
                }
            }
        }
        return finished;
    }
}
