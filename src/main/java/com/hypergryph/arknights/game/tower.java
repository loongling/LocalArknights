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

/**
 * 保全派驻（内部代号 tower / legion）
 *
 *   解锁是**客户端判定**：有任意干员 currentEquip（专属模组）+ 通关 main_01-05。
 *   服务端不需要解锁接口，也没有对应开关字段
 *
 * 存档结构 user.tower：
 *   current { status{state,tower,coord,tactical,strategy,start,isHard}, layer[], cards{}, godCard{}, halftime{}, trap[] }
 *   outer   { training{}, towers{}, hasTowerPass, pickedGodCard{}, tactical{}, strategy, squad }
 *   season  { id, finishTs, missions{...} }
 *
 * 接口（4 个，全部实测）：
 *   POST /tower/createGame    {"tower":"tower_tr_01","isHard":0}
 *   POST /tower/battleStart   {"stageId":"lt_tr_01","squad":{...}}
 *   POST /tower/battleFinish  {"data":"<AES hex>","battleData":{...}}
 *   POST /tower/settleGame    {}
 */
@RestController
@RequestMapping("/tower")
public class tower {

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

    /** 默认战术装备：每个职业取 isDefaultActive 的那一个 */
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

    /** 取/建 user.tower 节点 */
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

    /** 空 current（state=NONE） */
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

    /** 该区域是否已通关（演练看 outer.training，正式区看 outer.towers 有记录） */
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
        if (ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug")) {
            completeState = 3;
        }
        boolean pass = completeState >= 2;
        ArknightsApplication.LOGGER.info("[tower] battleFinish state={} kill={} pass={}", completeState, killCnt, pass);

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
        st.put("state", pass ? "END" : "STANDBY");
        st.put("coord", passed);

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
            } else {
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
                }
                outer.put("hasTowerPass", 1);
            }
        }

        node.put("current", emptyCurrent());
        userDao.setUserData(account.getUid(), userData);

        JSONObject result = okDelta(node);
        result.put("ts", ArknightsApplication.getTimestamp());
        return result;
    }
}
