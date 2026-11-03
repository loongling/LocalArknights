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

    private static JSONObject themeDetails(String theme) {
        JSONObject topic = ArknightsApplication.roguelikeTable == null
                ? null : ArknightsApplication.roguelikeTable.getJSONObject("details");
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
        JSONObject chars = userData.getJSONObject("troop") == null ? null
                : userData.getJSONObject("troop").getJSONObject("chars");
        if (chars == null || chars.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>(chars.keySet());
        Collections.shuffle(keys, RANDOM);
        JSONObject src = chars.getJSONObject(keys.get(0));
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

    /** 进入第 level 层（1 基），index = 1000 + level - 1 */
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
            zones.put(key, zoneTemplateFor(level));
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

        rl.put("current", cur);
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
            }
        }
        removePending(cur, "GAME_INIT_RELIC");
        userDao.setUserData(account.getUid(), userData);
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
        return delta(cur);
    }

    @PostMapping(value = {"/recruitChar"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject recruitChar(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject body,
                                  HttpServletRequest request, HttpServletResponse response) {
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
                    ticket.put("result", newChar);
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = delta(cur);
        result.put("chars", outChars);
        return result;
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
        return delta(cur);
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
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = delta(cur);
        if (battle) {
            result.put("battleId", UUID.randomUUID().toString());
        }
        return result;
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
        boolean win = true;
        int killed = 0;
        int missed = 0;
        if (meta != null) {
            int gameResult = meta.getIntValue("gameResult");
            win = gameResult == 1;
            killed = meta.getIntValue("killedEnemiesCnt");
            missed = meta.getIntValue("missedEnemiesCnt");
            int remain = meta.getIntValue("remainingLifePoint");
            JSONObject hp = prop.getJSONObject("hp");
            hp.put("current", remain);
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
        player.put("state", "PENDING");
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
        brief.put("seed", "");
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
        cur.getJSONObject("player").put("state", "WAIT_MOVE");
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
        boolean leave = isLeaveChoice(cur, choice);
        player.put("state", leave ? "WAIT_MOVE" : "PENDING");
        userDao.setUserData(account.getUid(), userData);
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
                String itemId = good.getString("itemId");
                applyShopItem(userData, cur, itemId, 1);
                ArknightsApplication.LOGGER.info("[rlv2] 商店购买 {} → {}（-{} 源石锭）", itemId, price, price);
            }
        }
        JSONObject player = cur.getJSONObject("player");
        if (leave) {
            removePending(cur, "SHOP");
            player.put("state", "WAIT_MOVE");
        } else {
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        JSONObject result = delta(cur);
        result.put("items", new JSONArray());
        return result;
    }

    /** 从 pending 里找正在进行的商店 */
    private static JSONObject findShop(JSONObject cur) {
        JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
        if (pending == null) {
            return null;
        }
        for (int i = 0; i < pending.size(); i++) {
            JSONObject p = pending.getJSONObject(i);
            if (!"SHOP".equals(p.getString("type"))) {
                continue;
            }
            JSONObject content = p.getJSONObject("content");
            JSONObject shop = content == null ? null : content.getJSONObject("shop");
            if (shop != null) {
                return shop;
            }
        }
        return null;
    }

    /** 购买/获得一件物品，落到对应的存档位置 */
    private static void applyShopItem(JSONObject userData, JSONObject cur, String itemId, int count) {
        if (itemId == null) {
            return;
        }
        if (itemId.contains("_relic")) {
            addRelic(cur, itemId, Math.max(count, 1));
        } else if (itemId.contains("recruit_ticket") || itemId.contains("upgrade_ticket")) {
            addRecruitTicket(cur, itemId, itemId.contains("upgrade_ticket"));
        } else if (itemId.endsWith("_hp")) {
            JSONObject hp = cur.getJSONObject("player").getJSONObject("property").getJSONObject("hp");
            hp.put("current", hp.getIntValue("current") + Math.max(count, 1));
        } else if (itemId.endsWith("_gold")) {
            JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
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

    // ───────────────────────────── 结束 ─────────────────────────────

    @PostMapping(value = {"/giveUpGame"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject giveUpGame(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody(required = false) JSONObject body,
                                 HttpServletRequest request, HttpServletResponse response) {
        return endGame(secret, "giveUpGame");
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
        boolean success = false;              // 没有通关判定数据，默认按失败/终止结算

        // 本局得分：官方 bp = buff.score（失败局不加分），这里成功才给分
        int score = success ? Math.max(10, level * 10) : 0;

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
        brief.put("ending", cur.getJSONObject("player").getString("toEnding") == null ? ""
                : cur.getJSONObject("player").getString("toEnding"));
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
        brief.put("seed", "");

        JSONObject record = new JSONObject(true);
        int battles = accObj == null ? 0 : accObj.getIntValue("battles");
        int kill = accObj == null ? 0 : accObj.getIntValue("kill");
        int perfect = accObj == null ? 0 : accObj.getIntValue("perfect");
        record.put("cntZone", level);
        record.put("cntArrivedNode", arrived);
        record.put("cntBattleNormal", battles);
        record.put("cntBattleElite", 0);
        record.put("cntBattleBoss", 0);
        record.put("cntRecruitChar", cur.getJSONObject("troop").getJSONObject("chars").size());
        record.put("cntUpgradeChar", 0);
        record.put("cntKillEnemy", kill);
        record.put("cntShopBuy", 0);
        record.put("cntPerfectBattle", perfect);

        // 局外：bp / buff / collect / record
        JSONObject themeOuter = theme == null ? null : Rlv2Helper.outerTheme(userData, theme);
        int bpFrom = 0;
        int bpTo = 0;
        if (themeOuter != null) {
            JSONObject bp = themeOuter.getJSONObject("bp");
            if (bp == null) {
                bp = new JSONObject(true);
                bp.put("point", 0);
                bp.put("reward", new JSONObject(true));
                themeOuter.put("bp", bp);
            }
            bpFrom = bp.getIntValue("point");
            bpTo = bpFrom + score;
            bp.put("point", bpTo);

            JSONObject buff = themeOuter.getJSONObject("buff");
            if (buff == null) {
                buff = new JSONObject(true);
                buff.put("pointOwned", 0);
                buff.put("pointCost", 0);
                buff.put("unlocked", new JSONObject(true));
                buff.put("score", 0);
                themeOuter.put("buff", buff);
            }
            buff.put("score", score);
            if (score > 0) {
                buff.put("pointOwned", buff.getIntValue("pointOwned") + score / 10);
            }

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

        rl.put("current", Rlv2Helper.emptyCurrent());

        JSONObject outerSummary = new JSONObject(true);
        JSONObject mission = new JSONObject(true);
        mission.put("before", new JSONArray());
        mission.put("after", new JSONArray());
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
