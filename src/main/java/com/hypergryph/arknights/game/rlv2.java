package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.function.Rlv2Helper;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 集成战略 RL v2（接口前缀 /rlv2/）
 *
 * 抓包结论（官服 2026-11-03）：
 *   - 客户端接口共 55 个，主循环实测用到的都在这里实现。
 *   - 局内推进是「客户端上报 + 服务端记账」：客户端决定走哪个节点/选什么，服务端维护 current。
 *   - 状态机：NONE → INIT（初始四步）→ WAIT_MOVE → PENDING（事件/战斗）→ …
 *
 * 存档结构见 {@link Rlv2Helper}。
 */
@RestController
@RequestMapping("/rlv2")
public class rlv2 {

    private static final Random RANDOM = new Random();

    private static JSONObject ZONE_TEMPLATES;

    /** 分层区域模板：{"1000": zone_1, "1001": zone_2, "1002": zone_3}（抓包抠出来的节点布局） */
    private static synchronized JSONObject zoneTemplates() {
        if (ZONE_TEMPLATES == null) {
            String path = System.getProperty("user.dir") + "/data/rlv2_zones.json";
            try {
                ZONE_TEMPLATES = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[rlv2] 读取 rlv2_zones.json 失败", e);
                ZONE_TEMPLATES = new JSONObject(true);
            }
        }
        return ZONE_TEMPLATES;
    }

    /** 取第 level 层（1 基）的模板；超出模板范围就复用最后一个 */
    private static JSONObject zoneTemplateFor(int level) {
        JSONObject all = zoneTemplates();
        int index = 1000 + (level - 1);
        JSONObject z = all.getJSONObject(String.valueOf(index));
        if (z != null) {
            return JSONObject.parseObject(z.toJSONString());
        }
        int last = -1;
        for (String k : all.keySet()) {
            try {
                last = Math.max(last, Integer.parseInt(k));
            } catch (Exception ignored) {
            }
        }
        if (last >= 0 && all.getJSONObject(String.valueOf(last)) != null) {
            return JSONObject.parseObject(all.getJSONObject(String.valueOf(last)).toJSONString());
        }
        return new JSONObject(true);
    }

    private static JSONObject bad(String msg, int code) {
        JSONObject o = new JSONObject(true);
        o.put("result", code);
        o.put("error", msg);
        return o;
    }

    private static JSONObject delta(JSONObject current) {
        JSONObject rl = new JSONObject(true);
        rl.put("current", current);
        JSONObject modified = new JSONObject(true);
        modified.put("rlv2", rl);
        JSONObject pd = new JSONObject(true);
        pd.put("modified", modified);
        pd.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", pd);
        return result;
    }

    /** 概率类事件选项的成功率（config.json → rogue.choiceChanceSuccess，默认 0.5） */
    private static double choiceChanceSuccess() {
        try {
            JSONObject cfg = ArknightsApplication.serverConfig;
            JSONObject rogue = cfg == null ? null : cfg.getJSONObject("rogue");
            if (rogue != null && rogue.containsKey("choiceChanceSuccess")) {
                return rogue.getDoubleValue("choiceChanceSuccess");
            }
        } catch (Exception ignored) {
        }
        return 0.5;
    }

    private static JSONObject CHOICE_EFFECTS;

    /** 事件选项真实效果表（由 _build_choice_effects2.py 从描述 + 抓包生成） */
    private static synchronized JSONObject choiceEffects() {
        if (CHOICE_EFFECTS == null) {
            String path = System.getProperty("user.dir") + "/data/rlv2_choice_effects.json";
            try {
                CHOICE_EFFECTS = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[rlv2] 读取 rlv2_choice_effects.json 失败: {}", e.toString());
                CHOICE_EFFECTS = new JSONObject(true);
            }
        }
        return CHOICE_EFFECTS;
    }

    private static JSONObject themeDetails(String theme) {
        JSONObject topic = ArknightsApplication.roguelikeTopicTable == null
                ? null : ArknightsApplication.roguelikeTopicTable.getJSONObject("details");
        return topic == null || theme == null ? null : topic.getJSONObject(theme);
    }

    private static JSONObject initConfig(String theme, String mode, int modeGrade) {
        JSONObject details = themeDetails(theme);
        JSONArray init = details == null ? null : details.getJSONArray("init");
        if (init == null) {
            return null;
        }
        JSONObject fallback = null;
        for (int i = 0; i < init.size(); i++) {
            JSONObject one = init.getJSONObject(i);
            if (fallback == null) {
                fallback = one;
            }
            if (!mode.equals(one.getString("modeId"))) {
                continue;
            }
            if (one.getIntValue("modeGrade") == modeGrade) {
                return one;
            }
            fallback = one;
        }
        return fallback;
    }

    private static String defaultEnding(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject endings = details == null ? null : details.getJSONObject("endings");
        if (endings == null || endings.isEmpty()) {
            return "";
        }
        for (String k : endings.keySet()) {
            JSONObject e = endings.getJSONObject(k);
            String id = e == null ? null : e.getString("id");
            return id == null ? k : id;
        }
        return "";
    }

    /** 造一个 rlv2 干员结构（从玩家自己的干员里随机取，保证客户端能正常显示） */
    private static JSONObject buildChar(JSONObject userData, int population) {
        return buildChar(userData, population, null);
    }

    /**
     * 构造一名可加入队伍的干员。
     * charId 非空时优先从该账号自己的干员里找同 id（助战招募用），找不到再随机。
     */
    private static JSONObject buildChar(JSONObject userData, int population, String charId) {
        JSONObject chars = userData.getJSONObject("troop") == null ? null
                : userData.getJSONObject("troop").getJSONObject("chars");
        if (chars == null || chars.isEmpty()) {
            return null;
        }
        JSONObject src = null;
        if (charId != null) {
            for (String k : chars.keySet()) {
                JSONObject one = chars.getJSONObject(k);
                if (charId.equals(one.getString("charId"))) {
                    src = one;
                    break;
                }
            }
        }
        if (src == null) {
            List<String> keys = new ArrayList<>(chars.keySet());
            Collections.shuffle(keys, RANDOM);
            src = chars.getJSONObject(keys.get(0));
        }
        JSONObject c = new JSONObject(true);
        c.put("instId", "0");
        c.put("charId", src.getString("charId"));
        c.put("type", "NORMAL");
        c.put("favorPoint", src.getIntValue("favorPoint"));
        c.put("potentialRank", src.getIntValue("potentialRank"));
        c.put("mainSkillLvl", src.getIntValue("mainSkillLvl"));
        c.put("skin", src.getString("skin"));
        c.put("level", src.getIntValue("level"));
        c.put("exp", src.getIntValue("exp"));
        c.put("evolvePhase", src.getIntValue("evolvePhase"));
        c.put("defaultSkillIndex", src.getIntValue("defaultSkillIndex"));
        c.put("skills", src.getJSONArray("skills"));
        c.put("upgradeLimited", false);
        c.put("upgradePhase", 0);
        c.put("isUpgrade", false);
        c.put("isCure", false);
        c.put("population", population);
        c.put("charBuff", new JSONArray());
        c.put("troopInstId", "0");
        c.put("master", new JSONObject(true));
        return c;
    }

    private static void addRelic(JSONObject cur, String relicId, int count) {
        recInc(cur, "cntGainRelic", Math.max(count, 1));
        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject relic = inv.getJSONObject("relic");
        if (relic == null) {
            relic = new JSONObject(true);
            inv.put("relic", relic);
        }
        int idx = 0;
        while (relic.containsKey("r_" + idx)) {
            idx++;
        }
        JSONObject r = new JSONObject(true);
        r.put("index", "r_" + idx);
        r.put("id", relicId);
        r.put("count", count);
        r.put("ts", ArknightsApplication.getTimestamp());
        relic.put("r_" + idx, r);
    }

    private static void enterFirstZone(JSONObject cur) {
        enterZone(cur, 1);
    }

    /** 进入第 level 层（1 基），index = 1000 + level - 1，地图按 seed 生成 */
    private static void enterZone(JSONObject cur, int level) {
        JSONObject map = cur.getJSONObject("map");
        JSONObject zones = map.getJSONObject("zones");
        if (zones == null) {
            zones = new JSONObject(true);
            map.put("zones", zones);
        }
        int index = 1000 + (level - 1);
        String key = String.valueOf(index);
        if (!zones.containsKey(key)) {
            JSONObject game = cur.getJSONObject("game");
            String seed = game == null ? null : game.getString("seed");
            String theme = game == null ? null : game.getString("theme");
            zones.put(key, com.hypergryph.arknights.core.function.RogueMapGen
                    .generateZone(seed, theme, level));
        }
        JSONObject player = cur.getJSONObject("player");
        JSONObject cursor = player.getJSONObject("cursor");
        cursor.put("zone", index);
        cursor.put("position", null);
        player.put("state", "WAIT_MOVE");
    }

    /** 当前所在层（1 基） */
    private static int currentLevel(JSONObject cur) {
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        int zone = cursor == null ? 0 : cursor.getIntValue("zone");
        return zone >= 1000 ? zone - 1000 + 1 : 1;
    }

    private static void removePending(JSONObject cur, String type) {
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending == null) {
            return;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (type.equals(pending.getJSONObject(i).getString("type"))) {
                pending.remove(i);
                return;
            }
        }
    }

    // ───────────────────────────── createGame ─────────────────────────────

    @PostMapping(value = {"/createGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject createGame(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String theme = body == null || body.getString("theme") == null ? "rogue_1" : body.getString("theme");
        String mode = body == null || body.getString("mode") == null ? "EASY" : body.getString("mode");
        int modeGrade = body == null ? 0 : body.getIntValue("modeGrade");
        String predefinedId = body == null ? null : body.getString("predefinedId");
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /rlv2/createGame theme=" + theme
                + " mode=" + mode + " grade=" + modeGrade);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject rl = userData.getJSONObject("rlv2");

        if (themeDetails(theme) == null) {
            return bad("未知的集成战略主题: " + theme, 1);
        }
        JSONObject cfg = initConfig(theme, mode, modeGrade);
        if (cfg == null) {
            return bad("该难度没有初始配置: " + mode + "/" + modeGrade, 1);
        }

        JSONObject cur = Rlv2Helper.emptyCurrent();
        JSONObject player = cur.getJSONObject("player");
        player.put("state", "INIT");

        JSONObject prop = player.getJSONObject("property");
        prop.put("level", 1);
        prop.put("maxLevel", 10);
        prop.put("exp", 0);
        JSONObject hp = new JSONObject(true);
        hp.put("current", cfg.getIntValue("initialHp"));
        hp.put("max", cfg.getIntValue("initialMaxHp"));
        prop.put("hp", hp);
        prop.put("gold", cfg.getIntValue("initialGold"));
        prop.put("shield", cfg.getIntValue("initialShield"));
        prop.put("capacity", cfg.getIntValue("initialSquadCapacity"));
        JSONObject pop = new JSONObject(true);
        pop.put("cost", 0);
        pop.put("max", cfg.getIntValue("initialPopulation"));
        prop.put("population", pop);
        prop.put("conPerfectBattle", 0);

        JSONObject game = cur.getJSONObject("game");
        game.put("mode", mode);
        game.put("predefined", predefinedId);
        game.put("theme", theme);
        // 种子：<20 位 base62>,<theme>,<grade>；同一 seed 生成同一张图
        game.put("seed", com.hypergryph.arknights.core.function.RogueMapGen.makeSeed(theme, modeGrade));
        JSONObject gameOuter = new JSONObject(true);
        gameOuter.put("support", false);
        gameOuter.put("legacy", new JSONArray());
        game.put("outer", gameOuter);
        game.put("start", ArknightsApplication.getTimestamp());
        game.put("modeGrade", modeGrade);
        game.put("equivalentGrade", modeGrade);

        cur.getJSONObject("troop").put("expeditionDetails", new JSONObject(true));
        cur.getJSONObject("inventory").put("exploreTool", new JSONObject(true));

        JSONArray pending = new JSONArray();

        JSONObject step0 = new JSONObject(true);
        step0.put("index", "e_0");
        step0.put("type", "GAME_INIT_MODE_RELIC");
        JSONObject c0 = new JSONObject(true);
        JSONObject initModeRelic = new JSONObject(true);
        JSONArray st0 = new JSONArray();
        st0.add(1);
        st0.add(4);
        initModeRelic.put("step", st0);
        JSONArray items0 = new JSONArray();
        items0.add(firstInitRelic(theme));
        initModeRelic.put("items", items0);
        c0.put("initModeRelic", initModeRelic);
        step0.put("content", c0);
        pending.add(step0);

        JSONObject step1 = new JSONObject(true);
        step1.put("index", "e_1");
        step1.put("type", "GAME_INIT_RELIC");
        JSONObject c1 = new JSONObject(true);
        JSONObject initRelic = new JSONObject(true);
        JSONArray st1 = new JSONArray();
        st1.add(2);
        st1.add(4);
        initRelic.put("step", st1);
        JSONObject items1 = new JSONObject(true);
        JSONArray bands = cfg.getJSONArray("initialBandRelic");
        String band = bands == null || bands.isEmpty() ? theme + "_band_1" : bands.getString(0);
        JSONObject b0 = new JSONObject(true);
        b0.put("id", band);
        b0.put("count", 1);
        items1.put("0", b0);
        initRelic.put("items", items1);
        c1.put("initRelic", initRelic);
        step1.put("content", c1);
        pending.add(step1);

        JSONObject step2 = new JSONObject(true);
        step2.put("index", "e_2");
        step2.put("type", "GAME_INIT_RECRUIT_SET");
        JSONObject c2 = new JSONObject(true);
        JSONObject initRecruitSet = new JSONObject(true);
        JSONArray st2 = new JSONArray();
        st2.add(3);
        st2.add(4);
        initRecruitSet.put("step", st2);
        initRecruitSet.put("option", cfg.getJSONArray("initialRecruitGroup"));
        c2.put("initRecruitSet", initRecruitSet);
        step2.put("content", c2);
        pending.add(step2);

        JSONObject step3 = new JSONObject(true);
        step3.put("index", "e_3");
        step3.put("type", "GAME_INIT_RECRUIT");
        JSONObject c3 = new JSONObject(true);
        JSONObject initRecruit = new JSONObject(true);
        JSONArray st3 = new JSONArray();
        st3.add(4);
        st3.add(4);
        initRecruit.put("step", st3);
        initRecruit.put("tickets", new JSONArray());
        initRecruit.put("showChar", new JSONArray());
        initRecruit.put("team", null);
        c3.put("initRecruit", initRecruit);
        step3.put("content", c3);
        pending.add(step3);

        player.put("pending", pending);
        player.put("toEnding", defaultEnding(theme));

        // rogue_4（萨卡兹）：构想系统 module.fragment —— 开局给几枚碎片，
        // 让 alchemy（解读）/ rerollNode（重掷）有资源可用
        seedFragmentModule(cur, theme);
        rl.put("current", cur);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter != null) {
            JSONObject record = themeOuter.getJSONObject("record");
            if (record == null) {
                record = new JSONObject(true);
                themeOuter.put("record", record);
            }
            record.put("last", ArknightsApplication.getTimestamp());
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject result = delta(cur);
        JSONObject outerOnly = new JSONObject(true);
        JSONObject themeOnly = new JSONObject(true);
        JSONObject recordOnly = new JSONObject(true);
        recordOnly.put("last", ArknightsApplication.getTimestamp());
        themeOnly.put("record", recordOnly);
        outerOnly.put(theme, themeOnly);
        result.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("rlv2")
                .put("outer", outerOnly);
        return result;
    }

    /** details.<theme>.relics 里第一个 INIT 类型的遗物 */
    private static String firstInitRelic(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        if (relics == null) {
            return theme + "_relic_a01";
        }
        for (String k : relics.keySet()) {
            JSONObject r = relics.getJSONObject(k);
            if (r != null && "INIT".equals(r.getString("type"))) {
                String id = r.getString("id");
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }
        }
        for (String k : relics.keySet()) {
            JSONObject r = relics.getJSONObject(k);
            if (r != null && r.getString("id") != null) {
                return r.getString("id");
            }
        }
        return theme + "_relic_a01";
    }

    /** 指定坐标（本层）是不是终点节点（zone_end） */
    private static boolean isZoneEndAt(JSONObject cur, JSONObject position) {
        if (position == null) {
            return false;
        }
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject z = zones == null ? null : zones.getJSONObject(String.valueOf(cursor.getIntValue("zone")));
        JSONObject nodes = z == null ? null : z.getJSONObject("nodes");
        if (nodes == null) {
            return false;
        }
        int x = position.getIntValue("x");
        int y = position.getIntValue("y");
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            if (pos != null && pos.getIntValue("x") == x && pos.getIntValue("y") == y) {
                return n.getBooleanValue("zone_end");
            }
        }
        return false;
    }

    /** 目标坐标是不是本层终点节点（nodes[*].zone_end == true） */
    private static boolean isZoneEnd(JSONObject cur, JSONObject to) {
        JSONObject map = cur.getJSONObject("map");
        JSONObject zones = map == null ? null : map.getJSONObject("zones");
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        int zone = cursor == null ? 0 : cursor.getIntValue("zone");
        JSONObject z = zones == null ? null : zones.getJSONObject(String.valueOf(zone));
        JSONObject nodes = z == null ? null : z.getJSONObject("nodes");
        if (nodes == null) {
            return false;
        }
        int x = to.getIntValue("x");
        int y = to.getIntValue("y");
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            if (pos == null || pos.getIntValue("x") != x || pos.getIntValue("y") != y) {
                continue;
            }
            return n.getBooleanValue("zone_end");
        }
        return false;
    }

    /**
     * 设置种子（参数预设 / 种子输入）。
     * 请求 {"seed":"U2i1e6oa183iOo4O90,rogue_1,0"} 或 {"seed":"<20位>"}
     * 只影响「还没生成过的层」，已生成的 zone 不变。
     */
    @PostMapping(value = {"/setSeed"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject setSeed(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject body,
                              HttpServletRequest request, HttpServletResponse response) {
        String seed = body == null ? null : body.getString("seed");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/setSeed seed=" + seed);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject game = cur.getJSONObject("game");
        if (game == null || "NONE".equals(game.getString("mode"))) {
            return bad("当前没有进行中的探索", 1);
        }
        if (seed == null || seed.isEmpty()) {
            return bad("缺少 seed", 1);
        }
        if (!com.hypergryph.arknights.core.function.RogueMapGen.isValidSeed(seed)) {
            return bad("预设参数格式不正确（应为 20 位 0-9AEIOUaeiou）", 1);
        }
        if (!seed.contains(",")) {
            seed = seed + "," + game.getString("theme") + "," + game.getIntValue("modeGrade");
        }
        game.put("seed", seed);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ───────────────────────────── 初始阶段 ─────────────────────────────

    @PostMapping(value = {"/chooseInitialRelic"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject chooseInitialRelic(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/chooseInitialRelic");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String select = body == null ? "0" : body.getString("select");
        JSONObject game = cur.getJSONObject("game");
        JSONObject cfg = initConfig(game.getString("theme"), game.getString("mode"), game.getIntValue("modeGrade"));
        if (cfg != null) {
            JSONArray bands = cfg.getJSONArray("initialBandRelic");
            int idx = 0;
            try {
                idx = Integer.parseInt(select);
            } catch (Exception ignored) {
            }
            if (bands != null && idx >= 0 && idx < bands.size()) {
                addRelic(cur, bands.getString(idx), 1);
            recInc(cur, "cntInitRelic", 1);   // 初始收藏品不计入「获得物品数」
            }
        }
        removePending(cur, "GAME_INIT_RELIC");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/chooseInitialRecruitSet"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject chooseInitialRecruitSet(@RequestHeader(value = "secret", required = false) String secret,
                                              @RequestBody(required = false) JSONObject body,
                                              HttpServletRequest request, HttpServletResponse response) {
        String select = body == null ? null : body.getString("select");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/chooseInitialRecruitSet select=" + select);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        removePending(cur, "GAME_INIT_RECRUIT_SET");

        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject recruit = inv.getJSONObject("recruit");
        if (recruit == null) {
            recruit = new JSONObject(true);
            inv.put("recruit", recruit);
        }
        String theme = cur.getJSONObject("game").getString("theme");
        String[] kinds = {"warrior", "support", "medic"};
        for (int i = 0; i < kinds.length; i++) {
            JSONObject ticket = new JSONObject(true);
            String index = "t_" + (i + 1);
            ticket.put("index", index);
            ticket.put("id", theme + "_recruit_ticket_" + kinds[i]);
            ticket.put("state", 1);
            ticket.put("list", new JSONArray());
            ticket.put("result", null);
            ticket.put("ts", ArknightsApplication.getTimestamp());
            ticket.put("from", "initial");
            ticket.put("mustExtra", 0);
            ticket.put("needAssist", true);
            recruit.put(index, ticket);
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/activeRecruitTicket"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject activeRecruitTicket(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        String id = body == null ? null : body.getString("id");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/activeRecruitTicket id=" + id);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject recruit = cur.getJSONObject("inventory").getJSONObject("recruit");
        if (recruit != null && id != null) {
            JSONObject ticket = recruit.getJSONObject(id);
            if (ticket != null) {
                ticket.put("state", 2);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/recruitChar"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject recruitChar(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        // 埋点：招募消耗希望（UsePopulation 任务）
        String ticketIndex = body == null ? null : body.getString("ticketIndex");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/recruitChar ticket=" + ticketIndex);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject troop = cur.getJSONObject("troop");
        JSONObject chars = troop.getJSONObject("chars");
        if (chars == null) {
            chars = new JSONObject(true);
            troop.put("chars", chars);
        }
        JSONArray outChars = new JSONArray();
        JSONObject newChar = buildChar(userData, 0);
        if (newChar != null) {
            int instId = chars.size();
            newChar.put("instId", String.valueOf(instId));
            newChar.put("troopInstId", String.valueOf(instId));
            chars.put(String.valueOf(instId), newChar);
            outChars.add(newChar);
            JSONObject recruit = cur.getJSONObject("inventory").getJSONObject("recruit");
            if (recruit != null && ticketIndex != null) {
                JSONObject ticket = recruit.getJSONObject(ticketIndex);
                if (ticket != null) {
                    ticket.put("state", 2);
                    ticket.put("list", new JSONArray());
                    ticket.put("result", newChar);
                }
            }
            removeRecruitPending(cur);
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = delta(cur);
        result.put("chars", outChars);
        return result;
    }

    /**
     * 助战招募：把好友的助战干员招进队伍（对应月度任务 HelpfulGainCharacter）。
     *
     * 请求体（按官服惯例，字段取宽松匹配）：{"charId":"char_xxx","assistUid":123,"ticketIndex":"t_3"}
     * 数据来源：quest 的 /quest/getAssistList（好友助战列表）。
     * 行为：加入 troop.chars → 券 state=2、result=该干员 → 移除 RECRUIT 待办 →
     *       cntRecruitAssist + 1、cntRecruitChar + 1。
     */
    @PostMapping(value = {"/recruitAssistChar"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject recruitAssistChar(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody(required = false) JSONObject body,
                                        HttpServletRequest request, HttpServletResponse response) {
        String charId = body == null ? null : body.getString("charId");
        long assistUid = body == null ? 0L : body.getLongValue("assistUid");
        String ticketIndex = body == null ? null : body.getString("ticketIndex");
        if (ticketIndex == null && body != null) {
            ticketIndex = body.getString("index");
        }
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/recruitAssistChar char="
                + charId + " assistUid=" + assistUid + " ticket=" + ticketIndex);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject troop = cur.getJSONObject("troop");
        JSONObject chars = troop.getJSONObject("chars");
        if (chars == null) {
            chars = new JSONObject(true);
            troop.put("chars", chars);
        }
        // 助战干员：优先从好友的存档里取真实数据，取不到就用自己同 id 干员模板
        JSONObject newChar = null;
        if (assistUid > 0) {
            List<Account> friendAccounts = userDao.queryAccountByUid(assistUid);
            if (!friendAccounts.isEmpty()) {
                JSONObject friendUser = JSONObject.parseObject(friendAccounts.get(0).getUser());
                if (charId != null) {
                    newChar = buildChar(friendUser, 0, charId);
                }
            }
        }
        if (newChar == null) {
            newChar = buildChar(userData, 0, charId);
        }
        JSONArray outChars = new JSONArray();
        if (newChar != null) {
            if (charId != null) {
                newChar.put("charId", charId);
            }
            newChar.put("type", "NORMAL");
            int instId = chars.size();
            newChar.put("instId", String.valueOf(instId));
            newChar.put("troopInstId", String.valueOf(instId));
            chars.put(String.valueOf(instId), newChar);
            outChars.add(newChar);

            JSONObject recruit = cur.getJSONObject("inventory").getJSONObject("recruit");
            if (recruit != null) {
                String useKey = ticketIndex;
                if (useKey == null && !recruit.isEmpty()) {
                    for (String k : recruit.keySet()) {
                        useKey = k;
                    }
                }
                if (useKey != null && recruit.getJSONObject(useKey) != null) {
                    JSONObject ticket = recruit.getJSONObject(useKey);
                    ticket.put("state", 2);
                    ticket.put("list", new JSONArray());
                    ticket.put("result", newChar);
                    ticket.put("from", "assist");
                }
            }
            removeRecruitPending(cur);
            recInc(cur, "cntRecruitAssist", 1);
            recInc(cur, "cntRecruitChar", 1);
            JSONObject prof = cur.getJSONObject("record").getJSONObject("cntRecruitProfession");
            if (prof == null) {
                prof = new JSONObject(true);
                cur.getJSONObject("record").put("cntRecruitProfession", prof);
            }
            String profession = professionOf(charId);
            if (profession != null) {
                prof.put(profession, prof.getIntValue(profession) + 1);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("chars", outChars);
        return result;
    }

    /** 干员职业（从 character_table 读），失败返回 null */
    private static String professionOf(String charId) {
        try {
            JSONObject table = ArknightsApplication.characterJson;
            JSONObject c = table == null ? null : table.getJSONObject(charId);
            return c == null ? null : c.getString("profession");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 关闭招募券（即招即用，不用则废）。
     * 请求 {"id":"t_19"} → 券 state 置 2、候选清空、结果 null，并移除 RECRUIT pending；
     * 券本身仍留在 inventory.recruit 里（官服实测：券总数不变）。
     */
    @PostMapping(value = {"/closeRecruitTicket"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject closeRecruitTicket(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        String id = body == null ? null : body.getString("id");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/closeRecruitTicket id=" + id);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject recruit = cur.getJSONObject("inventory").getJSONObject("recruit");
        if (recruit != null && id != null) {
            JSONObject ticket = recruit.getJSONObject(id);
            if (ticket != null) {
                ticket.put("state", 2);
                ticket.put("list", new JSONArray());
                ticket.put("result", null);
                ticket.put("ts", ArknightsApplication.getTimestamp());
            }
        }
        removeRecruitPending(cur);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 当前局主题 */
    private static String themeOf(JSONObject cur) {
        JSONObject game = cur.getJSONObject("game");
        return game == null ? "" : String.valueOf(game.getString("theme"));
    }

    /** 存券上限（官服实测：rogue_6 = 3、rogue_5 = 5；其余主题默认 3） */
    private static int stashedRecruitLimit(String theme) {
        if ("rogue_5".equals(theme)) {
            return 5;
        }
        if ("rogue_6".equals(theme)) {
            return 3;
        }
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        if (gameConst != null && gameConst.containsKey("stashedRecruitLimit")) {
            int v = gameConst.getIntValue("stashedRecruitLimit");
            if (v > 0) {
                return v;
            }
        }
        return 3;
    }

    /**
     * 存券（岁/黑流主题的「留存招募」）。
     * 官服实测（B服 2026-11-04）：券 state 1→2、list 清空、result null，券仍留在 inventory.recruit；
     * pending 里的 RECRUIT 被移除。同时把券的物品 id 记入 inventory.stashRecruit（供 useStashedTicket 取回）。
     */
    @PostMapping(value = {"/stashRecruitTicket"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject stashRecruitTicket(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        String index = body == null ? null : body.getString("index");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/stashRecruitTicket index=" + index);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject recruit = inv.getJSONObject("recruit");
        if (recruit != null && index != null && recruit.containsKey(index)) {
            JSONObject ticket = recruit.getJSONObject(index);
            String itemId = ticket.getString("id");
            ticket.put("state", 2);
            ticket.put("list", new JSONArray());
            ticket.put("result", null);
            ticket.put("ts", ArknightsApplication.getTimestamp());
            // 记入留存列表
            JSONArray stashed = inv.getJSONArray("stashRecruit");
            if (stashed == null) {
                stashed = new JSONArray();
                inv.put("stashRecruit", stashed);
            }
            // 官服实测：存进去的是「伺烛客券」变体（id 加 _candle 后缀），并带 stashRecruitLimit
            int limit = inv.getIntValue("stashRecruitLimit");
            if (limit <= 0) {
                limit = stashedRecruitLimit(themeOf(cur));
                inv.put("stashRecruitLimit", limit);
            }
            if (itemId != null && stashed.size() < limit) {
                stashed.add(itemId.endsWith("_candle") ? itemId : itemId + "_candle");
            }
            removeRecruitPending(cur);
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /**
     * 取用留存券。请求 {"id":"rogue_6_recruit_ticket_support"}（物品 id）
     * → 从 stashRecruit 移除，生成一张新的 state=1 招募券并弹 RECRUIT 待办。
     */
    @PostMapping(value = {"/useStashedTicket"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject useStashedTicket(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody(required = false) JSONObject body,
                                       HttpServletRequest request, HttpServletResponse response) {
        String itemId = body == null ? null : body.getString("id");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/useStashedTicket id=" + itemId);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject inv = cur.getJSONObject("inventory");
        JSONArray stashed = inv.getJSONArray("stashRecruit");
        boolean found = false;
        if (stashed != null && itemId != null) {
            for (int i = 0; i < stashed.size(); i++) {
                if (itemId.equals(stashed.getString(i))) {
                    stashed.remove(i);
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            addRecruitTicket(cur, itemId, false);
            // 弹 RECRUIT 待办
            JSONObject recruit = inv.getJSONObject("recruit");
            String lastIndex = null;
            if (recruit != null && !recruit.isEmpty()) {
                for (String k : recruit.keySet()) {
                    lastIndex = k;
                }
            }
            if (lastIndex != null) {
                JSONObject ticket = recruit.getJSONObject(lastIndex);
                ticket.put("state", 1);
                JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
                if (pending == null) {
                    pending = new JSONArray();
                    cur.getJSONObject("player").put("pending", pending);
                }
                JSONObject content = new JSONObject(true);
                JSONObject rec = new JSONObject(true);
                rec.put("ticket", lastIndex);
                content.put("recruit", rec);
                JSONObject p = new JSONObject(true);
                p.put("index", "e_6");
                p.put("type", "RECRUIT");
                p.put("content", content);
                pending.add(0, p);
                // 官服实测（027_24）：pending 同时带 USE_STASHED_TICKET
                JSONObject useStash = new JSONObject(true);
                useStash.put("index", "e_7");
                useStash.put("type", "USE_STASHED_TICKET");
                JSONObject uContent = new JSONObject(true);
                uContent.put("useStashedTicket", new JSONObject(true));
                useStash.put("content", uContent);
                pending.add(1, useStash);
                cur.getJSONObject("player").put("state", "PENDING");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 移除 pending 里的 RECRUIT 条目 */
    private static void removeRecruitPending(JSONObject cur) {
        JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
        if (pending == null) {
            return;
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            if ("RECRUIT".equals(pending.getJSONObject(i).getString("type"))) {
                pending.remove(i);
            }
        }
    }

    @PostMapping(value = {"/finishEvent"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject finishEvent(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/finishEvent");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()) {
            pending.remove(0);
        }
        if (pending == null || pending.isEmpty()) {
            JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
            if (zones == null || zones.isEmpty()) {
                enterFirstZone(cur);
            } else {
                player.put("state", "WAIT_MOVE");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ───────────────────────── gridZone（新主题网格地图）─────────────────────────

    /**
     * 网格地图移动/开战。请求体用 route:["303"]（节点 id 路径），
     * 官方语义：取 route 最后一个节点 id → 换成坐标 → 等同普通 moveTo。
     * 参考实现同样做法（convert_gridZone_req → rlv2_moveTo）。
     */
    @PostMapping(value = {"/gridZone/moveTo"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gridZoneMoveTo(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody(required = false) JSONObject body,
                                     HttpServletRequest request, HttpServletResponse response) {
        JSONObject translated = translateRoute(body, secret);
        if (translated == null) {
            return bad("route 无效", 1);
        }
        return moveInternal(secret, translated, false);
    }

    @PostMapping(value = {"/gridZone/moveAndBattleStart"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gridZoneMoveAndBattleStart(@RequestHeader(value = "secret", required = false) String secret,
                                                 @RequestBody(required = false) JSONObject body,
                                                 HttpServletRequest request, HttpServletResponse response) {
        JSONObject translated = translateRoute(body, secret);
        if (translated == null) {
            return bad("route 无效", 1);
        }
        return moveInternal(secret, translated, true);
    }

    /** route:["303"] → {to:{x,y}, stageId}（顺便扣网格行动力） */
    private static JSONObject translateRoute(JSONObject body, String secret) {
        if (body == null) {
            return null;
        }
        JSONArray route = body.getJSONArray("route");
        if (route == null || route.isEmpty()) {
            return null;
        }
        String nodeId = route.getString(route.size() - 1);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return null;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones == null ? null : zones.getJSONObject(String.valueOf(cursor.getIntValue("zone")));
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        JSONObject node = nodes == null ? null : nodes.getJSONObject(nodeId);
        if (node == null) {
            ArknightsApplication.LOGGER.warn("[rlv2] gridZone 找不到节点 {}", nodeId);
            return null;
        }
        // 扣网格行动力（步数 = route 长度 - 1）
        int steps = Math.max(1, route.size() - 1);
        JSONObject module = cur.getJSONObject("module");
        if (module != null) {
            JSONObject grid = module.getJSONObject("gridZone");
            if (grid != null) {
                int left = grid.getIntValue("step");
                grid.put("step", Math.max(0, left - steps));
            }
        }
        JSONObject out = new JSONObject(true);
        out.put("to", node.getJSONObject("pos"));
        if (body.getString("stageId") != null) {
            out.put("stageId", body.getString("stageId"));
        } else if (node.getString("stage") != null) {
            out.put("stageId", node.getString("stage"));
        }
        out.put("squad", body.getJSONObject("squad"));
        return out;
    }

    // ───────────────────────────── 局内推进 ─────────────────────────────

    @PostMapping(value = {"/moveTo"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject moveTo(@RequestHeader(value = "secret", required = false) String secret,
                             @RequestBody(required = false) JSONObject body,
                             HttpServletRequest request, HttpServletResponse response) {
        return moveInternal(secret, body, false);
    }

    @PostMapping(value = {"/moveAndBattleStart"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject moveAndBattleStart(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        return moveInternal(secret, body, true);
    }

    private JSONObject moveInternal(String secret, JSONObject body, boolean battle) {
        JSONObject to = body == null ? null : body.getJSONObject("to");
        ArknightsApplication.LOGGER.info("[rlv2] /" + (battle ? "moveAndBattleStart" : "moveTo") + " to=" + to);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        if (zones == null || zones.isEmpty()) {
            enterFirstZone(cur);
        }
        boolean advanced = false;
        JSONObject prevPos = player.getJSONObject("cursor").getJSONObject("position");
        boolean wasAtZoneEnd = isZoneEndAt(cur, prevPos);
        if (to != null) {
            player.getJSONObject("cursor").put("position", to);
            // 埋点：按节点类型计数（PassNodeType / PassNodeSkyType）
            String nodeTypeName = nodeTypeNameAt(cur, to);
            if (nodeTypeName != null) {
                recIncNodeType(cur, nodeTypeName);
            }
            JSONArray trace = player.getJSONArray("trace");
            if (trace == null) {
                trace = new JSONArray();
                player.put("trace", trace);
            }
            trace.add(to);
            // 官服语义：站在本层终点（zone_end 节点）后再移动一次 → 进入下一层
            if (!battle && wasAtZoneEnd) {
                int level = currentLevel(cur) + 1;
                enterZone(cur, level);
                player.getJSONObject("cursor").put("position", to);
                ArknightsApplication.LOGGER.info("[rlv2] 进入第 {} 层（zone {}）", level, 1000 + level - 1);
                advanced = true;
            }
        }
        if (!advanced) {
            if (battle) {
                player.put("state", "PENDING");
                player.put("pending", buildBattlePending(cur));
            } else {
                pushNodePending(cur);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        if (battle) {
            result.put("battleId", UUID.randomUUID().toString());
        }
        return result;
    }

    /** 按节点类型推送对应待办（SHOP / SCENE / TREASURE），参考官服 wire */
    private static void pushNodePending(JSONObject cur) {
        String theme = cur.getJSONObject("game").getString("theme");
        int type = cursorNodeType(cur);
        JSONObject player = cur.getJSONObject("player");
        int shopBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "SHOP");
        int incidentBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "INCIDENT");
        int restBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "REST");
        int treasureBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "TREASURE");
        if (type != 0 && type == shopBit) {
            player.put("pending", buildShopPending(cur));
            player.put("state", "PENDING");
        } else if (type != 0 && type == incidentBit) {
            JSONArray scene = buildScenePending(cur);
            if (scene != null) {
                player.put("pending", scene);
                player.put("state", "PENDING");
            } else {
                player.put("state", "WAIT_MOVE");
            }
        } else if (type != 0 && type == restBit) {
            JSONArray scene = buildScenePending(cur);
            if (scene != null) {
                player.put("pending", scene);
                player.put("state", "PENDING");
            } else {
                player.put("state", "WAIT_MOVE");
            }
        } else if (type != 0 && type == treasureBit) {
            // 古堡馈赠：直接给一件随机收藏品
            String relic = randomRelic(theme, cur);
            if (relic != null) {
                addRelic(cur, relic, 1);
            }
            player.put("state", "WAIT_MOVE");
        } else {
            player.put("state", "WAIT_MOVE");
        }
    }

    /** choices[choiceId].nextSceneId → 合法场景 id（存在于 choiceScenes 才返回） */
    private static String nextSceneId(String theme, String choiceId) {
        JSONObject details = themeDetails(theme);
        if (details == null || choiceId == null) {
            return null;
        }
        JSONObject choices = details.getJSONObject("choices");
        JSONObject scenes = details.getJSONObject("choiceScenes");
        JSONObject def = choices == null ? null : choices.getJSONObject(choiceId);
        if (def == null || scenes == null) {
            return null;
        }
        String next = def.getString("nextSceneId");
        if (next == null || next.isEmpty() || !scenes.containsKey(next)) {
            return null;
        }
        return next;
    }

    /**
     * 该事件选项是否直接引发战斗。
     * 官服数据判据（rogue_3 实证）：choices[id].icon == "battle" 或 displayData.funcIconId == "battle"。
     * 命中时官方下发 pending = [{type:"BATTLE", content:{"battle":{"state":0}}}]（state:0 = 尚未开始）。
     */
    private static boolean choiceTriggersBattle(String theme, String choiceId) {
        if (choiceId == null) {
            return false;
        }
        JSONObject details = themeDetails(theme);
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        JSONObject def = choices == null ? null : choices.getJSONObject(choiceId);
        if (def == null) {
            return false;
        }
        if ("battle".equalsIgnoreCase(def.getString("icon"))) {
            return true;
        }
        JSONObject display = def.getJSONObject("displayData");
        return display != null && "battle".equalsIgnoreCase(display.getString("funcIconId"));
    }

    /** 按指定场景 id 构造 SCENE 待办（选项按 scene 前缀匹配） */
    private static JSONArray buildSceneByPrefix(JSONObject cur, String sceneId) {
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (choices == null) {
            return null;
        }
        // 官服语义（参考实现实证）：只有 _enter 结尾的是决策页，其余是结果确认页，
        // 结果页仅提供 choice_leave，否则选项的 nextSceneId 会自引用成死循环
        if (!sceneId.endsWith("_enter")) {
            JSONObject leaveMap = new JSONObject(true);
            leaveMap.put("choice_leave", 1);
            JSONObject leaveExtra = new JSONObject(true);
            leaveExtra.put("rewards", new JSONArray());
            leaveExtra.put("costs", new JSONArray());
            JSONObject leaveAdditional = new JSONObject(true);
            leaveAdditional.put("choice_leave", leaveExtra);
            JSONObject sceneLeave = new JSONObject(true);
            sceneLeave.put("id", sceneId);
            sceneLeave.put("choices", leaveMap);
            sceneLeave.put("choiceAdditional", leaveAdditional);
            JSONObject contentLeave = new JSONObject(true);
            contentLeave.put("scene", sceneLeave);
            JSONObject pendingLeave = new JSONObject(true);
            pendingLeave.put("index", "e_6");
            pendingLeave.put("type", "SCENE");
            pendingLeave.put("content", contentLeave);
            JSONArray arrLeave = new JSONArray();
            arrLeave.add(pendingLeave);
            return arrLeave;
        }
        String prefix = sceneId.startsWith("scene_") ? sceneId.substring(6) : sceneId;
        if (prefix.endsWith("_enter")) {
            prefix = prefix.substring(0, prefix.length() - 6);
        }
        String choicePrefix = "choice_" + prefix;
        JSONObject choiceMap = new JSONObject(true);
        JSONObject additional = new JSONObject(true);
        for (String cid : choices.keySet()) {
            if (!cid.startsWith(choicePrefix)) {
                continue;
            }
            JSONObject def = choices.getJSONObject(cid);
            if ("HIDDEN".equals(def.getString("type"))) {
                continue;
            }
            choiceMap.put(cid, 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put(cid, extra);
        }
        if (choiceMap.isEmpty()) {
            choiceMap.put("choice_leave", 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put("choice_leave", extra);
        }
        JSONObject scene = new JSONObject(true);
        scene.put("id", sceneId);
        scene.put("choices", choiceMap);
        scene.put("choiceAdditional", additional);
        JSONObject content = new JSONObject(true);
        content.put("scene", scene);
        JSONObject pending = new JSONObject(true);
        pending.put("index", "e_6");
        pending.put("type", "SCENE");
        pending.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pending);
        return arr;
    }

    /** 商店待办（goods 5 件，价格 4~12 源石锭） */
    /** 生成 n 件商店商品（价格区间按参考实现：遗物 6~12 / 招募券 4~6 / 生命 3~5 / 源石锭 2~4） */
    private static JSONArray generateShopGoods(String theme, JSONObject details, String goldId, int n) {
        JSONArray goods = new JSONArray();
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        java.util.List<String> relicIds = new java.util.ArrayList<>();
        if (relics != null) {
            for (String k : relics.keySet()) {
                String id = relics.getJSONObject(k).getString("id");
                if (id != null && !id.contains("_band_")) {
                    relicIds.add(id);
                }
            }
        }
        JSONObject tickets = details == null ? null : details.getJSONObject("recruitTickets");
        java.util.List<String> ticketIds = tickets == null
                ? new java.util.ArrayList<>() : new java.util.ArrayList<>(tickets.keySet());
        java.util.List<String> tools = new java.util.ArrayList<>();
        if (details != null && details.getJSONObject("activeTools") != null) {
            tools.addAll(details.getJSONObject("activeTools").keySet());
        }
        for (int i = 0; i < n; i++) {
            String itemId;
            int price;
            int roll = RANDOM.nextInt(100);
            if (roll < 40 && !relicIds.isEmpty()) {
                itemId = relicIds.get(RANDOM.nextInt(relicIds.size()));
                price = 6 + RANDOM.nextInt(7);
            } else if (roll < 65 && !ticketIds.isEmpty()) {
                itemId = ticketIds.get(RANDOM.nextInt(ticketIds.size()));
                price = 4 + RANDOM.nextInt(3);
            } else if (roll < 78 && !tools.isEmpty()) {
                itemId = tools.get(RANDOM.nextInt(tools.size()));
                price = 6 + RANDOM.nextInt(5);
            } else if (roll < 90) {
                itemId = theme + "_hp";
                price = 3 + RANDOM.nextInt(3);
            } else {
                itemId = theme + "_gold";
                price = 2 + RANDOM.nextInt(3);
            }
            JSONObject good = new JSONObject(true);
            good.put("index", String.valueOf(i));
            good.put("itemId", itemId);
            good.put("count", 1);
            good.put("priceId", goldId);
            good.put("priceCount", price);
            good.put("origCost", price);
            good.put("displayPriceChg", false);
            good.put("_retainDiscount", 1);
            goods.add(good);
        }
        return goods;
    }

    private static JSONArray buildShopPending(JSONObject cur) {
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        String goldId = gameConst == null ? theme + "_gold" : gameConst.getString("goldItemId");
        int level = currentLevel(cur);

        JSONArray goods = generateShopGoods(theme, details, goldId, 5);
        Random rnd = null;
        JSONObject relics = null;
        java.util.List<String> relicIds = new java.util.ArrayList<>();
        if (relics != null) {
            for (String k : relics.keySet()) {
                String id = relics.getJSONObject(k).getString("id");
                if (id != null && !id.contains("_band_")) {
                    relicIds.add(id);
                }
            }
        }
        JSONObject tickets = details == null ? null : details.getJSONObject("recruitTickets");
        java.util.List<String> ticketIds = tickets == null
                ? new java.util.ArrayList<>() : new java.util.ArrayList<>(tickets.keySet());

        JSONObject bank = new JSONObject(true);
        bank.put("open", true);
        bank.put("canPut", true);
        bank.put("canWithdraw", true);
        bank.put("withdraw", 0);
        bank.put("cost", 1);
        JSONObject shop = new JSONObject(true);
        shop.put("bank", bank);
        shop.put("id", "zone_" + level + "_shop");
        shop.put("goods", goods);
        shop.put("_done", false);
        JSONObject content = new JSONObject(true);
        content.put("shop", shop);
        JSONObject pending = new JSONObject(true);
        pending.put("index", "e_3");
        // rogue_1 老主题用 SHOP，新主题用 BATTLE_SHOP
        pending.put("type", "rogue_1".equals(theme) || "rogue_2".equals(theme) || "rogue_3".equals(theme)
                ? "SHOP" : "BATTLE_SHOP");
        pending.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pending);
        return arr;
    }

    /** 事件场景待办：从 choiceScenes 里挑一个非 final 场景，选项按前缀匹配 */
    private static JSONArray buildScenePending(JSONObject cur) {
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (scenes == null || scenes.isEmpty() || choices == null) {
            return null;
        }
        // 主题前缀（新主题 scene_roN_*，老主题 scene_*）
        String themeNum = theme.substring(theme.length() - 1);
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (String id : scenes.keySet()) {
            if (id.contains("final") || id.contains("_fin") || id.contains("startbuff")) {
                continue;
            }
            if (!id.endsWith("_enter")) {
                continue;
            }
            if (id.startsWith("scene_ro" + themeNum + "_")) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            for (String id : scenes.keySet()) {
                if (id.contains("final") || id.contains("_fin") || id.contains("startbuff")) {
                    continue;
                }
                if (id.endsWith("_enter")) {
                    candidates.add(id);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String sceneId = candidates.get(RANDOM.nextInt(candidates.size()));
        String prefix = sceneId.startsWith("scene_") ? sceneId.substring(6) : sceneId;
        if (prefix.endsWith("_enter")) {
            prefix = prefix.substring(0, prefix.length() - 6);
        }
        String choicePrefix = "choice_" + prefix;

        JSONObject choiceMap = new JSONObject(true);
        JSONObject additional = new JSONObject(true);
        for (String cid : choices.keySet()) {
            if (!cid.startsWith(choicePrefix)) {
                continue;
            }
            String ctype = choices.getJSONObject(cid).getString("type");
            if ("HIDDEN".equals(ctype)) {
                continue;
            }
            choiceMap.put(cid, 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put(cid, extra);
        }
        if (choiceMap.isEmpty()) {
            choiceMap.put("choice_leave", 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put("choice_leave", extra);
        }
        JSONObject scene = new JSONObject(true);
        scene.put("id", sceneId);
        scene.put("choices", choiceMap);
        scene.put("choiceAdditional", additional);
        JSONObject content = new JSONObject(true);
        content.put("scene", scene);
        JSONObject pending = new JSONObject(true);
        pending.put("index", "e_6");
        pending.put("type", "SCENE");
        pending.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pending);
        return arr;
    }

    /** 战斗待办（对齐官服 wire：state=1 + 宝箱/陷阱满值 + 骰子序列 + 战斗种子） */
    private static JSONArray buildBattlePending(JSONObject cur) {
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        JSONObject boxInfo = new JSONObject(true);
        String badBox = gameConst == null ? null : gameConst.getString("badBoxTrapId");
        if (badBox != null && !badBox.isEmpty()) {
            boxInfo.put(badBox, 999);
        }
        JSONArray diceRoll = new JSONArray();
        Random rnd = new Random();
        for (int i = 0; i < 99; i++) {
            diceRoll.add(1 + rnd.nextInt(6));
        }
        JSONObject battleObj = new JSONObject(true);
        battleObj.put("state", 1);
        battleObj.put("chestCnt", 999);
        battleObj.put("goldTrapCnt", 999);
        battleObj.put("diceRoll", diceRoll);
        battleObj.put("boxInfo", boxInfo);
        battleObj.put("tmpChar", new JSONArray());
        battleObj.put("sanity", 0);
        battleObj.put("unKeepBuff", new JSONArray());
        battleObj.put("seed", rnd.nextInt(0x7FFFFFFF));
        battleObj.put("enemyHpInfo", new JSONObject(true));
        JSONObject content = new JSONObject(true);
        content.put("battle", battleObj);
        JSONObject pending = new JSONObject(true);
        pending.put("index", "e_4");
        pending.put("type", "BATTLE");
        pending.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pending);
        return arr;
    }

    /** 从 cursor 所在节点取 type 位值 */
    private static int cursorNodeType(JSONObject cur) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject cursor = player.getJSONObject("cursor");
        JSONObject position = cursor == null ? null : cursor.getJSONObject("position");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject z = zones == null ? null : zones.getJSONObject(String.valueOf(cursor.getIntValue("zone")));
        JSONObject nodes = z == null ? null : z.getJSONObject("nodes");
        if (position == null || nodes == null) {
            return 0;
        }
        int x = position.getIntValue("x");
        int y = position.getIntValue("y");
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            if (pos != null && pos.getIntValue("x") == x && pos.getIntValue("y") == y) {
                return n.getIntValue("type");
            }
        }
        return 0;
    }

    /** 按节点类型算经验（普通 12 / 紧急 24 / 首领 36） */
    private static int expGainFor(JSONObject cur, String theme) {
        int type = cursorNodeType(cur);
        int elite = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "BATTLE_ELITE");
        int boss = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "BATTLE_BOSS");
        if (boss != 0 && type == boss) {
            return 36;
        }
        if (elite != 0 && type == elite) {
            return 24;
        }
        return 12;
    }

    @PostMapping(value = {"/battleFinish"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battleFinish(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/battleFinish");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");

        JSONObject meta = null;
        String battleLog = body == null ? null : body.getString("battleLog");
        if (battleLog != null && !battleLog.isEmpty()) {
            try {
                meta = parseBattleLog(battleLog);
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[rlv2] battleLog 解析失败: {}", e.toString());
            }
        }

        JSONObject player = cur.getJSONObject("player");
        JSONObject prop = player.getJSONObject("property");
        int hpBefore = prop.getJSONObject("hp").getIntValue("current");
        boolean win = true;
        int killed = 0;
        int missed = 0;
        int hpDelta = 0;
        if (meta != null) {
            int gameResult = meta.getIntValue("gameResult");
            win = gameResult == 1;
            killed = meta.getIntValue("killedEnemiesCnt");
            missed = meta.getIntValue("missedEnemiesCnt");
            int remain = meta.getIntValue("remainingLifePoint");
            JSONObject hp = prop.getJSONObject("hp");
            hp.put("current", remain);
            hpDelta = remain - hpBefore;
            ArknightsApplication.LOGGER.info("[rlv2] 战斗结算 win={} 击杀={} 漏怪={} 剩余生命={} 关卡={}",
                    win, killed, missed, remain, meta.getString("stageId"));
        }

        // 累积本局战果（官方只在结算时一次性给出 record，这里自己记账）
        JSONObject record = cur.getJSONObject("record");
        if (record == null) {
            record = new JSONObject(true);
            record.put("brief", null);
            cur.put("record", record);
        }
        JSONObject acc = record.getJSONObject("acc");
        if (acc == null) {
            acc = new JSONObject(true);
            acc.put("battles", 0);
            acc.put("win", 0);
            acc.put("lose", 0);
            acc.put("kill", 0);
            acc.put("missed", 0);
            acc.put("perfect", 0);
            record.put("acc", acc);
        }
        acc.put("battles", acc.getIntValue("battles") + 1);
        acc.put("kill", acc.getIntValue("kill") + killed);
        acc.put("missed", acc.getIntValue("missed") + missed);
        // 按关卡 id 计数（月度任务 PassBattleStage 用）
        String stageKey = journalStageId(cur);
        if (stageKey != null) {
            JSONObject record0 = cur.getJSONObject("record");
            if (record0 == null) {
                record0 = new JSONObject(true);
                cur.put("record", record0);
            }
            JSONObject byStage = record0.getJSONObject("cntBattleByStage");
            if (byStage == null) {
                byStage = new JSONObject(true);
                record0.put("cntBattleByStage", byStage);
            }
            byStage.put(stageKey, byStage.getIntValue(stageKey) + 1);
        }
        if (win) {
            acc.put("win", acc.getIntValue("win") + 1);
            if (missed == 0) {
                acc.put("perfect", acc.getIntValue("perfect") + 1);
            }
        } else {
            acc.put("lose", acc.getIntValue("lose") + 1);
            // 官服：打输这一下就把本局简报写进 record.brief（over=true, success=0）
            record.put("brief", buildBrief(cur, 0));
        }
        // 官服实测 bp 在局内就涨（结算时 from==to），这里按战斗累积（系数待更多采样校准）
        if (win) {
            accumulateBp(userData, cur, 5);
            // 通关判定：本场是「险路恶敌（BOSS）」节点且获胜 → 记为通关
            String t = cur.getJSONObject("game").getString("theme");
            int bossBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(t, "BATTLE_BOSS");
            if (bossBit != 0 && cursorNodeType(cur) == bossBit) {
                record.put("clearedBoss", true);
                ArknightsApplication.LOGGER.info("[rlv2] 击败 BOSS，标记本局通关");
            }
        }

        // 经验与升级（官服余数模型：property.exp 是扣除后的余数）
        String theme = cur.getJSONObject("game").getString("theme");
        int expGain = win ? expGainFor(cur, theme) : 0;
        int popMaxUp = 0;
        if (expGain > 0) {
            JSONObject details = themeDetails(theme);
            JSONObject detailConst = details == null ? null : details.getJSONObject("detailConst");
            JSONObject levelTable = detailConst == null ? null : detailConst.getJSONObject("playerLevelTable");
            prop.put("exp", prop.getIntValue("exp") + expGain);
            if (levelTable != null) {
                while (true) {
                    JSONObject next = levelTable.getJSONObject(String.valueOf(prop.getIntValue("level") + 1));
                    if (next == null) {
                        break;
                    }
                    int need = next.getIntValue("exp");
                    if (prop.getIntValue("exp") < need) {
                        break;
                    }
                    prop.put("exp", prop.getIntValue("exp") - need);
                    prop.put("level", prop.getIntValue("level") + 1);
                    int up = next.getIntValue("populationUp");
                    popMaxUp += up;
                    JSONObject popMax = prop.getJSONObject("population");
                    popMax.put("max", popMax.getIntValue("max") + up);
                }
            }
        }

        // 战斗奖励：多选一（官服 wire：BATTLE_REWARD / battleReward）
        JSONObject details2 = themeDetails(theme);
        JSONObject gameConst2 = details2 == null ? null : details2.getJSONObject("gameConst");
        String goldId = gameConst2 == null ? theme + "_gold" : gameConst2.getString("goldItemId");
        // 官服 B服实测（2026-11-04 rogue_6）：三选一 = 金锭 / 招募券 / 收藏品
        JSONArray rewardOptions = new JSONArray();
        for (int gi = 0; gi < 3; gi++) {
            JSONObject group = new JSONObject(true);
            group.put("index", gi);
            JSONArray items = new JSONArray();
            JSONObject one = new JSONObject(true);
            one.put("sub", 0);
            if (gi == 0) {
                one.put("id", goldId);
                one.put("count", 2);
            } else if (gi == 1) {
                JSONObject detailsT = themeDetails(theme);
                JSONObject tickets = detailsT == null ? null : detailsT.getJSONObject("recruitTickets");
                if (tickets == null || tickets.isEmpty()) {
                    one.put("id", goldId);
                    one.put("count", 2);
                } else {
                    java.util.List<String> ids = new java.util.ArrayList<>(tickets.keySet());
                    one.put("id", ids.get(RANDOM.nextInt(ids.size())));
                    one.put("count", 1);
                }
            } else {
                String relic = randomRelic(theme, cur);
                one.put("id", relic == null ? goldId : relic);
                one.put("count", relic == null ? 3 : 1);
            }
            items.add(one);
            group.put("items", items);
            group.put("done", 0);
            rewardOptions.add(group);
        }
        JSONObject earn = new JSONObject(true);
        earn.put("damage", killed);
        earn.put("hp", hpDelta);
        earn.put("shield", 0);
        earn.put("exp", expGain);
        earn.put("populationMax", popMaxUp);
        earn.put("squadCapacity", 0);
        earn.put("maxHpUp", 0);
        JSONObject battleReward = new JSONObject(true);
        battleReward.put("earn", earn);
        battleReward.put("rewards", rewardOptions);
        battleReward.put("show", "1");
        battleReward.put("state", 0);
        battleReward.put("isPerfect", (meta != null && missed == 0) ? 1 : 0);
        JSONObject rewardContent = new JSONObject(true);
        rewardContent.put("battleReward", battleReward);
        JSONObject rewardPending = new JSONObject(true);
        rewardPending.put("index", "e_5");
        rewardPending.put("type", "BATTLE_REWARD");
        rewardPending.put("content", rewardContent);
        JSONArray pendingArr = new JSONArray();
        pendingArr.add(rewardPending);

        player.put("state", "PENDING");
        player.put("pending", pendingArr);
        userDao.setUserData(account.getUid(), userData);
        return delta(cur);
    }

    /** 解 battleLog：base64 → ZIP（单条 default_entry，deflate）→ JSON → journal.metadata */
    private static JSONObject parseBattleLog(String base64) throws Exception {
        byte[] zip = java.util.Base64.getDecoder().decode(base64);
        if (zip.length < 4 || zip[0] != 0x50 || zip[1] != 0x4b) {
            return null;
        }
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = zis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            JSONObject root = JSONObject.parseObject(bos.toString("UTF-8"));
            JSONObject journal = root == null ? null : root.getJSONObject("journal");
            return journal == null ? null : journal.getJSONObject("metadata");
        }
    }

    /** 生成一局的 brief（官服字段顺序/命名照抄） */
    private static JSONObject buildBrief(JSONObject cur, int success) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject prop = player.getJSONObject("property");
        JSONObject game = cur.getJSONObject("game");
        int level = currentLevel(cur);
        JSONObject brief = new JSONObject(true);
        brief.put("level", level);
        brief.put("over", true);
        brief.put("success", success);
        brief.put("ending", success == 1 && player.getString("toEnding") != null
                ? player.getString("toEnding") : "");
        brief.put("theme", game.getString("theme"));
        brief.put("mode", game.getString("mode"));
        brief.put("predefined", game.getString("predefined"));
        brief.put("band", "");
        brief.put("startTs", game.getLongValue("start"));
        brief.put("endTs", ArknightsApplication.getTimestamp());
        brief.put("endZoneId", "zone_" + level);
        JSONObject endProp = new JSONObject(true);
        endProp.put("hp", prop.getJSONObject("hp").getIntValue("current"));
        endProp.put("gold", prop.getIntValue("gold"));
        endProp.put("populationCost", prop.getJSONObject("population").getIntValue("cost"));
        endProp.put("populationMax", prop.getJSONObject("population").getIntValue("max"));
        brief.put("endProperty", endProp);
        brief.put("innerMission", false);
        brief.put("modeGrade", game.getIntValue("modeGrade"));
        brief.put("seed", game.getString("seed") == null ? "" : game.getString("seed"));
        return brief;
    }

    @PostMapping(value = {"/chooseBattleReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject chooseBattleReward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/chooseBattleReward");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        int idx = body == null ? 0 : body.getIntValue("index");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()
                && "BATTLE_REWARD".equals(pending.getJSONObject(0).getString("type"))) {
            JSONObject br = pending.getJSONObject(0).getJSONObject("content").getJSONObject("battleReward");
            JSONArray groups = br.getJSONArray("rewards");
            String theme = cur.getJSONObject("game").getString("theme");
            JSONObject details = themeDetails(theme);
            JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
            String goldId = gameConst == null ? theme + "_gold" : gameConst.getString("goldItemId");
            for (int gi = 0; gi < groups.size(); gi++) {
                JSONObject group = groups.getJSONObject(gi);
                if (group.getIntValue("index") != idx || group.getIntValue("done") == 1) {
                    continue;
                }
                group.put("done", 1);
                JSONArray items = group.getJSONArray("items");
                for (int k = 0; k < items.size(); k++) {
                    JSONObject it = items.getJSONObject(k);
                    String itemId = it.getString("id");
                    int count = it.getIntValue("count");
                    if (count <= 0) {
                        count = 1;
                    }
                    if (goldId != null && goldId.equals(itemId)) {
                        JSONObject prop = player.getJSONObject("property");
                        prop.put("gold", prop.getIntValue("gold") + count);
                        continue;
                    }
                    applyShopItem(userData, cur, itemId, count);
                    if (itemId != null && itemId.contains("recruit_ticket")) {
                        // 官服 B服实测：拿到招募券会立刻弹 RECRUIT 待办
                        JSONObject recruit = cur.getJSONObject("inventory").getJSONObject("recruit");
                        if (recruit != null && !recruit.isEmpty()) {
                            String lastKey = null;
                            for (String tk0 : recruit.keySet()) {
                                lastKey = tk0;
                            }
                            JSONObject tk = recruit.getJSONObject(lastKey);
                            if (tk != null) {
                                tk.put("state", 1);
                                pushRecruitPending(cur, lastKey);
                            }
                        }
                    }
                }
                break;
            }
        }
        userDao.setUserData(account.getUid(), userData);
        return delta(cur);
    }

    @PostMapping(value = {"/finishBattleReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject finishBattleReward(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/finishBattleReward");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        player.put("pending", new JSONArray());
        player.put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        return delta(cur);
    }

    @PostMapping(value = {"/selectChoice"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject selectChoice(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String choice = body == null ? null : body.getString("choice");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/selectChoice choice=" + choice);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONArray items = applyChoiceEffect(userData, cur, choice);
        JSONObject player = cur.getJSONObject("player");

        String theme = cur.getJSONObject("game").getString("theme");

        // 官服实测（024_26 rogue_3）：选项本身是「进入战斗」→ 推 BATTLE 待办（state:0）
        if (choiceTriggersBattle(theme, choice)) {
            JSONObject battleContent = new JSONObject(true);
            JSONObject battle = new JSONObject(true);
            battle.put("state", 0);
            battleContent.put("battle", battle);
            JSONObject p = new JSONObject(true);
            p.put("index", "e_22");
            p.put("type", "BATTLE");
            p.put("content", battleContent);
            JSONArray arr = new JSONArray();
            arr.add(p);
            player.put("pending", arr);
            player.put("state", "PENDING");
            userDao.setUserData(account.getUid(), userData);
            com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
            JSONObject battleResult = delta(cur);
            battleResult.put("items", items);
            return battleResult;
        }

        // 官方场景链：选项的 nextSceneId 指向下一段场景（choices 表）
        String nextScene = nextSceneId(theme, choice);
        if (nextScene != null) {
            JSONArray scene = buildSceneByPrefix(cur, nextScene);
            if (scene != null) {
                player.put("pending", scene);
                player.put("state", "PENDING");
                userDao.setUserData(account.getUid(), userData);
                com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
                JSONObject result = delta(cur);
                result.put("items", items);
                return result;
            }
        }

        boolean leave = isLeaveChoice(cur, choice);
        if (leave) {
            player.put("pending", new JSONArray());
            player.put("state", "WAIT_MOVE");
        } else {
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", items);
        return result;
    }

    /** 抓包实测到的选项效果（精确值），表里只有描述文本时用它兜底 */
    private static final java.util.Map<String, Object[]> CHOICE_SAMPLES = new java.util.HashMap<>();

    static {
        // choiceId → {itemId, count}
        CHOICE_SAMPLES.put("choice_hp_1", new Object[]{"rogue_1_hp", 3});
        CHOICE_SAMPLES.put("choice_rest_1", new Object[]{"rogue_1_hp", 5});
        CHOICE_SAMPLES.put("choice_ent1_3", new Object[]{"rogue_1_gold", 3});
        CHOICE_SAMPLES.put("choice_blood2_1", new Object[]{"rogue_1_gold", 20});
        CHOICE_SAMPLES.put("choice_up2_1", new Object[]{"rogue_1_upgrade_ticket_all", 1});
        CHOICE_SAMPLES.put("choice_recruit1_2", new Object[]{"rogue_1_recruit_ticket_all", 1});
        CHOICE_SAMPLES.put("choice_treasure1_1", new Object[]{"@relic", 1});
    }

    private static boolean isLeaveChoice(JSONObject cur, String choice) {
        if (choice == null) {
            return true;
        }
        if ("choice_leave".equals(choice)) {
            return true;
        }
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject def = details == null || details.getJSONObject("choices") == null ? null
                : details.getJSONObject("choices").getJSONObject(choice);
        return def != null && "LEAVE".equals(def.getString("type"));
    }

    /** 应用选项效果，返回 items（与官服 items 结构一致：{id,count} 或 {type,id,count}） */
    private static JSONArray applyChoiceEffect(JSONObject userData, JSONObject cur, String choice) {
        JSONArray items = new JSONArray();
        if (choice == null) {
            return items;
        }
        String theme = cur.getJSONObject("game").getString("theme");

        // ① 表驱动结算（覆盖六主题 2000+ 选项，来自描述文本 + 抓包真值）
        JSONObject themeEffects = choiceEffects().getJSONObject(theme);
        JSONObject eff = themeEffects == null ? null : themeEffects.getJSONObject(choice);
        if (eff != null) {
            JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
            JSONObject detailsC = themeDetails(theme);
            JSONObject choiceDef = detailsC == null || detailsC.getJSONObject("choices") == null ? null
                    : detailsC.getJSONObject("choices").getJSONObject(choice);
            String choiceDesc = choiceDef == null ? "" : String.valueOf(choiceDef.getString("description"));

            // 概率类选项：描述里带「几率」→ 按 config.rogue.choiceChanceSuccess 掷骰
            boolean chanceFail = false;
            if (choiceDesc.contains("几率")) {
                double rate = choiceChanceSuccess();
                chanceFail = RANDOM.nextDouble() >= rate;
            }

            JSONArray costs = eff.getJSONArray("costs");
            if (costs != null) {
                for (int i = 0; i < costs.size(); i++) {
                    JSONObject c = costs.getJSONObject(i);
                    String id = c.getString("id");
                    int n = c.getIntValue("count");
                    if (n <= 0) {
                        n = 1;
                    }
                    if (id != null && id.endsWith("_gold")) {
                        prop.put("gold", Math.max(0, prop.getIntValue("gold") - n));
                        recInc(cur, "cntUseGold", n);
                        JSONObject rec = cur.getJSONObject("record");
                        if (rec == null) {
                            rec = new JSONObject(true);
                            rec.put("brief", null);
                            cur.put("record", rec);
                        }
                        rec.put("lastSpendGold", n);   // 供「获得消耗的所有源石锭」使用
                    } else if (id != null && id.endsWith("_hp")) {
                        JSONObject hp = prop.getJSONObject("hp");
                        hp.put("current", Math.max(0, hp.getIntValue("current") - n));
                    } else if (id != null && id.endsWith("_population")) {
                        JSONObject pop = prop.getJSONObject("population");
                        pop.put("max", Math.max(0, pop.getIntValue("max") - n));
                    }
                }
            }
            // 动态量：描述「获得消耗的所有源石锭」→ 退还本次消耗的源石锭
            boolean refundSpent = choiceDesc.contains("获得消耗的所有源石锭");
            JSONArray rewards = eff.getJSONArray("rewards");
            if (refundSpent) {
                JSONObject rec = cur.getJSONObject("record");
                int spent = rec == null ? 0 : rec.getIntValue("lastSpendGold");
                if (spent > 0) {
                    prop.put("gold", prop.getIntValue("gold") + spent);
                    JSONObject one = new JSONObject(true);
                    one.put("id", theme + "_gold");
                    one.put("count", spent);
                    items.add(one);
                    return items;
                }
            }
            if (chanceFail) {
                ArknightsApplication.LOGGER.info("[rlv2] 概率选项 {} 未命中，只结算消耗", choice);
                return items;
            }
            if (rewards != null) {
                for (int i = 0; i < rewards.size(); i++) {
                    JSONObject it = rewards.getJSONObject(i);
                    String id = it.getString("id");
                    int n = it.getIntValue("count");
                    if (n <= 0) {
                        n = 1;
                    }
                    if (id == null) {
                        continue;
                    }
                    applyShopItem(userData, cur, id, n);
                    JSONObject one = new JSONObject(true);
                    one.put("id", id);
                    one.put("count", n);
                    items.add(one);
                }
            }
            if (eff.getBooleanValue("randomRelic")) {
                String relic = randomRelic(theme, cur);
                if (relic != null) {
                    addRelic(cur, relic, 1);
                    JSONObject one = new JSONObject(true);
                    one.put("id", relic);
                    one.put("count", 1);
                    items.add(one);
                }
            }
            if (eff.getBooleanValue("randomTicket")) {
                JSONObject details2 = themeDetails(theme);
                JSONObject tickets = details2 == null ? null : details2.getJSONObject("recruitTickets");
                if (tickets != null && !tickets.isEmpty()) {
                    java.util.List<String> ids = new java.util.ArrayList<>(tickets.keySet());
                    String tid = ids.get(RANDOM.nextInt(ids.size()));
                    addRecruitTicket(cur, tid, false);
                    JSONObject one = new JSONObject(true);
                    one.put("id", tid);
                    one.put("count", 1);
                    items.add(one);
                }
            }
            if (!items.isEmpty() || (costs != null && !costs.isEmpty())) {
                return items;
            }
        }

        JSONObject details = themeDetails(theme);
        JSONObject def = details == null || details.getJSONObject("choices") == null ? null
                : details.getJSONObject("choices").getJSONObject(choice);
        JSONObject display = def == null ? null : def.getJSONObject("displayData");
        String itemId = display == null ? null : display.getString("itemId");
        String func = display == null ? null : display.getString("funcIconId");
        String desc = def == null ? null : def.getString("description");

        String sampleItem = null;
        int count = 0;
        Object[] sample = CHOICE_SAMPLES.get(choice);
        if (sample != null) {
            sampleItem = (String) sample[0];
            count = (Integer) sample[1];
        } else if (desc != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<@[^>]*get>(\\d+)</>").matcher(desc);
            if (m.find()) {
                count = Integer.parseInt(m.group(1));
            }
        }
        if (sampleItem != null) {
            itemId = sampleItem;
        } else if (itemId == null && "hp".equals(func)) {
            itemId = theme + "_hp";
        } else if (itemId == null && "gold".equals(func)) {
            itemId = theme + "_gold";
        } else if (itemId == null && "relic".equals(func)) {
            itemId = "@relic";
        }

        // 消耗（描述里 消耗<@ro.lose>N</>）
        if (desc != null) {
            java.util.regex.Matcher lose = java.util.regex.Pattern
                    .compile("<@[^>]*lose>(\\d+)</>").matcher(desc);
            if (lose.find()) {
                int cost = Integer.parseInt(lose.group(1));
                JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
                prop.put("gold", Math.max(0, prop.getIntValue("gold") - cost));
            }
        }

        if (itemId == null || itemId.isEmpty()) {
            return items;
        }
        JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
        if (itemId.endsWith("_hp")) {
            JSONObject hp = prop.getJSONObject("hp");
            hp.put("current", hp.getIntValue("current") + Math.max(count, 1));
            JSONObject it = new JSONObject(true);
            it.put("id", itemId);
            it.put("count", Math.max(count, 1));
            items.add(it);
        } else if (itemId.endsWith("_gold")) {
            prop.put("gold", prop.getIntValue("gold") + Math.max(count, 1));
            JSONObject it = new JSONObject(true);
            it.put("id", itemId);
            it.put("count", Math.max(count, 1));
            items.add(it);
        } else if ("@relic".equals(itemId) || itemId.contains("_relic")) {
            if ("@relic".equals(itemId)) {
                String relic = randomRelic(theme, cur);
                if (relic != null) {
                    addRelic(cur, relic, 1);
                    JSONObject it = new JSONObject(true);
                    it.put("id", relic);
                    it.put("count", 1);
                    items.add(it);
                }
            } else {
                addRelic(cur, itemId, Math.max(count, 1));
                JSONObject it = new JSONObject(true);
                it.put("id", itemId);
                it.put("count", Math.max(count, 1));
                items.add(it);
            }
        } else if (itemId.contains("recruit_ticket")) {
            addRecruitTicket(cur, itemId, false);
            JSONObject it = new JSONObject(true);
            it.put("id", itemId);
            it.put("count", Math.max(count, 1));
            items.add(it);
        } else if (itemId.contains("upgrade_ticket")) {
            addRecruitTicket(cur, itemId, true);
            JSONObject it = new JSONObject(true);
            it.put("id", itemId);
            it.put("count", Math.max(count, 1));
            items.add(it);
        } else {
            JSONObject inv = cur.getJSONObject("inventory");
            JSONObject consumable = inv.getJSONObject("consumable");
            if (consumable == null) {
                consumable = new JSONObject(true);
                inv.put("consumable", consumable);
            }
            consumable.put(itemId, consumable.getIntValue(itemId) + Math.max(count, 1));
            JSONObject it = new JSONObject(true);
            it.put("id", itemId);
            it.put("count", Math.max(count, 1));
            items.add(it);
        }
        return items;
    }

    /** 随机取一个本主题、还没拥有的收藏品 */
    private static String randomRelic(String theme, JSONObject cur) {
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        if (relics == null || relics.isEmpty()) {
            return null;
        }
        JSONObject owned = cur.getJSONObject("inventory").getJSONObject("relic");
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String k : relics.keySet()) {
            String id = relics.getJSONObject(k).getString("id");
            if (id == null || id.contains("_band_") || id.contains("_relic_a")) {
                continue;
            }
            boolean has = false;
            if (owned != null) {
                for (String ok : owned.keySet()) {
                    if (id.equals(owned.getJSONObject(ok).getString("id"))) {
                        has = true;
                        break;
                    }
                }
            }
            if (!has) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids.get(RANDOM.nextInt(ids.size()));
    }

    private static void addRecruitTicket(JSONObject cur, String ticketId, boolean upgrade) {
        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject recruit = inv.getJSONObject("recruit");
        if (recruit == null) {
            recruit = new JSONObject(true);
            inv.put("recruit", recruit);
        }
        int i = recruit.size() + 1;
        String index = "t_" + i;
        while (recruit.containsKey(index)) {
            i++;
            index = "t_" + i;
        }
        JSONObject ticket = new JSONObject(true);
        ticket.put("index", index);
        ticket.put("id", ticketId);
        ticket.put("state", 1);
        ticket.put("list", new JSONArray());
        ticket.put("result", null);
        ticket.put("ts", ArknightsApplication.getTimestamp());
        ticket.put("from", upgrade ? "upgrade" : "choice");
        ticket.put("mustExtra", 0);
        ticket.put("needAssist", false);
        recruit.put(index, ticket);
    }

    @PostMapping(value = {"/shopAction"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject shopAction(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        boolean leave = body != null && body.getIntValue("leave") == 1;
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/shopAction leave=" + leave);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONArray bought = body == null ? null : body.getJSONArray("buy");
        JSONArray recycled = body == null ? null : body.getJSONArray("recycle");
        if (recycled != null && !recycled.isEmpty()) {
            // 卖出：官方数据未抓到样本，这里按「半价回收遗物」推断（每个 3 源石锭）
            JSONObject propR = cur.getJSONObject("player").getJSONObject("property");
            JSONObject relicInv = cur.getJSONObject("inventory").getJSONObject("relic");
            for (int i = 0; i < recycled.size(); i++) {
                String key = recycled.getString(i);
                if (relicInv != null && relicInv.containsKey(key)) {
                    relicInv.remove(key);
                    propR.put("gold", propR.getIntValue("gold") + 3);
                    recInc(cur, "cntShopSell", 1);
                    ArknightsApplication.LOGGER.info("[rlv2] 卖出收藏品 {} → +3 源石锭", key);
                }
            }
        }
        JSONObject shop = findShop(cur);
        if (shop != null && bought != null && !bought.isEmpty()) {
            JSONArray goods = shop.getJSONArray("goods");
            JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
            for (int i = 0; i < bought.size(); i++) {
                String idx = bought.getString(i);
                JSONObject good = null;
                if (goods != null) {
                    for (int k = 0; k < goods.size(); k++) {
                        if (idx.equals(goods.getJSONObject(k).getString("index"))) {
                            good = goods.getJSONObject(k);
                            break;
                        }
                    }
                }
                if (good == null) {
                    ArknightsApplication.LOGGER.warn("[rlv2] 商店里没有 index={} 的商品", idx);
                    continue;
                }
                int count = good.getIntValue("count");
                int price = good.getIntValue("priceCount");
                if (count <= 0) {
                    ArknightsApplication.LOGGER.warn("[rlv2] 商品 {} 已售罄", idx);
                    continue;
                }
                if (prop.getIntValue("gold") < price) {
                    ArknightsApplication.LOGGER.warn("[rlv2] 源石锭不足，买不了 {}", idx);
                    continue;
                }
                prop.put("gold", prop.getIntValue("gold") - price);
                good.put("count", count - 1);
                recInc(cur, "cntUseGold", price);
                recInc(cur, "cntShopBuy", 1);
                String itemId = good.getString("itemId");
                applyShopItem(userData, cur, itemId, 1);
                ArknightsApplication.LOGGER.info("[rlv2] 商店购买 {} → {}（-{} 源石锭）", itemId, price, price);
            }
        }
        JSONObject player = cur.getJSONObject("player");
        if (leave) {
            removePending(cur, "SHOP");
            // 官服 B服 实测（024_22 rogue_3）：离开商店后推 PREDICT 待办 {"predict":{}}
            String shopTheme = cur.getJSONObject("game").getString("theme");
            if (hasPredictModule(cur)) {
                JSONObject predict = new JSONObject(true);
                JSONObject content = new JSONObject(true);
                content.put("predict", predict);
                JSONObject p = new JSONObject(true);
                p.put("index", "e_19");
                p.put("type", "PREDICT");
                p.put("content", content);
                JSONArray pending = player.getJSONArray("pending");
                if (pending == null) {
                    pending = new JSONArray();
                    player.put("pending", pending);
                }
                pending.add(p);
                player.put("state", "PENDING");
                ArknightsApplication.LOGGER.info("[rlv2] 离开商店 → 推出 PREDICT 待办（theme={}）", shopTheme);
            }
            player.put("state", "WAIT_MOVE");
        } else {
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", new JSONArray());
        return result;
    }

    /** 把某个券压成 RECRUIT 待办（放到 pending 最前） */
    private static void pushRecruitPending(JSONObject cur, String ticketKey) {
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending == null) {
            pending = new JSONArray();
            player.put("pending", pending);
        }
        for (int i = 0; i < pending.size(); i++) {
            JSONObject p = pending.getJSONObject(i);
            if ("RECRUIT".equals(p.getString("type"))) {
                return;   // 已有招募待办，避免重复
            }
        }
        JSONObject rec = new JSONObject(true);
        rec.put("ticket", ticketKey);
        JSONObject content = new JSONObject(true);
        content.put("recruit", rec);
        JSONObject p = new JSONObject(true);
        p.put("index", "e_6");
        p.put("type", "RECRUIT");
        p.put("content", content);
        pending.add(0, p);
        player.put("state", "PENDING");
    }

    /**
     * 该主题是否有「预测/视界」系统。
     * 判据（数据驱动）：module.vision/predict 存在，或 gameConst.dsPredictTips 非空
     * （rogue_3/4/5/6 有该字段，rogue_1/2 没有 → 官方也只有这四主题会推 PREDICT）。
     */
    private static boolean hasPredictModule(JSONObject cur) {
        JSONObject module = cur.getJSONObject("module");
        if (module != null && (module.getJSONObject("vision") != null || module.getJSONObject("predict") != null)) {
            return true;
        }
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        String tips = gameConst == null ? null : gameConst.getString("dsPredictTips");
        return tips != null && !tips.isEmpty();
    }

    /**
     * 从 pending 里找正在进行的商店。
     * 官服实测（B服 2026-11-04）：SHOP 用 content.shop，BATTLE_SHOP 用 content.battleShop。
     */
    private static JSONObject findShop(JSONObject cur) {
        JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
        if (pending == null) {
            return null;
        }
        for (int i = 0; i < pending.size(); i++) {
            JSONObject p = pending.getJSONObject(i);
            String type = p.getString("type");
            if (!"SHOP".equals(type) && !"BATTLE_SHOP".equals(type)) {
                continue;
            }
            JSONObject content = p.getJSONObject("content");
            if (content == null) {
                continue;
            }
            JSONObject shop = content.getJSONObject("shop");
            if (shop == null) {
                shop = content.getJSONObject("battleShop");
            }
            if (shop != null) {
                return shop;
            }
        }
        return null;
    }

    /** 购买/获得一件物品，落到对应的存档位置（优先按 gameConst 的物品 id 映射） */
    private static void applyShopItem(JSONObject userData, JSONObject cur, String itemId, int count) {
        if (itemId == null) {
            return;
        }
        if (itemId.contains("_relic")) {
            addRelic(cur, itemId, Math.max(count, 1));
            return;
        }
        if (itemId.contains("recruit_ticket") || itemId.contains("upgrade_ticket")) {
            addRecruitTicket(cur, itemId, itemId.contains("upgrade_ticket"));
            return;
        }
        // gameConst 映射：hp / hpmax / shield / gold / hope / capacity / exp
        String theme = cur.getJSONObject("game") == null ? null : cur.getJSONObject("game").getString("theme");
        JSONObject details = theme == null ? null : themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
        if (gameConst != null) {
            int n = count;
            if (itemId.equals(gameConst.getString("hpItemId"))) {
                JSONObject hp = prop.getJSONObject("hp");
                int v = hp.getIntValue("current") + n;
                if (hp.getIntValue("max") > 0) {
                    v = Math.min(v, hp.getIntValue("max"));
                }
                hp.put("current", Math.max(0, v));
                return;
            }
            if (itemId.equals(gameConst.getString("maxHpItemId"))) {
                JSONObject hp = prop.getJSONObject("hp");
                hp.put("max", Math.max(0, hp.getIntValue("max") + n));
                if (n > 0) {
                    hp.put("current", hp.getIntValue("current") + n);
                }
                return;
            }
            if (itemId.equals(gameConst.getString("shieldItemId"))) {
                prop.put("shield", Math.max(0, prop.getIntValue("shield") + n));
                return;
            }
            if (itemId.equals(gameConst.getString("goldItemId"))) {
                prop.put("gold", Math.max(0, prop.getIntValue("gold") + n));
                return;
            }
            if (itemId.equals(gameConst.getString("populationItemId"))) {
                JSONObject pop = prop.getJSONObject("population");
                pop.put("max", Math.max(0, pop.getIntValue("max") + n));
                return;
            }
            if (itemId.equals(gameConst.getString("squadCapacityItemId"))) {
                prop.put("capacity", Math.max(0, prop.getIntValue("capacity") + n));
                return;
            }
            if (itemId.equals(gameConst.getString("expItemId"))) {
                prop.put("exp", Math.max(0, prop.getIntValue("exp") + n));
                return;
            }
        }
        // 兜底：按后缀判断
        if (itemId.endsWith("_hp")) {
            JSONObject hp = prop.getJSONObject("hp");
            hp.put("current", hp.getIntValue("current") + Math.max(count, 1));
        } else if (itemId.endsWith("_gold")) {
            prop.put("gold", prop.getIntValue("gold") + Math.max(count, 1));
        } else {
            JSONObject inv = cur.getJSONObject("inventory");
            JSONObject consumable = inv.getJSONObject("consumable");
            if (consumable == null) {
                consumable = new JSONObject(true);
                inv.put("consumable", consumable);
            }
            consumable.put(itemId, consumable.getIntValue(itemId) + Math.max(count, 1));
        }
    }

    /**
     * 局内累积 bp / 天赋点。官服实测（B服 2026-11-04 rogue_6 结算）：
     *   score Δ=245，bp.point Δ=274 → bp 增量带收藏品加成（bp_reward_increase）
     *   pointOwned：rogue_1 用 bp/10，其余主题用 score/200（六主题实测比值 200~202）
     */
    /** 取某坐标节点的类型名（BATTLE_NORMAL / INCIDENT / SHOP …） */
    private static String nodeTypeNameAt(JSONObject cur, JSONObject pos) {
        if (pos == null) {
            return null;
        }
        String theme = cur.getJSONObject("game").getString("theme");
        java.util.List<String> names = com.hypergryph.arknights.core.function.RogueMapGen.nodeTypeNames(theme);
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones == null ? null
                : zones.getJSONObject(String.valueOf(cur.getJSONObject("player").getJSONObject("cursor").getIntValue("zone")));
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null) {
            return null;
        }
        for (String key : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(key);
            JSONObject p = n.getJSONObject("pos");
            if (p == null) {
                continue;
            }
            if (p.getIntValue("x") == pos.getIntValue("x") && p.getIntValue("y") == pos.getIntValue("y")) {
                int type = n.getIntValue("type");
                if (names != null) {
                    for (int i = 0; i < names.size(); i++) {
                        if ((1 << i) == type) {
                            return names.get(i);
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** 当前战斗待办里的关卡 id（用于 PassBattleStage 任务统计） */
    private static String journalStageId(JSONObject cur) {
        JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
        if (pending == null) {
            return null;
        }
        for (int i = 0; i < pending.size(); i++) {
            JSONObject p = pending.getJSONObject(i);
            if (!"BATTLE".equals(p.getString("type"))) {
                continue;
            }
            JSONObject content = p.getJSONObject("content");
            JSONObject battle = content == null ? null : content.getJSONObject("battle");
            if (battle == null) {
                continue;
            }
            String stage = battle.getString("stageId");
            if (stage == null) {
                stage = battle.getString("stage");
            }
            if (stage != null) {
                return stage;
            }
        }
        return null;
    }

    /** record 计数器 +delta（不存在则建），供月度任务埋点用 */
    private static void recInc(JSONObject cur, String key, int delta) {
        if (cur == null || delta == 0) {
            return;
        }
        JSONObject record = cur.getJSONObject("record");
        if (record == null) {
            record = new JSONObject(true);
            cur.put("record", record);
        }
        record.put(key, record.getIntValue(key) + delta);
    }

    /** 按节点类型名计数（PassNodeType / PassNodeSkyType 用） */
    private static void recIncNodeType(JSONObject cur, String typeName) {
        if (cur == null || typeName == null) {
            return;
        }
        JSONObject record = cur.getJSONObject("record");
        if (record == null) {
            record = new JSONObject(true);
            cur.put("record", record);
        }
        JSONObject byType = record.getJSONObject("cntNodeByType");
        if (byType == null) {
            byType = new JSONObject(true);
            record.put("cntNodeByType", byType);
        }
        byType.put(typeName, byType.getIntValue(typeName) + 1);
    }

    private static void accumulateBp(JSONObject userData, JSONObject cur, int score) {
        if (score <= 0) {
            return;
        }
        String theme = cur.getJSONObject("game") == null ? null : cur.getJSONObject("game").getString("theme");
        if (theme == null || theme.isEmpty()) {
            return;
        }
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter == null) {
            return;
        }
        double bonus = bpBonus(cur, theme);
        int bpGain = (int) Math.round(score * (1.0 + bonus));

        JSONObject bp = themeOuter.getJSONObject("bp");
        if (bp == null) {
            bp = new JSONObject(true);
            bp.put("point", 0);
            bp.put("reward", new JSONObject(true));
            themeOuter.put("bp", bp);
        }
        bp.put("point", bp.getIntValue("point") + bpGain);

        JSONObject buff = themeOuter.getJSONObject("buff");
        if (buff == null) {
            buff = new JSONObject(true);
            buff.put("pointOwned", 0);
            buff.put("pointCost", 0);
            buff.put("unlocked", new JSONObject(true));
            buff.put("score", 0);
            themeOuter.put("buff", buff);
        }
        buff.put("score", buff.getIntValue("score") + score);
        buff.put("pointOwned", computePointOwned(theme, bp.getIntValue("point"), buff.getIntValue("score")));
    }

    /** 官方「通过层数」查表（PRTS）：层数 0..7；rogue_6 第 1 档是 40 */
    private static int layerScore(String theme, int zones) {
        int[] table = "rogue_6".equals(theme)
                ? new int[]{0, 40, 80, 150, 270, 400, 550, 650}
                : new int[]{0, 30, 80, 150, 270, 400, 550, 650};
        int idx = Math.max(0, Math.min(zones - 1, table.length - 1));
        return table[idx];
    }

    /**
     * 难度倍率。按主题分别取值（PRTS 各主题「得分效率 / 累计得分倍率」表）：
     *  rogue_1(傀影)  : 古堡观光 -50%；正式调查 0%；1..15 = +5/10/15/20/30/40/50…（封顶+50%）
     *  rogue_2(水月)  : 简单 -50%；0%..；1..18 = +5/10/15/20/30/40/50…（同 rogue_1 曲线）
     *  rogue_3(银凇)  : 挑战自然 0 级即 -40%（无简单难度），1=-20%，2=0%，3..=+10/20/25/30/…最大+50%
     *  rogue_4/5/6    : 0 级 ±0%，1..=+5/10/15/20/25/30/35/40/45/50…（最大+50%）
     */
    private static double difficultyMultiplier(String theme, String mode, int modeGrade) {
        if ("EASY".equals(mode) && !"rogue_3".equals(theme)) {
            return 0.5;   // rogue_3 没有简单难度
        }
        int[] table;
        if ("rogue_3".equals(theme)) {
            table = new int[]{-40, -20, 0, 10, 20, 25, 30, 35, 40, 45, 50, 50, 50, 50, 50, 50};
        } else if ("rogue_1".equals(theme) || "rogue_2".equals(theme)) {
            table = new int[]{0, 5, 10, 15, 20, 30, 40, 50, 50, 50, 50, 50, 50, 50, 50, 50};
        } else {
            table = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 50, 50, 50, 50, 50};
        }
        int g = Math.max(0, Math.min(modeGrade, table.length - 1));
        return 1.0 + table[g] / 100.0;
    }

    /** 官方公式的加数部分（未乘倍率） */
    private static int officialBaseScore(JSONObject cur, JSONObject record, String mode) {
        String theme = themeOf(cur);
        int zones = record.getIntValue("cntZone");
        int steps = record.getIntValue("cntArrivedNode");
        int normal = record.getIntValue("cntBattleNormal");
        int elite = record.getIntValue("cntBattleElite");
        int boss = record.getIntValue("cntBattleBoss");
        int recruit = record.getIntValue("cntRecruitChar");
        int items = gainedItemCount(cur, record);
        return layerScore(theme, zones) + steps + normal * 10 + elite * 20 + boss * 30
                + recruit * 2 + items * 5;
    }

    /**
     * 「获得物品数」：官方口径 = 收藏品 + 战术道具 + 单个剧目（不含源石锭与招募券）。
     * 优先用结算 record 里的 relicList/capsuleList/activeToolList/exploreToolList，
     * 没有就退回 inventory.relic 数量。
     */
    private static int gainedItemCount(JSONObject cur, JSONObject record) {
        int sum = -1;          // -1：官方口径不含「初始收藏品」，见下方 initRelic
        boolean any = false;
        if (record != null) {
            for (String key : new String[]{"relicList", "capsuleList", "activeToolList", "exploreToolList", "legacyList"}) {
                JSONArray arr = record.getJSONArray(key);
                if (arr != null) {
                    sum += arr.size();
                    any = true;
                }
            }
            // rogue_6 的废品（scrap）也算「战术道具」
            JSONObject scrap = record.getJSONObject("scrapCounter");
            if (scrap != null) {
                for (String k : scrap.keySet()) {
                    sum += scrap.getIntValue(k);
                }
                any = true;
            }
        }
        if (!any) {
            JSONObject inv = cur.getJSONObject("inventory");
            JSONObject relic = inv == null ? null : inv.getJSONObject("relic");
            sum += relic == null ? 0 : relic.size();
        }
        int initRelic = 1;     // 开局选秀给的收藏品（不含进「获得物品数」）
        JSONObject rec = cur.getJSONObject("record");
        if (rec != null && rec.containsKey("cntInitRelic")) {
            initRelic = rec.getIntValue("cntInitRelic");
        }
        return Math.max(0, sum - initRelic + 1);
    }

    /** 天赋点：rogue_1 = bp/10；其余主题 = score/200；再被 outerBuffTokenSum 截断 */
    private static int computePointOwned(String theme, int bpPoint, int score) {
        int ratioBase;
        int value;
        if ("rogue_1".equals(theme)) {
            ratioBase = outerBuffTokenRatio();
            value = bpPoint;
        } else {
            ratioBase = 200;
            value = score;
        }
        int owned = ratioBase <= 0 ? value : value / ratioBase;
        int cap = buffTokenSum(theme);
        if (cap > 0 && owned > cap) {
            owned = cap;
        }
        return Math.max(0, owned);
    }

    /** 全局 outerBuffTokenRatio（默认 10） */
    private static int outerBuffTokenRatio() {
        JSONObject constant = ArknightsApplication.roguelikeTopicTable == null ? null
                : ArknightsApplication.roguelikeTopicTable.getJSONObject("constant");
        if (constant != null && constant.getIntValue("outerBuffTokenRatio") > 0) {
            return constant.getIntValue("outerBuffTokenRatio");
        }
        return 10;
    }

    /** 收藏品提供的 bp 加成（buffs 里 key = bp_reward_increase 的 value 之和） */
    private static double bpBonus(JSONObject cur, String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject relicsTable = details == null ? null : details.getJSONObject("relics");
        JSONObject owned = cur.getJSONObject("inventory") == null ? null
                : cur.getJSONObject("inventory").getJSONObject("relic");
        if (relicsTable == null || owned == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (String k : owned.keySet()) {
            String id = owned.getJSONObject(k).getString("id");
            JSONObject relic = id == null ? null : relicsTable.getJSONObject(id);
            JSONArray buffs = relic == null ? null : relic.getJSONArray("buffs");
            if (buffs == null) {
                continue;
            }
            for (int i = 0; i < buffs.size(); i++) {
                JSONObject b = buffs.getJSONObject(i);
                if (!"bp_reward_increase".equals(b.getString("key"))) {
                    continue;
                }
                JSONArray bb = b.getJSONArray("blackboard");
                if (bb == null) {
                    continue;
                }
                for (int j = 0; j < bb.size(); j++) {
                    JSONObject one = bb.getJSONObject(j);
                    if ("value".equals(one.getString("key"))) {
                        sum += one.getDoubleValue("value");
                    }
                }
            }
        }
        return sum;
    }

    /** 该主题天赋点上限（details.<theme>.detailConst.outerBuffTokenSum） */
    private static int buffTokenSum(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject dc = details == null ? null : details.getJSONObject("detailConst");
        return dc == null ? 0 : dc.getIntValue("outerBuffTokenSum");
    }

    /** 招募助战列表（私服没有好友助战，返回空列表即可让客户端继续） */
    @PostMapping(value = {"/getTicketAssistList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getTicketAssistList(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        JSONObject out = new JSONObject(true);
        out.put("assistList", new JSONArray());
        out.put("playerDataDelta", new JSONObject(true));
        return out;
    }

    // ──────────────────── 主题专属系统兜底路由 ────────────────────

    /**
     * 六主题专属机制（骰子 / 炼金 / 图腾 / 铜钱 / 废品 / 献祭 / 远征 / 预测 …）
     * 私服暂未实现具体效果，统一按「清待办 + 回待移动」处理，避免客户端 404 中断。
     */
    @PostMapping(value = {
            "/useInspiration",
            "/setPinned", "/shopBattleStart",
            "/confirmTraderReturn",
            "/readEndingChange", "/chooseInitialExploreTool", "/confirmZoneReward",
            "/finishGame", "/specialZone/leave",
            "/upgradeNode", "/normal/refreshMission",
            "/gridZone/emptyStep", "/gridZone/readStepZero", "/scrap/identify", "/scrap/loseScrap"
    }, produces = {"application/json;charset=UTF-8"})
    public JSONObject themeFallback(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody(required = false) JSONObject body,
                                    HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] " + path + "（兜底：暂无实现）");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        JSONArray granted = new JSONArray();
        String headType = null;
        JSONObject head = null;
        if (pending != null && !pending.isEmpty()) {
            head = pending.getJSONObject(0);
            headType = head.getString("type");
        }
        // 结算待办里可能预置的奖励（骰子 result / 通用 result / rewards）
        if (head != null) {
            JSONObject content = head.getJSONObject("content");
            if (content != null) {
                JSONObject dice = content.getJSONObject("dice");
                if (dice != null && dice.getJSONObject("result") != null) {
                    grantRewardArray(userData, cur, dice.getJSONObject("result").getJSONArray("reward"), granted);
                }
                JSONObject res = content.getJSONObject("result");
                if (res != null) {
                    grantRewardArray(userData, cur, res.getJSONArray("reward"), granted);
                }
                grantRewardArray(userData, cur, content.getJSONArray("rewards"), granted);
            }
        }
        // 献祭：按请求里的收藏品槽位移除
        if ("SACRIFICE".equals(headType) && body != null) {
            String relicKey = body.getString("choice");
            JSONObject relicInv = cur.getJSONObject("inventory").getJSONObject("relic");
            if (relicInv != null && relicKey != null && relicInv.containsKey(relicKey)) {
                relicInv.remove(relicKey);
                ArknightsApplication.LOGGER.info("[rlv2] 献祭收藏品 {}", relicKey);
            }
        }
        if (pending != null && !pending.isEmpty()) {
            pending.remove(0);
        }
        if (pending == null || pending.isEmpty()) {
            player.put("state", "WAIT_MOVE");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("result", 1);
        result.put("items", granted);
        return result;
    }

    /** 把 [{id,count,type?}] 形式的奖励入账，并回填到 items 响应里 */
    private static void grantRewardArray(JSONObject userData, JSONObject cur, JSONArray rewards, JSONArray out) {
        if (rewards == null) {
            return;
        }
        for (int i = 0; i < rewards.size(); i++) {
            JSONObject it = rewards.getJSONObject(i);
            if (it == null) {
                continue;
            }
            String itemId = it.getString("id");
            int count = it.getIntValue("count");
            if (count <= 0) {
                count = 1;
            }
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }
            applyShopItem(userData, cur, itemId, count);
            JSONObject one = new JSONObject(true);
            one.put("id", itemId);
            one.put("count", count);
            out.add(one);
        }
    }

    // ───────────────────────────── battlePass ────────────────────────────

    /**
     * 领取 BP（里程碑）奖励。
     * 请求 {"theme":"rogue_1","rewards":["bp_level_2"]}
     * 响应 {items, playerDataDelta:{modified:{rlv2, inventory/status}}}
     * 表来源：details.<theme>.milestones[]，tokenNum = 需要的 bp 分数。
     */
    @PostMapping(value = {"/battlePass/getReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battlePassGetReward(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        return battlePassReward(secret, body, false);
    }

    /**
     * 购买 BP 奖励（花源石换分并直接领）。
     * 请求 {"theme":"rogue_1","reward":"bp_level_3","cost":1}
     * 实测：cost 1 → bp.point += 200、status.androidDiamond -= cost、并发放该级奖励。
     */
    @PostMapping(value = {"/battlePass/buyReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battlePassBuyReward(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        return battlePassReward(secret, body, true);
    }

    private JSONObject battlePassReward(String secret, JSONObject body, boolean buy) {
        String theme = body == null ? "rogue_1" : body.getString("theme");
        String clientIp = "";
        ArknightsApplication.LOGGER.info("[rlv2] /battlePass/" + (buy ? "buyReward" : "getReward") + " theme=" + theme);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        if (theme == null || theme.isEmpty()) {
            theme = "rogue_1";
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter == null) {
            return bad("未知主题: " + theme, 1);
        }
        JSONObject bp = themeOuter.getJSONObject("bp");
        if (bp == null) {
            bp = new JSONObject(true);
            bp.put("point", 0);
            bp.put("reward", new JSONObject(true));
            themeOuter.put("bp", bp);
        }
        JSONObject claimed = bp.getJSONObject("reward");
        if (claimed == null) {
            claimed = new JSONObject(true);
            bp.put("reward", claimed);
        }
        JSONObject status = userData.getJSONObject("status");
        JSONObject inventory = userData.getJSONObject("inventory");

        JSONArray ids = new JSONArray();
        int cost = 0;
        if (buy) {
            String one = body == null ? null : body.getString("reward");
            if (one == null) {
                return bad("缺少 reward", 1);
            }
            ids.add(one);
            cost = body.getIntValue("cost");
            if (cost <= 0) {
                cost = 1;
            }
            int diamond = status == null ? 0 : status.getIntValue("androidDiamond");
            if (diamond < cost) {
                return bad("源石不足（需要 " + cost + "，剩余 " + diamond + "）", 1);
            }
            if (status != null) {
                status.put("androidDiamond", diamond - cost);
            }
            // 实测：1 颗源石换 200 bp（= 第一级 tokenNum）
            bp.put("point", bp.getIntValue("point") + 200 * cost);
        } else {
            JSONArray req = body == null ? null : body.getJSONArray("rewards");
            if (req == null || req.isEmpty()) {
                return bad("缺少 rewards", 1);
            }
            ids.addAll(req);
        }

        int point = bp.getIntValue("point");
        JSONArray items = new JSONArray();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.getString(i);
            JSONObject def = milestoneDef(theme, id);
            if (def == null) {
                ArknightsApplication.LOGGER.warn("[rlv2] 没有这个 bp 奖励定义: {}", id);
                continue;
            }
            if (claimed.containsKey(id)) {
                continue;
            }
            int need = def.getIntValue("tokenNum");
            if (point < need) {
                ArknightsApplication.LOGGER.warn("[rlv2] bp 分数不足领取 {}（需要 {}，当前 {}）", id, need, point);
                continue;
            }
            grantBpItem(items, inventory, status, def);
            claimed.put(id, 1);
        }

        // bp 变了就同步 buff（pointOwned = floor(point / ratio)，并被 outerBuffTokenSum 截断）
        if (buy) {
            JSONObject buff = themeOuter.getJSONObject("buff");
            if (buff == null) {
                buff = new JSONObject(true);
                buff.put("pointOwned", 0);
                buff.put("pointCost", 0);
                buff.put("unlocked", new JSONObject(true));
                buff.put("score", 0);
                themeOuter.put("buff", buff);
            }
            int ratio = 10;
            JSONObject constant = ArknightsApplication.roguelikeTopicTable == null ? null
                    : ArknightsApplication.roguelikeTopicTable.getJSONObject("constant");
            if (constant != null && constant.getIntValue("outerBuffTokenRatio") > 0) {
                ratio = constant.getIntValue("outerBuffTokenRatio");
            }
            int owned = point / ratio;
            int cap = buffTokenSum(theme);
            if (cap > 0 && owned > cap) {
                owned = cap;
            }
            buff.put("pointOwned", owned);
            buff.put("score", point);
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject outerOut = new JSONObject(true);
        JSONObject themeOut = new JSONObject(true);
        themeOut.put("bp", bp);
        JSONObject buffOut = themeOuter.getJSONObject("buff");
        if (buy && buffOut != null) {
            themeOut.put("buff", buffOut);
        }
        outerOut.put(theme, themeOut);
        JSONObject rl = new JSONObject(true);
        rl.put("outer", outerOut);
        JSONObject modified = new JSONObject(true);
        modified.put("rlv2", rl);
        if (inventory != null) {
            modified.put("inventory", inventory);
        }
        if (status != null) {
            modified.put("status", status);
        }
        JSONObject pd = new JSONObject(true);
        pd.put("modified", modified);
        pd.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", pd);
        return result;
    }

    /** details.<theme>.milestones[] 里按 id 找 */
    private static JSONObject milestoneDef(String theme, String id) {
        JSONObject details = themeDetails(theme);
        JSONArray list = details == null ? null : details.getJSONArray("milestones");
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject one = list.getJSONObject(i);
            if (id.equals(one.getString("id"))) {
                return one;
            }
        }
        return null;
    }

    /** 发 BP 奖励物品：GOLD → status.gold，其余 → inventory */
    private static void grantBpItem(JSONArray items, JSONObject inventory, JSONObject status, JSONObject def) {
        String type = def.getString("itemType");
        String itemId = def.getString("itemID");
        int count = def.getIntValue("itemCount");
        if (itemId == null || count <= 0) {
            return;
        }
        JSONObject it = new JSONObject(true);
        it.put("type", type);
        it.put("id", itemId);
        it.put("count", count);
        items.add(it);
        if ("GOLD".equals(type) && status != null) {
            status.put("gold", status.getIntValue("gold") + count);
        } else if (inventory != null) {
            inventory.put(itemId, inventory.getIntValue(itemId) + count);
        }
    }

    // ───────────────────────────── unlockBuff ────────────────────────────

    /**
     * 局外天赋升级（normal 主题变体）。
     * 请求 {"theme":"rogue_1","buff":"outbuff_5"}
     * 响应只回 outer.<theme>.buff：{pointOwned, pointCost, unlocked{id:1}, score}
     *
     * 实测：pointOwned 不变（总量），pointCost 累加（46→30→40），unlocked 增加。
     * 单点天赋的价格从表 details.<theme>.buff? 里取；取不到就用 10 兜底。
     */
    @PostMapping(value = {"/normal/unlockBuff"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject unlockBuff(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String theme = body == null ? "rogue_1" : body.getString("theme");
        String buff = body == null ? null : body.getString("buff");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request)
                + "] /rlv2/normal/unlockBuff theme=" + theme + " buff=" + buff);

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        if (theme == null || theme.isEmpty()) {
            theme = "rogue_1";
        }
        if (buff == null || buff.isEmpty()) {
            return bad("缺少 buff", 1);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter == null) {
            return bad("未知主题: " + theme, 1);
        }
        JSONObject buffNode = themeOuter.getJSONObject("buff");
        if (buffNode == null) {
            buffNode = new JSONObject(true);
            buffNode.put("pointOwned", 0);
            buffNode.put("pointCost", 0);
            buffNode.put("unlocked", new JSONObject(true));
            buffNode.put("score", 0);
            themeOuter.put("buff", buffNode);
        }
        JSONObject unlocked = buffNode.getJSONObject("unlocked");
        if (unlocked == null) {
            unlocked = new JSONObject(true);
            buffNode.put("unlocked", unlocked);
        }
        int owned = buffNode.getIntValue("pointOwned");
        int cost = buffNode.getIntValue("pointCost");
        int price = buffPrice(theme, buff, owned - cost);
        if (!unlocked.containsKey(buff)) {
            if (owned - cost < price) {
                return bad("天赋点不足（需要 " + price + "，剩余 " + (owned - cost) + "）", 1);
            }
            unlocked.put(buff, 1);
            buffNode.put("pointCost", cost + price);
        }
        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        JSONObject rl = new JSONObject(true);
        JSONObject outerOut = new JSONObject(true);
        JSONObject themeOut = new JSONObject(true);
        themeOut.put("buff", buffNode);
        outerOut.put(theme, themeOut);
        rl.put("outer", outerOut);
        modified.put("rlv2", rl);
        JSONObject pd = new JSONObject(true);
        pd.put("modified", modified);
        pd.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", pd);
        return result;
    }

    /** 天赋单价：优先读表 details.<theme>.buff 的 price，取不到按层级兜底（10/20/30…） */
    private static int buffPrice(String theme, String buffId, int remain) {
        JSONObject details = themeDetails(theme);
        JSONObject buffs = details == null ? null : details.getJSONObject("buff");
        JSONObject one = buffs == null ? null : buffs.getJSONObject(buffId);
        if (one != null && one.getIntValue("price") > 0) {
            return one.getIntValue("price");
        }
        return 10;
    }

    // ───────────────────────── 月度任务（monthMission）─────────────────────────

    /**
     * 月度任务存档结构（官服 B服 实测）：
     * outer.<theme>.mission = {"updateId":"rogue_6_update_5","refresh":4,
     *   "list":[{"type":"A","mission":{"type":"A","tmpl":"PassBattleStage","id":"rogue_6_task_29",
     *                                 "state":1,"target":3,"value":3}}, ...]}
     * 任务定义来自 details.<theme>.monthMission（template / paramList[0]=target / tokenRewardNum）。
     */
    private static JSONObject ensureMonthMission(JSONObject userData, String theme) {
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter == null) {
            return null;
        }
        JSONObject mission = themeOuter.getJSONObject("mission");
        if (mission != null && mission.getJSONArray("list") != null && !mission.getJSONArray("list").isEmpty()) {
            return mission;
        }
        JSONObject details = themeDetails(theme);
        JSONArray defs = details == null ? null : details.getJSONArray("monthMission");
        if (defs == null || defs.isEmpty()) {
            return null;
        }
        // 按 A / B / C 三类各挑一个（官方同款：每类一个槽位）
        JSONArray list = new JSONArray();
        for (String type : new String[]{"A", "B", "C"}) {
            JSONObject picked = null;
            int totalWeight = 0;
            for (int i = 0; i < defs.size(); i++) {
                JSONObject d = defs.getJSONObject(i);
                if (type.equals(d.getString("taskClass"))) {
                    totalWeight += Math.max(1, d.getIntValue("innerClassWeight"));
                }
            }
            if (totalWeight <= 0) {
                continue;
            }
            int roll = RANDOM.nextInt(totalWeight);
            int acc = 0;
            for (int i = 0; i < defs.size(); i++) {
                JSONObject d = defs.getJSONObject(i);
                if (!type.equals(d.getString("taskClass"))) {
                    continue;
                }
                acc += Math.max(1, d.getIntValue("innerClassWeight"));
                if (roll < acc) {
                    picked = d;
                    break;
                }
            }
            if (picked == null) {
                continue;
            }
            JSONArray params = picked.getJSONArray("paramList");
            int target = 1;
            if (params != null && !params.isEmpty()) {
                try {
                    target = Integer.parseInt(params.getString(0));
                } catch (Exception ignored) {
                }
            }
            JSONObject one = new JSONObject(true);
            one.put("type", type);
            JSONObject m = new JSONObject(true);
            m.put("type", type);
            m.put("tmpl", picked.getString("template"));
            m.put("id", picked.getString("id"));
            m.put("state", 0);
            m.put("target", target);
            m.put("value", 0);
            one.put("mission", m);
            list.add(one);
        }
        mission = new JSONObject(true);
        mission.put("updateId", theme + "_update_1");
        mission.put("refresh", 0);
        mission.put("list", list);
        themeOuter.put("mission", mission);
        return mission;
    }

    /**
     * 本局给某个月度任务贡献多少进度。
     * 模板清单来自 details.<theme>.monthMission（六主题共 37 种），
     * 这里按本局 record 里已有的计数器映射；未埋点的（主题专属机制）返回 0。
     */
    private static int missionDelta(JSONObject def, JSONObject record, int level) {
        if (def == null) {
            return 0;
        }
        String tmpl = def.getString("template");
        JSONArray params = def.getJSONArray("paramList");
        String p0 = params != null && params.size() > 0 ? params.getString(0) : null;
        String p1 = params != null && params.size() > 1 ? params.getString(1) : null;
        if (tmpl == null) {
            return 0;
        }
        switch (tmpl) {
            case "PassNode":
                return record.getIntValue("cntArrivedNode");
            case "PassNodeType":              // param0 = 节点类型名（INCIDENT/SHOP/...）
            case "PassNodeSkyType": {
                JSONObject byType = record.getJSONObject("cntNodeByType");
                if (byType == null || p1 == null) {
                    return 0;
                }
                return byType.getIntValue(p1);
            }
            case "KillEnemy":
                return record.getIntValue("cntKillEnemy");
            case "KillBoss":
                return record.getIntValue("cntBattleBoss");
            case "GainCharacter":
                return record.getIntValue("cntRecruitChar");
            case "FreeGainCharacter":
                return record.getIntValue("cntRecruitFree");
            case "HelpfulGainCharacter":
                return record.getIntValue("cntRecruitAssist");
            case "GainCerternProfession": {   // param1 = 职业名，逗号分隔
                if (p1 == null) {
                    return 0;
                }
                JSONObject prof = record.getJSONObject("cntRecruitProfession");
                if (prof == null) {
                    return 0;
                }
                int sum = 0;
                for (String one : p1.split(",")) {
                    sum += prof.getIntValue(one.trim());
                }
                return sum;
            }
            case "AccelerateCharacter":
                return record.getIntValue("cntUpgradeChar");
            case "AccelerateLevel":
                return Math.max(0, level - 1);   // 升级次数 = 当前等级 - 1
            case "PassFloor":
                return record.getIntValue("cntZone");
            case "PerfectBattle":
                return record.getIntValue("cntPerfectBattle");
            case "PassBattleStage": {         // param1 = 关卡 id 列表（逗号分隔）
                JSONObject byStage = record.getJSONObject("cntBattleByStage");
                if (byStage == null) {
                    return 0;
                }
                if (p1 == null || p1.isEmpty()) {
                    int sum = 0;
                    for (String k : byStage.keySet()) {
                        sum += byStage.getIntValue(k);
                    }
                    return sum;
                }
                int sum = 0;
                for (String stage : p1.split(",")) {
                    sum += byStage.getIntValue(stage.trim());
                }
                return sum;
            }
            case "UseGold":
                return record.getIntValue("cntUseGold");
            case "SaveGold":
                return record.getIntValue("cntSaveGold");
            case "BuyItemInStore":
                return record.getIntValue("cntShopBuy");
            case "SoldItemInStore":
                return record.getIntValue("cntShopSell");
            case "UsePopulation":
                return record.getIntValue("cntUsePopulation");
            case "ProtectBox":
                return record.getIntValue("cntProtectBox");
            case "GainItem":                  // param1 = RELIC 等
                if ("RELIC".equals(p1)) {
                    return record.getIntValue("cntGainRelic");
                }
                return record.getIntValue("cntGainItem");
            case "PassEnding":
                return record.getIntValue("cntPassEnding");
            default:
                // CntBattlePawn / UseItem / UseTotem / CopperDraw / UseAlchemy / MoveByScrap /
                // GrowSeed / CerternItem / KillCerternEnemy / LoseChaosBuff / OpenRoad /
                // TempUpgrade / CandleCharacter / CompleteNodeMission / PassFloorDuringVariation
                // —— 依赖主题专属机制或更细的战斗过程数据，暂未埋点
                return 0;
        }
    }

    /** 结算月度任务：累加进度、置 state、返回 {before, after, token} */
    private static JSONObject settleMonthMission(JSONObject userData, String theme, JSONObject record, int level, int shopBuy) {
        JSONObject out = new JSONObject(true);
        JSONArray before = new JSONArray();
        JSONArray after = new JSONArray();
        int token = 0;
        JSONObject mission = ensureMonthMission(userData, theme);
        if (mission == null) {
            out.put("before", before);
            out.put("after", after);
            out.put("token", 0);
            return out;
        }
        JSONArray list = mission.getJSONArray("list");
        for (int i = 0; i < list.size(); i++) {
            JSONObject entry = list.getJSONObject(i);
            JSONObject m = entry.getJSONObject("mission");
            if (m == null) {
                continue;
            }
            before.add(JSONObject.parseObject(m.toJSONString()));
            JSONObject defObj = monthMissionDef(theme, m.getString("id"));
            int delta = missionDelta(defObj, record, level);
            int value = m.getIntValue("value") + delta;
            m.put("value", value);
            int target = m.getIntValue("target");
            if (target > 0 && value >= target && m.getIntValue("state") == 0) {
                m.put("state", 1);
                JSONObject def = monthMissionDef(theme, m.getString("id"));
                if (def != null) {
                    token += def.getIntValue("tokenRewardNum");
                }
            }
            after.add(JSONObject.parseObject(m.toJSONString()));
        }
        // 完成任务 → 发放 tokenRewardNum（进 bp.point，与官方「天赋点＝bp 进度」一致）
        if (token > 0) {
            JSONObject themeOuter2 = Rlv2Helper.outerTheme(userData, theme);
            if (themeOuter2 != null) {
                JSONObject bpObj = themeOuter2.getJSONObject("bp");
                if (bpObj == null) {
                    bpObj = new JSONObject(true);
                    bpObj.put("point", 0);
                    bpObj.put("reward", new JSONObject(true));
                    themeOuter2.put("bp", bpObj);
                }
                bpObj.put("point", bpObj.getIntValue("point") + token);
                ArknightsApplication.LOGGER.info("[rlv2] 月度任务完成，发放 {} 点", token);
            }
        }
        out.put("before", before);
        out.put("after", after);
        out.put("token", token);
        return out;
    }

    private static JSONObject monthMissionDef(String theme, String id) {
        JSONObject details = themeDetails(theme);
        JSONArray defs = details == null ? null : details.getJSONArray("monthMission");
        if (defs == null || id == null) {
            return null;
        }
        for (int i = 0; i < defs.size(); i++) {
            JSONObject d = defs.getJSONObject(i);
            if (id.equals(d.getString("id"))) {
                return d;
            }
        }
        return null;
    }

    // ─────────────────── 主题专属机制（照参考实现，含埋点）───────────────────

    /**
     * rogue_3 密文板/图腾：{"totemIndex":[..],"nodeIndex":[..]}
     * 官方语义（参考实现 capture_new r3 实证）：每枚图腾 +3 混沌值，到达 curMaxValue 升级。
     */
    @PostMapping(value = {"/useTotem"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject useTotem(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject body,
                               HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONArray totems = body == null ? null : body.getJSONArray("totemIndex");
        JSONArray nodes = body == null ? null : body.getJSONArray("nodeIndex");
        int cnt = totems == null ? 0 : totems.size();
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject chaos = module.getJSONObject("chaos");
        if (chaos == null) {
            chaos = new JSONObject(true);
            chaos.put("level", 0);
            chaos.put("value", 0);
            chaos.put("curMaxValue", 8);
            chaos.put("chaosList", new JSONArray());
            module.put("chaos", chaos);
        }
        int dValue = 3 * cnt;
        int value = chaos.getIntValue("value") + dValue;
        int level = chaos.getIntValue("level");
        int curMax = chaos.getIntValue("curMaxValue");
        if (curMax <= 0) {
            curMax = 8;
        }
        JSONArray chaosList = chaos.getJSONArray("chaosList");
        if (chaosList == null) {
            chaosList = new JSONArray();
        }
        int preLevel = level;
        while (value >= curMax) {
            value -= curMax;
            level++;
            chaosList.add("rogue_3_chaos_" + (6 + level));
        }
        chaos.put("level", level);
        chaos.put("value", value);
        chaos.put("curMaxValue", curMax);
        chaos.put("chaosList", chaosList);
        JSONObject deltaChaos = new JSONObject(true);
        deltaChaos.put("preLevel", preLevel);
        deltaChaos.put("afterLevel", level);
        deltaChaos.put("dValue", dValue);
        JSONArray dChaos = new JSONArray();
        if (level > preLevel) {
            dChaos.add("rogue_3_chaos_" + (6 + level));
        }
        deltaChaos.put("dChaos", dChaos);
        chaos.put("deltaChaos", deltaChaos);

        recInc(cur, "cntUseTotem", cnt);
        cur.getJSONObject("player").put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("nodeIndex", nodes == null ? new JSONArray() : nodes);
        return result;
    }

    /**
     * rogue_4 解读/炼金：{"fragmentIndex":["f_a","f_b"],"leave":0}
     * 官方语义（参考实现）：消耗构想碎片合成收藏品，碎片总价值 >= 8 触发终末收藏品（SSR）。
     */
    @PostMapping(value = {"/alchemy"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject alchemy(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject body,
                              HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        if (body != null && body.getBooleanValue("leave")) {
            player.put("pending", new JSONArray());
            player.put("state", "WAIT_MOVE");
            userDao.setUserData(account.getUid(), userData);
            return delta(cur);
        }
        String theme = cur.getJSONObject("game").getString("theme");
        JSONArray fragIndex = body == null ? null : body.getJSONArray("fragmentIndex");
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        JSONObject fragments = fragment == null ? null : fragment.getJSONObject("fragments");
        int totalValue = 0;
        if (fragIndex != null && fragments != null) {
            for (int i = 0; i < fragIndex.size(); i++) {
                String fid = fragIndex.getString(i);
                JSONObject f = fragments.getJSONObject(fid);
                if (f != null) {
                    totalValue += f.getIntValue("value");
                    fragments.remove(fid);
                }
            }
        }
        // 抽收藏品：价值 >= 8 → 终末池（_relic_final_），否则普通池
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> ssrPool = new java.util.ArrayList<>();
        if (relics != null) {
            for (String rid : relics.keySet()) {
                if (rid.startsWith("rogue_4_relic_final_")) {
                    ssrPool.add(rid);
                } else if (rid.startsWith("rogue_4_relic_")) {
                    normalPool.add(rid);
                }
            }
        }
        boolean isSsr = totalValue >= 8 && !ssrPool.isEmpty();
        String reward = null;
        if (isSsr) {
            reward = ssrPool.get(RANDOM.nextInt(ssrPool.size()));
        } else if (!normalPool.isEmpty()) {
            reward = normalPool.get(RANDOM.nextInt(normalPool.size()));
        }
        JSONArray items = new JSONArray();
        if (reward != null) {
            JSONObject one = new JSONObject(true);
            one.put("id", reward);
            one.put("count", 1);
            items.add(one);
        }
        JSONObject alchemyReward = new JSONObject(true);
        alchemyReward.put("items", items);
        alchemyReward.put("isSSR", isSsr);
        alchemyReward.put("isFail", items.isEmpty());
        JSONObject content = new JSONObject(true);
        content.put("alchemyReward", alchemyReward);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_6");
        pend.put("type", "ALCHEMY_REWARD");
        pend.put("content", content);
        JSONArray pendingArr = new JSONArray();
        pendingArr.add(pend);
        player.put("pending", pendingArr);
        player.put("state", "PENDING");
        recInc(cur, "cntUseAlchemy", 1);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** rogue_4 解读结算：{index} → 入账并关闭待办 */
    @PostMapping(value = {"/alchemyReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject alchemyReward(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody(required = false) JSONObject body,
                                    HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONArray granted = new JSONArray();
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()) {
            JSONObject head = pending.getJSONObject(0);
            JSONObject content = head.getJSONObject("content");
            JSONObject reward = content == null ? null : content.getJSONObject("alchemyReward");
            JSONArray items = reward == null ? null : reward.getJSONArray("items");
            grantRewardArray(userData, cur, items, granted);
        }
        player.put("pending", new JSONArray());
        player.put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", granted);
        return result;
    }

    /**
     * rogue_2 骰子：{"choice":"..."|"REROLL"}
     * 官方语义（参考实现）：结果由服务端预投在待办里，choice 仅确认；
     * REROLL 消耗 1 次重投机会。
     */
    @PostMapping(value = {"/diceChoice"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject diceChoice(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        JSONArray granted = new JSONArray();
        String choice = body == null ? null : body.getString("choice");
        if (pending != null && !pending.isEmpty()) {
            JSONObject head = pending.getJSONObject(0);
            if ("DICE".equals(head.getString("type"))) {
                JSONObject dice = head.getJSONObject("content").getJSONObject("dice");
                if ("REROLL".equals(choice) && dice != null && dice.getIntValue("rerollCount") > 0) {
                    dice.put("rerollCount", dice.getIntValue("rerollCount") - 1);
                    recInc(cur, "cntUseItem", 1);
                    userDao.setUserData(account.getUid(), userData);
                    return delta(cur);
                }
                JSONObject result = dice == null ? null : dice.getJSONObject("result");
                if (result != null) {
                    grantRewardArray(userData, cur, result.getJSONArray("reward"), granted);
                }
                pending.remove(0);
                if (pending.isEmpty()) {
                    player.put("state", "WAIT_MOVE");
                }
                recInc(cur, "cntUseItem", 1);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", granted);
        return result;
    }

    // ─────────────────── 主题专属：废品载具 / 铜钱（照参考实现 + B服抓包结构）───────────────────

    /** 找铜钱袋（不同主题放在 inventory.copper / module.copper 之一） */
    private static JSONObject copperBag(JSONObject cur) {
        JSONObject inv = cur.getJSONObject("inventory");
        if (inv != null && inv.getJSONObject("copper") != null) {
            JSONObject bag = inv.getJSONObject("copper").getJSONObject("bag");
            if (bag != null) {
                return bag;
            }
        }
        JSONObject module = cur.getJSONObject("module");
        if (module != null && module.getJSONObject("copper") != null) {
            return module.getJSONObject("copper").getJSONObject("bag");
        }
        return null;
    }

    /**
     * rogue_6 废品载具切换：{"scrapInstId":"s_3","toWalk":0}
     * 官服 B服 抓包实证：module.scrap = {activeVehicle:{isWalk,instId}, inventory:{s_N:{...}}, limit:8}
     */
    @PostMapping(value = {"/scrap/changeVehicle"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject scrapChangeVehicle(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody(required = false) JSONObject body,
                                         HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String instId = body == null ? null : body.getString("scrapInstId");
        boolean toWalk = body != null && body.getBooleanValue("toWalk");
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        if (scrap != null && instId != null) {
            JSONObject vehicle = scrap.getJSONObject("activeVehicle");
            if (vehicle == null) {
                vehicle = new JSONObject(true);
                scrap.put("activeVehicle", vehicle);
            }
            vehicle.put("instId", instId);
            vehicle.put("isWalk", toWalk);
            JSONObject inv = scrap.getJSONObject("inventory");
            JSONObject item = inv == null ? null : inv.getJSONObject(instId);
            if (item != null) {
                item.put("useCnt", item.getIntValue("useCnt") + 1);
            }
            if (toWalk) {
                recInc(cur, "cntMoveByScrap", 1);
            }
            recInc(cur, "cntUseItem", 1);
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /**
     * rogue_5 投钱确认（铜钱）：关闭 DRAW_COPPER 待办。
     * 参考实现语义：按投出的铜钱族给奖 —— H(花钱)→源石锭+1 / L(厉钱)→3 张招募券 / E(衡钱)→希望+3 / 其余→随机收藏品。
     */
    @PostMapping(value = {"/copper/confirmDraw", "/copper/draw"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject copperDraw(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject prop = player.getJSONObject("property");
        JSONArray granted = new JSONArray();
        if (pending != null) {
            for (int i = 0; i < pending.size(); i++) {
                JSONObject p = pending.getJSONObject(i);
                if (!"DRAW_COPPER".equals(p.getString("type"))) {
                    continue;
                }
                JSONObject content = p.getJSONObject("content");
                JSONObject draw = content == null ? null : content.getJSONObject("drawCopper");
                JSONArray coins = draw == null ? null : draw.getJSONArray("copper");
                JSONObject bag = copperBag(cur);
                if (coins != null) {
                    for (int k = 0; k < coins.size(); k++) {
                        String key = coins.getString(k);
                        JSONObject coin = bag == null ? null : bag.getJSONObject(key);
                        String coinId = coin == null ? null : coin.getString("id");
                        String fam = "";
                        if (coinId != null) {
                            String[] parts = coinId.split("_");
                            if (parts.length >= 5) {
                                fam = parts[3];
                            }
                        }
                        if ("H".equals(fam)) {
                            prop.put("gold", prop.getIntValue("gold") + 1);
                        } else if ("L".equals(fam)) {
                            JSONObject details = themeDetails(theme);
                            JSONObject tickets = details == null ? null : details.getJSONObject("recruitTickets");
                            if (tickets != null && !tickets.isEmpty()) {
                                java.util.List<String> ids = new java.util.ArrayList<>(tickets.keySet());
                                for (int t = 0; t < 3; t++) {
                                    addRecruitTicket(cur, ids.get(RANDOM.nextInt(ids.size())), false);
                                }
                            }
                        } else if ("E".equals(fam)) {
                            JSONObject pop = prop.getJSONObject("population");
                            pop.put("max", pop.getIntValue("max") + 3);
                        } else {
                            String relic = randomRelic(theme, cur);
                            if (relic != null) {
                                addRelic(cur, relic, 1);
                                JSONObject one = new JSONObject(true);
                                one.put("id", relic);
                                one.put("count", 1);
                                granted.add(one);
                            }
                        }
                    }
                }
                pending.remove(i);
                i--;
                recInc(cur, "cntUseCopper", 1);
            }
        }
        if (pending == null || pending.isEmpty()) {
            player.put("state", "WAIT_MOVE");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", granted);
        return result;
    }

    /**
     * rogue_5 换签：{"index":"c_1"} → 移除该签，补一枚新签（袋数不变）。
     * 参考实现：优先用 CHANGE_COPPER 待办预定的 newCopper，否则随机 F 族基础签。
     */
    @PostMapping(value = {"/copper/change"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject copperChange(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        String index = body == null ? null : body.getString("index");
        JSONObject bag = copperBag(cur);
        if (bag != null && index != null && bag.containsKey(index)) {
            String newCoinId = null;
            JSONArray pending = player.getJSONArray("pending");
            if (pending != null) {
                for (int i = 0; i < pending.size(); i++) {
                    JSONObject p = pending.getJSONObject(i);
                    if (!"CHANGE_COPPER".equals(p.getString("type"))) {
                        continue;
                    }
                    JSONObject cc = p.getJSONObject("content").getJSONObject("changeCopper");
                    if (cc != null) {
                        newCoinId = cc.getString("newCopper");
                    }
                    pending.remove(i);
                    break;
                }
            }
            bag.remove(index);
            if (newCoinId == null) {
                String theme = cur.getJSONObject("game").getString("theme");
                JSONObject details = themeDetails(theme);
                JSONObject coins = details == null ? null : details.getJSONObject("coppers");
                if (coins != null && !coins.isEmpty()) {
                    java.util.List<String> ids = new java.util.ArrayList<>(coins.keySet());
                    newCoinId = ids.get(RANDOM.nextInt(ids.size()));
                }
            }
            if (newCoinId != null) {
                int next = bag.size() + 1;
                String newKey = "c_" + next;
                while (bag.containsKey(newKey)) {
                    next++;
                    newKey = "c_" + next;
                }
                JSONObject entry = new JSONObject(true);
                entry.put("index", newKey);
                entry.put("id", newCoinId);
                entry.put("ts", ArknightsApplication.getTimestamp());
                bag.put(newKey, entry);
            }
            recInc(cur, "cntUseCopper", 1);
        }
        JSONArray pending2 = player.getJSONArray("pending");
        if (pending2 == null || pending2.isEmpty()) {
            player.put("state", "WAIT_MOVE");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ─────────────────── 主题专属：献祭 / 预测 / 远征（照参考实现 wire 语义）───────────────────

    /**
     * 献祭：{"choice":"r_0"} → 移除指定收藏品槽位并关闭 SACRIFICE 待办。
     * 参考实现（capture_new r2/r4 实证）：pending[0].type==SACRIFICE → 清待办、state=WAIT_MOVE、
     * choice 命中的收藏品从 inventory.relic 删除。
     */
    @PostMapping(value = {"/sacrificeChoice"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject sacrificeChoice(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody(required = false) JSONObject body,
                                      HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()
                && "SACRIFICE".equals(pending.getJSONObject(0).getString("type"))) {
            player.put("pending", new JSONArray());
        }
        player.put("state", "WAIT_MOVE");
        String choiceId = body == null ? null : body.getString("choice");
        if (choiceId != null) {
            JSONObject relicInv = cur.getJSONObject("inventory").getJSONObject("relic");
            if (relicInv != null && relicInv.containsKey(choiceId)) {
                String relicId = relicInv.getJSONObject(choiceId).getString("id");
                relicInv.remove(choiceId);
                ArknightsApplication.LOGGER.info("[rlv2] 献祭收藏品 {}={}", choiceId, relicId);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("result", 1);
        return result;
    }

    /**
     * 预测确认：{} → 关闭 PREDICT 待办，回 WAIT_MOVE（参考实现 capture_new r3 实证）。
     */
    @PostMapping(value = {"/confirmPredict"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject confirmPredict(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody(required = false) JSONObject body,
                                     HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()
                && "PREDICT".equals(pending.getJSONObject(0).getString("type"))) {
            player.put("pending", new JSONArray());
        }
        player.put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("result", 1);
        return result;
    }

    /**
     * 远征选项：{"choice":"N","leave":0} → leave=0 时发一件随机收藏品并关闭 EXPEDITION 待办。
     */
    @PostMapping(value = {"/expeditionChoice"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject expeditionChoice(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody(required = false) JSONObject body,
                                       HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null && !pending.isEmpty()
                && "EXPEDITION".equals(pending.getJSONObject(0).getString("type"))) {
            player.put("pending", new JSONArray());
        }
        player.put("state", "WAIT_MOVE");
        JSONArray items = new JSONArray();
        boolean leave = body != null && body.getBooleanValue("leave");
        if (!leave) {
            String theme = cur.getJSONObject("game").getString("theme");
            String relic = randomRelic(theme, cur);
            if (relic != null) {
                addRelic(cur, relic, 1);
                JSONObject one = new JSONObject(true);
                one.put("id", relic);
                one.put("count", 1);
                items.add(one);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("result", 1);
        result.put("items", items);
        return result;
    }

    // ─────────────────── 商店刷新 / 碎片装载 / 丢失碎片 / 放弃（官服 B服 抓包）───────────────────

    /**
     * 刷新商店：{} → 整批换新商品。
     * 消耗按 gameConst.shopRefreshCostId（rogue_5 = rogue_5_divinationkit 占卜签，
     * rogue_6 = rogue_6_gold 源石锭；rogue_4 无该字段 → 免费）。
     */
    @PostMapping(value = {"/refreshShop"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject refreshShop(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        JSONObject shop = findShop(cur);
        if (shop == null) {
            JSONObject out = delta(cur);
            out.put("result", 0);
            return out;
        }
        // 扣刷新消耗
        String costId = gameConst == null ? null : gameConst.getString("shopRefreshCostId");
        if (costId != null && !costId.isEmpty()) {
            JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
            if (costId.equals(theme + "_gold")) {
                if (prop.getIntValue("gold") < 1) {
                    JSONObject out = delta(cur);
                    out.put("result", 0);
                    return out;
                }
                prop.put("gold", prop.getIntValue("gold") - 1);
            } else if (!consumeCost(cur, theme, costId)) {
                ArknightsApplication.LOGGER.info("[rlv2] 刷新商店失败：{} 不足", costId);
                JSONObject out = delta(cur);
                out.put("result", 0);
                return out;
            }
        }
        String goldId = gameConst == null ? theme + "_gold" : gameConst.getString("goldItemId");
        JSONArray goods = generateShopGoods(theme, details, goldId, 5);
        shop.put("goods", goods);
        shop.put("_done", false);
        if (shop.getJSONObject("bank") != null) {
            shop.getJSONObject("bank").put("withdraw", 0);
        }
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/refreshShop theme={}", theme);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        out.put("result", 1);
        return out;
    }

    /**
     * 装载构想碎片：{"troopCarry":["6","7","8"]} → 指定碎片 used=true，ei 按顺序 0..n，
     * 重算 totalWeight / overWeight（官服 B服 抓包 2026-11-04 rogue_4）。
     */
    @PostMapping(value = {"/setTroopCarry"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject setTroopCarry(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody(required = false) JSONObject body,
                                    HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        JSONArray carry = body == null ? null : body.getJSONArray("troopCarry");
        if (fragment != null) {
            JSONObject fragments = fragment.getJSONObject("fragments");
            if (fragments != null) {
                java.util.Set<String> want = new java.util.HashSet<>();
                if (carry != null) {
                    for (int i = 0; i < carry.size(); i++) {
                        String idx = carry.getString(i);
                        want.add("f_" + idx);
                        want.add(idx);
                    }
                }
                for (String key : fragments.keySet()) {
                    JSONObject f = fragments.getJSONObject(key);
                    boolean used = want.contains(key) || want.contains(String.valueOf(f.getIntValue("index")))
                            || want.contains(f.getString("index"));
                    f.put("used", used);
                    f.put("ei", -1);
                }
                int ei = 0;
                if (carry != null) {
                    for (int i = 0; i < carry.size(); i++) {
                        String idx = carry.getString(i);
                        JSONObject f = fragments.getJSONObject(idx);
                        if (f == null) {
                            f = fragments.getJSONObject("f_" + idx);
                        }
                        if (f != null) {
                            f.put("used", true);
                            f.put("ei", ei++);
                        }
                    }
                }
                int total = 0;
                for (String key : fragments.keySet()) {
                    JSONObject f = fragments.getJSONObject(key);
                    if (f.getBooleanValue("used")) {
                        total += f.getIntValue("weight");
                    }
                }
                int limit = fragment.getIntValue("limitWeight");
                fragment.put("totalWeight", total);
                fragment.put("overWeight", Math.max(0, total - Math.max(0, limit)));
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 丢失构想碎片：{"fragmentIndex":"f_7"} → 从 fragments 删除 */
    @PostMapping(value = {"/loseFragment"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject loseFragment(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String index = body == null ? null : body.getString("fragmentIndex");
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        JSONObject fragments = fragment == null ? null : fragment.getJSONObject("fragments");
        if (fragments != null && index != null) {
            if (fragments.containsKey(index)) {
                fragments.remove(index);
            } else if (fragments.containsKey("f_" + index)) {
                fragments.remove("f_" + index);
            }
            ArknightsApplication.LOGGER.info("[rlv2] 丢失构想碎片 {}", index);
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ─────────────────── 节点重掷（rerollNode，仅 rogue_4 开放）───────────────────

    /**
     * 重掷节点。官服 B服 抓包（2026-11-04 rogue_4）：
     *   请求 {"nodeIndex":"0"}，响应只带 rlv2（nodes 全量替换，pending/state 不变）；
     *   消耗 gameConst.refreshNodeItemId（rogue_4 = rogue_4_fragment_I_1 构想碎片，
     *   rogue_1/2/3 无该字段、rogue_5/6 为空串 → 不开放）。
     * 新类型：保持本层节点类型分布（按出现频次加权），排除
     *   ① 自身当前类型（重掷必须换）② onceNodeTypeList（PORTAL/MISSION 等一次性节点）。
     */
    @PostMapping(value = {"/rerollNode"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject rerollNode(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String nodeIndex = body == null ? null : body.getString("nodeIndex");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/rerollNode node=" + nodeIndex);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        String costId = gameConst == null ? null : gameConst.getString("refreshNodeItemId");
        if (costId == null || costId.isEmpty()) {
            ArknightsApplication.LOGGER.info("[rlv2] 主题 {} 不开放节点重掷", theme);
            JSONObject out = delta(cur);
            out.put("result", 0);
            return out;
        }
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones == null ? null : zones.getJSONObject(String.valueOf(cursor.getIntValue("zone")));
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null || nodeIndex == null || !nodes.containsKey(nodeIndex)) {
            JSONObject out = delta(cur);
            out.put("result", 0);
            return out;
        }
        // 扣消耗（构想碎片在 module.fragment.fragments，其余类型退到 inventory）
        if (!consumeCost(cur, theme, costId)) {
            ArknightsApplication.LOGGER.info("[rlv2] 重掷失败：{} 不足", costId);
            JSONObject out = delta(cur);
            out.put("result", 0);
            return out;
        }
        // 按本层分布重抽类型
        JSONObject target = nodes.getJSONObject(nodeIndex);
        int oldType = target.getIntValue("type");
        JSONArray onceTypes = gameConst.getJSONArray("onceNodeTypeList");
        java.util.Map<Integer, Integer> pool = new java.util.LinkedHashMap<>();
        for (String key : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(key);
            if (n.getBooleanValue("zone_end")) {
                continue;
            }
            int t = n.getIntValue("type");
            if (t <= 0 || t == oldType) {
                continue;
            }
            String name = nodeTypeName(theme, t);
            if (onceTypes != null && name != null && onceTypes.contains(name)) {
                continue;
            }
            pool.merge(t, 1, Integer::sum);
        }
        int newType = oldType;
        int total = 0;
        for (int v : pool.values()) {
            total += v;
        }
        if (total > 0) {
            int roll = RANDOM.nextInt(total);
            int acc = 0;
            for (java.util.Map.Entry<Integer, Integer> e : pool.entrySet()) {
                acc += e.getValue();
                if (roll < acc) {
                    newType = e.getKey();
                    break;
                }
            }
        }
        target.put("type", newType);
        ArknightsApplication.LOGGER.info("[rlv2] 重掷节点 {}：{} → {}", nodeIndex, oldType, newType);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        out.put("result", 1);
        return out;
    }

    /** 类型位 → 类型名 */
    private static String nodeTypeName(String theme, int type) {
        java.util.List<String> names = com.hypergryph.arknights.core.function.RogueMapGen.nodeTypeNames(theme);
        for (int i = 0; i < names.size(); i++) {
            if ((1 << i) == type) {
                return names.get(i);
            }
        }
        return null;
    }

    /**
     * 初始化 rogue_4 的构想系统：module.fragment =
     * {totalWeight, limitWeight, overWeight, fragments, troopWeights, troopCarry, currInspiration}
     * 开局发 3 枚 refreshNodeItemId 碎片（rogue_4_fragment_I_1）。
     */
    private static void seedFragmentModule(JSONObject cur, String theme) {
        if (!"rogue_4".equals(theme)) {
            return;
        }
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        String costId = gameConst == null ? null : gameConst.getString("refreshNodeItemId");
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject fragment = new JSONObject(true);
        fragment.put("totalWeight", 0);
        fragment.put("limitWeight", 10);
        fragment.put("overWeight", 0);
        JSONObject fragments = new JSONObject(true);
        if (costId != null && !costId.isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                JSONObject one = new JSONObject(true);
                one.put("instId", "f_" + i);
                one.put("id", costId);
                one.put("value", 1);
                one.put("weight", 1);
                one.put("ts", ArknightsApplication.getTimestamp());
                fragments.put("f_" + i, one);
            }
        }
        fragment.put("fragments", fragments);
        fragment.put("troopWeights", new JSONObject(true));
        fragment.put("troopCarry", 0);
        fragment.put("currInspiration", 0);
        module.put("fragment", fragment);
    }

    /** 扣掉一次重掷消耗；构想碎片走 module.fragment.fragments，其它走 inventory */
    private static boolean consumeCost(JSONObject cur, String theme, String costId) {
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        JSONObject fragments = fragment == null ? null : fragment.getJSONObject("fragments");
        if (fragments != null) {
            for (String key : new java.util.ArrayList<>(fragments.keySet())) {
                JSONObject f = fragments.getJSONObject(key);
                String id = f == null ? null : f.getString("id");
                if (costId.equals(id) || costId.equals(key)) {
                    fragments.remove(key);
                    ArknightsApplication.LOGGER.info("[rlv2] 消耗构想碎片 {}", costId);
                    return true;
                }
            }
        }
        JSONObject inv = cur.getJSONObject("inventory");
        for (String bucket : new String[]{"relic", "consumable", "exploreTool"}) {
            JSONObject b = inv.getJSONObject(bucket);
            if (b == null) {
                continue;
            }
            for (String key : new java.util.ArrayList<>(b.keySet())) {
                JSONObject it = b.getJSONObject(key);
                String id = it == null ? null : it.getString("id");
                if (costId.equals(id)) {
                    int c = it.getIntValue("count");
                    if (c > 1) {
                        it.put("count", c - 1);
                    } else {
                        b.remove(key);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    // ───────────────────────── 跨局银行（bankPut / bankWithdraw）─────────────────────────

    /**
     * 存钱进「前瞻性投资系统」。
     * 官服实测（B服 2026-11-04）：请求体为空 {}，一次移动 bank.cost(=1) 枚源石锭；
     * bank.current 增加，gold 减少；bankMaxGold(gameConst, 默认 999) 为上限。
     * 本轮存入总额记在 bank.totalPut（用于 bankRewards 里程碑结算）。
     */
    @PostMapping(value = {"/bankPut"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject bankPut(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject body,
                              HttpServletRequest request, HttpServletResponse response) {
        return bankMove(secret, true);
    }

    /** 从银行取钱：一次取 bank.cost 枚，受 bank.withdrawLimit 限制（rogue_6 = 12）。 */
    @PostMapping(value = {"/bankWithdraw"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject bankWithdraw(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject body,
                                   HttpServletRequest request, HttpServletResponse response) {
        return bankMove(secret, false);
    }

    private JSONObject bankMove(String secret, boolean put) {
        ArknightsApplication.LOGGER.info("[rlv2] /bank" + (put ? "Put" : "Withdraw"));
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject shop = findShop(cur);
        if (shop == null) {
            return bad("当前不在商店", 1);
        }
        JSONObject bank = shop.getJSONObject("bank");
        if (bank == null) {
            bank = new JSONObject(true);
            bank.put("open", true);
            bank.put("canPut", true);
            bank.put("canWithdraw", true);
            bank.put("withdraw", 0);
            bank.put("cost", 1);
            shop.put("bank", bank);
        }
        int cost = bank.getIntValue("cost");
        if (cost <= 0) {
            cost = 1;
        }
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        int bankMax = gameConst == null ? 999 : gameConst.getIntValue("bankMaxGold");
        if (bankMax <= 0) {
            bankMax = 999;
        }
        int limit = bank.getIntValue("withdrawLimit");
        if (limit <= 0 && gameConst != null) {
            limit = gameConst.getIntValue("bankDrawLimit");
        }

        JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
        JSONObject status = cur.getJSONObject("status");
        if (status == null) {
            status = new JSONObject(true);
            status.put("bankPut", 0);
            cur.put("status", status);
        }
        if (put) {
            int current = status.getIntValue("bankPut");
            int gold = prop.getIntValue("gold");
            int move = Math.min(cost, gold);
            if (current + move > bankMax) {
                move = Math.max(0, bankMax - current);
            }
            if (move > 0) {
                prop.put("gold", gold - move);
                status.put("bankPut", current + move);
                status.put("bankTotalPut", status.getIntValue("bankTotalPut") + move);
                recInc(cur, "cntSaveGold", move);
            }
        } else {
            int current = status.getIntValue("bankPut");
            int withdrawn = bank.getIntValue("withdraw");
            int move = Math.min(cost, current);
            if (limit > 0 && withdrawn + move > limit) {
                move = Math.max(0, limit - withdrawn);
            }
            if (move > 0) {
                prop.put("gold", prop.getIntValue("gold") + move);
                status.put("bankPut", current - move);
                bank.put("withdraw", withdrawn + move);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 从 pending 里找正在进行的商店 */

    @PostMapping(value = {"/giveUpGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject giveUpGame(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        // 官服语义（B服 2026-11-04 抓包）：放弃 → 推 GAME_SETTLE 待办，客户端随后再调 gameSettle
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        JSONObject player = cur.getJSONObject("player");
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_1");
        pend.put("type", "GAME_SETTLE");
        pend.put("content", new JSONObject(true));
        JSONArray arr = new JSONArray();
        arr.add(pend);
        player.put("pending", arr);
        player.put("state", "PENDING");
        JSONObject rec = cur.getJSONObject("record");
        if (rec == null) {
            rec = new JSONObject(true);
            cur.put("record", rec);
        }
        rec.put("giveUp", true);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        out.put("result", 1);
        return out;
    }

    @PostMapping(value = {"/gameSettle"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gameSettle(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        return endGame(secret, "gameSettle");
    }

    private JSONObject endGame(String secret, String kind) {
        ArknightsApplication.LOGGER.info("[rlv2] /" + kind);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject rl = userData.getJSONObject("rlv2");
        JSONObject cur = rl.getJSONObject("current");
        JSONObject game = cur.getJSONObject("game");
        String theme = game == null ? null : game.getString("theme");
        boolean giveUp = "giveUpGame".equals(kind);

        int level = currentLevel(cur);
        JSONObject player = cur.getJSONObject("player");
        JSONArray trace = player.getJSONArray("trace");
        int arrived = trace == null ? 0 : trace.size();
        // 通关判定：击败过 BOSS 节点即视为通关（官服 brief.success=1）
        JSONObject recNode0 = cur.getJSONObject("record");
        boolean clearedBoss = recNode0 != null && recNode0.getBooleanValue("clearedBoss");
        boolean success = clearedBoss;

        // 本局得分：官方 bp = buff.score（失败局不加分），这里成功才给分
        // 得分公式（近似）：官服 2026-11-03 实测一次「打到第 6 层、8 普通 + 1 首领、27 节点、530 击杀」
        // 的失败局，bp 从 229 → 462，即本局 +233；月度小队那局 bp 27 → 27（+0）。
        // PRTS：月度小队 / 深入探索不进行探索分数结算。
        String runMode = game.getString("mode");
        boolean noScoreMode = "MONTH_TEAM".equals(runMode) || game.getString("predefined") != null;
        JSONObject accForScore = cur.getJSONObject("record") == null ? null
                : cur.getJSONObject("record").getJSONObject("acc");
        int scoreBattles = accForScore == null ? 0 : accForScore.getIntValue("battles");
        int scoreKill = accForScore == null ? 0 : accForScore.getIntValue("kill");
        int scorePerfect = accForScore == null ? 0 : accForScore.getIntValue("perfect");

        JSONObject accObj = null;
        JSONObject recNode = cur.getJSONObject("record");
        if (recNode != null) {
            accObj = recNode.getJSONObject("acc");
            if (accObj != null && accObj.getIntValue("lose") > 0) {
                success = false;
            }
        }

        JSONObject brief = new JSONObject(true);
        brief.put("level", level);
        brief.put("over", true);
        brief.put("success", success ? 1 : 0);
        String toEnding = player.getString("toEnding");
        brief.put("ending", success && toEnding != null ? toEnding : "");
        brief.put("theme", theme);
        brief.put("mode", game.getString("mode"));
        brief.put("predefined", game.getString("predefined"));
        brief.put("band", "");
        brief.put("startTs", game.getLongValue("start"));
        brief.put("endTs", ArknightsApplication.getTimestamp());
        brief.put("endZoneId", "zone_" + level);
        JSONObject endProp = new JSONObject(true);
        JSONObject prop = player.getJSONObject("property");
        endProp.put("hp", prop.getJSONObject("hp").getIntValue("current"));
        endProp.put("gold", prop.getIntValue("gold"));
        endProp.put("populationCost", prop.getJSONObject("population").getIntValue("cost"));
        endProp.put("populationMax", prop.getJSONObject("population").getIntValue("max"));
        brief.put("endProperty", endProp);
        brief.put("modeGrade", game.getIntValue("modeGrade"));
        brief.put("seed", game.getString("seed") == null ? "" : game.getString("seed"));

        JSONObject record = new JSONObject(true);
        JSONObject recStat0 = cur.getJSONObject("record");
        int battles = accObj == null ? 0 : accObj.getIntValue("battles");
        int kill = accObj == null ? 0 : accObj.getIntValue("kill");
        int perfect = accObj == null ? 0 : accObj.getIntValue("perfect");
        record.put("cntZone", level);
        record.put("cntArrivedNode", arrived);
        record.put("cntBattleNormal", battles);
        record.put("cntBattleElite", 0);
        record.put("cntBattleBoss", 0);
        record.put("cntRecruitChar", cur.getJSONObject("troop").getJSONObject("chars").size());
        record.put("cntRecruitAssist", recStat0 == null ? 0 : recStat0.getIntValue("cntRecruitAssist"));
        record.put("cntRecruitFree", recStat0 == null ? 0 : recStat0.getIntValue("cntRecruitFree"));
        record.put("cntRecruitNpc", recStat0 == null ? 0 : recStat0.getIntValue("cntRecruitNpc"));
        if (recStat0 != null && recStat0.getJSONObject("cntRecruitProfession") != null) {
            record.put("cntRecruitProfession", recStat0.getJSONObject("cntRecruitProfession"));
        }
        record.put("cntUpgradeChar", 0);
        record.put("cntKillEnemy", kill);
        JSONObject recStat = cur.getJSONObject("record");
        record.put("cntShopBuy", recStat == null ? 0 : recStat.getIntValue("cntShopBuy"));
        record.put("cntPerfectBattle", perfect);
        record.put("cntShopSell", recStat == null ? 0 : recStat.getIntValue("cntShopSell"));
        record.put("cntUseGold", recStat == null ? 0 : recStat.getIntValue("cntUseGold"));
        record.put("cntSaveGold", recStat == null ? 0 : recStat.getIntValue("cntSaveGold"));
        record.put("cntGainRelic", recStat == null ? 0 : recStat.getIntValue("cntGainRelic"));
        record.put("cntUsePopulation", recStat == null ? 0 : recStat.getIntValue("cntUsePopulation"));
        record.put("cntUseTotem", recStat == null ? 0 : recStat.getIntValue("cntUseTotem"));
        record.put("cntUseAlchemy", recStat == null ? 0 : recStat.getIntValue("cntUseAlchemy"));
        record.put("cntUseItem", recStat == null ? 0 : recStat.getIntValue("cntUseItem"));
        record.put("cntUseCopper", recStat == null ? 0 : recStat.getIntValue("cntUseCopper"));
        record.put("cntMoveByScrap", recStat == null ? 0 : recStat.getIntValue("cntMoveByScrap"));
        record.put("cntBattlePawn", recStat == null ? 0 : recStat.getIntValue("cntBattlePawn"));
        if (recStat != null && recStat.getJSONObject("cntBattleByStage") != null) {
            record.put("cntBattleByStage", recStat.getJSONObject("cntBattleByStage"));
        }
        record.put("cntProtectBox", recStat == null ? 0 : recStat.getIntValue("cntProtectBox"));
        record.put("cntPassEnding", success ? 1 : 0);
        // ── 官方探索分数公式（PRTS 六主题页一致）──
        //   通过层数查表 + 通过步数×1 + 普通战斗×10 + 精英战斗×20 + 领袖战斗×30
        //   + 招募干员×2 + 获得物品×5，求和后 × 难度倍率
        //   倍率：古堡观光(EASY) ×0.5；正式调查(grade 0) ×1.0；grade 1..15 见 gradeBonus()
        int modeGrade = game.getIntValue("modeGrade");
        int baseScore = officialBaseScore(cur, record, game.getString("mode"));
        double gradeMul = difficultyMultiplier(themeOf(cur), game.getString("mode"), modeGrade);
        int score = noScoreMode ? 0 : (int) Math.round(baseScore * gradeMul);
        ArknightsApplication.LOGGER.info("[rlv2] 结算算分 mode={} base={} grade={} 倍率={} score={}（战斗{} 击杀{} 节点{} 层{} 完美{} 通关{}）",
                runMode, baseScore, modeGrade, String.format("%.2f", gradeMul), score,
                scoreBattles, scoreKill, arrived, level, scorePerfect, success);


        if (recStat != null && recStat.getJSONObject("cntNodeByType") != null) {
            record.put("cntNodeByType", recStat.getJSONObject("cntNodeByType"));
        }

        // 局外：bp / buff / collect / record
        JSONObject themeOuter = theme == null ? null : Rlv2Helper.outerTheme(userData, theme);
        int bpFrom = 0;
        int bpTo = 0;
        int scoreFrom = 0;
        int scoreTo = 0;
        double bonusForHistory = 0.0;
        if (themeOuter != null) {
            JSONObject bp = themeOuter.getJSONObject("bp");
            if (bp == null) {
                bp = new JSONObject(true);
                bp.put("point", 0);
                bp.put("reward", new JSONObject(true));
                themeOuter.put("bp", bp);
            }
            bpFrom = bp.getIntValue("point");
            double relicBonus = bpBonus(cur, theme == null ? "" : theme);
            int bpGain = (int) Math.round(score * (1.0 + relicBonus));
            bpTo = bpFrom + bpGain;
            bp.put("point", bpTo);
            int bpTotal = bpTo;

            JSONObject buff = themeOuter.getJSONObject("buff");
            if (buff == null) {
                buff = new JSONObject(true);
                buff.put("pointOwned", 0);
                buff.put("pointCost", 0);
                buff.put("unlocked", new JSONObject(true));
                buff.put("score", 0);
                themeOuter.put("buff", buff);
            }
            // buff.score 累加本局得分；pointOwned 按主题规则换算（rogue_1 用 bp/10，其余 score/200）
            scoreFrom = buff.getIntValue("score");
            bonusForHistory = relicBonus;
            buff.put("score", scoreFrom + score);
            scoreTo = buff.getIntValue("score");
            buff.put("pointOwned", computePointOwned(theme == null ? "" : theme, bpTo, buff.getIntValue("score")));

            JSONObject rec = themeOuter.getJSONObject("record");
            if (rec == null) {
                rec = new JSONObject(true);
                themeOuter.put("record", rec);
            }
            rec.put("last", ArknightsApplication.getTimestamp());

            // 本局拿到的收藏品 → collect 里标记「已获得」
            JSONObject collect = themeOuter.getJSONObject("collect");
            if (collect != null && collect.getJSONObject("relic") != null) {
                JSONObject owned = cur.getJSONObject("inventory").getJSONObject("relic");
                JSONObject col = collect.getJSONObject("relic");
                if (owned != null) {
                    for (String k : owned.keySet()) {
                        String id = owned.getJSONObject(k).getString("id");
                        JSONObject c = id == null ? null : col.getJSONObject(id);
                        if (c != null && c.getIntValue("state") < 1) {
                            c.put("state", 1);
                        }
                    }
                }
            }
        }

        // 写入 DSH 结算历史（供 bp 公式回归；失败不影响结算）
        com.hypergryph.arknights.core.dao.RogueRunHistoryDao.save(
                account.getUid(), brief, record, bpFrom, bpTo, scoreFrom, scoreTo, bonusForHistory);
        rl.put("current", Rlv2Helper.emptyCurrent());
        // 本局结束：清掉 roguelike_run 里的当前局记录（历史统计另行落表）
        com.hypergryph.arknights.core.dao.RogueRunDao.clear(account.getUid());

        JSONObject outerSummary = new JSONObject(true);
        String settleTheme = game.getString("theme");
        JSONObject missionResult = settleMonthMission(userData, settleTheme, record, level, scoreBattles > 0 ? 0 : 0);
        JSONObject mission = new JSONObject(true);
        mission.put("before", missionResult.getJSONArray("before"));
        mission.put("after", missionResult.getJSONArray("after"));
        outerSummary.put("mission", mission);
        String[] bpKeys = {"missionBp", "relicBp", "totemBp", "fragmentBp", "copperBp", "scrapBp"};
        for (String k : bpKeys) {
            JSONObject o = new JSONObject(true);
            o.put("cnt", 0);
            o.put("from", bpFrom);
            o.put("to", bpTo);
            outerSummary.put(k, o);
        }
        outerSummary.put("relicUnlock", new JSONArray());
        outerSummary.put("totemUnlock", new JSONArray());
        outerSummary.put("fragmentUnlock", new JSONArray());
        outerSummary.put("copperUnlock", new JSONArray());
        outerSummary.put("scrapUnlock", new JSONArray());
        outerSummary.put("gp", 0);

        userDao.setUserData(account.getUid(), userData);

        JSONObject gameOut = new JSONObject(true);
        gameOut.put("brief", brief);
        gameOut.put("record", record);
        JSONObject result = delta(rl.getJSONObject("current"));
        result.put("game", gameOut);
        result.put("outer", outerSummary);
        return result;
    }
}
