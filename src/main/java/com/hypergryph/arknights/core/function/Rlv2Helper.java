package com.hypergryph.arknights.core.function;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 集成战略（RL v2，接口前缀 /rlv2/）存档节点工具。
 *
 * 存档结构 user.rlv2（抓包自官服）：
 *   current { player{state,property,cursor,trace,pending,status,toEnding,chgEnding},
 *             map{zones}, troop{chars,expedition,...}, inventory{relic,recruit,trap,consumable},
 *             game{mode,predefined,theme,outer,start,modeGrade}, buff{tmpHP,capsule,squadBuff},
 *             record{brief}, module{} }
 *   outer   { rogue_1..rogue_6 → {bank,bp,buff,collect,mission,challenge,monthTeam,record,activity} }
 */
public final class Rlv2Helper {

    private static JSONObject TEMPLATE;

    private Rlv2Helper() {
    }

    /** outer 部分的模板（从官服存档抠出来，放进 data/rlv2_template.json） */
    public static synchronized JSONObject template() {
        if (TEMPLATE == null) {
            String path = System.getProperty("user.dir") + "/data/rlv2_template.json";
            try {
                TEMPLATE = JSONObject.parseObject(
                        new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[rlv2] 读取 rlv2_template.json 失败", e);
                TEMPLATE = new JSONObject(true);
            }
        }
        return TEMPLATE;
    }

    /** 空的 current（未开局） */
    public static JSONObject emptyCurrent() {
        JSONObject cur = new JSONObject(true);

        JSONObject prop = new JSONObject(true);
        prop.put("exp", 0);
        prop.put("level", 1);
        prop.put("maxLevel", 0);
        JSONObject hp = new JSONObject(true);
        hp.put("current", 0);
        hp.put("max", 0);
        prop.put("hp", hp);
        prop.put("gold", 0);
        prop.put("shield", 0);
        prop.put("capacity", 0);
        JSONObject pop = new JSONObject(true);
        pop.put("cost", 0);
        pop.put("max", 0);
        prop.put("population", pop);
        prop.put("conPerfectBattle", 0);

        JSONObject player = new JSONObject(true);
        player.put("state", "NONE");
        player.put("property", prop);
        JSONObject cursor = new JSONObject(true);
        cursor.put("zone", 0);
        cursor.put("position", null);
        player.put("cursor", cursor);
        player.put("trace", new JSONArray());
        player.put("pending", new JSONArray());
        JSONObject status = new JSONObject(true);
        status.put("bankPut", 0);
        player.put("status", status);
        player.put("toEnding", "");
        player.put("chgEnding", false);
        cur.put("player", player);

        JSONObject map = new JSONObject(true);
        map.put("zones", new JSONObject(true));
        cur.put("map", map);

        JSONObject troop = new JSONObject(true);
        troop.put("chars", new JSONObject(true));
        troop.put("expedition", new JSONArray());
        troop.put("expeditionReturn", null);
        troop.put("hasExpeditionReturn", false);
        cur.put("troop", troop);

        JSONObject inv = new JSONObject(true);
        inv.put("relic", new JSONObject(true));
        inv.put("recruit", new JSONObject(true));
        inv.put("trap", null);
        inv.put("consumable", new JSONObject(true));
        cur.put("inventory", inv);

        JSONObject game = new JSONObject(true);
        game.put("mode", "NONE");
        game.put("predefined", null);
        game.put("theme", "");
        JSONObject outer = new JSONObject(true);
        outer.put("support", false);
        game.put("outer", outer);
        game.put("start", -1);
        game.put("modeGrade", 0);
        cur.put("game", game);

        JSONObject buff = new JSONObject(true);
        buff.put("tmpHP", 0);
        buff.put("capsule", null);
        buff.put("squadBuff", new JSONArray());
        cur.put("buff", buff);

        JSONObject record = new JSONObject(true);
        record.put("brief", null);
        cur.put("record", record);

        cur.put("module", new JSONObject(true));
        return cur;
    }

    /**
     * 保证 user.rlv2 存在且结构完整。
     *
     * @return true 表示有改动
     */
    public static boolean ensure(JSONObject user) {
        if (user == null) {
            return false;
        }
        boolean changed = false;
        JSONObject rl = user.getJSONObject("rlv2");
        if (rl == null) {
            rl = new JSONObject(true);
            JSONObject tpl = template();
            if (tpl != null && tpl.getJSONObject("outer") != null) {
                rl.put("outer", JSONObject.parseObject(tpl.getJSONObject("outer").toJSONString()));
            } else {
                rl.put("outer", new JSONObject(true));
            }
            rl.put("current", emptyCurrent());
            user.put("rlv2", rl);
            changed = true;
        }
        if (rl.getJSONObject("current") == null) {
            rl.put("current", emptyCurrent());
            changed = true;
        }
        if (rl.getJSONObject("outer") == null) {
            rl.put("outer", new JSONObject(true));
            changed = true;
        }
        // 补齐 6 个主题的骨架（缺哪个补哪个，不覆盖已有数据）
        JSONObject outer = rl.getJSONObject("outer");
        for (String theme : new String[]{"rogue_1", "rogue_2", "rogue_3", "rogue_4", "rogue_5", "rogue_6"}) {
            if (outer.containsKey(theme)) {
                continue;
            }
            JSONObject tplTheme = template().getJSONObject("outer") == null ? null
                    : template().getJSONObject("outer").getJSONObject(theme);
            if (tplTheme != null) {
                outer.put(theme, JSONObject.parseObject(tplTheme.toJSONString()));
                changed = true;
            }
        }
        return changed;
    }

    /** outer.<theme> 下的某个子节点，缺失则建 */
    public static JSONObject outerTheme(JSONObject user, String theme) {
        JSONObject rl = user.getJSONObject("rlv2");
        JSONObject outer = rl == null ? null : rl.getJSONObject("outer");
        if (outer == null) {
            return null;
        }
        JSONObject t = outer.getJSONObject(theme);
        if (t == null) {
            t = new JSONObject(true);
            outer.put(theme, t);
        }
        return t;
    }
}
