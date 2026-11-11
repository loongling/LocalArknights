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

@RestController
@RequestMapping("/rlv2")
public class rlv2 {

    private static final Random RANDOM = new Random();

    private static JSONObject ZONE_TEMPLATES;

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

    // ───────── 集成战略招募（官服 007_02~007_08 实测）─────────
    //   chooseInitialRecruitSet {"select":"recruit_group_2"} → 3 张券 state=0 list=[]
    //   activeRecruitTicket    {"id":"t_3"}                  → state=1 list=候选(36/34 个)
    //   recruitChar            {"ticketIndex":"t_3","optionId":"34"} → state=2 list=[] result=选中的那个
    private static final String[][] RECRUIT_GROUP_PROFESSIONS = {
            {"recruit_group_1", "PIONEER", "SNIPER", "SPECIAL"},
            {"recruit_group_2", "TANK", "CASTER", "SNIPER"},
            {"recruit_group_3", "WARRIOR", "SUPPORT", "MEDIC"},
    };

    /** 招募组合 → 该主题的 3 张招募券 id（按职业匹配 recruitTickets） */
    private static java.util.List<String> ticketIdsForGroup(String theme, String group) {
        java.util.List<String> out = new java.util.ArrayList<>();
        JSONObject details = themeDetails(theme);
        JSONObject tickets = details == null ? null : details.getJSONObject("recruitTickets");
        if (tickets == null || tickets.isEmpty()) {
            return out;
        }
        java.util.List<String> profs = new java.util.ArrayList<>();
        for (String[] g : RECRUIT_GROUP_PROFESSIONS) {
            if (g[0].equals(group)) {
                for (int i = 1; i < g.length; i++) {
                    profs.add(g[i]);
                }
            }
        }
        if (profs.isEmpty()) {
            java.util.List<String> all = new java.util.ArrayList<>(tickets.keySet());
            java.util.Collections.shuffle(all, RANDOM);
            return all.subList(0, Math.min(3, all.size()));
        }
        for (String prof : profs) {
            for (String k : tickets.keySet()) {
                JSONObject t = tickets.getJSONObject(k);
                if (t != null && prof.equals(t.getString("profession"))) {
                    out.add(k);
                    break;
                }
            }
        }
        return out;
    }

    private static int intOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 该局队伍里是否已有这个干员（返回现有节点，没找到返回 null） */
    private static JSONObject ownedInRun(JSONObject cur, String charId) {
        JSONObject troop = cur.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        if (chars == null) {
            return null;
        }
        for (String k : chars.keySet()) {
            JSONObject one = chars.getJSONObject(k);
            if (one != null && charId.equals(one.getString("charId"))) {
                return one;
            }
        }
        return null;
    }

    /** 这个干员最高能到哪个精英化阶段（1★/2★ 只有 0，3★ 到 1，4★+ 到 2） */
    private static int maxEvolvePhase(String charId) {
        JSONObject table = ArknightsApplication.characterJson;
        JSONObject def = table == null ? null : table.getJSONObject(charId);
        JSONArray phases = def == null ? null : def.getJSONArray("phases");
        if (phases == null || phases.isEmpty()) {
            return 0;
        }
        return phases.size() - 1;
    }

    /** character_table 里某个精英化阶段（0/1/2）的等级上限 */
    private static int maxLevelOfPhase(String charId, int phase) {
        JSONObject table = ArknightsApplication.characterJson;
        JSONObject def = table == null ? null : table.getJSONObject(charId);
        JSONArray phases = def == null ? null : def.getJSONArray("phases");
        if (phases == null || phases.isEmpty()) {
            return 1;
        }
        int idx = Math.max(0, Math.min(phase, phases.size() - 1));
        return Math.max(1, phases.getJSONObject(idx).getIntValue("maxLevel"));
    }

    /**
     * 招募候选的精英化规则（官服抓包实测，同一局内重复招募同一人）：
     *   首次出现 → evolvePhase=1 / isUpgrade=false / upgradeLimited=true
     *   再次出现 → evolvePhase=2 / isUpgrade=true  / upgradeLimited=false
     *   已到 2 → 不再出现在候选里
     */
    private static void applyRecruitPhase(JSONObject cand, JSONObject cur) {
        String charId = cand.getString("charId");
        if (charId == null) {
            cand.put("_skip", true);
            return;
        }
        int maxPhase = maxEvolvePhase(charId);
        JSONObject owned = ownedInRun(cur, charId);
        int ownedPhase = owned == null ? -1 : owned.getIntValue("evolvePhase");
        // 已经到该干员最高阶段（1★/2★ 的首招就是 0）→ 不再出现
        if (owned != null && ownedPhase >= maxPhase) {
            cand.put("_skip", true);
            return;
        }
        int phase = ownedPhase < 0 ? Math.min(1, maxPhase) : Math.min(ownedPhase + 1, maxPhase);
        boolean upgrade = ownedPhase >= 0 && phase > ownedPhase;
        cand.put("evolvePhase", phase);
        cand.put("level", maxLevelOfPhase(charId, phase));
        cand.put("isUpgrade", upgrade);
        cand.put("upgradeLimited", phase < maxPhase);
        cand.put("upgradePhase", upgrade ? 1 : 0);
    }

    /**
     * rogue_6 的网格渲染插件（RL06DungeonGridZonePlugin）要求 player.cursor.position 非空，
     * 否则客户端直接报 "[rl06] _SetNodesCustomViewModelDistance fail ... gets null"，
     * 地图整块不渲染。这里把玩家放到最左列（同列取 y 最小）的节点上。
     */
    private static void placeOnGridEntry(JSONObject cur, JSONObject zone) {
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        JSONObject best = null;
        int bestX = Integer.MAX_VALUE;
        int bestY = Integer.MAX_VALUE;
        for (String nk : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(nk);
            JSONObject pos = n == null ? null : n.getJSONObject("pos");
            if (pos == null || n.getIntValue("type") == 268435456) {
                // EMPTY（空洞）节点不能站人，客户端会报「非法位置」
                continue;
            }
            int x = pos.getIntValue("x");
            int y = pos.getIntValue("y");
            boolean better = x < bestX || (x == bestX && y < bestY);
            if (better) {
                bestX = x;
                bestY = y;
                best = n;
            }
        }
        if (best == null) {
            return;
        }
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        cursor.put("position", best.getJSONObject("pos"));
        best.put("visibility", 1);
        ArknightsApplication.LOGGER.info("[rlv2] 网格入口落点 ({},{})", bestX, bestY);
    }

    /** config.rogue.enableAllChar：true 时招募券候选取「全部干员」而不只是玩家自己的干员库 */
    private static boolean enableAllChar() {
        JSONObject cfg = ArknightsApplication.serverConfig;
        JSONObject rogue = cfg == null ? null : cfg.getJSONObject("rogue");
        return rogue != null && rogue.getBooleanValue("enableAllChar");
    }

    /**
     * 用 character_table 造一个候选干员（未拥有的干员也能出现在券里）。
     * 参照官服候选形态：技能全满（specializeLevel=3）、等级受精英化上限限制。
     */
    private static JSONObject buildCharFromTable(String charId) {
        JSONObject table = ArknightsApplication.characterJson;
        JSONObject def = table == null ? null : table.getJSONObject(charId);
        if (def == null) {
            return null;
        }
        JSONArray phases = def.getJSONArray("phases");
        int phase = phases == null || phases.isEmpty() ? 0 : phases.size() - 1;
        int level = 1;
        if (phases != null && !phases.isEmpty()) {
            JSONObject last = phases.getJSONObject(phases.size() - 1);
            level = Math.max(1, last.getIntValue("maxLevel"));
        }
        JSONObject c = new JSONObject(true);
        c.put("instId", "0");
        c.put("charId", charId);
        c.put("type", "NORMAL");
        c.put("favorPoint", 25570);
        c.put("potentialRank", Math.max(0, def.getIntValue("maxPotentialLevel")));
        c.put("mainSkillLvl", 7);
        c.put("skin", charId + "#1");
        c.put("level", level);
        c.put("exp", 0);
        c.put("evolvePhase", phase);
        c.put("defaultSkillIndex", 0);  // 合法下标（0）
        JSONArray skills = new JSONArray();
        JSONArray defSkills = def.getJSONArray("skills");
        if (defSkills != null) {
            for (int i = 0; i < defSkills.size(); i++) {
                JSONObject sk = defSkills.getJSONObject(i);
                String skillId = sk.getString("skillId");
                if (skillId == null) {
                    continue;
                }
                JSONObject one = new JSONObject(true);
                one.put("skillId", skillId);
                one.put("unlock", 1);
                one.put("state", 0);
                one.put("specializeLevel", 3);
                one.put("completeUpgradeTime", -1);
                skills.add(one);
            }
        }
        c.put("skills", skills);
        c.put("upgradeLimited", false);
        c.put("upgradePhase", 0);
        c.put("isUpgrade", false);
        c.put("isCure", "MEDIC".equals(def.getString("profession")));
        c.put("population", 0);
        c.put("charBuff", new JSONArray());
        c.put("troopInstId", "0");
        c.put("master", new JSONObject(true));
        return c;
    }

    private static String charProfession(String charId) {
        JSONObject table = ArknightsApplication.characterJson;
        JSONObject one = table == null ? null : table.getJSONObject(charId);
        return one == null ? null : one.getString("profession");
    }

    /** 招募券 id → 职业（rogue_2_recruit_ticket_tank → TANK） */
    private static String professionOfTicket(String theme, String ticketId) {
        JSONObject details = themeDetails(theme);
        JSONObject tickets = details == null ? null : details.getJSONObject("recruitTickets");
        JSONObject t = tickets == null || ticketId == null ? null : tickets.getJSONObject(ticketId);
        return t == null ? null : t.getString("profession");
    }


    private static JSONObject bad(String msg, int code) {
        JSONObject o = new JSONObject(true);
        o.put("result", code);
        o.put("error", msg);
        return o;
    }

    /** 递归剥掉以 _ 开头的服务端私有键（不下发给客户端） */
    private static void stripPrivate(JSONObject o) {
        if (o == null) {
            return;
        }
        for (String k : new java.util.ArrayList<>(o.keySet())) {
            if (k.startsWith("_")) {
                o.remove(k);
            } else {
                Object v = o.get(k);
                if (v instanceof JSONObject) {
                    stripPrivate((JSONObject) v);
                }
            }
        }
    }

    /** 下发前兜底：已定型的事件/商店格必须带 realContent.scene（否则客户端画不出图标） */
    private static void ensureScenesOnNodes(JSONObject current) {
        try {
            JSONObject game = current.getJSONObject("game");
            String theme = game == null ? null : game.getString("theme");
            JSONObject zones = current.getJSONObject("map") == null ? null
                    : current.getJSONObject("map").getJSONObject("zones");
            if (theme == null || zones == null) {
                return;
            }
            for (String zk : zones.keySet()) {
                JSONObject zone = zones.getJSONObject(zk);
                JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
                if (nodes == null) {
                    continue;
                }
                for (String nk : nodes.keySet()) {
                    JSONObject n = nodes.getJSONObject(nk);
                    int t = n.getIntValue("type");
                    if (t == 1 || t == 2 || t == 4 || t == 256 || t == 268435456
                            || t == 8388608 || t == 16777216) {
                        continue;
                    }
                    if (n.getJSONObject("realContent") != null) {
                        continue;
                    }
                    String sc = sceneForTypeName(theme, typeNameOf(theme, t), nk);
                    if (sc != null) {
                        JSONObject rc = new JSONObject(true);
                        rc.put("scene", sc);
                        n.put("realContent", rc);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONObject delta(JSONObject current) {
        ensureScenesOnNodes(current);
        stripPrivate(current);
        JSONObject rl = new JSONObject(true);
        // 回到全量下发：之前为省流量只发变化子树，客户端部分接口会报「未知系统错误」
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


    /**
     * rogue_6 结局按持有收藏品动态判定（PRTS「沉沦者的黑流树海」结局规则）：
     *   持有【怦然信标】(rogue_6_relic_final_3)            → ro6_ending_3 纠缠，调和
     *   持有【谒日之祈】(_7) 或【祭日之契】(_8)            → ro6_ending_4 旧日重燃
     *   同时持有【沙盘α】(_1)+【沙盘β】(_2)               → ro6_ending_2 维度重构
     *   否则                                               → ro6_ending_1 强制重启（默认）
     */
    private static String resolveEndingRogue6(JSONObject cur, String fallback) {
        try {
            JSONObject rec = cur.getJSONObject("record");
            java.util.Set<String> have = new java.util.HashSet<>();
            Object rl = rec == null ? null : rec.get("relicList");
            if (rl instanceof com.alibaba.fastjson.JSONArray) {
                com.alibaba.fastjson.JSONArray arr = (com.alibaba.fastjson.JSONArray) rl;
                for (int i = 0; i < arr.size(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof JSONObject) {
                        String id = ((JSONObject) o).getString("id");
                        if (id != null) have.add(id);
                    } else if (o instanceof String) {
                        have.add((String) o);
                    }
                }
            } else if (rl instanceof com.alibaba.fastjson.JSONObject) {
                com.alibaba.fastjson.JSONObject obj = (com.alibaba.fastjson.JSONObject) rl;
                for (String k : obj.keySet()) {
                    JSONObject one = obj.getJSONObject(k);
                    String id = one == null ? null : one.getString("id");
                    if (id != null) {
                        have.add(id);
                    }
                }
            }
            if (have.contains("rogue_6_relic_final_3")) {
                return "ro6_ending_3";
            }
            if (have.contains("rogue_6_relic_final_7") || have.contains("rogue_6_relic_final_8")) {
                return "ro6_ending_4";
            }
            if (have.contains("rogue_6_relic_final_1") && have.contains("rogue_6_relic_final_2")) {
                return "ro6_ending_2";
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] resolveEndingRogue6 失败: {}", e.toString());
        }
        return fallback != null && !fallback.isEmpty() ? fallback : "ro6_ending_1";
    }


    /** rogue_6：本局已获得的 final 系收藏品 id 集合（record.relicList 兼容数组/对象两种形态） */
    private static java.util.Set<String> rogue6FinalRelics(JSONObject cur) {
        java.util.Set<String> have = new java.util.HashSet<>();
        try {
            JSONObject rec = cur.getJSONObject("record");
            Object rl = rec == null ? null : rec.get("relicList");
            if (rl instanceof com.alibaba.fastjson.JSONArray) {
                com.alibaba.fastjson.JSONArray arr = (com.alibaba.fastjson.JSONArray) rl;
                for (int i = 0; i < arr.size(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof JSONObject) {
                        String id = ((JSONObject) o).getString("id");
                        if (id != null && id.contains("_relic_final_")) {
                            have.add(id);
                        }
                    } else if (o instanceof String) {
                        have.add((String) o);
                    }
                }
            } else if (rl instanceof com.alibaba.fastjson.JSONObject) {
                com.alibaba.fastjson.JSONObject obj = (com.alibaba.fastjson.JSONObject) rl;
                for (String k : obj.keySet()) {
                    JSONObject one = obj.getJSONObject(k);
                    String id = one == null ? null : one.getString("id");
                    if (id != null && id.contains("_relic_final_")) {
                        have.add(id);
                    }
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] rogue6FinalRelics 失败: {}", e.toString());
        }
        return have;
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

    private static JSONObject buildChar(JSONObject userData, int population) {
        return buildChar(userData, population, null);
    }

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
        // skills 兜底：队伍节点没有技能数据时用 character_table 补（否则客户端技能格为空会报未知故障）
        JSONArray skillArr = src.getJSONArray("skills");
        if (skillArr == null || skillArr.isEmpty()) {
            skillArr = new JSONArray();
            JSONObject table = ArknightsApplication.characterJson;
            JSONObject def = table == null ? null : table.getJSONObject(c.getString("charId"));
            JSONArray defSkills = def == null ? null : def.getJSONArray("skills");
            if (defSkills != null) {
                for (int si = 0; si < defSkills.size(); si++) {
                    String skillId = defSkills.getJSONObject(si).getString("skillId");
                    if (skillId == null) {
                        continue;
                    }
                    JSONObject one = new JSONObject(true);
                    one.put("skillId", skillId);
                    one.put("unlock", 1);
                    one.put("state", 0);
                    one.put("specializeLevel", 0);
                    one.put("completeUpgradeTime", -1);
                    skillArr.add(one);
                }
            }
        }
        c.put("skills", skillArr);
        // defaultSkillIndex 必须是合法下标（-1 会让客户端技能选择越界 → 未知故障）
        int dsi = src.getIntValue("defaultSkillIndex");
        if (dsi < 0 || dsi >= Math.max(1, skillArr.size())) {
            dsi = 0;
        }
        c.put("defaultSkillIndex", dsi);
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
        enterZone(null, cur, 1);
    }

    private static void enterFirstZone(JSONObject userData, JSONObject cur) {
        enterZone(userData, cur, 1);
    }

    /** 局外成长里已解锁的 buff（user.rlv2.outer.<theme>.buff.unlocked 的键） */
    private static java.util.Set<String> unlockedDevBuffs(JSONObject userData, String theme) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (userData == null || theme == null) {
            return out;
        }
        JSONObject rlv2 = userData.getJSONObject("rlv2");
        JSONObject outer = rlv2 == null ? null : rlv2.getJSONObject("outer");
        JSONObject themeOuter = outer == null ? null : outer.getJSONObject(theme);
        JSONObject buff = themeOuter == null ? null : themeOuter.getJSONObject("buff");
        JSONObject unlocked = buff == null ? null : buff.getJSONObject("unlocked");
        if (unlocked != null) {
            out.addAll(unlocked.keySet());
        }
        return out;
    }

    private static void enterZone(JSONObject cur, int level) {
        enterZone(null, cur, level);
    }

    /**
     * 把「通用作战节点」type=256 解析成具体类型：按 stage 前缀 → 1 普通 / 2 紧急 / 4 首领。
     * 官方模板里 256 的 stage 既有 ro6_n_* 也有 ro6_e_*，说明 256 只是「待定型」，
     * 直接发给客户端会让它画不出图标（表现为**白色空白方块**）。
     */
    private static void resolveBattleTypes(JSONObject cur) {
        JSONObject zones = cur.getJSONObject("map") == null ? null
                : cur.getJSONObject("map").getJSONObject("zones");
        if (zones == null) {
            return;
        }
        for (String zk : zones.keySet()) {
            JSONObject nodes = zones.getJSONObject(zk).getJSONObject("nodes");
            if (nodes == null) {
                continue;
            }
            for (String nk : nodes.keySet()) {
                JSONObject n = nodes.getJSONObject(nk);
                if (n == null || n.getIntValue("type") != 256) {
                    continue;
                }
                String st = n.getString("stage");
                if (st == null || st.isEmpty()) {
                    continue;
                }
                String[] parts = st.split("_");
                String kind = parts.length > 1 ? parts[1] : "";
                if ("e".equals(kind)) {
                    n.put("type", 2);
                } else if ("b".equals(kind)) {
                    n.put("type", 4);
                } else {
                    n.put("type", 1);
                }
            }
        }
    }

    /**
     * 节点类型/关卡重分布：抓包模板只是**某一次**的分布（zone_1 里 6 个作战），
     * 直接照抄会导致「每层全是作战、没有事件」。这里按 seed 重新分配节点类型：
     *   普通层：作战 ~45%、不期而遇 ~22%、安全角落 ~9%、得偿所愿 ~7%、
     *          失与得 ~6%、先行一步 ~6%、误入奇境 ~5%（商店类由模板的 BATTLE_SHOP 保留）
     * 末两列/终点(FINAL)与空洞(EMPTY)保持不动，stage 按新类型从官方关卡池取。
     */
    private static void randomizeNodeTypes(JSONObject cur, JSONObject zone, String theme, int level,
            String seed) {
        JSONObject details = themeDetails(theme);
        if (details == null || zone == null) {
            return;
        }
        JSONObject nodes = zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        java.util.Random rnd = new java.util.Random(
                (seed == null ? 0L : seed.hashCode()) * 131L + level * 10007L);
        String prefix = themePrefix(theme);
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> elitePool = new java.util.ArrayList<>();
        JSONObject stages = details.getJSONObject("stages");
        if (stages != null) {
            String np = prefix + "_n_" + level + "_";
            String ep = prefix + "_e_" + level + "_";
            for (String id : stages.keySet()) {
                if (id.startsWith(np)) {
                    normalPool.add(id);
                } else if (id.startsWith(ep)) {
                    elitePool.add(id);
                }
            }
        }
        java.util.Collections.sort(normalPool);
        java.util.Collections.sort(elitePool);

        // 加权类型池（用 RogueMapGen.typeBit 保证位值正确）
        String[] weighted = {
                "BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL",
                "BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL",
                "INCIDENT", "INCIDENT", "INCIDENT", "INCIDENT", "INCIDENT",
                "REST", "REST",
                "WISH", "SACRIFICE", "EXPEDITION", "PORTAL",
                "BATTLE_ELITE"
        };
        for (String nk : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(nk);
            int curType = n.getIntValue("type");
            // 空洞 / 终点 / 商店类 保持模板原样
            if (curType == 268435456 || curType == 8388608 || curType == 16777216
                    || curType == 4096 || curType == 2097152 || curType == 33554432) {
                continue;
            }
            String name = weighted[rnd.nextInt(weighted.length)];
            int bit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, name);
            if (bit == 0) {
                continue;
            }
            n.put("type", bit);
            n.remove("realContent");
            if ("BATTLE_NORMAL".equals(name) || "BATTLE_ELITE".equals(name)) {
                java.util.List<String> pool = "BATTLE_ELITE".equals(name) && !elitePool.isEmpty()
                        ? elitePool : normalPool;
                if (!pool.isEmpty()) {
                    n.put("stage", pool.get(rnd.nextInt(pool.size())));
                } else {
                    n.remove("stage");
                }
            } else {
                n.remove("stage");
            }
        }
    }

    /** 按当前坐标重建四邻接 next（只连存在且相邻的格子） */
    private static void rebuildNext(JSONObject nodes) {
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            int x = pos.getIntValue("x");
            int y = pos.getIntValue("y");
            JSONArray next = new JSONArray();
            for (String k2 : nodes.keySet()) {
                if (k2.equals(k)) {
                    continue;
                }
                JSONObject p2 = nodes.getJSONObject(k2).getJSONObject("pos");
                int dx = Math.abs(p2.getIntValue("x") - x);
                int dy = Math.abs(p2.getIntValue("y") - y);
                if (dx + dy == 1) {
                    JSONObject t = new JSONObject(true);
                    t.put("x", p2.getIntValue("x"));
                    t.put("y", p2.getIntValue("y"));
                    next.add(t);
                }
            }
            n.put("next", next);
        }
    }

    /**
     * 去掉 EMPTY（268435456）空洞格：
     * 我们的客户端把 type=268435456 的格子画成「白色空白方块」，而官服模板里这种格子很多
     * （zone_1 就有 4 个）。直接把空洞删掉、把它的邻居互相连起来，路径不会断。
     */
    private static JSONObject removeEmptyNodes(JSONObject zone) {
        if (zone == null) {
            return null;
        }
        JSONObject nodes = zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return zone;
        }
        java.util.List<String> empties = new java.util.ArrayList<>();
        for (String k : nodes.keySet()) {
            if (nodes.getJSONObject(k).getIntValue("type") == 268435456) {
                empties.add(k);
            }
        }
        if (empties.isEmpty()) {
            return zone;
        }
        for (String ek : empties) {
            JSONObject e = nodes.getJSONObject(ek);
            JSONObject epos = e.getJSONObject("pos");
            int ex = epos.getIntValue("x");
            int ey = epos.getIntValue("y");
            // 空洞的邻居
            java.util.List<JSONObject> nbPos = new java.util.ArrayList<>();
            for (String k2 : nodes.keySet()) {
                if (k2.equals(ek) || empties.contains(k2)) {
                    continue;
                }
                JSONObject p2 = nodes.getJSONObject(k2).getJSONObject("pos");
                if (Math.abs(p2.getIntValue("x") - ex) + Math.abs(p2.getIntValue("y") - ey) == 1) {
                    nbPos.add(p2);
                }
            }
            // 邻居两两相连（空洞是可通行的中继格）
            for (int i = 0; i < nbPos.size(); i++) {
                for (int j = i + 1; j < nbPos.size(); j++) {
                    JSONObject a = nbPos.get(i);
                    JSONObject b = nbPos.get(j);
                    addNextIfAbsent(nodes, a, b);
                    addNextIfAbsent(nodes, b, a);
                }
            }
            nodes.remove(ek);
        }
        // 删完再按几何重建一次，清掉指向已删除格子的 next
        rebuildNext(nodes);
        // 只保留真正相邻的边（上面补的「跨空洞」边距离为 2，会破坏四邻接）→ 用跳格连通表修正
        return zone;
    }

    private static void addNextIfAbsent(JSONObject nodes, JSONObject from, JSONObject to) {
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            if (pos.getIntValue("x") != from.getIntValue("x") || pos.getIntValue("y") != from.getIntValue("y")) {
                continue;
            }
            JSONArray next = n.getJSONArray("next");
            if (next == null) {
                next = new JSONArray();
                n.put("next", next);
            }
            for (int i = 0; i < next.size(); i++) {
                JSONObject t = next.getJSONObject(i);
                if (t.getIntValue("x") == to.getIntValue("x") && t.getIntValue("y") == to.getIntValue("y")) {
                    return;
                }
            }
            JSONObject t = new JSONObject(true);
            t.put("x", to.getIntValue("x"));
            t.put("y", to.getIntValue("y"));
            next.add(t);
            return;
        }
    }

    /**
     * 同一层抓包模板每局都用 → 地图一模一样。
     * 按 seed + 层数做确定性镜像（水平/垂直），保持四邻接与 index：
     * 同一 seed 稳定，不同 seed 出不同地图。
     */
    /**
     * 同一层抓包模板每局都用 → 地图一模一样。
     * 只做**垂直镜像**（水平镜像会把 FINAL/终点列翻到入口列，玩家会出生在终点格上 → 空白节点/卡住），
     * 再对中间列做「类型洗牌」（保持类型多重集不变、不影响入口列和终点列）。
     * 同一个 seed 结果稳定，不同 seed 布局/节点分布不同。
     */
    private static JSONObject randomizeZone(JSONObject zone, String seed, int level) {
        if (zone == null) {
            return null;
        }
        JSONObject nodes = zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return zone;
        }
        long h = (seed == null ? 0L : seed.hashCode()) * 31L + level * 7919L;
        java.util.Random rnd = new java.util.Random(h);

        int maxX = 0;
        int maxY = 0;
        for (String k : nodes.keySet()) {
            JSONObject pos = nodes.getJSONObject(k).getJSONObject("pos");
            maxX = Math.max(maxX, pos.getIntValue("x"));
            maxY = Math.max(maxY, pos.getIntValue("y"));
        }

        // 垂直镜像（y 轴翻转），x 不变 → 入口仍在最左列、终点仍在最右列
        if (rnd.nextBoolean() && maxY > 0) {
            JSONObject rebuilt = new JSONObject(true);
            for (String k : nodes.keySet()) {
                JSONObject n = nodes.getJSONObject(k);
                JSONObject pos = n.getJSONObject("pos");
                int nx = pos.getIntValue("x");
                int ny = maxY - pos.getIntValue("y");
                JSONObject p2 = new JSONObject(true);
                p2.put("x", nx);
                p2.put("y", ny);
                n.put("pos", p2);
                n.remove("realContent");
                String idx = String.valueOf(nx * 100 + ny);
                n.put("index", idx);
                rebuilt.put(idx, n);
            }
            nodes = rebuilt;
            zone.put("nodes", nodes);
            // 关键：镜像/重编号后必须重建 next（四邻接），否则 next 里还是旧坐标
            // → 客户端画出斜线、点击「无法到达」
            rebuildNext(nodes);
        }

        // 中间列类型洗牌：交换若干对节点的 (type, stage, realContent)，入口列/终点列保持不动
        java.util.List<String> interior = new java.util.ArrayList<>();
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            int x = pos.getIntValue("x");
            int type = n.getIntValue("type");
            // 空洞(268435456) 和终点(8388608) 是地图骨架，不能参与洗牌（否则空洞会跑到别的位置，地图很怪）
            if (x > 0 && x < maxX && type != 268435456 && type != 8388608 && type != 16777216) {
                interior.add(k);
            }
        }
        int swaps = interior.size() / 4;
        for (int t = 0; t < swaps; t++) {
            String a = interior.get(rnd.nextInt(interior.size()));
            String b = interior.get(rnd.nextInt(interior.size()));
            if (a.equals(b)) {
                continue;
            }
            JSONObject na = nodes.getJSONObject(a);
            JSONObject nb = nodes.getJSONObject(b);
            Object ta = na.get("type");
            Object sa = na.get("stage");
            Object ra = na.get("realContent");
            na.put("type", nb.get("type"));
            if (nb.get("stage") != null) {
                na.put("stage", nb.get("stage"));
            } else {
                na.remove("stage");
            }
            na.remove("realContent");
            nb.put("type", ta);
            if (sa != null) {
                nb.put("stage", sa);
            } else {
                nb.remove("stage");
            }
            nb.remove("realContent");
        }
        return zone;
    }

    private static void enterZone(JSONObject userData, JSONObject cur, int level) {
        JSONObject map = cur.getJSONObject("map");
        JSONObject zones = map.getJSONObject("zones");
        if (zones == null) {
            zones = new JSONObject(true);
            map.put("zones", zones);
        }
        // 官方最大层数（details.<theme>.zones 里 zone_N 的个数：rogue_6=7）
        int maxFloor = com.hypergryph.arknights.core.function.RogueMapGen.officialZoneCount(
                cur.getJSONObject("game").getString("theme"));
        if ("rogue_6".equals(cur.getJSONObject("game").getString("theme")) && maxFloor > 0) {
            // PRTS 层级判定：第Ⅵ层「源流交汇处」是隐藏层，需持有【怦然信标】才可进入；
            // 没有信标 → 第Ⅴ层就是终点，不允许生成第 6/7 层
            boolean beacon0 = rogue6FinalRelics(cur).contains("rogue_6_relic_final_3");
            int hardCap = beacon0 ? 6 : 5;
            if (maxFloor > hardCap) {
                maxFloor = hardCap;
            }
        }
        if (maxFloor > 0 && level > maxFloor) {
            ArknightsApplication.LOGGER.info("[rlv2] 已到最后一层（{} 层），level={} 不再生成新层", maxFloor, level);
            level = maxFloor;
        }
        int index = 1000 + (level - 1);
        String key = String.valueOf(index);
        if (!zones.containsKey(key)) {
            JSONObject game = cur.getJSONObject("game");
            String seed = game == null ? null : game.getString("seed");
            String theme = game == null ? null : game.getString("theme");
            java.util.Set<String> unlocked = unlockedDevBuffs(userData, theme);
            JSONObject genZone;
            if ("rogue_6".equals(theme)) {
                // rogue_6 专用生成器（抓包统计配方 + PRTS 规则，2026-11-11 重写）
                genZone = com.hypergryph.arknights.core.function.RogueMapGen.R6MapGen
                        .generateZone(seed, level, unlocked, rogue6FinalRelics(cur));
            } else {
                genZone = com.hypergryph.arknights.core.function.RogueMapGen
                        .generateZone(seed, theme, level, unlocked);
            }
            if (genZone == null) {
                genZone = com.hypergryph.arknights.core.function.RogueMapGen
                        .generateZone(seed, theme, level, unlocked);
            }
            zones.put(key, genZone);
        }
        JSONObject player = cur.getJSONObject("player");
        if (level <= 1) {
            player.put("cleared", new JSONArray());   // 新局重置「已消费节点」表，避免沿用上一局
        }
        JSONObject cursor = player.getJSONObject("cursor");
        cursor.put("zone", index);
        player.put("state", "WAIT_MOVE");
        // 官方实测（2026-11-06 抓包 002_16）：进入区域后玩家站在**入口格**上 ——
        // 入口就是 x 最小的节点（rogue_6 首层是 (0,1) 的 EMPTY 格），并且要写 trace，
        // 否则 RL06 网格插件算不了距离、地图整块不渲染。
        JSONObject zoneNow0 = zones.getJSONObject(key);
        JSONObject nodesNow0 = zoneNow0 == null ? null : zoneNow0.getJSONObject("nodes");
        if (nodesNow0 != null && !nodesNow0.isEmpty()) {
            JSONObject entrance = null;
            int minXEntr = Integer.MAX_VALUE;
            // 优先最左列的 EMPTY 格（官服入口就是它），其次最左列非 FINAL/EVACUATE 的格子
            for (String nk : nodesNow0.keySet()) {
                JSONObject n = nodesNow0.getJSONObject(nk);
                JSONObject pos = n == null ? null : n.getJSONObject("pos");
                if (pos == null) {
                    continue;
                }
                int x = pos.getIntValue("x");
                int type = n.getIntValue("type");
                boolean bad = type == 8388608 || type == 16777216;
                if (x < minXEntr || (x == minXEntr && entrance != null && bad)) {
                    if (entrance == null || type == 268435456 || (x == minXEntr && !bad)) {
                        minXEntr = x;
                        entrance = n;
                    }
                }
            }
            if (entrance != null) {
                cursor.put("position", entrance.getJSONObject("pos"));
                JSONArray trace0 = new JSONArray();
                JSONObject tr0 = new JSONObject(true);
                tr0.put("zone", index);
                tr0.put("position", entrance.getJSONObject("pos"));
                trace0.add(tr0);
                player.put("trace", trace0);
                ArknightsApplication.LOGGER.info("[rlv2] 进区域 {} 落点 ({},{})", key, minXEntr,
                        entrance.getJSONObject("pos").getIntValue("y"));
            }
        }
        // 新区域：保证至少有一列可见节点，客户端才有可点的格子
        JSONObject newZone = zones.getJSONObject(key);
        JSONObject newNodes = newZone == null ? null : newZone.getJSONObject("nodes");
        if (newNodes != null && !newNodes.isEmpty()) {
            boolean anyVisible = false;
            int minX = Integer.MAX_VALUE;
            for (String nk : newNodes.keySet()) {
                JSONObject n = newNodes.getJSONObject(nk);
                if (n == null) {
                    continue;
                }
                if (n.getIntValue("visibility") == 1) {
                    anyVisible = true;
                }
                JSONObject pos = n.getJSONObject("pos");
                if (pos != null) {
                    minX = Math.min(minX, pos.getIntValue("x"));
                }
            }
            if (!anyVisible) {
                for (String nk : newNodes.keySet()) {
                    JSONObject n = newNodes.getJSONObject(nk);
                    JSONObject pos = n == null ? null : n.getJSONObject("pos");
                    // 只点亮最左列的**非空洞**节点（EMPTY 点亮会变成白色空白方块）
                    if (pos != null && pos.getIntValue("x") == minX
                            && n.getIntValue("type") != 268435456) {
                        n.put("visibility", 1);
                    }
                }
                ArknightsApplication.LOGGER.info("[rlv2] 新区域 {} 没有可见节点 → 点亮最左列 x={}", key, minX);
            }
        }
        // 网格主题（rogue_6）：RL06 插件要求 cursor.position 非空，否则地图不渲染
        String themeNow = cur.getJSONObject("game") == null ? null : cur.getJSONObject("game").getString("theme");
        // 新区域生成后立刻同步网格节点（否则 rogue_6 地图不渲染）
        if (themeNow != null) {
            seedGridZoneModule(cur, themeNow);
        }
    }

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
        // 官方 createGame 就带 hpShowState（缺了客户端血量条不显示）
        prop.put("hpShowState", "NORMAL");

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

        // 初始化步骤链（官服抓包归纳：顺序与总步数都随主题/模式变化）
        //   月度小队：TEAM → [MODE_RELIC] → RELIC → RECRUIT
        //   挑战模式：[MODE_RELIC] → RELIC → [EXPLORE_TOOL] → RECRUIT_SET → RECRUIT
        //   常规/简单：[MODE_RELIC] → RELIC → SUPPORT → [EXPLORE_TOOL] → RECRUIT_SET → RECRUIT
        // 每个 step 统一编号 [i+1, n]，index = e_i（官服 022_02 水月 EASY / 006_00 萨米 CHALLENGE 实测）
        java.util.List<JSONObject> chain = new java.util.ArrayList<>();

        if ("MONTH_TEAM".equals(mode) && predefinedId != null && !predefinedId.isEmpty()) {
            JSONObject teamPend = buildInitTeamPending(theme, predefinedId);
            if (teamPend != null) {
                chain.add(teamPend);
            }
        }

        String modeRelicId = pickModeRelic(theme, mode);
        if (modeRelicId != null) {
            JSONObject p = new JSONObject(true);
            p.put("type", "GAME_INIT_MODE_RELIC");
            JSONObject initModeRelic = new JSONObject(true);
            JSONArray ids = new JSONArray();
            ids.add(modeRelicId);
            initModeRelic.put("items", ids);
            JSONObject content = new JSONObject(true);
            content.put("initModeRelic", initModeRelic);
            p.put("content", content);
            chain.add(p);
        }

        {
            // 开局变体收藏品：候选池 = details.<theme>.init[...] 的 initialBandRelic
            JSONObject p = new JSONObject(true);
            p.put("type", "GAME_INIT_RELIC");
            JSONObject initRelic = new JSONObject(true);
            JSONObject items = new JSONObject(true);
            JSONArray pool = cfg.getJSONArray("initialBandRelic");
            int idx = 0;
            if (pool != null) {
                // 官方 createGame 只给 6 个候选（002_06 实测 items 数量=6），不是把池子全给
                java.util.List<String> poolIds = new java.util.ArrayList<>();
                for (int b = 0; b < pool.size(); b++) {
                    poolIds.add(pool.getString(b));
                }
                java.util.Collections.shuffle(poolIds, RANDOM);
                int pick = Math.min(6, poolIds.size());
                for (int b = 0; b < pick; b++) {
                    JSONObject one = new JSONObject(true);
                    one.put("id", poolIds.get(b));
                    one.put("count", 1);
                    items.put(String.valueOf(idx++), one);
                }
            }
            if (idx == 0) {
                JSONObject one = new JSONObject(true);
                one.put("id", theme + "_band_1");
                one.put("count", 1);
                items.put("0", one);
            }
            initRelic.put("items", items);
            JSONObject content = new JSONObject(true);
            content.put("initRelic", initRelic);
            p.put("content", content);
            chain.add(p);
        }

        // 官方抓包（2026-11-06 002_06 / 003_xx）：rogue_5、rogue_6 的初始化链里没有「起始增益」
        //   rogue_6 NORMAL = MODE_RELIC[1,4] → RELIC[2,4] → RECRUIT_SET[3,4] → RECRUIT[4,4]
        //   rogue_5 NORMAL = RELIC[1,3] → RECRUIT_SET[2,3] → RECRUIT[3,3]
        boolean hasSupportStep = !"rogue_5".equals(theme) && !"rogue_6".equals(theme);
        if (hasSupportStep && !"CHALLENGE".equals(mode) && !"MONTH_TEAM".equals(mode)) {
            JSONObject supportPend = buildInitSupportPending(theme);
            if (supportPend != null) {
                chain.add(supportPend);
            }
        }

        if (!exploreToolIds(theme).isEmpty()) {
            chain.add(buildInitExploreToolPending(theme, 0));
        }

        if (!"MONTH_TEAM".equals(mode)) {
            {
                JSONObject p = new JSONObject(true);
                p.put("type", "GAME_INIT_RECRUIT_SET");
                JSONObject initRecruitSet = new JSONObject(true);
                JSONArray option = cfg.getJSONArray("initialRecruitGroup");
                if (option == null || option.isEmpty()) {
                    option = new JSONArray();
                    option.add("recruit_group_1");
                    option.add("recruit_group_2");
                    option.add("recruit_group_3");
                    option.add("recruit_group_random");
                }
                initRecruitSet.put("option", option);
                JSONObject content = new JSONObject(true);
                content.put("initRecruitSet", initRecruitSet);
                p.put("content", content);
                chain.add(p);
            }
            {
                JSONObject p = new JSONObject(true);
                p.put("type", "GAME_INIT_RECRUIT");
                JSONObject initRecruit = new JSONObject(true);
                initRecruit.put("tickets", new JSONArray());
                initRecruit.put("showChar", new JSONArray());
                // 官服：挑战模式 team = predefinedId，常规/简单为 null
                initRecruit.put("team", "CHALLENGE".equals(mode) ? predefinedId : null);
                JSONObject content = new JSONObject(true);
                content.put("initRecruit", initRecruit);
                p.put("content", content);
                chain.add(p);
            }
        }

        int initTotal = chain.size();
        for (int ci = 0; ci < initTotal; ci++) {
            JSONObject p = chain.get(ci);
            p.put("index", "e_" + ci);
            JSONObject content = p.getJSONObject("content");
            if (content != null && !content.isEmpty()) {
                String key = content.keySet().iterator().next();
                JSONObject stepBody = content.getJSONObject(key);
                if (stepBody != null) {
                    JSONArray step = new JSONArray();
                    step.add(ci + 1);
                    step.add(initTotal);
                    stepBody.put("step", step);
                }
            }
            pending.add(p);
        }

        player.put("pending", pending);
        player.put("toEnding", defaultEnding(theme));

        // rogue_4（萨卡兹）：构想系统 module.fragment —— 开局给几枚碎片，
        // 让 alchemy（解读）/ rerollNode（重掷）有资源可用
        seedFragmentModule(cur, theme);
        seedDiceModule(cur, theme);
        seedScrapModule(cur, theme);
        seedSanModule(cur, theme);
        seedNodeUpgradeModule(cur, theme);
        seedWeatherModule(cur, theme);
        seedGridZoneModule(cur, theme);
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

    /**
     * 开局「模式收藏品」：官服只在部分模式下给一件（抓包实测）
     *   简单 EASY   → *_relic_grace_*（水月）/ *_relic_a*（傀影）
     *   挑战 CHALLENGE → *_relic_legacy_*
     *   月度 MONTH_TEAM → *_relic_m*
     *   常规 NORMAL → 多数不给（返回 null，客户端就不会多出一个空的初始道具步骤）
     */
    private static String pickModeRelic(String theme, String mode) {
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        if (relics == null || relics.isEmpty()) {
            return null;
        }
        // 简单难度「静音小队」是固定的初始收藏品（官服抓包：傀影 EASY = rogue_1_relic_a01，
        // 水月 EASY = rogue_2_relic_grace_52），不能随机挑，否则客户端显示成别的收藏品
        if ("EASY".equals(mode)) {
            if ("rogue_1".equals(theme) && relics.containsKey("rogue_1_relic_a01")) {
                return "rogue_1_relic_a01";
            }
            if ("rogue_2".equals(theme) && relics.containsKey("rogue_2_relic_grace_52")) {
                return "rogue_2_relic_grace_52";
            }
        }
        // 官服 B服 createGame 实测：黑流树海(rogue_6) 普通难度也有模式固定收藏品
        //   MODE_RELIC[1,5] → RELIC[2,5] → SUPPORT[3,5] → RECRUIT_SET[4,5] → RECRUIT[5,5]
        // 少了第一步客户端 wizard 少一步，会卡在招募/地图之前
        if ("rogue_6".equals(theme) && "NORMAL".equals(mode)
                && relics.containsKey("rogue_6_relic_fight_29")) {
            return "rogue_6_relic_fight_29";
        }
        if ("rogue_3".equals(theme) && "CHALLENGE".equals(mode)
                && relics.containsKey("rogue_3_relic_legacy_30")) {
            return "rogue_3_relic_legacy_30";
        }
        java.util.List<String> prefer = new java.util.ArrayList<>();
        if ("EASY".equals(mode)) {
            prefer.add("_relic_grace_");
            prefer.add("_relic_a");
        } else if ("CHALLENGE".equals(mode)) {
            prefer.add("_relic_legacy_");
            prefer.add("_relic_a");
        } else if ("MONTH_TEAM".equals(mode)) {
            prefer.add("_relic_m");
        } else {
            return null;
        }
        for (String pat : prefer) {
            java.util.List<String> hits = new java.util.ArrayList<>();
            for (String k : relics.keySet()) {
                if (k.contains(pat)) {
                    hits.add(k);
                }
            }
            if (!hits.isEmpty()) {
                return hits.get(RANDOM.nextInt(hits.size()));
            }
        }
        return null;
    }

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

    /**
     * 官服语义（2026-11-06 官服抓包实证，seed zone_1）：
     *   type=256 且**无 stage** → 客户端显示「未知的诡秘」（非战斗未定型）
     *   type=256 且**有 stage** → 客户端显示「未知的凶戾」（战斗未定型）
     *   type=1/2/4 已定型 → 直接显示「作战」
     *   268435456=空洞(白格,vis0) / 8388608=险路尽头(vis0)
     * 所以 256 是**占位**：玩家靠近时服务端才把它定成具体类型。
     * 这里每次同步都把「距 cursor 曼哈顿 ≤ 2」的 256 定型：
     *   战斗 55% → type 1/2 + stage，visibility=2（凶戾）
     *   其余 45% → 事件/商店类具体 type，visibility=1（诡秘）
     */
    private static void resolveFrontierNodes(JSONObject cur, String theme) {
        String themeStr = theme == null ? cur.getJSONObject("game").getString("theme") : theme;
        JSONObject details = themeDetails(themeStr);
        JSONObject zones = cur.getJSONObject("map") == null ? null
                : cur.getJSONObject("map").getJSONObject("zones");
        if (details == null || zones == null) {
            return;
        }
        String zid = String.valueOf(cur.getJSONObject("player").getJSONObject("cursor").getIntValue("zone"));
        JSONObject zone = zones.getJSONObject(zid);
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null) {
            return;
        }
        // 官服节点不带 realContent（041_13 实证：事件格 {type:32,visibility:0,fts}），
        // 之前我们写的 realContent.scene 会让客户端按内容渲染，内容结束后节点就空了 → 看起来「消失」
        // （已移除：不再剥离 realContent，官服事件格本来就带 scene）
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position");
        int cx = cursor.getIntValue("x");
        int cy = cursor.getIntValue("y");
        int level = currentLevel(cur);
        String prefix = themePrefix(themeStr);
        JSONObject stages = details.getJSONObject("stages");
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> elitePool = new java.util.ArrayList<>();
        if (stages != null) {
            String np = prefix + "_n_" + level + "_";
            String ep = prefix + "_e_" + level + "_";
            for (String id : stages.keySet()) {
                if (id.startsWith(np)) {
                    normalPool.add(id);
                } else if (id.startsWith(ep)) {
                    elitePool.add(id);
                }
            }
        }
        java.util.Collections.sort(normalPool);
        java.util.Collections.sort(elitePool);
        java.util.List<String> eventPool = new java.util.ArrayList<>();
        eventPool.add("INCIDENT"); eventPool.add("INCIDENT"); eventPool.add("INCIDENT");
        eventPool.add("REST");
        eventPool.add("WISH");
        eventPool.add("SACRIFICE");
        eventPool.add("BATTLE_SHOP");
        if (level > 1) {
            eventPool.add("EXPEDITION");
            eventPool.add("PORTAL");
        }

        for (String nk : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(nk);
            if (n == null || n.getIntValue("type") != 256) {
                continue;
            }
            JSONObject pos = n.getJSONObject("pos");
            int d = Math.abs(pos.getIntValue("x") - cx) + Math.abs(pos.getIntValue("y") - cy);
            java.util.Random rnd = new java.util.Random(
                    (nk + ":" + level + ":" + themeStr).hashCode());
            boolean battle = n.getString("stage") != null && !n.getString("stage").isEmpty();
            if (!battle) {
                if (d > 1) {
                    continue;   // 只有紧邻一格才定型，避免每走一步刷一片图标
                }
                battle = rnd.nextDouble() < 0.55;
            }
            if (battle) {
                boolean elite = rnd.nextDouble() < 0.25;
                java.util.List<String> pool = elite && !elitePool.isEmpty() ? elitePool : normalPool;
                if (pool.isEmpty()) {
                    pool = elitePool.isEmpty() ? normalPool : elitePool;
                }
                if (!pool.isEmpty()) {
                    n.put("stage", pool.get(rnd.nextInt(pool.size())));
                }
                n.put("type", elite ? 2 : 1);
                n.put("visibility", 2);   // 战斗 → 「未知的凶戾」
            } else {
                String name = eventPool.get(rnd.nextInt(eventPool.size()));
                int bit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(themeStr, name);
                if (bit == 0) {
                    bit = 32;
                }
                n.put("type", bit);
                n.remove("stage");
                n.put("visibility", 1);   // 非战斗 → 「未知的诡秘」
            }
        }
    }

    /** 各主题「险路尽头/层出口」的节点位值（抓包实证） */
    private static int exitBitOf(String theme) {
        if (theme == null) {
            return 8388608;
        }
        switch (theme) {
            case "rogue_4":
                return 131072;
            case "rogue_5":
                return 1048576;
            case "rogue_6":
                return 8388608;
            default:
                return 8388608;
        }
    }

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

    @PostMapping(value = {"/gridZone/readStepZero"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gridZoneReadStepZero(@RequestHeader(value = "secret", required = false) String secret,
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
        JSONObject module = cur.getJSONObject("module");
        JSONObject grid = module == null ? null : module.getJSONObject("gridZone");
        if (grid != null) {
            grid.put("needConfirmStepZero", false);
        }
        JSONArray pend = new JSONArray();
        JSONObject one = new JSONObject(true);
        one.put("index", "e_9");
        String nodeType = nodeTypeNameAt(cur, player.getJSONObject("cursor").getJSONObject("position"));
        if (nodeType != null && nodeType.startsWith("BATTLE")) {
            JSONObject content = new JSONObject(true);
            JSONObject battle = new JSONObject(true);
            battle.put("state", 0);
            content.put("battle", battle);
            one.put("type", "BATTLE");
            one.put("content", content);
        } else {
            JSONObject content = new JSONObject(true);
            JSONObject battle = new JSONObject(true);
            battle.put("state", 0);
            content.put("battle", battle);
            one.put("type", "BATTLE");
            one.put("content", content);
        }
        pend.add(one);
        player.put("pending", pend);
        player.put("state", "PENDING");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/gridZone/emptyStep"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject gridZoneEmptyStep(@RequestHeader(value = "secret", required = false) String secret,
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
        JSONObject grid = module == null ? null : module.getJSONObject("gridZone");
        if (grid != null) {
            int remain = Math.max(0, grid.getIntValue("stepRemain") - 1);
            grid.put("stepRemain", remain);
            if (remain == 0) {
                grid.put("needConfirmStepZero", true);
            }
        }
        cur.getJSONObject("player").put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 载具可落点的节点类别判定（node 字段里的类别名 → 节点类型名） */
    private static boolean vehicleNodeCategoryOk(java.util.List<String> categories, String typeName, boolean empty) {
        if (categories == null || categories.isEmpty()) {
            return true;
        }
        if (categories.contains("ALL")) {
            return true;
        }
        if (empty) {
            return categories.contains("EMPTY");
        }
        if (typeName == null) {
            return false;
        }
        if (categories.contains(typeName)) {
            return true;
        }
        boolean battle = "BATTLE_NORMAL".equals(typeName) || "BATTLE_ELITE".equals(typeName)
                || "BATTLE_BOSS".equals(typeName) || "BATTLE_SAVAGE".equals(typeName);
        if (categories.contains("BATTLES") && battle) {
            return true;
        }
        if (categories.contains("EVENTS")) {
            return !battle && !"BATTLE_SHOP".equals(typeName) && !"SCRAP_SHOP".equals(typeName);
        }
        return false;
    }

    /**
     * 载具落点合法性：FULL_MAP 不限格数、FULL_ROW_COL 只同行列、RANGE 按 moveScrapRangeData 的 {row,col} 偏移表；
     * 再叠加 node 类别过滤（ALL / EVENTS / BATTLES / BATTLE_SHOP / SCRAP_SHOP / FINAL / EMPTY …）。
     * 返回 false = 这台载具去不了那个格子。
     */
    private static boolean vehicleTargetAllowed(JSONObject cur, JSONObject targetNode) {
        if (targetNode == null) {
            return false;
        }
        String theme = themeOf(cur);
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONObject vehicle = scrap == null ? null : scrap.getJSONObject("activeVehicle");
        if (vehicle == null || vehicle.getBooleanValue("isWalk")) {
            return false;
        }
        String instId = vehicle.getString("instId");
        JSONObject inv = scrap.getJSONObject("inventory");
        JSONObject item = inv == null || instId == null ? null : inv.getJSONObject(instId);
        String scrapId = item == null ? null : item.getString("id");
        JSONObject mods = themeModules(theme);
        JSONObject scrapData = mods == null ? null : mods.getJSONObject("scrap");
        JSONObject moves = scrapData == null ? null : scrapData.getJSONObject("moveScrapData");
        JSONObject def = moves == null || scrapId == null ? null : moves.getJSONObject(scrapId);
        if (def == null) {
            // 未知载具：不额外限制（客户端自己会拦）
            return true;
        }
        JSONObject from = cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position");
        JSONObject to = targetNode.getJSONObject("pos");
        if (from == null || to == null) {
            return false;
        }
        int dx = to.getIntValue("x") - from.getIntValue("x");
        int dy = to.getIntValue("y") - from.getIntValue("y");
        String rangeType = def.getString("rangeType");
        boolean rangeOk = true;
        if ("FULL_MAP".equals(rangeType)) {
            rangeOk = true;
        } else if ("FULL_ROW_COL".equals(rangeType)) {
            rangeOk = dx == 0 || dy == 0;
        } else if ("RANGE".equals(rangeType)) {
            String rangeId = def.getString("range");
            JSONObject rangeData = scrapData == null ? null : scrapData.getJSONObject("moveScrapRangeData");
            JSONObject rdef = rangeData == null || rangeId == null ? null : rangeData.getJSONObject(rangeId);
            JSONArray grids = rdef == null ? null : rdef.getJSONArray("grids");
            if (grids == null) {
                rangeOk = true;
            } else {
                rangeOk = false;
                for (int i = 0; i < grids.size(); i++) {
                    JSONObject g = grids.getJSONObject(i);
                    if (g.getIntValue("col") == dx && g.getIntValue("row") == dy) {
                        rangeOk = true;
                        break;
                    }
                }
            }
        }
        if (!rangeOk) {
            return false;
        }
        // node 类别过滤
        java.util.List<String> categories = new java.util.ArrayList<>();
        JSONArray nodeArr = def.getJSONArray("node");
        if (nodeArr != null) {
            for (int i = 0; i < nodeArr.size(); i++) {
                categories.add(nodeArr.getString(i));
            }
        }
        int typeBit = targetNode.getIntValue("type");
        boolean empty = typeBit == 268435456;
        String typeName = empty ? null : nodeTypeNameAt(cur, to);
        return vehicleNodeCategoryOk(categories, typeName, empty);
    }

    private static int gridMoveCost(JSONObject cur, JSONObject targetNode) {
        String theme = themeOf(cur);
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONObject vehicle = scrap == null ? null : scrap.getJSONObject("activeVehicle");
        boolean isWalk = vehicle == null || !vehicle.containsKey("isWalk") || vehicle.getBooleanValue("isWalk");
        if (!isWalk) {
            String instId = vehicle.getString("instId");
            JSONObject inv = scrap.getJSONObject("inventory");
            JSONObject item = inv == null || instId == null ? null : inv.getJSONObject(instId);
            int step = 1;
            if (item != null) {
                String scrapId = item.getString("id");
                step = scrapMoveStep(theme, scrapId);
                // 载具全免费（config.rogue.freeVehicle=true）：不计使用次数
                if (!freeRide(cur)) {
                    item.put("useCnt", item.getIntValue("useCnt") + 1);
                }
            }
            return Math.max(0, step);
        }
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        JSONObject from = cursor.getJSONObject("position");
        JSONObject to = targetNode == null ? null : targetNode.getJSONObject("pos");
        if (from == null || to == null) {
            return 1;
        }
        int dx = Math.abs(from.getIntValue("x") - to.getIntValue("x"));
        int dy = Math.abs(from.getIntValue("y") - to.getIntValue("y"));
        return Math.max(1, dx + dy);
    }

    private static int scrapMoveStep(String theme, String scrapId) {
        JSONObject mods = themeModules(theme);
        JSONObject scrapData = mods == null ? null : mods.getJSONObject("scrap");
        JSONObject moves = scrapData == null ? null : scrapData.getJSONObject("moveScrapData");
        JSONObject def = moves == null || scrapId == null ? null : moves.getJSONObject(scrapId);
        return def == null ? 1 : def.getIntValue("step");
    }

    /**
     * 网格地图（rogue_6 黑流树海）模块同步：把 map.zones 里的节点镜像到
     * module.gridZone.zones.<zoneId>.nodes，并按「已访问 / 可前往 / 未知」写 state。
     * 官服结构：{zones:{zone_1:{nodes:{"0":{content:{},state:0|1|2,show:true,island:false}}}},
     *            stepRemain, needConfirmStepZero}
     * 之前只在 createGame 里同步过一次，那时 map.zones 还是空的 → 客户端拿不到网格数据，地图不渲染。
     */
    private static void seedGridZoneModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        if (mods == null || mods.getJSONObject("gridZone") == null) {
            return;
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject old = module.getJSONObject("gridZone");
        JSONObject grid = new JSONObject(true);
        JSONObject gridZones = new JSONObject(true);

        JSONObject map = cur.getJSONObject("map");
        JSONObject mapZones = map == null ? null : map.getJSONObject("zones");
        JSONObject cursorPos = cur.getJSONObject("player") == null
                || cur.getJSONObject("player").getJSONObject("cursor") == null ? null
                : cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position");
        int curX = cursorPos == null ? Integer.MIN_VALUE : cursorPos.getIntValue("x");
        int curY = cursorPos == null ? Integer.MIN_VALUE : cursorPos.getIntValue("y");

        if (mapZones != null) {
            for (String zk : mapZones.keySet()) {
                JSONObject src = mapZones.getJSONObject(zk);
                if (src == null) {
                    continue;
                }
                JSONObject nodes = src.getJSONObject("nodes");
                JSONObject gNodes = new JSONObject(true);
                java.util.Set<String> visited = new java.util.HashSet<>();
                java.util.Set<String> reachable = new java.util.HashSet<>();
                if (nodes != null) {
                    // 已访问：有 fts 或正好是当前坐标
                    for (String nk : nodes.keySet()) {
                        JSONObject n = nodes.getJSONObject(nk);
                        if (n == null) {
                            continue;
                        }
                        boolean isVisited = n.containsKey("fts");
                        JSONObject pos = n.getJSONObject("pos");
                        if (pos != null && pos.getIntValue("x") == curX && pos.getIntValue("y") == curY) {
                            isVisited = true;
                        }
                        if (isVisited) {
                            visited.add(nk);
                            JSONArray next = n.getJSONArray("next");
                            if (next != null) {
                                for (int i = 0; i < next.size(); i++) {
                                    JSONObject np = next.getJSONObject(i);
                                    reachable.add(np.getIntValue("x") + "," + np.getIntValue("y"));
                                }
                            }
                        }
                    }
                        // 官服：state 2=已走访，1=已揭示未走访，0=未知
                        // 之前这里从不更新（只有 EMPTY 写 2）→ 走一步后相邻格仍是「未揭示」，
                        // 客户端显示成「诡秘的预感」且点不动，事件自然不触发
                        java.util.Set<String> visitedXY = new java.util.HashSet<>();
                        JSONArray traceArr = cur.getJSONObject("player").getJSONArray("trace");
                        if (traceArr != null) {
                            for (int ti = 0; ti < traceArr.size(); ti++) {
                                JSONObject t = traceArr.getJSONObject(ti);
                                JSONObject tp = t == null ? null : t.getJSONObject("position");
                                if (tp != null) {
                                    visitedXY.add(tp.getIntValue("x") + "," + tp.getIntValue("y"));
                                }
                            }
                        }
                        if (cursorPos != null) {
                            visitedXY.add(cursorPos.getIntValue("x") + "," + cursorPos.getIntValue("y"));
                        }
                        java.util.Set<String> nearXY = new java.util.HashSet<>();
                        for (String v : visitedXY) {
                            String[] xy = v.split(",");
                            int vx = Integer.parseInt(xy[0]);
                            int vy = Integer.parseInt(xy[1]);
                            // 揭示半径 2（曼哈顿），官方开局就能看到前方一片而不是只有紧邻一格
                            for (int dx = -2; dx <= 2; dx++) {
                                for (int dy = -2; dy <= 2; dy++) {
                                    if (Math.abs(dx) + Math.abs(dy) <= 2) {
                                        nearXY.add((vx + dx) + "," + (vy + dy));
                                    }
                                }
                            }
                        }
                        for (String nk : nodes.keySet()) {
                            JSONObject n = nodes.getJSONObject(nk);
                            JSONObject npos = n == null ? null : n.getJSONObject("pos");
                            int px = npos == null ? Integer.MIN_VALUE : npos.getIntValue("x");
                            int py = npos == null ? Integer.MIN_VALUE : npos.getIntValue("y");
                            String xy = px + "," + py;
                            boolean isVisitedNow = visitedXY.contains(xy);
                            boolean isNearNow = nearXY.contains(xy);
                            // EMPTY 是「空洞格」：官服里它 state 恒为 2、visibility 恒为 0（不渲染成节点）。
                            // 之前给相邻 EMPTY 写 state=1 → 客户端把它当普通节点画，就是一个白色空白方块！
                            boolean isEmptyNode = n != null && n.getIntValue("type") == 268435456;
                            // 揭示必须是「只增不减」的：官方 visibility 一旦揭示就永久保留。
                            // 之前每次移动都从 trace 重算并写回 0 → 走一步后原先揭示的格子又变回「未揭示」，
                            // 地图看起来像被改写了（这就是「刚走一步地图就变了」的逻辑 bug）
                            int oldState = old != null && old.getJSONObject("zones") != null
                                    && old.getJSONObject("zones").getJSONObject(src.getString("id")) != null
                                    && old.getJSONObject("zones").getJSONObject(src.getString("id"))
                                            .getJSONObject("nodes") != null
                                    && old.getJSONObject("zones").getJSONObject(src.getString("id"))
                                            .getJSONObject("nodes").getJSONObject(nk) != null
                                    ? old.getJSONObject("zones").getJSONObject(src.getString("id"))
                                            .getJSONObject("nodes").getJSONObject(nk).getIntValue("state")
                                    : 0;
                            // 官服实证（041_13）：玩家**正站着**的格 state 仍是 0；只有离开过的格才变 2。
                            // 之前一下脚就写 2 → 客户端把图标当「已消耗」收掉，看起来「一点就消失」
                            boolean standingHere = cursorPos != null
                                    && cursorPos.getIntValue("x") == px && cursorPos.getIntValue("y") == py;
                            // 只给空洞写 2（官服空洞就是 state=2）。
                            // 走访过的普通格**保持 0**：我们的客户端在 state=2 时会把图标收掉（看起来「一离开就消失」），
                            // 官服虽然写 2 但它的 UI 是「变暗」而不是隐藏，这里先保证不消失。
                            // 官服：gz.state=2 只在「退出选项」后出现（041_12），不能由 fts 推（fts 踏入就写，041_10 时 state 仍 0）
                            boolean gzDone = n != null && n.getIntValue("gzDone") > 0;
                            int newState = (isEmptyNode || gzDone) ? 2 : 0;
                            JSONObject gNode = new JSONObject(true);
                            // 保留上一份 gridZone 里该节点的 content（商店 goods 必须留着，否则商品消失）
                            JSONObject prevContent = null;
                            if (old != null && old.getJSONObject("zones") != null) {
                                JSONObject oz = old.getJSONObject("zones").getJSONObject(src.getString("id"));
                                if (oz != null && oz.getJSONObject("nodes") != null
                                        && oz.getJSONObject("nodes").getJSONObject(nk) != null) {
                                    prevContent = oz.getJSONObject("nodes").getJSONObject(nk)
                                            .getJSONObject("content");
                                }
                            }
                            gNode.put("content", prevContent == null ? new JSONObject(true) : prevContent);
                            // 官服：有内容的已走访格 state=1（商店），没内容的已走访格 state=2
                            boolean hasContent = prevContent != null && !prevContent.isEmpty();
                            gNode.put("state", hasContent && newState == 2 ? 1 : Math.max(oldState, newState));
                            gNode.put("show", true);
                            gNode.put("island", false);
                            gNodes.put(nk, gNode);
                            if (n != null) {
                                // 官服语义（抓包 035 实证）：
                                //   visibility=0 → 完全揭示（险路尽头/作战/空洞都显示本体图标）
                                //   visibility=1 → 未知的诡秘   visibility=2 → 未知的凶戾
                                // 所以：空洞/出口恒 0；已定型(非256)且玩家在附近 → 0；未定型 256 保持 1/2
                                int typeNow = n.getIntValue("type");
                                int curVis = n.getIntValue("visibility");
                                int vis;
                                if (isEmptyNode || typeNow == 8388608 || typeNow == 16777216) {
                                    vis = 0;                       // 空洞/出口：本体
                                } else if (isVisitedNow) {
                                    vis = curVis;                  // 踩上去不改 visibility（改了图标会「消失」）
                                } else if (typeNow == 256) {
                                    vis = (curVis == 0) ? 2 : curVis;   // 未定型：保持诡秘/凶戾
                                } else {
                                    vis = curVis;                  // 已定型但没走过：保持原值（别每步刷）
                                }
                                n.put("visibility", vis);
                            }
                        }
                    }
                JSONObject gz = new JSONObject(true);
                gz.put("nodes", gNodes);
                gridZones.put(src.getString("id") == null ? ("zone_" + zk) : src.getString("id"), gz);
            }
        }

        grid.put("zones", gridZones);
        boolean noZone = mapZones == null || mapZones.isEmpty();
        grid.put("stepRemain", noZone ? 0
                : (old == null || old.getIntValue("stepRemain") <= 0 ? 5 : old.getIntValue("stepRemain")));
        grid.put("needConfirmStepZero", old != null && old.getBooleanValue("needConfirmStepZero"));
        module.put("gridZone", grid);
        // 每次同步顺手补齐节点 realContent（幂等：已有的不动），保证同一节点的事件固定
        ensureBattleStages(cur, theme);
        // 不再每步提前定型：占位格只在「被点进」时定型（见 resolveClickedPlaceholder）
        // resolveFrontierNodes(cur, theme);
        ArknightsApplication.LOGGER.info("[rlv2] 同步网格地图模块（theme={}，zones={}，节点={}）", theme,
                gridZones.size(), countGridNodes(gridZones));
    }

    private static int countGridNodes(JSONObject gridZones) {
        int n = 0;
        for (String k : gridZones.keySet()) {
            JSONObject z = gridZones.getJSONObject(k);
            JSONObject nodes = z == null ? null : z.getJSONObject("nodes");
            n += nodes == null ? 0 : nodes.size();
        }
        return n;
    }

    @PostMapping(value = {"/upgradeNode"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject upgradeNode(@RequestHeader(value = "secret", required = false) String secret,
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
        String theme = themeOf(cur);
        String nodeType = body == null ? null : body.getString("nodeType");
        if (nodeType == null || nodeType.isEmpty()) {
            nodeType = nodeTypeNameAt(cur, cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position"));
        }
        JSONObject player = cur.getJSONObject("player");
        // 官服状态位置：module.nodeUpgrade.nodeTypeInfoMap[<nodeType>].currUpgradeIndex（-1 起）
        JSONObject module0 = cur.getJSONObject("module");
        JSONObject nodeUpgrade = module0 == null ? null : module0.getJSONObject("nodeUpgrade");
        JSONObject infoMap = nodeUpgrade == null ? null : nodeUpgrade.getJSONObject("nodeTypeInfoMap");
        JSONObject info = infoMap == null || nodeType == null ? null : infoMap.getJSONObject(nodeType);
        if (info == null) {
            return bad("该主题没有节点升级模块或节点类型非法", 1);
        }
        int currIdx = info.getIntValue("currUpgradeIndex");
        int currentLevel = currIdx + 1;
        JSONObject step = nodeUpgradeStep(theme, nodeType, currentLevel + 1);
        if (step == null) {
            return bad("该节点没有下一级升级", 1);
        }
        String costItem = step.getString("costItemId");
        int costCount = step.getIntValue("costItemCount");
        // 消耗构想：fragments 里 id 匹配且未使用的，按数量标记 used
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        JSONObject frags = fragment == null ? null : fragment.getJSONObject("fragments");
        int consumed = 0;
        if (frags != null && costItem != null) {
            for (String k : frags.keySet()) {
                if (consumed >= costCount) {
                    break;
                }
                JSONObject f = frags.getJSONObject(k);
                if (costItem.equals(f.getString("id")) && !f.getBooleanValue("used")) {
                    f.put("used", true);
                    f.put("ei", ArknightsApplication.getTimestamp());
                    consumed++;
                }
            }
        }
        if (consumed < costCount) {
            return bad("构想不足（需要 " + costCount + " 个 " + costItem + "）", 1);
        }
        info.put("currUpgradeIndex", currIdx + 1);
        // 官服实测（012_07）：升 1 级后 module.fragment.limitWeight 16 → 17（负荷临界点提升）
        JSONObject fragModule = module0 == null ? null : module0.getJSONObject("fragment");
        if (fragModule != null) {
            fragModule.put("limitWeight", fragModule.getIntValue("limitWeight") + 1);
        }
        player.put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        ArknightsApplication.LOGGER.info("[rlv2] 节点升级 {} → lv{}（{}，消耗 {}×{}）",
                nodeType, currentLevel + 1, step.getString("upgradeId"), costItem, costCount);
        JSONObject out = delta(cur);
        out.put("result", 1);
        return out;
    }

    private static JSONObject nodeUpgradeStep(String theme, String nodeType, int level) {
        JSONObject mods = themeModules(theme);
        JSONObject nu = mods == null ? null : mods.getJSONObject("nodeUpgrade");
        JSONObject map = nu == null ? null : nu.getJSONObject("nodeUpgradeDataMap");
        JSONObject def = map == null || nodeType == null ? null : map.getJSONObject(nodeType);
        JSONArray list = def == null ? null : def.getJSONArray("permItemList");
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject it = list.getJSONObject(i);
            if (it.getIntValue("nodeLevel") == level) {
                return it;
            }
        }
        return null;
    }

    /** 该主题的探索道具 id 列表（官方：目前只有 rogue_3 萨米有 6 个） */
    private static java.util.List<String> exploreToolIds(String theme) {
        java.util.List<String> out = new java.util.ArrayList<>();
        JSONObject details = themeDetails(theme);
        JSONObject tools = details == null ? null : details.getJSONObject("exploreTools");
        if (tools == null) {
            return out;
        }
        for (String k : tools.keySet()) {
            out.add(k);
        }
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * 开局第三步：选择探索道具（萨米）。
     * 官方 006_00 抓包：{"step":[3,5],"items":{"0":{"id":"rogue_3_explore_tool_1","count":1},…共 6 个}}
     */
    private static JSONObject buildInitExploreToolPending(String theme, int total) {
        JSONArray step = new JSONArray();
        step.add(3);
        step.add(total);
        JSONObject items = new JSONObject(true);
        java.util.List<String> ids = exploreToolIds(theme);
        for (int i = 0; i < ids.size(); i++) {
            JSONObject one = new JSONObject(true);
            one.put("id", ids.get(i));
            one.put("count", 1);
            items.put(String.valueOf(i), one);
        }
        JSONObject initExploreTool = new JSONObject(true);
        initExploreTool.put("step", step);
        initExploreTool.put("items", items);
        JSONObject content = new JSONObject(true);
        content.put("initExploreTool", initExploreTool);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_2");
        pend.put("type", "GAME_INIT_EXPLORE_TOOL");
        pend.put("content", content);
        return pend;
    }

    /**
     * 选择开局的探索道具（萨米 rogue_3）。对应 pending GAME_INIT_EXPLORE_TOOL。
     * 请求形如 {"itemId":"rogue_3_explore_tool_3"}（也兼容 {"select":"2"} / {"index":2} 的序号形式）。
     */
    @PostMapping(value = {"/chooseInitialExploreTool"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject chooseInitialExploreTool(@RequestHeader(value = "secret", required = false) String secret,
                                               @RequestBody(required = false) JSONObject body,
                                               HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/chooseInitialExploreTool");
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject cur = userData.getJSONObject("rlv2").getJSONObject("current");
        String theme = themeOf(cur);

        JSONObject pendingPend = null;
        JSONArray pending = cur.getJSONObject("player").getJSONArray("pending");
        if (pending != null) {
            for (int i = 0; i < pending.size(); i++) {
                if ("GAME_INIT_EXPLORE_TOOL".equals(pending.getJSONObject(i).getString("type"))) {
                    pendingPend = pending.getJSONObject(i);
                    break;
                }
            }
        }
        if (pendingPend == null) {
            return bad("当前没有可选的探索道具", 1);
        }
        JSONObject items = pendingPend.getJSONObject("content").getJSONObject("initExploreTool").getJSONObject("items");
        // 官服实测（007_01）：请求 {"select":"1"} —— 序号字符串，对应 items 的键
        String want = null;
        if (body != null) {
            String sel = body.getString("select");
            if (sel == null && body.get("index") != null) {
                sel = String.valueOf(body.getIntValue("index"));
            }
            if (sel != null) {
                JSONObject one = items.getJSONObject(sel);
                want = one == null ? null : one.getString("id");
            }
            if (want == null) {
                want = body.getString("itemId");
            }
        }
        boolean allowed = false;
        for (String k : items.keySet()) {
            JSONObject one = items.getJSONObject(k);
            if (one != null && want.equals(one.getString("id"))) {
                allowed = true;
                break;
            }
        }
        if (want == null || !allowed) {
            return bad("探索道具不在可选中", 1);
        }
        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject exploreTool = inv.getJSONObject("exploreTool");
        if (exploreTool == null) {
            exploreTool = new JSONObject(true);
            inv.put("exploreTool", exploreTool);
        }
        // 官服实测（007_01）：键是 pending 的 index（"e_2"），值只有 index/id/count/ts
        //   inventory.exploreTool = {"e_2":{"index":"e_2","id":"rogue_3_explore_tool_2","count":1,"ts":<ts>}}
        String instId = pendingPend.getString("index") == null ? want : pendingPend.getString("index");
        JSONObject entry = new JSONObject(true);
        entry.put("index", instId);
        entry.put("id", want);
        entry.put("count", 1);
        entry.put("ts", ArknightsApplication.getTimestamp());
        exploreTool.put(instId, entry);
        removePending(cur, "GAME_INIT_EXPLORE_TOOL");
        ArknightsApplication.LOGGER.info("[rlv2] 选择探索道具 {}（instId={}）", want, instId);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /**
     * 废品鉴定。官方抓包（016_05，rogue_6）：
     *   请求 {"count":2}
     *   → 消耗 count 个 identifyScrapId（moduleConsts.identifyScrapId = rogue_6_scrap_G_01）
     *   → deleted.rlv2.current.module.scrap.inventory = ["s_1","s_2"]
     *   → 鉴定后价值提升（实测 s_4 的 value 2 → 12，2 件各 +5）
     */
    @PostMapping(value = {"/scrap/identify"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject scrapIdentify(@RequestHeader(value = "secret", required = false) String secret,
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
        String theme = themeOf(cur);
        int count = body == null ? 1 : Math.max(1, body.getIntValue("count"));
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONArray removed = new JSONArray();
        int upgraded = 0;
        if (scrap != null) {
            JSONObject inv = scrap.getJSONObject("inventory");
            String identifyId = identifyScrapId(theme);
            if (inv != null && identifyId != null) {
                java.util.List<String> keys = new java.util.ArrayList<>(inv.keySet());
                for (String k : keys) {
                    if (removed.size() >= count) {
                        break;
                    }
                    if (identifyId.equals(inv.getJSONObject(k).getString("id"))) {
                        inv.remove(k);
                        removed.add(k);
                    }
                }
                // 每鉴定 1 件，随机让一件自然物价值 +5（最高 12，与抓包 2→12 的上限一致）
                JSONObject best = null;
                for (String k : inv.keySet()) {
                    JSONObject it = inv.getJSONObject(k);
                    if (it.getString("id") != null && it.getString("id").startsWith(theme + "_scrap_G_")) {
                        if (best == null || it.getIntValue("value") > best.getIntValue("value")) {
                            best = it;
                        }
                    }
                }
                for (int i = 0; i < removed.size(); i++) {
                    if (best != null) {
                        best.put("value", Math.min(12, best.getIntValue("value") + 5));
                        upgraded++;
                    } else {
                        JSONObject fresh = new JSONObject(true);
                        String instId = "s_" + (inv.size() + 100 + i);
                        fresh.put("instId", instId);
                        fresh.put("id", theme + "_scrap_G_03");
                        fresh.put("value", 5);
                        fresh.put("useCnt", 0);
                        fresh.put("ts", ArknightsApplication.getTimestamp());
                        inv.put(instId, fresh);
                        upgraded++;
                    }
                }
            }
        }
        recInc(cur, "cntScrapIdentify", removed.size());
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        if (!removed.isEmpty()) {
            JSONObject invDel = new JSONObject(true);
            invDel.put("inventory", removed);
            JSONObject scrapDel = new JSONObject(true);
            scrapDel.put("scrap", invDel);
            JSONObject moduleDel = new JSONObject(true);
            moduleDel.put("module", scrapDel);
            JSONObject curDel = new JSONObject(true);
            curDel.put("current", moduleDel);
            JSONObject rlDel = new JSONObject(true);
            rlDel.put("rlv2", curDel);
            out.getJSONObject("playerDataDelta").put("deleted", rlDel);
        }
        ArknightsApplication.LOGGER.info("[rlv2] 废品鉴定：消耗 {} 件，提升 {} 次", removed.size(), upgraded);
        return out;
    }

    private static String identifyScrapId(String theme) {
        JSONObject mods = themeModules(theme);
        JSONObject scrapData = mods == null ? null : mods.getJSONObject("scrap");
        JSONObject consts = scrapData == null ? null : scrapData.getJSONObject("moduleConsts");
        return consts == null ? null : consts.getString("identifyScrapId");
    }

    /**
     * 载具全免费开关：config.json → `rogue.freeVehicle: true`
     * true = 所有载具（MOVE 零件）都不消耗行动力、不限使用次数，
     *        但**仍然按该载具自己的 rangeType / range / node 规则校验落点是否合法**。
     */
    private static boolean freeVehicleEnabled() {
        JSONObject cfg = ArkaightsApplicationSafeConfig();
        JSONObject rogue = cfg == null ? null : cfg.getJSONObject("rogue");
        return rogue != null && rogue.getBooleanValue("freeVehicle");
    }

    private static JSONObject ArkaightsApplicationSafeConfig() {
        return ArknightsApplication.serverConfig;
    }

    /** 开局发一辆车（可选）：config.json → rogue.grantVehicle = {"rogue_6":"rogue_6_scrap_M_08"} */
    private static String grantVehicleFor(String theme) {
        JSONObject cfg = ArknightsApplication.serverConfig;
        JSONObject rogue = cfg == null ? null : cfg.getJSONObject("rogue");
        JSONObject grant = rogue == null ? null : rogue.getJSONObject("grantVehicle");
        String id = grant == null || theme == null ? null : grant.getString(theme);
        return id == null || id.isEmpty() ? null : id;
    }

    /** 当前是否「坐着载具」且开了全免费 */
    private static boolean freeRide(JSONObject cur) {
        if (!freeVehicleEnabled()) {
            return false;
        }
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONObject vehicle = scrap == null ? null : scrap.getJSONObject("activeVehicle");
        return vehicle != null && !vehicle.getBooleanValue("isWalk");
    }

    /** 当前载具的移动范围类型（moveScrapData[scrapId].rangeType）；步行返回 null */
    private static String gridVehicleRangeType(JSONObject cur) {
        String theme = themeOf(cur);
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONObject vehicle = scrap == null ? null : scrap.getJSONObject("activeVehicle");
        if (vehicle == null || vehicle.getBooleanValue("isWalk")) {
            return null;
        }
        String instId = vehicle.getString("instId");
        JSONObject inv = scrap.getJSONObject("inventory");
        JSONObject item = inv == null || instId == null ? null : inv.getJSONObject(instId);
        String scrapId = item == null ? null : item.getString("id");
        JSONObject mods = themeModules(theme);
        JSONObject scrapData = mods == null ? null : mods.getJSONObject("scrap");
        JSONObject moves = scrapData == null ? null : scrapData.getJSONObject("moveScrapData");
        JSONObject def = moves == null || scrapId == null ? null : moves.getJSONObject(scrapId);
        return def == null ? null : def.getString("rangeType");
    }

    private static void seedScrapModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        JSONObject scrapData = mods == null ? null : mods.getJSONObject("scrap");
        if (scrapData == null) {
            return;
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject scrap = new JSONObject(true);
        JSONObject vehicle = new JSONObject(true);
        vehicle.put("isWalk", true);
        scrap.put("activeVehicle", vehicle);
        // 官方抓包（2026-11-06 002_06）：开局就带 2 个 G_01 零件，携带上限 10
        JSONObject scrapInv = new JSONObject(true);
        long nowTs = ArknightsApplication.getTimestamp();
        for (int i = 1; i <= 2; i++) {
            JSONObject one = new JSONObject(true);
            String instId = "s_" + i;
            one.put("instId", instId);
            one.put("id", theme + "_scrap_G_01");
            one.put("value", 2);
            one.put("useCnt", 0);
            one.put("ts", nowTs);
            scrapInv.put(instId, one);
        }
        // 自由移动载具（config.rogue.freeVehicle）：开局直接进背包并挂上，全图任意移动 + 0 行动力
        String freeVehicle = grantVehicleFor(theme);
        if (freeVehicle != null) {
            String freeInst = "s_f1";
            JSONObject freeItem = new JSONObject(true);
            freeItem.put("instId", freeInst);
            freeItem.put("id", freeVehicle);
            freeItem.put("value", 0);
            freeItem.put("useCnt", 0);
            freeItem.put("ts", nowTs);
            scrapInv.put(freeInst, freeItem);
            JSONObject freeVeh = new JSONObject(true);
            freeVeh.put("isWalk", false);
            freeVeh.put("instId", freeInst);
            scrap.put("activeVehicle", freeVeh);
            ArknightsApplication.LOGGER.info("[rlv2] 发放自由移动载具 {}（{}）", freeVehicle, freeInst);
        }
        scrap.put("inventory", scrapInv);
        scrap.put("limit", 10);
        module.put("scrap", scrap);
        ArknightsApplication.LOGGER.info("[rlv2] 初始化废品模块（theme={}，limit=10，开局 2 个 G_01）", theme);
    }

    /** 天气模块（rogue_6）：官方 createGame 就带这套空结构 */
    private static void seedWeatherModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        if (mods == null || mods.getJSONObject("weather") == null) {
            return;
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject weather = new JSONObject(true);
        weather.put("currentMain", "");
        weather.put("currentSub", "");
        weather.put("eye", "");
        weather.put("effectArea", new JSONObject(true));
        weather.put("weatherStep", 0);
        module.put("weather", weather);
    }

    @PostMapping(value = {"/scrap/loseScrap"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject scrapLoseScrap(@RequestHeader(value = "secret", required = false) String secret,
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
        String instId = body == null ? null : body.getString("instId");
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        JSONArray removed = new JSONArray();
        if (scrap != null && instId != null) {
            JSONObject inv = scrap.getJSONObject("inventory");
            if (inv != null && inv.containsKey(instId)) {
                inv.remove(instId);
                removed.add(instId);
                ArknightsApplication.LOGGER.info("[rlv2] 丢失废品 {}（剩余 {}）", instId, inv.size());
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        if (!removed.isEmpty()) {
            JSONObject invDel = new JSONObject(true);
            invDel.put("inventory", removed);
            JSONObject scrapDel = new JSONObject(true);
            scrapDel.put("scrap", invDel);
            JSONObject moduleDel = new JSONObject(true);
            moduleDel.put("module", scrapDel);
            JSONObject curDel = new JSONObject(true);
            curDel.put("current", moduleDel);
            JSONObject rlDel = new JSONObject(true);
            rlDel.put("rlv2", curDel);
            JSONObject deleted = new JSONObject(true);
            deleted.put("rlv2", curDel.getJSONObject("rlv2"));
            out.getJSONObject("playerDataDelta").put("deleted", deleted);
        }
        return out;
    }

    private static JSONObject buildInitSupportPending(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (scenes == null || choices == null) {
            return null;
        }
        String themeNum = theme.substring(theme.length() - 1);
        String sceneId = "scene_ro" + themeNum + "_startbuff_enter";
        if (!scenes.containsKey(sceneId)) {
            return null;
        }
        String prefix = "choice_ro" + themeNum + "_startbuff_";
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String cid : choices.keySet()) {
            if (cid.startsWith(prefix)) {
                pool.add(cid);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        java.util.Collections.shuffle(pool, RANDOM);
        JSONObject choiceMap = new JSONObject(true);
        for (int i = 0; i < Math.min(3, pool.size()); i++) {
            choiceMap.put(pool.get(i), 1);
        }
        JSONObject scene = new JSONObject(true);
        scene.put("id", sceneId);
        scene.put("choices", choiceMap);
        JSONObject initSupport = new JSONObject(true);
        JSONArray step = new JSONArray();
        step.add(3);
        step.add(5);
        initSupport.put("step", step);
        initSupport.put("scene", scene);
        JSONObject content = new JSONObject(true);
        content.put("initSupport", initSupport);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_3");
        pend.put("type", "GAME_INIT_SUPPORT");
        pend.put("content", content);
        return pend;
    }

    private static void addMonthTeamChars(JSONObject userData, JSONObject cur, JSONObject initTeamPend) {
        JSONObject content = initTeamPend.getJSONObject("content");
        JSONObject initTeam = content == null ? null : content.getJSONObject("initTeam");
        JSONArray chars = initTeam == null ? null : initTeam.getJSONArray("chars");
        if (chars == null) {
            return;
        }
        JSONObject troop = cur.getJSONObject("troop");
        JSONObject owned = troop.getJSONObject("chars");
        if (owned == null) {
            owned = new JSONObject(true);
            troop.put("chars", owned);
        }
        for (int i = 0; i < chars.size(); i++) {
            String charId = chars.getJSONObject(i).getString("charId");
            if (charId == null) {
                continue;
            }
            // 月度小队干员是「预设满配」，不依赖玩家是否拥有：
            // 官服实测 evolvePhase=1 / level=80 / potentialRank=5 / mainSkillLvl=7 / favorPoint=25570
            JSONObject one = new JSONObject(true);
            one.put("charId", charId);
            one.put("type", "MONTHLY");
            one.put("evolvePhase", 1);
            one.put("level", 80);
            one.put("exp", 0);
            one.put("favorPoint", 25570);
            one.put("potentialRank", 5);
            one.put("mainSkillLvl", 7);
            one.put("skills", new JSONArray());
            one.put("skin", charId + "#2");
            one.put("isUpgrade", false);
            one.put("upgradePhase", 0);
            one.put("population", 6);
            int instId = 1 + owned.size();
            one.put("instId", String.valueOf(instId));
            one.put("troopInstId", String.valueOf(instId));
            owned.put(String.valueOf(instId), one);
            ArknightsApplication.LOGGER.info("[rlv2] 月度小队加入干员 {} (instId={})", charId, instId);
        }
    }

    private static JSONObject buildInitTeamPending(String theme, String predefinedId) {
        JSONObject details = themeDetails(theme);
        JSONObject squads = details == null ? null : details.getJSONObject("monthSquad");
        JSONObject squad = squads == null ? null : squads.getJSONObject(predefinedId);
        if (squad == null) {
            return null;
        }
        JSONObject initTeam = new JSONObject(true);
        JSONArray step = new JSONArray();
        step.add(1);
        step.add(4);
        initTeam.put("step", step);
        JSONArray chars = new JSONArray();
        JSONArray teamChars = squad.getJSONArray("teamChars");
        if (teamChars != null) {
            for (int i = 0; i < teamChars.size(); i++) {
                JSONObject tc = teamChars.getJSONObject(i);
                JSONObject one = new JSONObject(true);
                one.put("charId", tc.getString("teamCharId"));
                one.put("tmplId", null);
                one.put("uniEquipIdOfChar", null);
                one.put("type", "MONTHLY");
                chars.add(one);
            }
        }
        initTeam.put("chars", chars);
        initTeam.put("team", predefinedId);
        JSONObject content = new JSONObject(true);
        content.put("initTeam", initTeam);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_0");
        pend.put("type", "GAME_INIT_TEAM");
        pend.put("content", content);
        return pend;
    }

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
                String bandId = bands.getString(idx);
                addRelic(cur, bandId, 1);
            recInc(cur, "cntInitRelic", 1);   // 初始收藏品不计入「获得物品数」
            // 官服 brief.band 就是这件开局变体收藏品（例：rogue_6_band_8）
            JSONObject gOuter = game.getJSONObject("outer");
            if (gOuter == null) {
                gOuter = new JSONObject(true);
                game.put("outer", gOuter);
            }
            gOuter.put("band", bandId);
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
        java.util.List<String> ticketIds = ticketIdsForGroup(theme, select);
        if (ticketIds.isEmpty()) {
            JSONObject allTickets = themeDetails(theme) == null ? null
                    : themeDetails(theme).getJSONObject("recruitTickets");
            if (allTickets != null) {
                java.util.List<String> keys = new ArrayList<>(allTickets.keySet());
                java.util.Collections.shuffle(keys, RANDOM);
                ticketIds = keys.subList(0, Math.min(3, keys.size()));
            }
        }
        // 官服：券初始 state=0（未激活），list 为空；激活后才有候选
        // 官方券号从 t_3 开始（007_02 实测 t_3/t_4/t_5）
        JSONArray newTickets = new JSONArray();
        for (int i = 0; i < ticketIds.size(); i++) {
            JSONObject ticket = new JSONObject(true);
            String index = "t_" + (i + 3);
            newTickets.add(index);
            ticket.put("index", index);
            ticket.put("id", ticketIds.get(i));
            ticket.put("state", 0);
            ticket.put("list", new JSONArray());
            ticket.put("result", null);
            ticket.put("ts", ArknightsApplication.getTimestamp());
            ticket.put("from", "initial");
            ticket.put("mustExtra", 0);
            ticket.put("needAssist", true);
            recruit.put(index, ticket);
        }
        // 关键：把三张券号回填进 GAME_INIT_RECRUIT 待办，客户端靠它渲染初始招募界面
        JSONObject player0 = cur.getJSONObject("player");
        JSONArray pending0 = player0 == null ? null : player0.getJSONArray("pending");
        if (pending0 != null) {
            for (int i = 0; i < pending0.size(); i++) {
                JSONObject pend0 = pending0.getJSONObject(i);
                if (!"GAME_INIT_RECRUIT".equals(pend0.getString("type"))) {
                    continue;
                }
                JSONObject initRecruit0 = pend0.getJSONObject("content") == null ? null
                        : pend0.getJSONObject("content").getJSONObject("initRecruit");
                if (initRecruit0 != null) {
                    initRecruit0.put("tickets", newTickets);
                }
            }
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
                String theme = cur.getJSONObject("game").getString("theme");
                String prof = professionOfTicket(theme, ticket.getString("id"));
                JSONObject ownedChars = userData.getJSONObject("troop") == null ? null
                        : userData.getJSONObject("troop").getJSONObject("chars");
                JSONArray list = new JSONArray();
                if (enableAllChar()) {
                    // 全干员池：按券的职业过滤 character_table（未拥有的也进候选）
                    JSONObject table = ArknightsApplication.characterJson;
                    java.util.List<String> allIds = new ArrayList<>();
                    if (table != null) {
                        for (String cid : table.keySet()) {
                            if (!cid.startsWith("char_")) {
                                continue;
                            }
                            JSONObject def = table.getJSONObject(cid);
                            if (def == null || def.getBooleanValue("isNotObtainable")) {
                                continue;
                            }
                            if (prof != null) {
                                String cp = def.getString("profession");
                                if (cp != null && !prof.equals(cp)) {
                                    continue;
                                }
                            }
                            allIds.add(cid);
                        }
                    }
                    java.util.Collections.shuffle(allIds, RANDOM);
                    for (String cid : allIds) {
                        if (list.size() >= 40) {
                            break;
                        }
                        JSONObject cand = buildCharFromTable(cid);
                        if (cand == null) {
                            continue;
                        }
                        applyRecruitPhase(cand, cur);
                        if (cand.getBooleanValue("_skip")) {
                            continue;
                        }
                        cand.remove("_skip");
                        cand.put("instId", String.valueOf(list.size()));
                        cand.put("troopInstId", "0");
                        list.add(cand);
                    }
                } else if (ownedChars != null && !ownedChars.isEmpty()) {
                    java.util.List<String> keys = new ArrayList<>(ownedChars.keySet());
                    java.util.Collections.shuffle(keys, RANDOM);
                    for (String k : keys) {
                        if (list.size() >= 36) {
                            break;
                        }
                        JSONObject one = ownedChars.getJSONObject(k);
                        String charId = one == null ? null : one.getString("charId");
                        if (charId == null) {
                            continue;
                        }
                        if (prof != null) {
                            String cp = charProfession(charId);
                            if (cp != null && !prof.equals(cp)) {
                                continue;
                            }
                        }
                        JSONObject cand = buildChar(userData, 0, charId);
                        if (cand == null) {
                            continue;
                        }
                        applyRecruitPhase(cand, cur);
                        if (cand.getBooleanValue("_skip")) {
                            continue;
                        }
                        cand.remove("_skip");
                        cand.put("instId", String.valueOf(list.size()));
                        cand.put("troopInstId", "0");
                        list.add(cand);
                    }
                }
                ticket.put("state", 1);
                ticket.put("list", list);
                // 官服实测：激活后推 RECRUIT 待办（content = {"recruit":{"ticket":"t_3"}}），
                // 客户端靠它切到候选列表界面；recruitChar 完成后再移除
                JSONArray pendArr = cur.getJSONObject("player").getJSONArray("pending");
                if (pendArr == null) {
                    pendArr = new JSONArray();
                    cur.getJSONObject("player").put("pending", pendArr);
                }
                int nextIdx = 5;
                for (int i = 0; i < pendArr.size(); i++) {
                    String ix = pendArr.getJSONObject(i).getString("index");
                    if (ix != null && ix.startsWith("e_")) {
                        nextIdx = Math.max(nextIdx, intOrDefault(ix.substring(2), 5) + 1);
                    }
                }
                // 先清掉旧的 RECRUIT 待办，避免重复（重复会让客户端招募界面异常）
                for (int i = pendArr.size() - 1; i >= 0; i--) {
                    if ("RECRUIT".equals(pendArr.getJSONObject(i).getString("type"))) {
                        pendArr.remove(i);
                    }
                }
                JSONObject recruitContent = new JSONObject(true);
                JSONObject recruitInner = new JSONObject(true);
                recruitInner.put("ticket", id);
                recruitContent.put("recruit", recruitInner);
                JSONObject recruitPend = new JSONObject(true);
                recruitPend.put("index", "e_" + nextIdx);
                recruitPend.put("type", "RECRUIT");
                recruitPend.put("content", recruitContent);
                pendArr.add(0, recruitPend);
                ArknightsApplication.LOGGER.info("[rlv2] 激活招募券 {}（{}）→ 候选 {} 个，推出 RECRUIT 待办",
                        id, prof, list.size());
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
        // 官服抓包（2026-11-04 rogue_2）：{"ticketIndex":"t_9","optionId":"8"} —— 选的是券内候选的下标
        String optionId = body == null ? null : body.getString("optionId");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/recruitChar ticket="
                + ticketIndex + " optionId=" + optionId);
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
        // 官服：optionId 是券内候选列表的下标，选中者的 instId 就等于该下标
        JSONObject newChar = null;
        JSONObject recruit0 = cur.getJSONObject("inventory").getJSONObject("recruit");
        if (recruit0 != null && ticketIndex != null) {
            JSONObject t0 = recruit0.getJSONObject(ticketIndex);
            JSONArray candList = t0 == null ? null : t0.getJSONArray("list");
            int idx = intOrDefault(optionId, -1);
            if (candList != null && idx >= 0 && idx < candList.size()) {
                newChar = JSONObject.parseObject(candList.getJSONObject(idx).toJSONString());
            }
        }
        if (newChar == null) {
            newChar = buildChar(userData, 0);
        }
        if (newChar != null) {
            JSONObject existed = ownedInRun(cur, newChar.getString("charId"));
            boolean upgrade = newChar.getBooleanValue("isUpgrade") || existed != null;
            if (existed != null) {
                // 官服：同名干员再次招募 = 原地精英化升级（队伍条目 instId 不变，upgradePhase 记 1）
                existed.put("evolvePhase", Math.max(existed.getIntValue("evolvePhase"), newChar.getIntValue("evolvePhase")));
                existed.put("level", newChar.getIntValue("level"));
                existed.put("isUpgrade", true);
                existed.put("upgradeLimited", false);
                existed.put("upgradePhase", 1);
                String oldInst = existed.getString("instId") == null
                        ? existed.getString("troopInstId") : existed.getString("instId");
                newChar.put("instId", optionId == null ? oldInst : optionId);
                newChar.put("troopInstId", oldInst);
            } else {
                // 官服 instId 取 optionId；两张券用同一个下标时会撞车，撞了就让位到下一个空位
                int instId = optionId != null ? intOrDefault(optionId, chars.size()) : chars.size();
                int guard = 0;
                while (chars.containsKey(String.valueOf(instId)) && guard++ < 500) {
                    instId++;
                }
                newChar.put("instId", String.valueOf(instId));
                newChar.put("troopInstId", String.valueOf(instId));
                chars.put(String.valueOf(instId), newChar);
            }
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

    @PostMapping(value = {"/recruitAssistChar"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject recruitAssistChar(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody(required = false) JSONObject body,
                                        HttpServletRequest request, HttpServletResponse response) {
        // 官服抓包（2026-11-04 rogue_2）：
        // {"ticketIndex":"t_3","profession":"SNIPER","assistUid":"172925215","assistCharId":"char_1035_wisdel"}
        String charId = body == null ? null : body.getString("assistCharId");
        if (charId == null && body != null) {
            charId = body.getString("charId");
        }
        String profession = body == null ? null : body.getString("profession");
        long assistUid = body == null ? 0 : body.getLongValue("assistUid");
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
            newChar.put("type", "ASSIST");   // 助战干员（官方 chars[].type = "ASSIST"）
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
            String profName = professionOf(charId);
            if (profName == null) {
                profName = profession;   // 请求里带的 profession（官方抓包字段）
            }
            if (profName != null) {
                prof.put(profName, prof.getIntValue(profName) + 1);
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("chars", outChars);
        return result;
    }

    private static String professionOf(String charId) {
        try {
            JSONObject table = ArknightsApplication.characterJson;
            JSONObject c = table == null ? null : table.getJSONObject(charId);
            return c == null ? null : c.getString("profession");
        } catch (Exception e) {
            return null;
        }
    }

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

    private static String themeOf(JSONObject cur) {
        JSONObject game = cur.getJSONObject("game");
        return game == null ? "" : String.valueOf(game.getString("theme"));
    }

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

    private static void grantDiceCount(JSONObject cur, String theme, String choice) {
        JSONObject module = cur.getJSONObject("module");
        JSONObject diceState = module == null ? null : module.getJSONObject("dice");
        if (diceState == null) {
            return;
        }
        JSONObject choiceDef = choiceDef(theme, choice);
        if (choiceDef == null) {
            return;
        }
        JSONObject display = choiceDef.getJSONObject("displayData");
        String itemId = display == null ? null : display.getString("itemId");
        if (itemId == null || !itemId.endsWith("_dice")) {
            return;
        }
        int gain = parseGainCount(choiceDef.getString("description"));
        if (gain > 0) {
            diceState.put("count", diceState.getIntValue("count") + gain);
            ArknightsApplication.LOGGER.info("[rlv2] 事件选项发放掷骰次数 +{} → {}", gain, diceState.getIntValue("count"));
        }
    }

    private static boolean choiceTriggersDice(String theme, String choice) {
        if (choice == null) {
            return false;
        }
        if ("choice_leave".equals(choice)) {
            return true;
        }
        JSONObject def = choiceDef(theme, choice);
        return def != null && "DICE".equalsIgnoreCase(def.getString("leftDecoType"));
    }

    private static JSONObject choiceDef(String theme, String choice) {
        JSONObject details = themeDetails(theme);
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        return choices == null || choice == null ? null : choices.getJSONObject(choice);
    }

    private static int parseGainCount(String desc) {
        if (desc == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<@[^>]*get>\\s*(\\d+)\\s*</>").matcher(desc);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static void seedDiceModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        JSONObject diceData = mods == null ? null : mods.getJSONObject("dice");
        JSONObject defs = diceData == null ? null : diceData.getJSONObject("dice");
        if (defs == null) {
            return;
        }
        String diceId = theme + "_dice_1";
        if (!defs.containsKey(diceId)) {
            java.util.List<String> keys = new java.util.ArrayList<>(defs.keySet());
            if (keys.isEmpty()) {
                return;
            }
            diceId = keys.get(0);
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject dice = new JSONObject(true);
        dice.put("id", diceId);
        dice.put("count", 1);
        module.put("dice", dice);
        ArknightsApplication.LOGGER.info("[rlv2] 初始化骰子模块 {} count=1（theme={}）", diceId, theme);
    }

    private static String randomRelicId(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        if (relics == null || relics.isEmpty()) {
            return null;
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String k : relics.keySet()) {
            String id = relics.getJSONObject(k).getString("id");
            if (id != null && !id.contains("_band_")) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids.get(RANDOM.nextInt(ids.size()));
    }

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
            // 月度小队：消费 GAME_INIT_TEAM 时把固定小队干员加进队伍
            // 官服 018_01：finishEvent 后 troop.chars 多出 type=MONTHLY 的干员
            JSONObject head = pending.getJSONObject(0);
            if (head != null && "GAME_INIT_TEAM".equals(head.getString("type"))) {
                addMonthTeamChars(userData, cur, head);
            }
            pending.remove(0);
        }
        if (pending == null || pending.isEmpty()) {
            JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
            if (zones == null || zones.isEmpty()) {
                enterFirstZone(userData, cur);
            } else {
                player.put("state", "WAIT_MOVE");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ───────────────────────── gridZone（新主题网格地图）─────────────────────────

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
        // 官方 route 是整条路径（相邻格逐格走）。逐段校验：每段必须在上一格的 next 里，
        // 否则就是斜穿/跳格，直接拒绝，避免客户端算出非法行走路径。
        // 载具（freeVehicle=true 时任意载具都走这条）：按载具自己的 rangeType/range/node 校验落点，
        // 满足则允许跨格直达；不满足直接拒。步行仍然要求逐格相邻。
        if (freeRide(cur)) {
            if (!vehicleTargetAllowed(cur, node)) {
                ArknightsApplication.LOGGER.warn("[rlv2] 载具({})无法移动到 {}（rangeType={}）",
                        nodeId, node.getJSONObject("pos"), gridVehicleRangeType(cur));
                return null;
            }
        } else if (route.size() > 1) {
            for (int i = 1; i < route.size(); i++) {
                JSONObject from = nodes.getJSONObject(route.getString(i - 1));
                JSONObject toNode = nodes.getJSONObject(route.getString(i));
                if (from == null || toNode == null) {
                    ArknightsApplication.LOGGER.warn("[rlv2] route 路径节点缺失: {}", route);
                    return null;
                }
                JSONObject toPos = toNode.getJSONObject("pos");
                boolean linked = false;
                JSONArray nextArr = from.getJSONArray("next");
                if (nextArr != null) {
                    for (int k = 0; k < nextArr.size(); k++) {
                        JSONObject e = nextArr.getJSONObject(k);
                        if (e.getIntValue("x") == toPos.getIntValue("x")
                                && e.getIntValue("y") == toPos.getIntValue("y")) {
                            linked = true;
                            break;
                        }
                    }
                }
                if (!linked) {
                    ArknightsApplication.LOGGER.warn("[rlv2] route 路径不相邻: {} -> {} ({})",
                            route.getString(i - 1), route.getString(i), route);
                    return null;
                }
            }
        }
        // 扣网格行动力。官方实测（15 条抓包 100% 吻合）：
        //   步行 (isWalk=true)      → 扣「曼哈顿距离」步
        //   装了零件 (isWalk=false)  → 扣「零件的 step 值」步（moveScrapData[scrapId].step，0 或 1）
        // 扣步在 moveInternal 里做（translateRoute 只是纯换算，不落库）
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
            enterFirstZone(userData, cur);
        }
        boolean advanced = false;
        JSONObject prevPos = player.getJSONObject("cursor").getJSONObject("position");
        boolean wasAtZoneEnd = isZoneEndAt(cur, prevPos);
        // /moveTo（非 gridZone 版）也走同一套载具落点校验，避免绕过 translateRoute
        if (to != null && freeRide(cur)) {
            JSONObject targetNodeCheck = nodeAtPos(cur, to);
            if (targetNodeCheck != null && !vehicleTargetAllowed(cur, targetNodeCheck)) {
                ArknightsApplication.LOGGER.warn("[rlv2] 载具({})无法移动到 {}（被 range/node 规则拒绝）",
                        gridVehicleRangeType(cur), to);
                return bad("载具无法移动到该位置", 1);
            }
        }
        if (to != null) {
            // 网格行动力：这里才真正扣（translateRoute 里的计算不落库，之前一直白扣）
            JSONObject gridMod = cur.getJSONObject("module") == null ? null
                    : cur.getJSONObject("module").getJSONObject("gridZone");
            if (gridMod != null) {
                JSONObject toNodeForCost = nodeAtPos(cur, to);
                int cost = toNodeForCost == null ? 1 : gridMoveCost(cur, toNodeForCost);
                boolean zeroStepVehicle = freeRide(cur);
                if (cost <= 0 && !zeroStepVehicle) {
                    cost = 1;
                }
                if (zeroStepVehicle) {
                    // 自由载具：不扣行动力，也不触发「步数为 0」确认
                    gridMod.put("needConfirmStepZero", false);
                    ArknightsApplication.LOGGER.info("[rlv2] 自由载具移动（不扣步数），剩余 {}",
                            gridMod.getIntValue("stepRemain"));
                } else {
                    int remainSteps = Math.max(0, gridMod.getIntValue("stepRemain") - cost);
                    gridMod.put("stepRemain", remainSteps);
                    if (remainSteps == 0) {
                        gridMod.put("needConfirmStepZero", true);
                    }
                    ArknightsApplication.LOGGER.info("[rlv2] 网格步数 -{} → {}", cost, remainSteps);
                }
            }
            player.getJSONObject("cursor").put("position", to);
            if ("rogue_6".equals(themeOf(cur))) {
                seedGridZoneModule(cur, themeOf(cur));
            }
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
            // 官服 trace 元素是 {zone, position}，不是裸 {x,y}；
            // 裸坐标会让客户端的网格插件解析失败 → 地图上「点了没反应」
            // 官服实证（041_10）：fts 在**踏入该格**的这一包就写入
            {
                String zid0 = String.valueOf(player.getJSONObject("cursor").getIntValue("zone"));
                JSONObject z0 = cur.getJSONObject("map").getJSONObject("zones").getJSONObject(zid0);
                JSONObject n0 = z0 == null ? null : z0.getJSONObject("nodes")
                        .getJSONObject(to.getIntValue("x") * 100 + to.getIntValue("y") + "");
                if (n0 != null && n0.getLongValue("fts") == 0) {
                    n0.put("fts", ArknightsApplication.getTimestamp());
                }
            }
            // 官服实证（041_10）：fts 在踏入该格这一包写入
            {
                String zid0 = String.valueOf(player.getJSONObject("cursor").getIntValue("zone"));
                JSONObject z0 = cur.getJSONObject("map").getJSONObject("zones").getJSONObject(zid0);
                JSONObject n0 = z0 == null ? null : z0.getJSONObject("nodes")
                        .getJSONObject(to.getIntValue("x") * 100 + to.getIntValue("y") + "");
                if (n0 != null && n0.getLongValue("fts") == 0) {
                    n0.put("fts", ArknightsApplication.getTimestamp());
                }
            }
            JSONObject traceEntry = new JSONObject(true);
            traceEntry.put("zone", player.getJSONObject("cursor").getIntValue("zone"));
            traceEntry.put("position", to);
            trace.add(traceEntry);
            // ⚠ 已删除「站在终点再走一步就进下一层」：官服是 selectChoice 选 *_final*_数字 才进层，
            // 旧逻辑会把玩家瞬移到新层的旧坐标 → 地图突变、出口消失（2026-11-06 修）
        }
        if (!advanced) {
            if (battle) {
                player.put("state", "PENDING");
                player.put("pending", buildBattlePending(cur));
            } else {
                pushNodePending(cur);
            // 走到某格后，紧邻这一圈的未定型格才显现内容（用户实测：距离 1 且连线）
            revealAroundCursor(cur, themeOf(cur), 1);
            if ("rogue_6".equals(themeOf(cur))) {
                seedGridZoneModule(cur, themeOf(cur));
            }
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

    /**
     * 到达节点后触发内容。官服实测（gridZone/moveTo 219 条）：
     *   - 战斗节点 → 响应里没有 pending（客户端自己弹「开始作战」，再调 moveAndBattleStart）
     *   - 商店节点 → pending BATTLE_SHOP（54 条）
     *   - 事件节点 → pending SCENE（115 条）
     * 之前只按主题的 SHOP/INCIDENT/REST 位判断，而 rogue_6 没有 SHOP 位（商店是 BATTLE_SHOP/SCRAP_SHOP），
     * 得偿所愿/失与得/先行一步/狭路相逢/命运所指 等也都不匹配 → 踩上去什么都不发生。
     * 改成按节点类型名分类。
     */
    /**
     * 点进「未定型占位(256)」时才定型（只改被点的这一格，避免每走一步刷新整片地图）：
     *   visibility=2（未知的凶戾）→ 战斗：type 1/2 + stage，客户端随后调 moveAndBattleStart
     *   visibility=1（未知的诡秘）→ 事件：不期而遇/安全角落/得偿所愿/失与得/诡意行商 → 推 SCENE
     */
    private static void resolveClickedPlaceholder(JSONObject cur, String theme) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject pos = player.getJSONObject("cursor").getJSONObject("position");
        JSONObject node = nodeAtPos(cur, pos);
        if (node == null || node.getIntValue("type") != 256) {
            return;
        }
        JSONObject details = themeDetails(theme);
        int level = currentLevel(cur);
        String prefix = themePrefix(theme);
        JSONObject stages = details == null ? null : details.getJSONObject("stages");
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> elitePool = new java.util.ArrayList<>();
        if (stages != null) {
            String np = prefix + "_n_" + level + "_";
            String ep = prefix + "_e_" + level + "_";
            for (String id : stages.keySet()) {
                if (id.startsWith(np)) {
                    normalPool.add(id);
                } else if (id.startsWith(ep)) {
                    elitePool.add(id);
                }
            }
        }
        java.util.Collections.sort(normalPool);
        java.util.Collections.sort(elitePool);
        java.util.Random rnd = new java.util.Random(
                (String.valueOf(node.get("index")) + ":" + level + ":" + theme).hashCode());
        if (node.getIntValue("visibility") == 2) {
            boolean elite = rnd.nextDouble() < 0.25;
            java.util.List<String> pool = elite && !elitePool.isEmpty() ? elitePool : normalPool;
            if (pool == null || pool.isEmpty()) {
                pool = elitePool.isEmpty() ? normalPool : elitePool;
            }
            if (pool != null && !pool.isEmpty()) {
                node.put("stage", pool.get(rnd.nextInt(pool.size())));
            }
            node.put("type", elite ? 2 : 1);
        } else {
            String[] pool = {"INCIDENT", "INCIDENT", "REST", "WISH", "SACRIFICE", "BATTLE_SHOP"};
            String name = pool[rnd.nextInt(pool.length)];
            int bit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, name);
            node.put("type", bit == 0 ? 32 : bit);
            node.remove("stage");
        }
        // 事件/商店类必须带 realContent.scene，否则客户端画不出图标（「点一下就消失」）
        if (node.getIntValue("type") != 1 && node.getIntValue("type") != 2
                && node.getIntValue("type") != 4) {
            String scene = sceneForTypeName(theme, typeNameOf(theme, node.getIntValue("type")),
                    String.valueOf(node.get("index")));
            if (scene != null) {
                JSONObject rc = new JSONObject(true);
                rc.put("scene", scene);
                if (WRITE_REALCONTENT) {
                node.put("realContent", rc);
                }
            }
        }
        node.put("visibility", 0);
        // 官服形态：已定型的事件/商店格必须带 realContent.scene，否则客户端画不出图标
        if (node.getIntValue("type") != 1 && node.getIntValue("type") != 2
                && node.getIntValue("type") != 4) {
            String sc = sceneForTypeName(theme, typeNameOf(theme, node.getIntValue("type")),
                    String.valueOf(node.get("index")));
            if (sc != null) {
                JSONObject rc = new JSONObject(true);
                rc.put("scene", sc);
                node.put("realContent", rc);
                ArknightsApplication.LOGGER.info("[rlv2] 写入 realContent.scene={}", sc);
            }
        }
        ArknightsApplication.LOGGER.info("[rlv2] 点击未定型格 {} → type={}（vis={}）",
                node.get("index"), node.getIntValue("type"), node.getIntValue("visibility"));
    }

    /**
     * 官服实证（抓包 041_05 / 041_09）：
     * 玩家**打完战斗 / 离开场景 / 离开商店**后，服务端一次性把「前方 BFS 深度 2 内」的
     * 未定型格(256) 定型，并把 visibility 置 0（完全揭示）。
     * 也就是说：揭示发生在「结算」时，而不是每次移动时 —— 所以地图不会每步重绘。
     */
    /** 结局分支标记（沙盘α/β、怦然信标、谒日之祈/祭日之契） */
    private static JSONObject endingFlags(JSONObject cur) {
        JSONObject flags = cur.getJSONObject("_endingFlags");
        if (flags == null) {
            flags = new JSONObject(true);
            flags.put("sandbox", false);   // 沙盘α/β（结局2 前提）
            flags.put("beacon", false);    // 怦然信标（结局3 前提，可进第6层）
            flags.put("prayer", false);    // 谒日之祈 + 祭日之契（结局4 前提）
            cur.put("_endingFlags", flags);
        }
        return flags;
    }

    /** 按选择 id 记录结局分支（选项 id 里带关键词就置位） */
    private static void noteEndingChoice(JSONObject cur, String choice) {
        if (choice == null) {
            return;
        }
        String c = choice.toLowerCase();
        JSONObject flags = endingFlags(cur);
        boolean changed = false;
        // 真实数据里没有 sandbox/beacon 字样，结局分支就是 *_end2_* / *_end3_* / *_end4_*
        if (c.contains("_end2")) {
            changed |= !flags.getBooleanValue("sandbox");
            flags.put("sandbox", true);
        }
        if (c.contains("_end3")) {
            changed |= !flags.getBooleanValue("beacon");
            flags.put("beacon", true);
        }
        if (c.contains("_end4")) {
            changed |= !flags.getBooleanValue("prayer");
            flags.put("prayer", true);
        }
        if (changed) {
            ArknightsApplication.LOGGER.info("[rlv2] 结局分支标记更新: {}", flags.toJSONString());
        }
    }

    private static void revealAroundCursor(JSONObject cur, String theme, int depth) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject cursor = player.getJSONObject("cursor");
        String zid = String.valueOf(cursor.getIntValue("zone"));
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones == null ? null : zones.getJSONObject(zid);
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null) {
            return;
        }
        // 已走访坐标 = trace + 当前
        java.util.Set<String> visited = new java.util.HashSet<>();
        JSONArray trace = player.getJSONArray("trace");
        if (trace != null) {
            for (int i = 0; i < trace.size(); i++) {
                JSONObject t = trace.getJSONObject(i);
                JSONObject tp = t == null ? null : t.getJSONObject("position");
                Integer tz = t == null ? null : t.getInteger("zone");
                if (tp != null && (tz == null || tz.intValue() == cursor.getIntValue("zone"))) {
                    visited.add(tp.getIntValue("x") + "," + tp.getIntValue("y"));
                }
            }
        }
        JSONObject cp = cursor.getJSONObject("position");
        visited.add(cp.getIntValue("x") + "," + cp.getIntValue("y"));

        int level = currentLevel(cur);
        for (String nk : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(nk);
            if (n == null || n.getIntValue("type") != 256) {
                continue;
            }
            JSONObject pos = n.getJSONObject("pos");
            int x = pos.getIntValue("x");
            int y = pos.getIntValue("y");
            // 与任一走访格直接相邻（四邻接）
            boolean adjacent = visited.contains((x + 1) + "," + y) || visited.contains((x - 1) + "," + y)
                    || visited.contains(x + "," + (y + 1)) || visited.contains(x + "," + (y - 1));
            if (adjacent) {
                resolvePlaceholderNode(n, theme, level);
            }
        }
    }

    /** 把一格 256 定型：vis=2（凶戾）→ 战斗；vis=1（诡秘）→ 事件/商店 */
    /** 按类型名挑一个该主题真实存在的 *_enter 场景（确定性：节点 index 哈希） */
    private static String sceneForTypeName(String theme, String typeName, String nodeIndex) {
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        if (scenes == null || typeName == null) {
            return null;
        }
        // 类型 → 场景关键词（rogue_6 实测前缀）
        String[] tokens;
        switch (typeName) {
            case "REST": tokens = new String[]{"_rest_enter", "_rest"}; break;
            case "WISH": tokens = new String[]{"_wish_enter", "_wish"}; break;
            case "SACRIFICE": tokens = new String[]{"_sacrifice1_enter", "_sacrifice"}; break;
            case "EXPEDITION": tokens = new String[]{"_scout_enter", "_scout"}; break;
            case "PORTAL": tokens = new String[]{"_portal", "enter"}; break;
            case "EVACUATE": tokens = new String[]{"_evacuate_enter", "_evacuate"}; break;
            case "FINAL": tokens = new String[]{"_final1_enter", "_final"}; break;
            case "DUEL": tokens = new String[]{"_duel", "enter"}; break;
            case "INCIDENT": tokens = new String[]{"_normal1_enter", "_surprise_enter", "_relic", "_bomb", "_task", "_sala", "_normal"}; break;
            default: tokens = new String[]{"enter"};
        }
        for (String tok : tokens) {
            for (String k : scenes.keySet()) {
                if (k.contains(tok) && k.endsWith("enter")) {
                    return k;
                }
            }
            for (String k : scenes.keySet()) {
                if (k.contains(tok)) {
                    return k;
                }
            }
        }
        // 兜底：任何 *_enter
        for (String k : scenes.keySet()) {
            if (k.endsWith("_enter")) {
                return k;
            }
        }
        return null;
    }

    private static void resolvePlaceholderNode(JSONObject node, String theme, int level) {
        JSONObject details = themeDetails(theme);
        String prefix = themePrefix(theme);
        JSONObject stages = details == null ? null : details.getJSONObject("stages");
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> elitePool = new java.util.ArrayList<>();
        if (stages != null) {
            String np = prefix + "_n_" + level + "_";
            String ep = prefix + "_e_" + level + "_";
            for (String id : stages.keySet()) {
                if (id.startsWith(np)) {
                    normalPool.add(id);
                } else if (id.startsWith(ep)) {
                    elitePool.add(id);
                }
            }
        }
        java.util.Collections.sort(normalPool);
        java.util.Collections.sort(elitePool);
        java.util.Random rnd = new java.util.Random(
                (String.valueOf(node.get("index")) + ":" + level + ":" + theme).hashCode());
        if (node.getIntValue("visibility") == 2) {
            boolean elite = rnd.nextDouble() < 0.25;
            java.util.List<String> pool = elite && !elitePool.isEmpty() ? elitePool : normalPool;
            if (pool == null || pool.isEmpty()) {
                pool = elitePool.isEmpty() ? normalPool : elitePool;
            }
            if (pool != null && !pool.isEmpty()) {
                node.put("stage", pool.get(rnd.nextInt(pool.size())));
            }
            node.put("type", elite ? 2 : 1);
        } else {
            String[] pool = {"INCIDENT", "INCIDENT", "REST", "WISH", "SACRIFICE", "BATTLE_SHOP"};
            String name = pool[rnd.nextInt(pool.length)];
            int bit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, name);
            node.put("type", bit == 0 ? 32 : bit);
            node.remove("stage");
        }
        node.put("visibility", 0);   // 揭示后完全可见（官服 200/202/301 都是 vis=0）
    }

    /** 官服标记「已经过」的字段：map.zones.nodes[id].fts = 完成时间戳（抓包 041_05/041_13 实证） */
    /** 实验开关：false = 不写 fts（用于定位「节点消失」的元凶） */
    private static final boolean WRITE_FTS = false;
    /** 实验开关：false = 不写 realContent */
    private static final boolean WRITE_REALCONTENT = true;

    private static void markNodeFinished(JSONObject cur) {
        if (!WRITE_FTS) {
            // 保底：完成的事件格恢复成「未定型」外观 —— 客户端按 type=256 + visibility=1 画「未知的诡秘」，
            // 节点永远可见；是否已消费由服务端 player.cleared 记着。
            JSONObject pl = cur.getJSONObject("player");
            JSONObject ps = pl.getJSONObject("cursor").getJSONObject("position");
            JSONObject nd = nodeAtPos(cur, ps);
            if (nd != null) {
                int t = nd.getIntValue("type");
                if (t != 1 && t != 2 && t != 4 && t != 268435456 && t != 8388608 && t != 16777216) {
                    nd.put("type", 256);
                    nd.put("visibility", 1);
                    nd.remove("fts");
                    nd.remove("realContent");
                }
            }
            return;
        }
        JSONObject player = cur.getJSONObject("player");
        JSONObject pos = player.getJSONObject("cursor").getJSONObject("position");
        JSONObject node = nodeAtPos(cur, pos);
        if (node != null && node.getLongValue("fts") == 0) {
            node.put("fts", ArknightsApplication.getTimestamp());
            ArknightsApplication.LOGGER.info("[rlv2] 节点 {} 完成，写入 fts", node.get("index"));
        }
    }

    /** 把当前格的 gridZone.state 置 2（官服：退出选项时） */
    private static void markCurrentState2(JSONObject cur) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject cursor = player.getJSONObject("cursor");
        String zid = String.valueOf(cursor.getIntValue("zone"));
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones == null ? null : zones.getJSONObject(zid);
        if (zone == null) {
            return;
        }
        String nid = cursor.getJSONObject("position").getIntValue("x") * 100
                + cursor.getJSONObject("position").getIntValue("y") + "";
        JSONObject gz = cur.getJSONObject("module");
        JSONObject gzs = gz == null ? null : gz.getJSONObject("gridZone");
        JSONObject gzZones = gzs == null ? null : gzs.getJSONObject("zones");
        JSONObject gzZone = gzZones == null ? null : gzZones.getJSONObject(zone.getString("id"));
        JSONObject gzNode = gzZone == null ? null : gzZone.getJSONObject("nodes");
        JSONObject target = gzNode == null ? null : gzNode.getJSONObject(nid);
        if (target != null) {
            target.put("state", 2);
        }
        JSONObject mapNode = zone.getJSONObject("nodes").getJSONObject(nid);
        if (mapNode != null) {
            mapNode.put("gzDone", 1);
        }
    }

    /** 节点是否已经完成（有 fts） */
    private static boolean nodeFinished(JSONObject node) {
        return node != null && node.getLongValue("fts") > 0;
    }

    /** 已触发过的节点（防重复触发）：官服靠节点上的 fts 判定 */
    /**
     * 节点去重：记录**在本局 cur 里**（`_cleared`，不下发），因此天然按局隔离，
     * 不会像之前写在 player 上那样跨局残留。
     */
    private static final boolean DEDUPE_ENABLED = true;

    private static boolean markClearedOnce(JSONObject cur, JSONObject pos) {
        if (!DEDUPE_ENABLED) {
            return false;
        }
        JSONArray cleared = cur.getJSONArray("_cleared");
        if (cleared == null) {
            cleared = new JSONArray();
            cur.put("_cleared", cleared);
        }
        String key = pos.getIntValue("x") * 100 + pos.getIntValue("y") + "";
        for (int i = 0; i < cleared.size(); i++) {
            if (key.equals(cleared.getString(i))) {
                return true;
            }
        }
        cleared.add(key);
        return false;
    }



    private static void pushNodePending(JSONObject cur) {
        String theme = cur.getJSONObject("game").getString("theme");
        // 点进来的那一格如果是「未定型占位(256)」，此刻才定型（只改这一格）
        resolveClickedPlaceholder(cur, theme);
        JSONObject player = cur.getJSONObject("player");
        JSONObject pos = player.getJSONObject("cursor").getJSONObject("position");
        int type = cursorNodeType(cur);
        String typeName = nodeTypeNameAt(cur, pos);
        boolean empty = type == 268435456;
        // 先清空残留 pending：否则上一节点的 SCENE/BATTLE 会一直挂着，看起来像「节点不触发」或「触发错节点」
        player.put("pending", new JSONArray());

        // 古堡馈赠（rogue_1 的 TREASURE 位）：直接给一件收藏品
        int treasureBit = com.hypergryph.arknights.core.function.RogueMapGen.typeBit(theme, "TREASURE");
        if (!empty && treasureBit != 0 && type == treasureBit) {
            String relic = randomRelic(theme, cur);
            if (relic != null) {
                addRelic(cur, relic, 1);
            }
            player.put("state", "WAIT_MOVE");
            return;
        }

        // 商店类：SHOP / BATTLE_SHOP / SCRAP_SHOP / EMPLOY
        if ("SHOP".equals(typeName) || "BATTLE_SHOP".equals(typeName)
                || "SCRAP_SHOP".equals(typeName) || "EMPLOY".equals(typeName)) {
            player.put("pending", buildShopPending(cur));
            player.put("state", "PENDING");
            return;
        }

        // 战斗类 / 空洞 / 层终点门（8388608）：不在这里起内容，客户端会调 moveAndBattleStart 或直接继续前进
        if ("BATTLE_NORMAL".equals(typeName) || "BATTLE_ELITE".equals(typeName)
                || "BATTLE_BOSS".equals(typeName) || "BATTLE_SAVAGE".equals(typeName)
                || "BATTLE".equals(typeName)) {
            JSONObject bn = nodeAtPos(cur, pos);
            ArknightsApplication.LOGGER.info("[rlv2] 落在作战节点 type={} stage={} visibility={} —— 等客户端调 moveAndBattleStart",
                    bn == null ? -1 : bn.getIntValue("type"),
                    bn == null ? null : bn.getString("stage"), bn == null ? -1 : bn.getIntValue("visibility"));
        }
        if (empty || "BATTLE_NORMAL".equals(typeName) || "BATTLE_ELITE".equals(typeName)
                || "BATTLE_BOSS".equals(typeName) || "BATTLE_SAVAGE".equals(typeName)
                || "BATTLE".equals(typeName)) {
            player.put("state", "WAIT_MOVE");
            return;
        }

        // 未知位兜底：带 stage 的当作战（客户端点它会走 moveAndBattleStart），没 stage 的当事件
        if (typeName == null) {
            JSONObject curNode = nodeAtPos(cur, pos);
            String st = curNode == null ? null : curNode.getString("stage");
            if (st != null && !st.isEmpty()) {
                player.put("state", "WAIT_MOVE");
                return;
            }
            JSONArray fallback = buildScenePending(cur);
            if (fallback != null) {
                player.put("pending", fallback);
                player.put("state", "PENDING");
            } else {
                player.put("state", "WAIT_MOVE");
            }
            return;
        }
        // 险路尽头：官服就开 scene_ro<主题>_final1_enter（抓包 035 实证），
        // 里面选 *_final*_数字 才进下一层（抓包 006_06）
        // ——不要再当空节点跳过，否则「点了不传送」
        // 其余按事件处理（INCIDENT / REST / WISH / SACRIFICE / EXPEDITION / PORTAL /
        // EVACUATE / LIGHT / DOOR …）
        if (markClearedOnce(cur, pos)) {
            ArknightsApplication.LOGGER.info("[rlv2] 节点 {} 已在 cleared 里 → 不再触发", pos);
            player.put("state", "WAIT_MOVE");   // 已经触发过，不重复开事件
            return;
        }
        ArknightsApplication.LOGGER.info("[rlv2] 触发判定：typeName={} cleared未命中，准备建场景", typeName);
        // 兜底：确保事件格带 realContent.scene，否则 buildScenePending 可能返回 null（点了没反应）
        {
            JSONObject nd = nodeAtPos(cur, pos);
            if (nd != null && nd.getJSONObject("realContent") == null) {
                String sc = sceneForTypeName(theme, typeName, String.valueOf(nd.get("index")));
                if (sc != null) {
                    JSONObject rc = new JSONObject(true);
                    rc.put("scene", sc);
                    nd.put("realContent", rc);
                }
            }
        }
        JSONArray scene = buildScenePending(cur);
        if (scene == null) {
            // 兜底：直接用节点 realContent 里的 sceneId 构造 SCENE 待办
            JSONObject nd2 = nodeAtPos(cur, pos);
            JSONObject rc2 = nd2 == null ? null : nd2.getJSONObject("realContent");
            String sid = rc2 == null ? null : rc2.getString("scene");
            if (sid == null) {
                sid = sceneForTypeName(theme, typeName, String.valueOf(nd2 == null ? "0" : nd2.get("index")));
            }
            if (sid != null) {
                scene = buildSceneById(cur, sid);
            }
        }
        if (scene != null) {
            player.put("pending", scene);
            player.put("state", "PENDING");
            ArknightsApplication.LOGGER.info("[rlv2] 已推 SCENE 待办，数量={}", scene.size());
        } else {
            ArknightsApplication.LOGGER.info("[rlv2] 场景构建失败 → WAIT_MOVE（typeName={}）", typeName);
            player.put("state", "WAIT_MOVE");
        }
    }

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
        // PRTS：沙盘β「大概率在第Ⅰ层到第Ⅲ层的诡意行商中以 1 源石锭的价格出售」
        // （rogue_6 专用；结局2 维度重构 的关键道具）
        if ("rogue_6".equals(theme) && !goods.isEmpty() && RANDOM.nextInt(100) < 60) {
            int slot = RANDOM.nextInt(goods.size());
            JSONObject g2 = goods.getJSONObject(slot);
            g2.put("itemId", "rogue_6_relic_final_2");
            g2.put("count", 1);
            g2.put("priceId", goldId);
            g2.put("priceCount", 1);
            g2.put("origCost", 1);
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

    /** 结局/终局/开局/复活 类场景：只能由结局流程打开，普通节点不能抽到 */
    private static boolean isEndingScene(String id) {
        if (id == null) {
            return true;
        }
        if (id.contains("startbuff") || id.contains("_revive")) {
            return true;
        }
        return java.util.regex.Pattern.compile(".*_end\\d*.*").matcher(id).matches();
    }

    /**
     * 战斗节点必须有 stage：官方模板里有些战斗格 stage 是空的（未揭示），
     * 直接进战斗会拿到 stageId=null → 客户端进不去（表现为「点了没反应」）。
     * 这里按层从官方关卡池里确定性补一个（同节点每次一样）。
     */
    private static void ensureBattleStages(JSONObject cur, String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject stages = details == null ? null : details.getJSONObject("stages");
        JSONObject zones = cur.getJSONObject("map") == null ? null
                : cur.getJSONObject("map").getJSONObject("zones");
        if (stages == null || zones == null) {
            return;
        }
        String prefix = themePrefix(theme); // ro6
        int level = currentLevel(cur);
        String normalPrefix = prefix + "_n_" + level + "_";
        String elitePrefix = prefix + "_e_" + level + "_";
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> elitePool = new java.util.ArrayList<>();
        for (String id : stages.keySet()) {
            if (id.startsWith(normalPrefix)) {
                normalPool.add(id);
            } else if (id.startsWith(elitePrefix)) {
                elitePool.add(id);
            }
        }
        if (normalPool.isEmpty() && elitePool.isEmpty()) {
            return;
        }
        java.util.Collections.sort(normalPool);
        java.util.Collections.sort(elitePool);
        for (String zk : zones.keySet()) {
            JSONObject nodes = zones.getJSONObject(zk).getJSONObject("nodes");
            if (nodes == null) {
                continue;
            }
            for (String nk : nodes.keySet()) {
                JSONObject n = nodes.getJSONObject(nk);
                if (n == null) {
                    continue;
                }
                String st = n.getString("stage");
                if (st != null && !st.isEmpty()) {
                    continue;
                }
                int type = n.getIntValue("type");
                boolean elite = type == 2;
                // 只给**已定型**的战斗格补 stage；256 是未定型占位，由 resolveFrontierNodes 决定是否变战斗
                boolean battle = type == 1 || type == 2 || type == 4;
                if (!battle) {
                    continue;
                }
                java.util.List<String> pool = elite && !elitePool.isEmpty() ? elitePool : normalPool;
                if (pool.isEmpty()) {
                    pool = elitePool;
                }
                int h = Math.abs(nk.hashCode());
                n.put("stage", pool.get(h % pool.size()));
            }
        }
    }

    /** 主题对应的关卡前缀（ro6 / ro5 …） */
    private static String themePrefix(String theme) {
        if (theme == null || theme.length() < 6) {
            return "ro1";
        }
        return "ro" + theme.substring(theme.length() - 1);
    }

    /** 节点在当前层里的 index 键（用来做「同类型不同节点 → 不同事件」的确定性选择） */
    private static String cursorNodeIndex(JSONObject cur) {
        JSONObject player = cur.getJSONObject("player");
        JSONObject pos = player.getJSONObject("cursor").getJSONObject("position");
        JSONObject zones = cur.getJSONObject("map").getJSONObject("zones");
        JSONObject zone = zones.getJSONObject(String.valueOf(player.getJSONObject("cursor").getIntValue("zone")));
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null || pos == null) {
            return "0";
        }
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject p = n.getJSONObject("pos");
            if (p != null && p.getIntValue("x") == pos.getIntValue("x")
                    && p.getIntValue("y") == pos.getIntValue("y")) {
                return k;
            }
        }
        return "0";
    }

    /**
     * 给该层每个节点补 `realContent`（官服就是这样：节点的具体内容在生成时定死，存在 map 节点上）。
     * 抓包实证（parsed_b_rlv2_19_1104/005_00）：
     *   type=32(INCIDENT) 节点 302 → realContent={"scene":"scene_ro6_bomb1_enter"}
     * 也有完整形状 {"battle":[],"scene":null,"battleShop":null,"wish":null}。
     * 之前服务器每次随机抽场景 → 同一层所有「不期而遇」都变成同一个事件，换层/回看还会变。
     */
    private static void ensureZoneRealContent(JSONObject cur, String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        JSONObject zones = cur.getJSONObject("map") == null ? null
                : cur.getJSONObject("map").getJSONObject("zones");
        if (zones == null || scenes == null) {
            return;
        }
        for (String zk : zones.keySet()) {
            JSONObject nodes = zones.getJSONObject(zk).getJSONObject("nodes");
            if (nodes == null) {
                continue;
            }
            for (String nk : nodes.keySet()) {
                JSONObject n = nodes.getJSONObject(nk);
                if (n == null || (n.getJSONObject("realContent") != null
                        && !n.getJSONObject("realContent").isEmpty())) {
                    continue;
                }
                int type = n.getIntValue("type");
                if (type == 268435456) {
                    continue;
                }
                String typeName = typeNameOf(theme, type);
                // 官服里只有「事件节点」带 realContent={"scene":...}；
                // 战斗/商店节点没有 realContent（我们之前给它们写 {"battle":[...]} / {"battleShop":{}}
                // → 客户端拿它当内容去画图标，画不出来就是白色空白方块）
                boolean battleKind = "BATTLE_NORMAL".equals(typeName) || "BATTLE_ELITE".equals(typeName)
                        || "BATTLE_BOSS".equals(typeName) || "BATTLE_SAVAGE".equals(typeName)
                        || "BATTLE".equals(typeName) || "TREASURE".equals(typeName);
                boolean shopKind = "SHOP".equals(typeName) || "BATTLE_SHOP".equals(typeName)
                        || "SCRAP_SHOP".equals(typeName) || "EMPLOY".equals(typeName);
                if (battleKind || shopKind || "FINAL".equals(typeName) || "EVACUATE".equals(typeName)) {
                    n.remove("realContent");
                } else {
                    String scene = pickSceneForNode(cur, theme, typeName, nk, scenes);
                    if (scene != null) {
                        JSONObject rc = new JSONObject(true);
                        rc.put("scene", scene);
                        if (WRITE_REALCONTENT) {
                        n.put("realContent", rc);
                        }
                    }
                }
            }
        }
    }

    /** 按节点类型挑一个场景；用节点 index 做确定性偏移，保证同一节点每次都是同一个事件 */
    private static String pickSceneForNode(JSONObject cur, String theme, String typeName,
            String nodeIndex, JSONObject scenes) {
        String themeNum = theme.substring(theme.length() - 1);
        String[] groups;
        if ("MISSION".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_task"};
        } else if ("WISH".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_wish"};
        } else if ("REST".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_res", "_rest"};
        } else if ("SACRIFICE".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_sacrifice"};
        } else if ("EXPEDITION".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_scout"};
        } else if ("PORTAL".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_portal"};
        } else if ("EVACUATE".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_evacuate"};
        } else if ("DUEL".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_bat"};
        } else if ("FINAL".equalsIgnoreCase(typeName)) {
            groups = new String[]{"_final"};
        } else {
            groups = new String[]{"_normal", "_surprise", "_relic", "_sala", "_chimera", "_bomb", "_hire"};
        }
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String id : scenes.keySet()) {
            if (isEndingScene(id) || !id.endsWith("_enter")) {
                continue;
            }
            if (id.startsWith("scene_ro" + themeNum + "_")) {
                for (String g : groups) {
                    if (id.contains(g)) {
                        pool.add(id);
                        break;
                    }
                }
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int h = nodeIndex == null ? 0 : Math.abs(nodeIndex.hashCode());
        return pool.get(h % pool.size());
    }

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
            if (isEndingScene(id)) {
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
                if (isEndingScene(id)) {
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
        // 节点类型 → 场景组（按 rogue_6 的 62 个 *_enter 场景名归纳）：
        //   WISH→wish  REST→res/rest  SACRIFICE→sacrifice  EXPEDITION→scout  PORTAL→portal
        //   EVACUATE→evacuate  DUEL→bat  MISSION→task  其余事件→normal/surprise/relic/sala/chimera/bomb/hire
        // 之前只有 MISSION 特判、其它随机 → 普通事件可能抽到 scene_ro6_end3_enter 这类**结局**场景，
        // 客户端流程对不上，表现为「点了节点没反应」
        // 官服语义：场景在生成时就定死并存在节点 realContent 里，直接用
        JSONObject rcNode = nodeAtPos(cur,
                cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position"));
        String realScene = rcNode == null || rcNode.getJSONObject("realContent") == null ? null
                : rcNode.getJSONObject("realContent").getString("scene");
        if (realScene != null && scenes.containsKey(realScene)) {
            return buildSceneById(cur, realScene);
        }
        String nodeType = nodeTypeNameAt(cur,
                cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position"));
        String[] groups;
        if ("MISSION".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_task"};
        } else if ("WISH".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_wish"};
        } else if ("REST".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_res", "_rest"};
        } else if ("SACRIFICE".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_sacrifice"};
        } else if ("EXPEDITION".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_scout"};
        } else if ("PORTAL".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_portal"};
        } else if ("EVACUATE".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_evacuate"};
        } else if ("DUEL".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_bat"};
        } else if ("FINAL".equalsIgnoreCase(nodeType)) {
            groups = new String[]{"_final"};
        } else {
            groups = new String[]{"_normal", "_surprise", "_relic", "_sala", "_chimera", "_bomb", "_hire"};
        }
        java.util.List<String> prefer = new java.util.ArrayList<>();
        for (String id : candidates) {
            for (String g : groups) {
                if (id.contains(g)) {
                    prefer.add(id);
                    break;
                }
            }
        }
        if (!prefer.isEmpty()) {
            candidates = prefer;
        } else {
            java.util.List<String> others = new java.util.ArrayList<>();
            for (String id : candidates) {
                if (!id.contains("_task")) {
                    others.add(id);
                }
            }
            if (!others.isEmpty()) {
                candidates = others;
            }
        }
        String sceneId = candidates.get(RANDOM.nextInt(candidates.size()));
        return buildSceneById(cur, sceneId);
    }

    /** 用指定 sceneId 构造 SCENE pending（选项按 choice_<prefix> 过滤） */
    private static JSONArray buildSceneById(JSONObject cur, String sceneId) {
        String theme = cur.getJSONObject("game").getString("theme");
        JSONObject details = themeDetails(theme);
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (choices == null || sceneId == null) {
            return null;
        }
        String prefix = sceneId.startsWith("scene_") ? sceneId.substring(6) : sceneId;
        if (prefix.endsWith("_enter")) {
            prefix = prefix.substring(0, prefix.length() - 6);
        }
        String choicePrefix = "choice_" + prefix;

        JSONObject choiceMap = new JSONObject(true);
        JSONObject additional = new JSONObject(true);
        boolean missionScene = sceneId.contains("_task_enter");
        for (String cid : choices.keySet()) {
            if (!cid.startsWith(choicePrefix)) {
                continue;
            }
            JSONObject cdef = choices.getJSONObject(cid);
            String ctype = cdef.getString("type");
            if ("HIDDEN".equals(ctype)) {
                continue;
            }
            // 委托选择页只要 4 个 leftDecoType=TASK 的选项；
            // taskreward* 是「完成委托后的奖励」，不能出现在这里（官服只给 4 个）
            if (missionScene) {
                // 委托页 = 3 个 TASK 难度选项 + 1 个 NEXT（关闭终端）
                // 官服抓包 012_12：choice_ro2_task_1..4，其中 task_4 是 type=NEXT / leftDecoType=NONE
                boolean isTask = "TASK".equalsIgnoreCase(cdef.getString("leftDecoType"));
                boolean isNext = "NEXT".equalsIgnoreCase(ctype) && !cid.contains("taskreward");
                if (!isTask && !isNext) {
                    continue;
                }
            } else if (cid.contains("taskreward")) {
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

    // 按节点类型算经验（普通 12 / 紧急 24 / 首领 36）
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
        int sanityAfter = decreaseSanity(cur, 5);
        // 萨米：生效中的灵感（临时战斗增益）只能通过战斗被消耗 → 结算时清空
        JSONObject fragMod = cur.getJSONObject("module") == null ? null
                : cur.getJSONObject("module").getJSONObject("fragment");
        if (fragMod != null && fragMod.getJSONObject("currInspiration") != null) {
            ArknightsApplication.LOGGER.info("[rlv2] 灵感已被战斗消耗: {}",
                    fragMod.getJSONObject("currInspiration").getString("id"));
            fragMod.put("currInspiration", null);
        }

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
        // 商店开战后的处理（官服 012_00 实测）：BATTLE_SHOP 的 canBattle/hasBoss 一律置 false；
        // 商品价格**只有击败老板才清零**（老板跑了就维持原价）。
        // 老板是否被击败只能从 data（AES 加密的战斗统计）里读，靠 enemyStats 的 HP_ZERO 判断。
        if (oldPendingHasType(player, "BATTLE_SHOP")) {
            markShopRobbed(player);
            if (ignoreShopBossKill()) {
                // config.rogue.ignoreShopBossKill = true → 不看老板，直接免费
                ArknightsApplication.LOGGER.info("[rlv2] ignoreShopBossKill=true → 商店直接全免费");
                makeShopFree(player);
            } else {
                String rawData = body == null ? null : body.getString("data");
                if (rawData != null && !rawData.isEmpty()) {
                    long loginTime = userData.getJSONObject("status") == null ? 0
                            : userData.getJSONObject("status").getLongValue("lastOnlineTs");
                    JSONObject decrypted = com.hypergryph.arknights.core.decrypt.Utils
                            .BattleData_decrypt(rawData, String.valueOf(loginTime));
                    if (decrypted != null && shopBossDefeated(decrypted, theme)) {
                        makeShopFree(player);
                    }
                }
            }
        }
        JSONArray pendingArr = new JSONArray();
        pendingArr.add(rewardPending);
        JSONArray oldPending = player.getJSONArray("pending");
        if (oldPending != null) {
            for (int i = 0; i < oldPending.size(); i++) {
                JSONObject one = oldPending.getJSONObject(i);
                if ("BATTLE".equals(one.getString("type"))) {
                    continue;
                }
                pendingArr.add(one);
            }
        }

        player.put("state", "PENDING");
        player.put("pending", pendingArr);
        userDao.setUserData(account.getUid(), userData);
        return delta(cur);
    }

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
        // 官服：brief.band = 开局变体收藏品（createGame/chooseInitialRelic 时记录在 game.outer.band）
        JSONObject briefGameOuter = game.getJSONObject("outer");
        brief.put("band", briefGameOuter == null ? ""
                : (briefGameOuter.getString("band") == null ? "" : briefGameOuter.getString("band")));
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
        // 官服实证：fts 不是结算时写的，而是「离开该格」时写的（见 moveTo）

        // 险路恶敌（第3/5层出口）：战斗结算后进下一层
        {
            JSONObject pos0 = cur.getJSONObject("player").getJSONObject("cursor").getJSONObject("position");
            JSONObject nd0 = nodeAtPos(cur, pos0);
            // 恶敌出口：第3层官服实证是 BATTLE_BOSS(4)，第5/6层是 BATTLE_SAVAGE(134217728)
            int nd0Type = nd0 == null ? 0 : nd0.getIntValue("type");
            if (nd0 != null && (nd0Type == 134217728 || nd0Type == 4) && nd0.getBooleanValue("zone_end")) {
                int lv = currentLevel(cur);
                String th0 = cur.getJSONObject("game").getString("theme");
                if ("rogue_6".equals(th0) && lv >= 5) {
                    java.util.Set<String> finals0 = rogue6FinalRelics(cur);
                    boolean beacon0 = finals0.contains("rogue_6_relic_final_3");
                    JSONObject eflags0 = endingFlags(cur);
                    boolean prayer0 = eflags0.getBooleanValue("prayer");
                    if (lv < 6 && (beacon0 || prayer0)) {
                        // PRTS：持有【怦然信标】通过第Ⅴ层战斗后 → 进入第Ⅵ层「源流交汇处」
                        ArknightsApplication.LOGGER.info("[rlv2] 第5层BOSS通关且持有信标 → 进入第Ⅵ层（源流交汇处）");
                        enterZone(null, cur, lv + 1);
                        userDao.setUserData(account.getUid(), userData);
                        return delta(cur);
                    }
                    // 第Ⅴ层（无信标）或第Ⅵ层 BOSS 打完 = 本局结束 → 推 GAME_SETTLE
                    String planned;
                    if (beacon0) {
                        planned = eflags0.getBooleanValue("prayer") ? "ro6_ending_4" : "ro6_ending_3";
                    } else if (finals0.contains("rogue_6_relic_final_7") || finals0.contains("rogue_6_relic_final_8")) {
                        planned = "ro6_ending_4";
                    } else if (finals0.contains("rogue_6_relic_final_1") && finals0.contains("rogue_6_relic_final_2")) {
                        planned = "ro6_ending_2";
                    } else {
                        planned = "ro6_ending_1";
                    }
                    cur.getJSONObject("player").put("toEnding", planned);
                    JSONObject content0 = buildGiveUpSettleContent(cur, userData);
                    content0.put("success", 1);
                    JSONObject rr0 = content0.getJSONObject("result");
                    if (rr0 != null) {
                        JSONObject bb0 = rr0.getJSONObject("brief");
                        if (bb0 != null) {
                            bb0.put("success", 1);
                            bb0.put("over", true);
                            bb0.put("ending", planned);
                            bb0.put("level", lv);
                            bb0.put("endZoneId", "zone_" + lv);
                        }
                    }
                    JSONObject sp0 = new JSONObject(true);
                    sp0.put("index", "e_88");
                    sp0.put("type", "GAME_SETTLE");
                    sp0.put("content", content0);
                    JSONArray pa0 = new JSONArray();
                    pa0.add(sp0);
                    cur.getJSONObject("player").put("pending", pa0);
                    cur.getJSONObject("player").put("state", "PENDING");
                    ArknightsApplication.LOGGER.info("[rlv2] 第5层险路恶敌通关 → 结局 {}", planned);
                } else {
                    ArknightsApplication.LOGGER.info("[rlv2] 险路恶敌战斗结束：第 {} 层 → 第 {} 层", lv, lv + 1);
                    enterZone(null, cur, lv + 1);
                }
                userDao.setUserData(account.getUid(), userData);
                return delta(cur);
            }
        }
        revealAroundCursor(cur, cur.getJSONObject("game").getString("theme"), 2);
        seedGridZoneModule(cur, cur.getJSONObject("game").getString("theme"));
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
        noteEndingChoice(cur, choice);
        // ── 险路尽头：在 applyChoiceEffect 之前拦截，任何非 leave 选项都推进/结算 ──
        // （applyChoiceEffect 会先把选项当普通场景选项消费掉 → 之前「选了却不进下一层」）
        {
            JSONObject playerE = cur.getJSONObject("player");
            String thE = cur.getJSONObject("game").getString("theme");
            JSONObject hereE = nodeAtPos(cur, playerE.getJSONObject("cursor").getJSONObject("position"));
            boolean atExitE = hereE != null && hereE.getIntValue("type") == exitBitOf(thE);
            if (atExitE && choice != null && !isLeaveChoice(cur, choice)) {
                int levelE = currentLevel(cur);
                int maxFloorE = com.hypergryph.arknights.core.function.RogueMapGen.officialZoneCount(thE);
                JSONObject eflagsE = endingFlags(cur);
                boolean prayerE = eflagsE.getBooleanValue("prayer");
                java.util.Set<String> finalsE = rogue6FinalRelics(cur);
                boolean beaconE = finalsE.contains("rogue_6_relic_final_3");
                boolean sandboxE = finalsE.contains("rogue_6_relic_final_1")
                        && finalsE.contains("rogue_6_relic_final_2");
                String planned = null;
                if ("rogue_6".equals(thE)) {
                    if (levelE >= 6 && beaconE) {
                        planned = prayerE ? "ro6_ending_4" : "ro6_ending_3";
                    } else if (levelE >= 5) {
                        if (beaconE) {
                            planned = null;   // 持信标：第Ⅴ层通关 → 进第Ⅵ层源流交汇处
                        } else if (finalsE.contains("rogue_6_relic_final_7")
                                || finalsE.contains("rogue_6_relic_final_8")) {
                            planned = "ro6_ending_4";
                        } else if (sandboxE) {
                            planned = "ro6_ending_2";
                        } else {
                            planned = "ro6_ending_1";
                        }
                    }
                } else if (maxFloorE > 0 && levelE >= maxFloorE) {
                    planned = defaultEnding(thE);
                }
                playerE.put("pending", new JSONArray());
                playerE.put("state", "WAIT_MOVE");
                if (planned != null) {
                    playerE.put("toEnding", planned);
                    JSONObject content = buildGiveUpSettleContent(cur, userData);
                    content.put("success", 1);
                    JSONObject rr = content.getJSONObject("result");
                    if (rr != null) {
                        JSONObject bb = rr.getJSONObject("brief");
                        if (bb != null) {
                            bb.put("success", 1);
                            bb.put("over", true);
                            bb.put("ending", planned);
                            bb.put("level", levelE);
                            bb.put("endZoneId", "zone_" + levelE);
                        }
                    }
                    JSONObject sp = new JSONObject(true);
                    sp.put("index", "e_88");
                    sp.put("type", "GAME_SETTLE");
                    sp.put("content", content);
                    JSONArray pa = new JSONArray();
                    pa.add(sp);
                    playerE.put("pending", pa);
                    playerE.put("state", "PENDING");
                    ArknightsApplication.LOGGER.info("[rlv2] 出口推进 → 结局 {}（第 {} 层）", planned, levelE);
                } else {
                    enterZone(userData, cur, levelE + 1);
                    ArknightsApplication.LOGGER.info("[rlv2] 出口推进 → 第 {} 层", levelE + 1);
                }
                userDao.setUserData(account.getUid(), userData);
                com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
                return delta(cur);
            }
        }
        JSONArray items = applyChoiceEffect(userData, cur, choice);
        JSONObject player = cur.getJSONObject("player");
        // 开局起始增益（GAME_INIT_SUPPORT）：官服实测 selectChoice {"choice":"choice_ro4_startbuff_7"}
        // 即消费该待办（不带 sceneId），否则客户端会一直卡在这个初始化步骤上
        if (choice != null && choice.contains("_startbuff_")) {
            removePending(cur, "GAME_INIT_SUPPORT");
            ArknightsApplication.LOGGER.info("[rlv2] 开局起始增益已选（{}）→ 消费 GAME_INIT_SUPPORT", choice);
            // 官服实测：初始化阶段（state=INIT）的每一步都不改 state，
            // 这里必须直接返回；否则会掉进下面的通用分支把 state 改成 PENDING，
            // 客户端就认为初始化结束 → 招募套餐/招募/进图全都不再出现
            userDao.setUserData(account.getUid(), userData);
            com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
            JSONObject initResult = delta(cur);
            initResult.put("items", items);
            return initResult;
        }

        String theme = cur.getJSONObject("game").getString("theme");

        // 官服实测（024_26 rogue_3）：选项本身是「进入战斗」→ 推 BATTLE 待办（state:0）
        // 水月（rogue_2）：选项 displayData.itemId 以 _dice 结尾 → 「获得N掷骰次数」（此处只加次数，
        // 掷骰待办在 pending 落定后再推，否则会被场景分支覆盖）
        grantDiceCount(cur, theme, choice);

        // 灯火（san）：选项描述里带「获得N灯火 / 消耗N灯火」→ 直接增减（上限 100）
        // 官服表里 16 个选项带这类文案，例如 choice_ro2_rest_6「获得15灯火」、
        // choice_ro2_bossb2_2「消耗20灯火，获得一件随机收藏品」
        JSONObject choiceDef = choiceDef(theme, choice);
        if (choiceDef != null) {
            int lampDelta = parseLampDelta(choiceDef.getString("description"));
            if (lampDelta != 0) {
                JSONObject module = cur.getJSONObject("module");
                JSONObject san = module == null ? null : module.getJSONObject("san");
                if (san != null) {
                    int now = Math.max(0, Math.min(100, san.getIntValue("sanity") + lampDelta));
                    san.put("sanity", now);
                    ArknightsApplication.LOGGER.info("[rlv2] 灯火 {}（{}）→ {}", lampDelta > 0 ? "+" : "", lampDelta, now);
                }
            }
            // 获得启示：官服文案「获得启示」→ 授予一个 squadBuff（灯火明亮给强化版）
            if (grantsInspiration(choiceDef.getString("title"), choiceDef.getString("description"))) {
                grantInspiration(cur, theme);
            }
        }

        // 地区委托：选中 choice_ro2_task_N → 记下难度档与委托 id（官服 record.nodeMission={"rogue_2_task_016":2}）
        if (choice != null && choice.matches("choice_ro\\d+_task_\\d+")) {
            JSONObject rec = cur.getJSONObject("record");
            if (rec == null) {
                rec = new JSONObject(true);
                cur.put("record", rec);
            }
            String tierStr = choice.substring(choice.lastIndexOf('_') + 1);
            try {
                rec.put("pendingTaskTier", Integer.parseInt(tierStr));
            } catch (Exception ignored) {
            }
            rec.put("pendingTaskId", theme + "_task_" + String.format("%03d", 1 + RANDOM.nextInt(99)));
        }

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
                // 官服实测（009_10 rogue_2）：许愿类选项（leftDecoType=DICE）→ pending 变成 ["DICE","SCENE"]
                if (choiceTriggersDice(theme, choice)) {
                    pushDiceIfNeeded(cur, theme, player);
                }
                userDao.setUserData(account.getUid(), userData);
                com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
                JSONObject result = delta(cur);
                result.put("items", items);
                return result;
            }
        }

        // 官服实测（006_06 rogue_6）：/rlv2/selectChoice {"choice":"choice_ro6_final1_3"}
        // 直接把 cursor.zone 1001 → 1002，即「在险路尽头场景里选推进」= 进下一层
        if (choice != null && !isLeaveChoice(cur, choice)) {
            JSONObject cursorPos = player.getJSONObject("cursor").getJSONObject("position");
            JSONObject here = nodeAtPos(cur, cursorPos);
            int hereType = here == null ? 0 : here.getIntValue("type");
            String th = cur.getJSONObject("game").getString("theme");
            boolean atExit = hereType == exitBitOf(th) || (choice.contains("_final") && !choice.contains("leave"));
            if (atExit) {
                int level = currentLevel(cur);
                int maxFloor = com.hypergryph.arknights.core.function.RogueMapGen.officialZoneCount(th);
                // 结局规则（维基 + B服 2026-11-05 14:00 结算包实证）：
                //   结局1 强制重启：无前置，打完第 5 层最终 BOSS 即达成
                //   结局2 维度重构：持沙盘α/β → 第5层「命运所指」选「修正结果」
                //   结局3 纠缠调和：持怦然信标 → 打完第5层 BOSS 继续到第6层 → 「险路恶敌」
                //   结局4 旧日重燃：持怦然信标 + 谒日之祈/祭日之契 → 第6层「险路恶敌」
                JSONObject eflags = endingFlags(cur);
                java.util.Set<String> finals = rogue6FinalRelics(cur);
                boolean hasBeacon = finals.contains("rogue_6_relic_final_3");
                boolean hasSandbox = finals.contains("rogue_6_relic_final_1")
                        && finals.contains("rogue_6_relic_final_2");
                boolean hasPrayer = eflags.getBooleanValue("prayer");
                String plannedEnding = null;
                if ("rogue_6".equals(th)) {
                    if (level >= 6 && hasBeacon) {
                        plannedEnding = hasPrayer ? "ro6_ending_4" : "ro6_ending_3";
                    } else if (level >= 5) {
                        if (hasBeacon) {
                            plannedEnding = null;   // 信标路线 → 第Ⅵ层
                        } else if (finals.contains("rogue_6_relic_final_7")
                                || finals.contains("rogue_6_relic_final_8")) {
                            plannedEnding = "ro6_ending_4";
                        } else if (hasSandbox) {
                            plannedEnding = "ro6_ending_2";
                        } else {
                            plannedEnding = "ro6_ending_1";
                        }
                    }
                } else if (maxFloor > 0 && level >= maxFloor) {
                    plannedEnding = defaultEnding(th);
                }
                if (plannedEnding != null) {
                    // 已在最后一层 → 不再生成新层，推 GAME_SETTLE（官服 2026-11-05 14:00 抓包实证）
                    // pending = {index:"e_88", type:"GAME_SETTLE", content:{success:1, result:{brief, record}}}
                    // 且 player.toEnding = "ro6_ending_N"
                    String ending = plannedEnding;
                    player.put("toEnding", ending);
                    ArknightsApplication.LOGGER.info("[rlv2] 结局判定：level={} 沙盘={} 信标={} 祈愿={} → {}",
                            level, hasSandbox, hasBeacon, hasPrayer, ending);
                    JSONObject content = buildGiveUpSettleContent(cur, userData);
                    content.put("success", 1);
                    JSONObject result = content.getJSONObject("result");
                    if (result != null) {
                        JSONObject b = result.getJSONObject("brief");
                        if (b != null) {
                            b.put("success", 1);
                            b.put("over", true);
                            b.put("ending", ending);
                            b.put("level", level);
                            b.put("endZoneId", "zone_" + level);
                        }
                    }
                    JSONObject settlePend = new JSONObject(true);
                    settlePend.put("index", "e_88");
                    settlePend.put("type", "GAME_SETTLE");
                    settlePend.put("content", content);
                    JSONArray pendArr = new JSONArray();
                    pendArr.add(settlePend);
                    player.put("pending", pendArr);
                    player.put("state", "PENDING");
                    ArknightsApplication.LOGGER.info("[rlv2] 第 {} 层（最后一层）通关 → 结局 {}，推 GAME_SETTLE", level, ending);
                    userDao.setUserData(account.getUid(), userData);
                    com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
                    JSONObject endResult = delta(cur);
                    endResult.put("items", items);
                    return endResult;
                }
                ArknightsApplication.LOGGER.info("[rlv2] 险路尽头选择 {}（第 {} 层）→ 进入第 {} 层", choice, level, level + 1);
                player.put("pending", new JSONArray());
                player.put("state", "WAIT_MOVE");
                enterZone(userData, cur, level + 1);
                userDao.setUserData(account.getUid(), userData);
                com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
                JSONObject advResult = delta(cur);
                advResult.put("items", items);
                return advResult;
            }
        }

        boolean leave = isLeaveChoice(cur, choice);
        if (leave) {
            player.put("pending", new JSONArray());
            player.put("state", "WAIT_MOVE");
            // 官服实证（041_12）：退出选项这一包，把当前格的 gridZone.state 置 2（= 已完成 ✓）
            markCurrentState2(cur);
            // 官服实证（041_09）：离开场景 → 揭示前方（fts 留到离开该格时再写）

            revealAroundCursor(cur, theme, 2);
            seedGridZoneModule(cur, theme);
            // 官服实测（009_12 rogue_2）：离开场景时若还有剩余掷骰次数，会再弹 DICE
            if (choiceTriggersDice(theme, choice)) {
                pushDiceIfNeeded(cur, theme, player);
            }
        } else {
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", items);
        return result;
    }

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
                // 价格直接取 goods.priceCount：打劫成功的商店里它已经被清零（见 makeShopFree）
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

    private static JSONObject player(JSONObject cur) {
        return cur.getJSONObject("player");
    }

    private static JSONObject themeModules(String theme) {
        if (ArknightsApplication.roguelikeTopicTable == null) {
            return null;
        }
        JSONObject modules = ArknightsApplication.roguelikeTopicTable.getJSONObject("modules");
        return modules == null ? null : modules.getJSONObject(theme);
    }

    private static void pushDiceIfNeeded(JSONObject cur, String theme, JSONObject player) {
        JSONObject module = cur.getJSONObject("module");
        JSONObject diceState = module == null ? null : module.getJSONObject("dice");
        if (diceState == null) {
            return;
        }
        int count = diceState.getIntValue("count");
        if (count <= 0) {
            return;
        }
        JSONObject mods = themeModules(theme);
        JSONObject diceData = mods == null ? null : mods.getJSONObject("dice");
        if (diceData == null) {
            return;
        }
        JSONObject diceDefs = diceData.getJSONObject("dice");
        JSONObject def = diceDefs == null ? null : diceDefs.getJSONObject(diceState.getString("id"));
        int faces = def == null ? 6 : def.getIntValue("diceFaceCount");
        if (faces <= 0) {
            faces = 6;
        }
        int roll = 1 + RANDOM.nextInt(faces);
        JSONObject events = diceData.getJSONObject("diceEvents");
        // 官方规则（6 次抓包验证）：
        //   ① 先定组（每次不同，按上下文/随机）
        //   ② 组内取 dicePointMax >= roll 且最小的那个
        //   例：roll=5 → group_1 命中 e2@5(GOOD)、group_13 命中 e28@5、group_16 命中 e34@5
        java.util.Map<String, java.util.List<JSONObject>> byGroup = new java.util.LinkedHashMap<>();
        if (events != null) {
            for (String key : events.keySet()) {
                JSONObject ev = events.getJSONObject(key);
                String g = ev.getString("diceGroupId");
                byGroup.computeIfAbsent(g, k -> new java.util.ArrayList<>()).add(ev);
            }
        }
        String eventId = null;
        String groupId = null;
        // 官方 sanRanges（rogue_2）：灯火值决定掷骰组
        //   100 → dicegroup_1<通明>；99 → dicegroup_2<摇曳>；49 → dicegroup_3<暗淡>；0 → dicegroup_4<寂灭>
        String sanGroup = diceGroupBySanity(cur, theme);
        if (sanGroup != null && byGroup.containsKey(sanGroup)) {
            int bestMax = Integer.MAX_VALUE;
            for (JSONObject ev : byGroup.get(sanGroup)) {
                int max = ev.getIntValue("dicePointMax");
                if (max >= roll && max < bestMax) {
                    bestMax = max;
                    eventId = ev.getString("diceEventId");
                }
            }
            if (eventId != null) {
                groupId = sanGroup;
            }
        }
        if (!byGroup.isEmpty() && eventId == null) {
            java.util.List<String> groups = new java.util.ArrayList<>(byGroup.keySet());
            // 优先选「本组能匹配到事件」的组，再从中随机
            java.util.List<String> usable = new java.util.ArrayList<>();
            for (String g : groups) {
                int best = Integer.MAX_VALUE;
                for (JSONObject ev : byGroup.get(g)) {
                    int max = ev.getIntValue("dicePointMax");
                    if (max >= roll && max < best) {
                        best = max;
                    }
                }
                if (best != Integer.MAX_VALUE) {
                    usable.add(g);
                }
            }
            java.util.List<String> pool = usable.isEmpty() ? groups : usable;
            groupId = pool.get(RANDOM.nextInt(pool.size()));
            int bestMax = Integer.MAX_VALUE;
            for (JSONObject ev : byGroup.get(groupId)) {
                int max = ev.getIntValue("dicePointMax");
                if (max >= roll && max < bestMax) {
                    bestMax = max;
                    eventId = ev.getString("diceEventId");
                }
            }
        }
        if (eventId == null) {
            return;
        }
        JSONObject result = new JSONObject(true);
        result.put("diceRoll", roll);
        result.put("diceEventId", eventId);
        result.put("diceRuleGroupId", groupId);
        // 官服抓包（diceEve_19 结果 GOOD）里 result 带 reward，表里 diceEvents 没有 reward 字段
        // → 按 diceResultClass 推断：GOOD/BEST 发一件收藏品
        String resultClass = null;
        if (events != null && eventId != null) {
            for (String key : events.keySet()) {
                JSONObject ev = events.getJSONObject(key);
                if (eventId.equals(ev.getString("diceEventId"))) {
                    resultClass = ev.getString("diceResultClass");
                    break;
                }
            }
        }
        if ("GOOD".equalsIgnoreCase(resultClass) || "BEST".equalsIgnoreCase(resultClass)) {
            String relicId = randomRelicId(theme);
            if (relicId != null) {
                JSONArray reward = new JSONArray();
                JSONObject one = new JSONObject(true);
                one.put("id", relicId);
                one.put("type", "ITEM");
                one.put("count", 1);
                reward.add(one);
                result.put("reward", reward);
                addRelic(cur, relicId, 1);
                ArknightsApplication.LOGGER.info("[rlv2] 掷骰 {} → 发收藏品 {}", resultClass, relicId);
            }
        }
        JSONObject dice = new JSONObject(true);
        dice.put("result", result);
        dice.put("rerollCount", 0);
        dice.put("_isSancheck", false);
        dice.put("_done", false);
        dice.put("_history", new JSONArray());
        JSONObject content = new JSONObject(true);
        content.put("dice", dice);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_18");
        pend.put("type", "DICE");
        pend.put("content", content);
        JSONArray pending = player.getJSONArray("pending");
        if (pending == null) {
            pending = new JSONArray();
            player.put("pending", pending);
        }
        pending.add(0, pend);
        diceState.put("count", count - 1);
        ArknightsApplication.LOGGER.info("[rlv2] 掷骰 {} 面 → 点数 {} 事件 {}", faces, roll, eventId);
    }

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

    /** 按坐标在当前区域里找节点 */
    private static JSONObject nodeAtPos(JSONObject cur, JSONObject pos) {
        if (pos == null) {
            return null;
        }
        JSONObject zones = cur.getJSONObject("map") == null ? null : cur.getJSONObject("map").getJSONObject("zones");
        if (zones == null) {
            return null;
        }
        JSONObject zone = zones.getJSONObject(String.valueOf(cur.getJSONObject("player").getJSONObject("cursor").getIntValue("zone")));
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null) {
            return null;
        }
        for (String key : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(key);
            JSONObject p = n == null ? null : n.getJSONObject("pos");
            if (p != null && p.getIntValue("x") == pos.getIntValue("x")
                    && p.getIntValue("y") == pos.getIntValue("y")) {
                return n;
            }
        }
        return null;
    }

    /**
     * rogue_6 真实节点位值（抓包实证，和 nodeTypeData 的键顺序**不一致**！）：
     *   16→REST(rest_enter×7)  32→INCIDENT(normal1/bomb1/res4/task2 …48 次)
     *   512→WISH(wish_enter×9) 1024→SACRIFICE(sacrifice1_enter×7) 2048→EXPEDITION(scout_enter×11)
     *   4096→BATTLE_SHOP(pending BATTLE_SHOP×34) 8192→PORTAL(portal3a_enter×7)
     *   2097152→SCRAP_SHOP(BATTLE_SHOP×13) 8388608→FINAL(final1_enter×28)
     *   16777216→EVACUATE(evacuate_enter) 268435456→EMPTY(无 pending×17)
     *   1/2/4 = 普通/紧急/首领作战（ro6_n_ / ro6_e_ / ro6_b_ 关卡）
     */
    private static String rogue6TypeName(int type) {
        // GA metadata Torappu.RoguelikeEventType 权威位值（2026-11-11 解码实证）
        switch (type) {
            case 1:
                return "BATTLE_NORMAL";
            case 2:
                return "BATTLE_ELITE";
            case 4:
                return "BATTLE_BOSS";
            case 8:
                return "SHOP";
            case 16:
                return "REST";
            case 32:
                return "INCIDENT";
            case 64:
                return "TREASURE";
            case 128:
                return "ENTERTAINMENT";
            case 256:
                // 未定型占位：具体类型由 stage 前缀/定型结果决定
                return "UNKNOWN";
            case 512:
                return "WISH";
            case 1024:
                return "SACRIFICE";
            case 2048:
                return "EXPEDITION";
            case 4096:
                return "BATTLE_SHOP";
            case 8192:
                return "PORTAL";
            case 16384:
                return "MISSION";
            case 32768:
                return "STORY";
            case 65536:
                return "STORY_HIDDEN";
            case 131072:
                return "ALCHEMY";
            case 262144:
                return "DUEL";
            case 524288:
                return "STASHED_RECRUIT";
            case 1048576:
                return "SPECIAL_ZONE";
            case 2097152:
                return "SCRAP_SHOP";
            case 4194304:
                return "DOOR";
            case 8388608:
                return "FINAL";
            case 16777216:
                return "EVACUATE";
            case 33554432:
                return "EMPLOY";
            case 67108864:
                return "LIGHT";
            case 134217728:
                return "BATTLE_SAVAGE";
            case 268435456:
                return "EMPTY";
            default:
                return null;
        }
    }

    /** 节点位值 → 类型名；rogue_6 用实证表，其它主题暂用 nodeTypeData 顺序 */
    private static String typeNameOf(String theme, int type) {
        if ("rogue_6".equals(theme)) {
            String n = rogue6TypeName(type);
            if (n != null) {
                return n;
            }
        }
        java.util.List<String> names =
                com.hypergryph.arknights.core.function.RogueMapGen.nodeTypeNames(theme);
        if (names != null) {
            for (int i = 0; i < names.size(); i++) {
                if ((1 << i) == type) {
                    return names.get(i);
                }
            }
        }
        return null;
    }

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
                return typeNameOf(theme, n.getIntValue("type"));
            }
        }
        return null;
    }

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

    private static int layerScore(String theme, int zones) {
        int[] table = "rogue_6".equals(theme)
                ? new int[]{0, 40, 80, 150, 270, 400, 550, 650}
                : new int[]{0, 30, 80, 150, 270, 400, 550, 650};
        int idx = Math.max(0, Math.min(zones - 1, table.length - 1));
        return table[idx];
    }

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

    private static int gainedItemCount(JSONObject cur, JSONObject record) {
        int sum = -1;          // -1：官方口径不含「初始收藏品」，见下方 initRelic
        boolean any = false;
        if (record != null) {
            // 实测核对（7 个官服样本）：capsuleList 不计入「获得物品数」，含它平均误差 2.91%、不含 1.45%
            for (String key : new String[]{"relicList", "activeToolList", "exploreToolList", "legacyList"}) {
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

    private static int rlv2ScoreCap(String theme) {
        try {
            JSONObject cfg = ArknightsApplication.serverConfig;
            if (cfg != null) {
                JSONObject rogue = cfg.getJSONObject("rogue");
                JSONObject caps = rogue == null ? null : rogue.getJSONObject("scoreCap");
                int override = caps == null ? 0 : caps.getIntValue(theme);
                if (override > 0) {
                    return override;
                }
            }
        } catch (Exception ignored) {
        }
        int sum = buffTokenSum(theme);
        if (sum <= 0) {
            return 0;
        }
        int ratio = "rogue_1".equals(theme) ? outerBuffTokenRatio() : 200;
        return sum * ratio;
    }

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

    private static int outerBuffTokenRatio() {
        JSONObject constant = ArknightsApplication.roguelikeTopicTable == null ? null
                : ArknightsApplication.roguelikeTopicTable.getJSONObject("constant");
        if (constant != null && constant.getIntValue("outerBuffTokenRatio") > 0) {
            return constant.getIntValue("outerBuffTokenRatio");
        }
        return 10;
    }

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

    private static int buffTokenSum(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject dc = details == null ? null : details.getJSONObject("detailConst");
        return dc == null ? 0 : dc.getIntValue("outerBuffTokenSum");
    }

    @PostMapping(value = {"/getTicketAssistList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getTicketAssistList(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        JSONObject out = new JSONObject(true);
        out.put("assistList", new JSONArray());
        out.put("playerDataDelta", new JSONObject(true));
        return out;
    }

    private static java.util.Set<String> bossIds(String theme) {
        java.util.Set<String> out = new java.util.HashSet<>();
        JSONObject details = themeDetails(theme);
        JSONObject gc = details == null ? null : details.getJSONObject("gameConst");
        JSONArray arr = gc == null ? null : gc.getJSONArray("bossIds");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.getString(i));
            }
        }
        return out;
    }

    private static boolean ignoreShopBossKill() {
        JSONObject cfg = ArknightsApplication.serverConfig;
        JSONObject rogue = cfg == null ? null : cfg.getJSONObject("rogue");
        return rogue != null && rogue.getBooleanValue("ignoreShopBossKill");
    }

    private static boolean shopBossDefeated(JSONObject decrypted, String theme) {
        if (decrypted == null) {
            return false;
        }
        JSONObject stats = decrypted.getJSONObject("battleData") == null ? null
                : decrypted.getJSONObject("battleData").getJSONObject("stats");
        JSONArray enemyStats = stats == null ? null : stats.getJSONArray("enemyStats");
        if (enemyStats == null) {
            return false;
        }
        java.util.Set<String> bosses = bossIds(theme);
        for (int i = 0; i < enemyStats.size(); i++) {
            JSONObject one = enemyStats.getJSONObject(i);
            JSONObject key = one.getJSONObject("Key");
            if (key == null) {
                continue;
            }
            String enemyId = key.getString("enemyId");
            if (enemyId == null || !bosses.contains(enemyId)) {
                continue;
            }
            if ("HP_ZERO".equals(key.getString("counterType")) && one.getIntValue("Value") > 0) {
                ArknightsApplication.LOGGER.info("[rlv2] 商店老板 {} 被击败（HP_ZERO={}）", enemyId, one.getIntValue("Value"));
                return true;
            }
        }
        return false;
    }

    private static void makeShopFree(JSONObject player) {
        JSONArray arr = player.getJSONArray("pending");
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject one = arr.getJSONObject(i);
            if (!"BATTLE_SHOP".equals(one.getString("type"))) {
                continue;
            }
            JSONObject shop = one.getJSONObject("content").getJSONObject("battleShop");
            if (shop == null) {
                continue;
            }
            JSONArray goods = shop.getJSONArray("goods");
            if (goods != null) {
                for (int g = 0; g < goods.size(); g++) {
                    goods.getJSONObject(g).put("priceCount", 0);
                }
            }
            ArknightsApplication.LOGGER.info("[rlv2] 商店被打劫：{} 件商品全部免费", goods == null ? 0 : goods.size());
        }
    }

    private static boolean oldPendingHasType(JSONObject player, String type) {
        JSONArray arr = player.getJSONArray("pending");
        if (arr == null) {
            return false;
        }
        for (int i = 0; i < arr.size(); i++) {
            if (type.equals(arr.getJSONObject(i).getString("type"))) {
                return true;
            }
        }
        return false;
    }

    private static void markShopRobbed(JSONObject player) {
        JSONArray arr = player.getJSONArray("pending");
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject one = arr.getJSONObject(i);
            if (!"BATTLE_SHOP".equals(one.getString("type"))) {
                continue;
            }
            JSONObject shop = one.getJSONObject("content").getJSONObject("battleShop");
            if (shop == null) {
                continue;
            }
            shop.put("canBattle", false);
            shop.put("hasBoss", false);
            ArknightsApplication.LOGGER.info("[rlv2] 商店已被打劫 → canBattle=false（此后购买免单）");
        }
    }

    @PostMapping(value = {"/shopBattleStart"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject shopBattleStart(@RequestHeader(value = "secret", required = false) String secret,
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
        if (pending == null) {
            pending = new JSONArray();
            player.put("pending", pending);
        }
        // 找到商店待办，取出商品列表作为 addExcludeList
        JSONArray exclude = new JSONArray();
        for (int i = 0; i < pending.size(); i++) {
            JSONObject one = pending.getJSONObject(i);
            if (!"BATTLE_SHOP".equals(one.getString("type"))
                    && !"SHOP".equals(one.getString("type"))) {
                continue;
            }
            JSONObject shop = one.getJSONObject("content").getJSONObject("battleShop");
            if (shop == null) {
                shop = one.getJSONObject("content").getJSONObject("shop");
            }
            JSONArray goods = shop == null ? null : shop.getJSONArray("goods");
            if (goods != null) {
                for (int g = 0; g < goods.size(); g++) {
                    String itemId = goods.getJSONObject(g).getString("itemId");
                    if (itemId != null) {
                        exclude.add(itemId);
                    }
                }
            }
            break;
        }
        JSONObject battle = new JSONObject(true);
        battle.put("state", 0);
        battle.put("addExcludeList", exclude);
        JSONObject battleContent = new JSONObject(true);
        battleContent.put("battle", battle);
        JSONObject battlePend = new JSONObject(true);
        battlePend.put("index", "e_13");
        battlePend.put("type", "BATTLE");
        battlePend.put("content", battleContent);
        // BATTLE 放在队首，商店待办留在后面（官方顺序：BATTLE, BATTLE_SHOP）
        JSONArray next = new JSONArray();
        next.add(battlePend);
        for (int i = 0; i < pending.size(); i++) {
            next.add(pending.getJSONObject(i));
        }
        player.put("pending", next);
        player.put("state", "PENDING");
        ArknightsApplication.LOGGER.info("[rlv2] 商店开战（addExcludeList {} 项）", exclude.size());
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    // ──────────────────── 主题专属系统兜底路由 ────────────────────

    @PostMapping(value = {
            "/confirmTraderReturn",
            "/readEndingChange", "/confirmZoneReward",
            "/finishGame", "/specialZone/leave"
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
        // 即不要贸然消费客户端的待办（否则界面会卡）。只有带奖励或献祭的待办才吃。
        boolean consumeHead = ("SACRIFICE".equals(headType)) || (granted != null && !granted.isEmpty())
                || (head != null && head.getJSONObject("content") != null
                    && (head.getJSONObject("content").getJSONObject("dice") != null
                        || head.getJSONObject("content").getJSONObject("result") != null
                        || head.getJSONObject("content").getJSONArray("rewards") != null));
        if (consumeHead && pending != null && !pending.isEmpty()) {
            pending.remove(0);
            if (pending.isEmpty()) {
                player.put("state", "WAIT_MOVE");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("result", 1);
        result.put("items", granted);
        return result;
    }

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

    @PostMapping(value = {"/battlePass/getReward"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject battlePassGetReward(@RequestHeader(value = "secret", required = false) String secret,
                                          @RequestBody(required = false) JSONObject body,
                                          HttpServletRequest request, HttpServletResponse response) {
        return battlePassReward(secret, body, false);
    }

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

    private static JSONObject refreshNodesDeleted(JSONObject cur, int mode) {
        JSONObject deleted = new JSONObject(true);
        JSONObject zonesOut = new JSONObject(true);
        JSONObject map = cur.getJSONObject("map");
        JSONObject zones = map == null ? null : map.getJSONObject("zones");
        if (zones == null) {
            return deleted;
        }
        JSONObject cursor = cur.getJSONObject("player").getJSONObject("cursor");
        String curZone = String.valueOf(cursor.getIntValue("zone"));
        for (String zk : zones.keySet()) {
            if (mode == 0 && !zk.equals(curZone)) {
                continue;
            }
            JSONObject nodes = zones.getJSONObject(zk).getJSONObject("nodes");
            if (nodes == null) {
                continue;
            }
            JSONObject nodesOut = new JSONObject(true);
            for (String nk : nodes.keySet()) {
                JSONObject node = nodes.getJSONObject(nk);
                if (node == null || !node.containsKey("refresh")) {
                    continue;
                }
                node.remove("refresh");
                JSONArray fields = new JSONArray();
                fields.add("refresh");
                nodesOut.put(nk, fields);
            }
            if (!nodesOut.isEmpty()) {
                JSONObject zoneOut = new JSONObject(true);
                zoneOut.put("nodes", nodesOut);
                zonesOut.put(zk, zoneOut);
            }
        }
        if (!zonesOut.isEmpty()) {
            JSONObject mapOut = new JSONObject(true);
            mapOut.put("zones", zonesOut);
            JSONObject curOut = new JSONObject(true);
            curOut.put("map", mapOut);
            deleted.put("rlv2", curOut);
        }
        return deleted;
    }

    private static int buffPrice(String theme, String buffId, int remain) {
        JSONObject dev = developmentDef(theme, buffId);
        if (dev != null && dev.getIntValue("tokenCost") > 0) {
            return dev.getIntValue("tokenCost");
        }
        return 10;
    }

    private static JSONObject developmentDef(String theme, String buffId) {
        JSONObject table = ArknightsApplication.roguelikeTopicTable;
        JSONObject customize = table == null ? null : table.getJSONObject("customizeData");
        JSONObject themeData = customize == null ? null : customize.getJSONObject(theme);
        if (themeData == null) {
            return null;
        }
        // rogue_1 的局外树在 customizeData.<theme>.developments；
        // rogue_3~6 在 customizeData.<theme>.commonDevelopment.developments（之前只查了前者，价格全退化成 10）
        JSONObject devs = themeData.getJSONObject("developments");
        if (devs != null && devs.getJSONObject(buffId) != null) {
            return devs.getJSONObject(buffId);
        }
        JSONObject common = themeData.getJSONObject("commonDevelopment");
        JSONObject commonDevs = common == null ? null : common.getJSONObject("developments");
        return commonDevs == null ? null : commonDevs.getJSONObject(buffId);
    }

    @PostMapping(value = {"/useInspiration"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject useInspiration(@RequestHeader(value = "secret", required = false) String secret,
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
        String theme = themeOf(cur);

        // 萨米（rogue_4）：useInspiration 的真实语义是「用一枚构想换成灵感」
        // 抓包中的数据形态：module.fragment.fragments = {"f_1":{"index","id","used":false,"weight","value","ei":-1}}
        //   module.fragment.currInspiration = null  ← 灵感槽
        // 参考实现（_refs/openbachelors-roguelike）也是 {"fragmentIndex":"f_N"} → 消耗并回报
        String fragIndex = body == null ? null : body.getString("fragmentIndex");
        JSONObject module = cur.getJSONObject("module");
        JSONObject fragment = module == null ? null : module.getJSONObject("fragment");
        if (fragIndex != null && fragment != null) {
            JSONObject frags = fragment.getJSONObject("fragments");
            JSONObject one = frags == null ? null : frags.getJSONObject(fragIndex);
            if (one == null) {
                return bad("找不到该构想", 1);
            }
            if (one.getBooleanValue("used")) {
                return bad("该构想已使用", 1);
            }
            // 表：modules.rogue_4.fragment.fragmentTypeData
            //   IDEA=构想(W_1) / WISH=遗愿(D_xx) / INSPIRATION=灵感(F_xx)
            // 灵感 = 「可以在探索中直接使用以获取临时的战斗增益，同时仅生效一个，仅可通过战斗被消耗」
            // 状态字段 module.fragment.currInspiration（官服样本全是 null，结构按 fragment 对象镜像，推断）
            String fragId = one.getString("id");
            boolean isInspiration = fragId != null && fragId.contains("_fragment_F_");
            if (!isInspiration) {
                return bad("该构想不是灵感，无法使用", 1);
            }
            one.put("used", true);
            // 官服：构想本体的 ei 保持不变（F_07 仍是 -1），只有 currInspiration.ei 有值
            // 官服实测（020_08 useInspiration {"fragmentIndex":"f_3"}）：
            //   currInspiration = {"instId":"f_3","id":"rogue_4_fragment_F_07","ei":2}
            //   字段名是 instId（不是 index）；构想本体的 ei 保持 -1 不变
            //   ei 的含义仍未确定（该样本 ei=2，同时指挥等级=2，暂按「灵感等级≈指挥等级」推断）
            JSONObject insp = new JSONObject(true);
            insp.put("instId", fragIndex);
            insp.put("id", fragId);
            JSONObject playerNow = cur.getJSONObject("player");
            int instEe = one.getIntValue("ei");
            if (instEe < 0) {
                // 唯一官服样本：指挥等级 2 → ei 2（推断为灵感等级）
                instEe = playerNow.getJSONObject("property").getIntValue("level");
            }
            insp.put("ei", instEe);
            fragment.put("currInspiration", insp);
            JSONObject player0 = cur.getJSONObject("player");
            player0.put("state", "WAIT_MOVE");
            userDao.setUserData(account.getUid(), userData);
            com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
            ArknightsApplication.LOGGER.info("[rlv2] 使用灵感（萨米）：构想 {} → currInspiration", fragIndex);
            JSONObject out0 = delta(cur);
            out0.put("result", 1);
            return out0;
        }

        JSONObject details = themeDetails(theme);
        JSONObject pool = details == null ? null : details.getJSONObject("squadBuffData");
        JSONObject buff = cur.getJSONObject("buff");
        if (buff == null) {
            buff = new JSONObject(true);
            buff.put("tmpHP", 0);
            buff.put("capsule", null);
            buff.put("squadBuff", new JSONArray());
            cur.put("buff", buff);
        }
        JSONArray owned = buff.getJSONArray("squadBuff");
        if (owned == null) {
            owned = new JSONArray();
            buff.put("squadBuff", owned);
        }
        java.util.Set<String> have = new java.util.HashSet<>();
        for (int i = 0; i < owned.size(); i++) {
            JSONObject one = owned.getJSONObject(i);
            if (one != null && one.getString("buffId") != null) {
                have.add(one.getString("buffId"));
            }
        }
        String wanted = body == null ? null : body.getString("id");
        String pick = null;
        if (wanted != null && pool != null && pool.containsKey(wanted) && !have.contains(wanted)) {
            pick = wanted;
        } else if (pool != null) {
            java.util.List<String> candidates = new java.util.ArrayList<>();
            for (String k : pool.keySet()) {
                if (!have.contains(k)) {
                    candidates.add(k);
                }
            }
            if (!candidates.isEmpty()) {
                pick = candidates.get(RANDOM.nextInt(candidates.size()));
            }
        }
        if (pick == null) {
            return bad("没有可获得的启示", 1);
        }
        JSONObject entry = new JSONObject(true);
        entry.put("buffId", pick);
        owned.add(entry);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        ArknightsApplication.LOGGER.info("[rlv2] 使用启示 → {}（当前 {} 个）", pick, owned.size());
        JSONObject result = delta(cur);
        result.put("result", 1);
        return result;
    }

    // ───────────────────────── 月度任务（monthMission）─────────────────────────

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

    /** 从月任务池里挑一个指定类型、且不与现有列表重复的任务定义 */
    private static JSONObject pickMonthMission(String theme, String type, JSONArray exclude) {
        JSONObject details = themeDetails(theme);
        JSONArray defs = details == null ? null : details.getJSONArray("monthMission");
        if (defs == null || defs.isEmpty()) {
            return null;
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        if (exclude != null) {
            for (int i = 0; i < exclude.size(); i++) {
                JSONObject one = exclude.getJSONObject(i);
                JSONObject m = one == null ? null : one.getJSONObject("mission");
                if (m != null && m.getString("id") != null) {
                    used.add(m.getString("id"));
                }
            }
        }
        int totalWeight = 0;
        for (int i = 0; i < defs.size(); i++) {
            JSONObject d = defs.getJSONObject(i);
            if (type.equals(d.getString("taskClass")) && !used.contains(d.getString("id"))) {
                totalWeight += Math.max(1, d.getIntValue("innerClassWeight"));
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = RANDOM.nextInt(totalWeight);
        int acc = 0;
        JSONObject picked = null;
        for (int i = 0; i < defs.size(); i++) {
            JSONObject d = defs.getJSONObject(i);
            if (!type.equals(d.getString("taskClass")) || used.contains(d.getString("id"))) {
                continue;
            }
            acc += Math.max(1, d.getIntValue("innerClassWeight"));
            if (roll < acc) {
                picked = d;
                break;
            }
        }
        return picked;
    }

    /**
     * 月委托刷新。官服抓包（2026-11-06 12:5x，connection 007/014）：
     *   POST /rlv2/normal/refreshMission {"theme":"rogue_6","index":0}
     *   → playerDataDelta.modified.rlv2.outer.<theme>.mission = {updateId, refresh: 3→2, list:[4 个]}
     * 只改 outer.<theme>.mission，不动 current。
     */
    @PostMapping(value = {"/normal/refreshMission"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject refreshMonthMission(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody(required = false) JSONObject body,
            HttpServletRequest request, HttpServletResponse response) {
        String theme = body == null || body.getString("theme") == null ? "rogue_1" : body.getString("theme");
        int index = body == null ? 0 : body.getIntValue("index");
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request)
                + "] /rlv2/normal/refreshMission theme=" + theme + " index=" + index);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject mission = ensureMonthMission(userData, theme);
        if (mission == null) {
            return bad("该主题没有月委托", 1);
        }
        int refreshLeft = mission.getIntValue("refresh");
        if (refreshLeft <= 0) {
            return bad("刷新次数不足", 1);
        }
        JSONArray list = mission.getJSONArray("list");
        if (list == null || list.isEmpty()) {
            return bad("月委托列表为空", 1);
        }
        if (index < 0 || index >= list.size()) {
            index = 0;
        }
        String type = list.getJSONObject(index).getString("type");
        JSONObject picked = pickMonthMission(theme, type, list);
        if (picked == null) {
            return bad("没有可刷新的委托", 1);
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
        list.set(index, one);
        mission.put("refresh", refreshLeft - 1);
        mission.put("list", list);
        JSONObject themeOuter = Rlv2Helper.outerTheme(userData, theme);
        if (themeOuter != null) {
            themeOuter.put("mission", mission);
        }
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[rlv2] 刷新月委托 {}[{}] → {}（剩余刷新 {}）", theme, index,
                picked.getString("id"), refreshLeft - 1);

        JSONObject out = new JSONObject(true);
        JSONObject pdd = new JSONObject(true);
        JSONObject modified = new JSONObject(true);
        JSONObject rlv2Mod = new JSONObject(true);
        JSONObject outer = new JSONObject(true);
        JSONObject themeObj = new JSONObject(true);
        themeObj.put("mission", mission);
        outer.put(theme, themeObj);
        rlv2Mod.put("outer", outer);
        modified.put("rlv2", rlv2Mod);
        pdd.put("modified", modified);
        pdd.put("deleted", new JSONObject());
        out.put("playerDataDelta", pdd);
        return out;
    }

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
            JSONObject deletedOut = refreshNodesDeleted(cur, 1);
            userDao.setUserData(account.getUid(), userData);
            com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
            JSONObject leaveOut = delta(cur);
            if (deletedOut.containsKey("rlv2")) {
                JSONObject curDel = new JSONObject(true);
                curDel.put("current", deletedOut.getJSONObject("rlv2"));
                JSONObject rlDel = new JSONObject(true);
                rlDel.put("rlv2", curDel);
                leaveOut.getJSONObject("playerDataDelta").put("deleted", rlDel);
            }
            return leaveOut;
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
                    // 官服实测（013_01）：炼金消耗的构想被标 used=true，条目保留，ei 不变
                    f.put("used", true);
                }
            }
        }
        // 抽收藏品：价值 >= 8 → 终末池（_relic_final_），否则普通池
        JSONObject details = themeDetails(theme);
        JSONObject relics = details == null ? null : details.getJSONObject("relics");
        java.util.List<String> normalPool = new java.util.ArrayList<>();
        java.util.List<String> ssrPool = new java.util.ArrayList<>();
        String relicPfxFinal = theme + "_relic_final_";
        String relicPfxNormal = theme + "_relic_";
        if (relics != null) {
            for (String rid : relics.keySet()) {
                if (rid.startsWith(relicPfxFinal)) {
                    ssrPool.add(rid);
                } else if (rid.startsWith(relicPfxNormal)) {
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
        // 官服实测（013_01）：ALCHEMY_REWARD.items 是 2 件，客户端 alchemyReward{index} 二选一
        java.util.LinkedHashSet<String> picked = new java.util.LinkedHashSet<>();
        if (reward != null) {
            picked.add(reward);
        }
        java.util.List<String> pool2 = isSsr ? ssrPool : normalPool;
        if (pool2 != null && !pool2.isEmpty()) {
            java.util.List<String> shuffled = new java.util.ArrayList<>(pool2);
            java.util.Collections.shuffle(shuffled, RANDOM);
            for (String rid : shuffled) {
                if (picked.size() >= 2) {
                    break;
                }
                picked.add(rid);
            }
        }
        for (String rid : picked) {
            JSONObject one = new JSONObject(true);
            one.put("id", rid);
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
        pend.put("index", "e_25");
        pend.put("type", "ALCHEMY_REWARD");
        pend.put("content", content);
        // 官服实测：奖励之后还挂一条 ALCHEMY（可以继续炼金）
        JSONObject alchemyPend = new JSONObject(true);
        alchemyPend.put("index", "e_24");
        alchemyPend.put("type", "ALCHEMY");
        JSONObject alchemyContent = new JSONObject(true);
        JSONObject alchemyState = new JSONObject(true);
        alchemyState.put("isEnd", 0);
        alchemyState.put("canAlchemy", true);
        alchemyContent.put("alchemy", alchemyState);
        alchemyPend.put("content", alchemyContent);
        JSONArray pendingArr = new JSONArray();
        pendingArr.add(pend);
        pendingArr.add(alchemyPend);
        player.put("pending", pendingArr);
        player.put("state", "PENDING");
        recInc(cur, "cntUseAlchemy", 1);
        JSONObject alchemyDeleted = refreshNodesDeleted(cur, 0);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject alchemyOut = delta(cur);
        if (alchemyDeleted.containsKey("rlv2")) {
            JSONObject curDel = new JSONObject(true);
            curDel.put("current", alchemyDeleted.getJSONObject("rlv2"));
            JSONObject rlDel = new JSONObject(true);
            rlDel.put("rlv2", curDel);
            alchemyOut.getJSONObject("playerDataDelta").put("deleted", rlDel);
        }
        return alchemyOut;
    }

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
        // 官服实测（013_02）：alchemyReward{index} 是从 items 里二选一，只发选中的那件
        int pick = body == null ? 0 : body.getIntValue("index");
        if (pending != null && !pending.isEmpty()) {
            JSONObject head = pending.getJSONObject(0);
            JSONObject content = head.getJSONObject("content");
            JSONObject reward = content == null ? null : content.getJSONObject("alchemyReward");
            JSONArray items = reward == null ? null : reward.getJSONArray("items");
            if (items != null && pick >= 0 && pick < items.size()) {
                JSONArray one = new JSONArray();
                one.add(items.getJSONObject(pick));
                grantRewardArray(userData, cur, one, granted);
            } else {
                grantRewardArray(userData, cur, items, granted);
            }
            // 只弹掉 ALCHEMY_REWARD，ALCHEMY（继续炼金）要留在队首
            pending.remove(0);
        }
        if (pending == null || pending.isEmpty()) {
            player.put("state", "WAIT_MOVE");
        } else {
            player.put("state", "PENDING");
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        result.put("items", granted);
        return result;
    }

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
        // 官方抓包：{"scrapInstId":"s_4","toWalk":0} 上车 / {"scrapInstId":"","toWalk":1} 下车步行
        boolean toWalk = false;
        if (body != null) {
            Object tv = body.get("toWalk");
            if (tv instanceof Number) {
                toWalk = ((Number) tv).intValue() != 0;
            } else {
                toWalk = body.getBooleanValue("toWalk");
            }
        }
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        if (scrap != null && instId != null) {
            JSONObject vehicle = scrap.getJSONObject("activeVehicle");
            if (vehicle == null) {
                vehicle = new JSONObject(true);
                scrap.put("activeVehicle", vehicle);
            }
            if (toWalk) {
                vehicle.remove("instId");
                vehicle.put("isWalk", true);
            } else {
                vehicle.put("isWalk", false);
                if (instId != null && !instId.isEmpty()) {
                    vehicle.put("instId", instId);
                }
            }
            JSONObject inv = scrap.getJSONObject("inventory");
            JSONObject item = inv == null ? null : inv.getJSONObject(instId);
            if (item != null) {
                // 载具全免费时换乘也不计次数（否则客户端会当成一次性道具消耗掉）
                if (!freeRide(cur)) {
                    item.put("useCnt", item.getIntValue("useCnt") + 1);
                }
            }
            if (toWalk) {
                recInc(cur, "cntMoveByScrap", 1);
            }
            recInc(cur, "cntUseItem", 1);
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject vehicleOut = delta(cur);
        if (toWalk) {
            // 官服实测：deleted.rlv2.current.module.scrap.activeVehicle = ["instId"]
            JSONArray fields = new JSONArray();
            fields.add("instId");
            JSONObject vehDel = new JSONObject(true);
            vehDel.put("activeVehicle", fields);
            JSONObject scrapDel = new JSONObject(true);
            scrapDel.put("scrap", vehDel);
            JSONObject moduleDel = new JSONObject(true);
            moduleDel.put("module", scrapDel);
            JSONObject curDel = new JSONObject(true);
            curDel.put("current", moduleDel);
            JSONObject rlDel = new JSONObject(true);
            rlDel.put("rlv2", curDel);
            vehicleOut.getJSONObject("playerDataDelta").put("deleted", rlDel);
        }
        return vehicleOut;
    }

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
        String theme = themeOf(cur);
        String choiceId = body == null ? null : body.getString("choice");
        boolean leave = body != null && body.getIntValue("leave") == 1;
        JSONArray removed = new JSONArray();
        if (choiceId != null) {
            JSONObject relicInv = cur.getJSONObject("inventory").getJSONObject("relic");
            if (relicInv != null && relicInv.containsKey(choiceId)) {
                String relicId = relicInv.getJSONObject(choiceId).getString("id");
                relicInv.remove(choiceId);
                removed.add(choiceId);
                ArknightsApplication.LOGGER.info("[rlv2] 献祭收藏品 {}={}", choiceId, relicId);
            }
        }
        if (leave) {
            player.put("pending", new JSONArray());
            player.put("state", "WAIT_MOVE");
        } else {
            // 官服实测（032_15，rogue_2）：{"choice":"r_12","leave":0}
            //   → pending SCENE scene_ro2_sacrifice_2，choices 为 2 个回礼交易项（实测 sacrifice_5/6）
            //   → deleted.inventory.relic = ["r_12"]
            JSONArray scene = buildSacrificeResultPending(cur, theme);
            if (scene != null) {
                player.put("pending", scene);
                player.put("state", "PENDING");
            } else {
                player.put("pending", new JSONArray());
                player.put("state", "WAIT_MOVE");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject result = delta(cur);
        if (!removed.isEmpty()) {
            JSONObject relicDel = new JSONObject(true);
            relicDel.put("relic", removed);
            JSONObject invDel = new JSONObject(true);
            invDel.put("inventory", relicDel);
            JSONObject curDel = new JSONObject(true);
            curDel.put("current", invDel);
            JSONObject rlDel = new JSONObject(true);
            rlDel.put("rlv2", curDel);
            result.getJSONObject("playerDataDelta").put("deleted", rlDel);
        }
        return result;
    }

    private static JSONArray buildSacrificeResultPending(JSONObject cur, String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (scenes == null || choices == null) {
            return null;
        }
        String themeNum = theme.substring(theme.length() - 1);
        // 官服 2 次样本（r_12 / r_19，都是 rogue_2_relic_fight_*）都是 scene_ro2_sacrifice_2 + {5,6}，
        // 暂定固定为该回礼页；表里没有「献祭物档位 → 场景」的映射字段可查。
        String sceneId = "scene_ro" + themeNum + "_sacrifice_2";
        if (!scenes.containsKey(sceneId)) {
            for (int i = 2; i <= 5; i++) {
                String id = "scene_ro" + themeNum + "_sacrifice_" + i;
                if (scenes.containsKey(id)) {
                    sceneId = id;
                    break;
                }
            }
        }
        String prefix = "choice_ro" + themeNum + "_sacrifice_";
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String cid : choices.keySet()) {
            if (!cid.startsWith(prefix)) {
                continue;
            }
            JSONObject cdef = choices.getJSONObject(cid);
            if ("SACRIFICE".equals(cdef.getString("type"))) {
                continue;
            }
            if (cid.endsWith("_1") || cid.endsWith("_2")) {
                continue;
            }
            pool.add(cid);
        }
        if (pool.isEmpty()) {
            return null;
        }
        // 官服样本固定给「交换情报」两项（sacrifice_5 / sacrifice_6）
        String p5 = "choice_ro" + themeNum + "_sacrifice_5";
        String p6 = "choice_ro" + themeNum + "_sacrifice_6";
        java.util.List<String> picked = new java.util.ArrayList<>();
        if (pool.contains(p5) && pool.contains(p6)) {
            picked.add(p5);
            picked.add(p6);
        } else {
            java.util.Collections.shuffle(pool, RANDOM);
            for (int i = 0; i < Math.min(2, pool.size()); i++) {
                picked.add(pool.get(i));
            }
        }
        JSONObject choiceMap = new JSONObject(true);
        JSONObject additional = new JSONObject(true);
        for (int i = 0; i < picked.size(); i++) {
            String cid = picked.get(i);
            choiceMap.put(cid, 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put(cid, extra);
        }
        JSONObject scene = new JSONObject(true);
        scene.put("id", sceneId);
        scene.put("choices", choiceMap);
        scene.put("choiceAdditional", additional);
        JSONObject content = new JSONObject(true);
        content.put("scene", scene);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_40");
        pend.put("type", "SCENE");
        pend.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pend);
        return arr;
    }

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
            if ("rogue_6".equals(theme)) {
                // PRTS：先行一步选「尝试探索树的源头」送出干员 → 返回时带来
                // 2 希望和收藏品【怦然信标】（结局3 纠缠，调和 的关键道具）
                addRelic(cur, "rogue_6_relic_final_3", 1);
                JSONObject prop = cur.getJSONObject("player").getJSONObject("property");
                prop.put("hope", prop.getIntValue("hope") + 2);
                JSONObject one = new JSONObject(true);
                one.put("id", "rogue_6_relic_final_3");
                one.put("count", 1);
                items.add(one);
                ArknightsApplication.LOGGER.info("[rlv2] rogue_6 先行一步 → 干员返回，获得【怦然信标】+2 希望");
            } else {
                String relic = randomRelic(theme, cur);
                if (relic != null) {
                    addRelic(cur, relic, 1);
                    JSONObject one = new JSONObject(true);
                    one.put("id", relic);
                    one.put("count", 1);
                    items.add(one);
                }
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
        pushDiceIfNeeded(cur, theme, player(cur));
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/refreshShop theme={}", theme);
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        JSONObject out = delta(cur);
        out.put("result", 1);
        return out;
    }

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
            // 官服实测（parsed_b_rlv2_19_1104/016_00、016_01）：丢构想不是删除条目，
            // 而是把 used 置为 true（条目仍留在 fragments 里，weight 仍计入）
            JSONObject one = fragments.getJSONObject(index);
            if (one == null) {
                one = fragments.getJSONObject("f_" + index);
            }
            if (one != null) {
                // 官服：丢弃只改 used，ei 保持不变（016_00 f_7 ei 仍为 -1）
                one.put("used", true);
                ArknightsApplication.LOGGER.info("[rlv2] 丢弃构想 {}（{}）→ used=true", index, one.getString("id"));
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/setPinned"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject setPinned(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject body,
                               HttpServletRequest request, HttpServletResponse response) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            return bad("无法查询到此账户", 2);
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        Rlv2Helper.ensure(userData);
        JSONObject rl = userData.getJSONObject("rlv2");
        String id = body == null ? null : body.getString("id");
        if (id == null || id.isEmpty()) {
            rl.put("pinned", null);
        } else {
            rl.put("pinned", id);
        }
        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /rlv2/setPinned id=" + id);
        JSONObject modified = new JSONObject(true);
        JSONObject rlv2Delta = new JSONObject(true);
        // 官服：current 的每个字段都下发 null（表示局内无变化）
        JSONObject current = new JSONObject(true);
        for (String k : new String[]{"player", "map", "troop", "inventory", "game", "buff", "record", "module"}) {
            current.put(k, null);
        }
        rlv2Delta.put("current", current);
        rlv2Delta.put("outer", rl.getJSONObject("outer"));
        rlv2Delta.put("pinned", rl.get("pinned"));
        modified.put("rlv2", rlv2Delta);
        JSONObject pdd = new JSONObject(true);
        pdd.put("modified", modified);
        pdd.put("deleted", new JSONObject(true));
        JSONObject out = new JSONObject(true);
        out.put("playerDataDelta", pdd);
        return out;
    }

    // ─────────────────── 地区委托（nodeMission，官服 2026-11-04 抓包）───────────────────

    @PostMapping(value = {"/nodeMission/closeTip"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject nodeMissionCloseTip(@RequestHeader(value = "secret", required = false) String secret,
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
        player.put("pending", new JSONArray());
        player.put("state", "WAIT_MOVE");
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    @PostMapping(value = {"/nodeMission/confirm"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject nodeMissionConfirm(@RequestHeader(value = "secret", required = false) String secret,
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
        JSONObject player = cur.getJSONObject("player");
        int tier = 1;
        JSONObject rec = cur.getJSONObject("record");
        if (rec != null && rec.containsKey("pendingTaskTier")) {
            tier = Math.max(1, Math.min(3, rec.getIntValue("pendingTaskTier")));
        }
        JSONArray scene = buildTaskRewardPending(cur, theme, tier);
        if (scene != null) {
            player.put("pending", scene);
            player.put("state", "PENDING");
        } else {
            player.put("pending", new JSONArray());
            player.put("state", "WAIT_MOVE");
        }
        // 记录完成的委托（官服 record.nodeMission = {"rogue_2_task_016": 2}）
        if (rec != null) {
            JSONObject nm = rec.getJSONObject("nodeMission");
            if (nm == null) {
                nm = new JSONObject(true);
                rec.put("nodeMission", nm);
            }
            String taskId = rec.getString("pendingTaskId");
            if (taskId != null) {
                nm.put(taskId, nm.getIntValue(taskId) + 1);
                rec.remove("pendingTaskId");
            }
        }
        userDao.setUserData(account.getUid(), userData);
        com.hypergryph.arknights.core.dao.RogueRunDao.save(account.getUid(), cur);
        return delta(cur);
    }

    /** 委托奖励三选一：scene_ro{N}_taskreward<tier>_enter + 随机 3 个 taskrewardN_* 选项 */
    private static JSONArray buildTaskRewardPending(JSONObject cur, String theme, int tier) {
        JSONObject details = themeDetails(theme);
        JSONObject scenes = details == null ? null : details.getJSONObject("choiceScenes");
        JSONObject choices = details == null ? null : details.getJSONObject("choices");
        if (scenes == null || choices == null) {
            return null;
        }
        String themeNum = theme.substring(theme.length() - 1);
        String sceneId = "scene_ro" + themeNum + "_taskreward" + tier + "_enter";
        if (!scenes.containsKey(sceneId)) {
            for (String id : scenes.keySet()) {
                if (id.contains("taskreward") && id.endsWith("_enter")) {
                    sceneId = id;
                    break;
                }
            }
        }
        String prefix = "choice_ro" + themeNum + "_taskreward" + tier + "_";
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String cid : choices.keySet()) {
            if (cid.startsWith(prefix)) {
                pool.add(cid);
            }
        }
        java.util.Collections.shuffle(pool, RANDOM);
        JSONObject choiceMap = new JSONObject(true);
        JSONObject additional = new JSONObject(true);
        int n = Math.min(3, pool.size());
        for (int i = 0; i < n; i++) {
            String cid = pool.get(i);
            choiceMap.put(cid, 1);
            JSONObject extra = new JSONObject(true);
            extra.put("rewards", new JSONArray());
            extra.put("costs", new JSONArray());
            additional.put(cid, extra);
        }
        JSONObject scene = new JSONObject(true);
        scene.put("id", sceneId);
        scene.put("choices", choiceMap);
        scene.put("choiceAdditional", additional);
        scene.put("independent", true);
        JSONObject content = new JSONObject(true);
        content.put("scene", scene);
        JSONObject pend = new JSONObject(true);
        pend.put("index", "e_" + (60 + tier));
        pend.put("type", "SCENE");
        pend.put("content", content);
        JSONArray arr = new JSONArray();
        arr.add(pend);
        return arr;
    }

    // ─────────────────── 节点重掷（rerollNode，仅 rogue_4 开放）───────────────────

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

    private static String nodeTypeName(String theme, int type) {
        java.util.List<String> names = com.hypergryph.arknights.core.function.RogueMapGen.nodeTypeNames(theme);
        for (int i = 0; i < names.size(); i++) {
            if ((1 << i) == type) {
                return names.get(i);
            }
        }
        return null;
    }

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

    @PostMapping(value = {"/bankPut"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject bankPut(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject body,
                              HttpServletRequest request, HttpServletResponse response) {
        return bankMove(secret, true);
    }

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

    /**
     * 放弃时推的 GAME_SETTLE 内容（官服 B服 009_00 实测结构）：
     *   {success, result:{brief, record, buffBankPut}, detailStr, popReport}
     * 之前这里塞的是空对象 → 客户端放弃后结算界面拿不到 brief/record 直接卡住。
     */
    /** base64(zlib(utf8))：官服 detailStr 就是这个形态（解压后是 {brief,troopChars,initial,zones}） */
    private static String zlibBase64(String text) {
        try {
            byte[] raw = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.util.zip.Deflater def = new java.util.zip.Deflater();
            def.setInput(raw);
            def.finish();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!def.finished()) {
                int n = def.deflate(buf);
                bos.write(buf, 0, n);
            }
            def.end();
            return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] detailStr 压缩失败: {}", e.toString());
            return "";
        }
    }

    /**
     * 修正在途招募券的候选列表：老数据里 1★/2★ 干员被写成 evolvePhase=1，
     * 客户端查属性表 IndexOutOfRange → 干员卡 NRE → 招募界面崩掉。
     * 这里按 character_table 的阶段数夹回去，并把等级改成该阶段上限。
     * 返回是否改动。
     */
    public static boolean fixRecruitListPhases(JSONObject cur) {
        JSONObject inv = cur.getJSONObject("inventory");
        JSONObject recruit = inv == null ? null : inv.getJSONObject("recruit");
        if (recruit == null || recruit.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (String tk : recruit.keySet()) {
            JSONObject ticket = recruit.getJSONObject(tk);
            JSONArray list = ticket == null ? null : ticket.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                JSONObject cand = list.getJSONObject(i);
                if (cand == null) {
                    continue;
                }
                String charId = cand.getString("charId");
                if (charId == null) {
                    continue;
                }
                int maxPhase = maxEvolvePhase(charId);
                int ph = Math.min(Math.max(0, cand.getIntValue("evolvePhase")), maxPhase);
                int lv = maxLevelOfPhase(charId, ph);
                if (cand.getIntValue("evolvePhase") != ph || cand.getIntValue("level") != lv) {
                    cand.put("evolvePhase", ph);
                    cand.put("level", lv);
                    changed = true;
                    ArknightsApplication.LOGGER.info("[rlv2] 修正候选 {} 阶段 {}→{} 等级→{}", charId,
                            cand.getIntValue("evolvePhase"), ph, lv);
                }
                if (cand.getIntValue("defaultSkillIndex") < 0) {
                    cand.put("defaultSkillIndex", 0);
                    changed = true;
                }
                if (cand.getJSONArray("skills") == null) {
                    cand.put("skills", new JSONArray());
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * syncData 里的集成战略自愈：把「卡住」的局内状态修好，客户端才不会卡在结算/待办上。
     *  1) GAME_SETTLE 待办的 content 是空对象（旧版 giveUpGame 的坏数据）→ 用当前局状态重建 brief/record
     *  2) state=PENDING 但 pending 为空 → 放回 WAIT_MOVE（否则客户端一直等一个不存在的待办）
     * 返回是否改动过。
     */
    public static boolean repairCurrent(JSONObject userData) {
        if (userData == null) {
            return false;
        }
        JSONObject rl = userData.getJSONObject("rlv2");
        JSONObject cur = rl == null ? null : rl.getJSONObject("current");
        if (cur == null) {
            return false;
        }
        JSONObject game = cur.getJSONObject("game");
        if (game == null || game.getString("theme") == null || game.getString("theme").isEmpty()) {
            return false;
        }
        JSONObject player = cur.getJSONObject("player");
        if (player == null) {
            return false;
        }
        boolean changed = false;
        // 网格主题（rogue_6）：旧存档 / 早期 createGame 只在空地图时同步过一次，
        // gridZone.zones 是空的 → 客户端地图不渲染。这里补同步。
        JSONObject mapZones = cur.getJSONObject("map") == null ? null
                : cur.getJSONObject("map").getJSONObject("zones");
        JSONObject gridZone = cur.getJSONObject("module") == null ? null
                : cur.getJSONObject("module").getJSONObject("gridZone");
        if (fixRecruitListPhases(cur)) {
            changed = true;
            ArknightsApplication.LOGGER.info("[rlv2] 已修正在途招募券候选的精英化阶段");
        }
        if (mapZones != null && !mapZones.isEmpty()) {
            String repTheme = game.getString("theme");
            JSONObject repMods = themeModules(repTheme);
            boolean gridTheme = repMods != null && repMods.getJSONObject("gridZone") != null;
            JSONObject repCursor = player.getJSONObject("cursor");
            boolean posMissing = false; // 入口格由 enterZone 按官方规则写入，这里不再自动补
            if (posMissing) {
                JSONObject curZone = mapZones.getJSONObject(String.valueOf(repCursor.getIntValue("zone")));
                placeOnGridEntry(cur, curZone);
                changed = true;
            }
            boolean gridEmpty = gridZone == null || gridZone.getJSONObject("zones") == null
                    || gridZone.getJSONObject("zones").isEmpty();
            if (gridEmpty || posMissing) {
                seedGridZoneModule(cur, repTheme);
                changed = true;
                ArknightsApplication.LOGGER.info("[rlv2] 补齐网格地图数据（theme={}）", repTheme);
            }
        }
        JSONArray pending = player.getJSONArray("pending");
        if (pending != null) {
            for (int i = 0; i < pending.size(); i++) {
                JSONObject pend = pending.getJSONObject(i);
                if (pend == null) {
                    continue;
                }
                if (!"GAME_SETTLE".equals(pend.getString("type"))) {
                    continue;
                }
                JSONObject content = pend.getJSONObject("content");
                boolean broken = content == null || content.isEmpty()
                        || content.getJSONObject("result") == null;
                if (broken) {
                    // 旧版 giveUpGame 写下的坏待办：当时其实没结算成功，
                    // 直接把这一局「还」给玩家（清待办 + 回到 WAIT_MOVE），不销毁存档
                    pending.remove(i);
                    i--;
                    player.put("state", "WAIT_MOVE");
                    changed = true;
                    ArknightsApplication.LOGGER.info("[rlv2] 清掉坏的 GAME_SETTLE 待办 → 恢复可行动（theme={}，节点保留）",
                            game.getString("theme"));
                }
            }
            if (pending.isEmpty() && "PENDING".equals(player.getString("state"))) {
                player.put("state", "WAIT_MOVE");
                changed = true;
                ArknightsApplication.LOGGER.info("[rlv2] PENDING 但无待办 → 状态放回 WAIT_MOVE");
            }
        }
        return changed;
    }

    private static JSONObject buildGiveUpSettleContent(JSONObject cur, JSONObject userData) {
        JSONObject game = cur.getJSONObject("game") == null ? new JSONObject(true) : cur.getJSONObject("game");
        JSONObject player = cur.getJSONObject("player");
        JSONObject prop = player == null ? new JSONObject(true) : player.getJSONObject("property");
        JSONObject record0 = cur.getJSONObject("record");
        JSONObject troop = cur.getJSONObject("troop");
        JSONObject inv = cur.getJSONObject("inventory");

        int level = 1;
        if (player != null && player.getJSONObject("cursor") != null) {
            int zone = player.getJSONObject("cursor").getIntValue("zone");
            if (zone >= 1000) {
                level = zone - 999;
            }
        }

        JSONObject endProp = new JSONObject(true);
        endProp.put("hp", prop == null || prop.getJSONObject("hp") == null ? 0 : prop.getJSONObject("hp").getIntValue("current"));
        endProp.put("gold", prop == null ? 0 : prop.getIntValue("gold"));
        endProp.put("populationCost", prop == null || prop.getJSONObject("population") == null ? 0 : prop.getJSONObject("population").getIntValue("cost"));
        endProp.put("populationMax", prop == null || prop.getJSONObject("population") == null ? 0 : prop.getJSONObject("population").getIntValue("max"));
        endProp.put("vision", 0);
        endProp.put("chaos", new JSONArray());

        JSONObject brief = new JSONObject(true);
        brief.put("level", level);
        brief.put("over", true);
        brief.put("success", 0);
        brief.put("ending", "");
        brief.put("theme", game.getString("theme"));
        brief.put("mode", game.getString("mode"));
        brief.put("predefined", game.getString("predefined"));
        JSONObject outer = game.getJSONObject("outer");
        brief.put("band", outer == null || outer.getString("band") == null ? "" : outer.getString("band"));
        brief.put("startTs", game.getLongValue("start"));
        brief.put("endTs", ArknightsApplication.getTimestamp());
        brief.put("endZoneId", "zone_" + level);
        brief.put("endProperty", endProp);
        brief.put("innerMission", false);
        JSONArray imp = new JSONArray();
        imp.add(0);
        imp.add(1);
        brief.put("innerMissionProcess", imp);
        brief.put("innerMissionProcessAddition", new JSONObject(true));
        brief.put("modeGrade", game.getIntValue("modeGrade"));
        brief.put("seed", game.getString("seed") == null ? "" : game.getString("seed"));

        JSONObject record = new JSONObject(true);
        int cnt = 0;
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        record.put("cntZone", level);
        record.put("cntBattleNormal", 0);
        record.put("cntBattleElite", 0);
        record.put("cntBattleBoss", 0);
        record.put("cntArrivedNode", record0 == null ? 0 : record0.getIntValue("cntArrivedNode"));
        record.put("cntRecruitChar", chars == null ? 0 : chars.size());
        record.put("cntUpgradeChar", 0);
        record.put("cntKillEnemy", record0 == null ? 0 : record0.getIntValue("cntKillEnemy"));
        record.put("cntShopBuy", record0 == null ? 0 : record0.getIntValue("cntShopBuy"));
        record.put("cntPerfectBattle", 0);
        record.put("cntProtectBox", 0);
        record.put("cntRecruitFree", 0);
        record.put("cntRecruitAssist", 0);
        record.put("cntRecruitNpc", 0);
        record.put("cntRecruitProfession", new JSONObject(true));
        JSONArray troopChars = new JSONArray();
        if (chars != null) {
            for (String k : chars.keySet()) {
                JSONObject one = chars.getJSONObject(k);
                if (one == null) {
                    continue;
                }
                JSONObject c = new JSONObject(true);
                c.put("instId", one.getString("instId") == null ? k : one.getString("instId"));
                c.put("charId", one.getString("charId"));
                c.put("type", one.getString("type") == null ? "NORMAL" : one.getString("type"));
                c.put("upgradePhase", one.getIntValue("upgradePhase"));
                c.put("evolvePhase", one.getIntValue("evolvePhase"));
                c.put("level", one.getIntValue("level"));
                troopChars.add(c);
            }
        }
        record.put("troopChars", troopChars);
        record.put("cntArrivedNodeType", new JSONObject(true));
        JSONArray relicList = new JSONArray();
        JSONObject relics = inv == null ? null : inv.getJSONObject("relic");
        if (relics != null) {
            for (String k : relics.keySet()) {
                String id = relics.getJSONObject(k) == null ? null : relics.getJSONObject(k).getString("id");
                if (id != null) {
                    relicList.add(id);
                }
            }
        }
        record.put("relicList", relicList);
        record.put("capsuleList", new JSONArray());
        record.put("activeToolList", new JSONArray());
        JSONArray toolList = new JSONArray();
        JSONObject tools = inv == null ? null : inv.getJSONObject("exploreTool");
        if (tools != null) {
            for (String k : tools.keySet()) {
                String id = tools.getJSONObject(k) == null ? null : tools.getJSONObject(k).getString("id");
                if (id != null) {
                    toolList.add(id);
                }
            }
        }
        record.put("exploreToolList", toolList);
        JSONArray zones = new JSONArray();
        JSONObject zone = new JSONObject(true);
        zone.put("index", 1000);
        zone.put("zoneId", "zone_" + level);
        zone.put("variation", new JSONArray());
        zones.add(zone);
        record.put("zones", zones);
        record.put("nodeMission", new JSONObject(true));
        // 官服 record 还有 legacyList（传承收藏品）与 scrapCounter（rogue_6 零件计数）
        JSONArray legacyList = new JSONArray();
        JSONObject gameOuter = game.getJSONObject("outer");
        JSONArray legacyArr = gameOuter == null ? null : gameOuter.getJSONArray("legacy");
        if (legacyArr != null) {
            for (int i = 0; i < legacyArr.size(); i++) {
                legacyList.add(legacyArr.get(i));
            }
        }
        record.put("legacyList", legacyList);
        JSONObject scrapCounter = new JSONObject(true);
        JSONObject module = cur.getJSONObject("module");
        JSONObject scrap = module == null ? null : module.getJSONObject("scrap");
        if (scrap != null && scrap.getJSONObject("scrapCounter") != null) {
            scrapCounter = scrap.getJSONObject("scrapCounter");
        }
        record.put("scrapCounter", scrapCounter);

        JSONObject result = new JSONObject(true);
        result.put("brief", brief);
        result.put("record", record);
        result.put("buffBankPut", 0);

        JSONObject content = new JSONObject(true);
        content.put("success", 0);
        content.put("result", result);
        // detailStr：官服是 base64(zlib(JSON))，JSON = {brief, troopChars, initial, zones}
        JSONObject detail = new JSONObject(true);
        detail.put("brief", brief);
        detail.put("troopChars", troopChars);
        JSONObject initial = new JSONObject(true);
        initial.put("mode", game.getString("mode"));
        initial.put("band", outer == null || outer.getString("band") == null ? "" : outer.getString("band"));
        initial.put("relics", relicList);
        initial.put("support", "");
        initial.put("supportMulti", new JSONArray());
        initial.put("recruitSet", "");
        JSONArray recruits = new JSONArray();
        for (int i = 0; i < troopChars.size(); i++) {
            JSONObject c = new JSONObject(true);
            c.put("charId", troopChars.getJSONObject(i).getString("charId"));
            c.put("type", troopChars.getJSONObject(i).getString("type"));
            recruits.add(c);
        }
        initial.put("recruits", recruits);
        detail.put("initial", initial);
        JSONArray detailZones = new JSONArray();
        for (int i = 0; i < zones.size(); i++) {
            JSONObject z = JSONObject.parseObject(zones.getJSONObject(i).toJSONString());
            z.put("pass", false);
            z.put("steps", new JSONArray());
            z.put("snapRecruits", new JSONArray());
            z.put("snapSettleCover", new JSONObject(true));
            z.put("module", new JSONObject(true));
            detailZones.add(z);
        }
        detail.put("zones", detailZones);
        content.put("detailStr", zlibBase64(detail.toJSONString()));
        content.put("popReport", false);
        return content;
    }

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
        pend.put("index", "e_9");
        pend.put("type", "GAME_SETTLE");
        pend.put("content", buildGiveUpSettleContent(cur, userData));
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
        // 已经推到 GAME_SETTLE（player.toEnding 有值）= 通关；否则看有没有打过 BOSS
        String endTag = player.getString("toEnding");
        boolean success = clearedBoss || (endTag != null && !endTag.isEmpty());

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
        if ("rogue_6".equals(theme) && success) {
            toEnding = resolveEndingRogue6(cur, toEnding);
            player.put("toEnding", toEnding);
        }
        brief.put("ending", success && toEnding != null && !toEnding.isEmpty() ? toEnding : "");
        brief.put("theme", theme);
        brief.put("mode", game.getString("mode"));
        brief.put("predefined", game.getString("predefined"));
        // 官服：brief.band = 开局变体收藏品（chooseInitialRelic 记在 game.outer.band）
        JSONObject endGameOuter = game.getJSONObject("outer");
        brief.put("band", endGameOuter == null ? ""
                : (endGameOuter.getString("band") == null ? "" : endGameOuter.getString("band")));
        brief.put("innerMission", false);
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
            // 探索分数有主题上限（官服 B服 2026-11-04 实测：rogue_2 停在 22400，
            // 且 pointOwned = 22400/200 = 112，正好对应 gpScoreRatio=200）
            int scoreCap = rlv2ScoreCap(theme == null ? "" : theme);
            int scoreNext = scoreFrom + score;
            if (scoreCap > 0 && scoreNext > scoreCap) {
                scoreNext = scoreCap;
            }
            buff.put("score", scoreNext);
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

        // 不往任何外部库写「私服」结算历史：私服数据无参考价值，
        // 官服数据由离线工具 _mem/ImportHistory.java 解析抓包后写入（source='official'）。
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
        // 官服 rogue_6 结算带 spOperatorInfo（特殊/助战干员的经验变化），无则空数组
        outerSummary.put("spOperatorInfo", new JSONArray());

        userDao.setUserData(account.getUid(), userData);

        // ── 补齐官服 gameSettle 里我们之前没给的字段（B服 009_01 对比得出）──
        // brief.endProperty 少 vision/chaos；brief 少 innerMissionProcess(Addition)
        if (brief.getJSONObject("endProperty") != null) {
            JSONObject ep = brief.getJSONObject("endProperty");
            if (!ep.containsKey("vision")) {
                ep.put("vision", 0);
            }
            if (!ep.containsKey("chaos")) {
                ep.put("chaos", new JSONArray());
            }
        }
        if (!brief.containsKey("innerMissionProcess")) {
            JSONArray imp0 = new JSONArray();
            imp0.add(0);
            imp0.add(1);
            brief.put("innerMissionProcess", imp0);
        }
        if (!brief.containsKey("innerMissionProcessAddition")) {
            brief.put("innerMissionProcessAddition", new JSONObject(true));
        }
        // record 少 cntRecruitProfession/troopChars/cntArrivedNodeType/relicList/capsuleList/
        // activeToolList/exploreToolList/zones/nodeMission —— 复用放弃结算里的构建逻辑补齐
        JSONObject settleExtra = buildGiveUpSettleContent(cur, userData);
        JSONObject extraRec = settleExtra.getJSONObject("result") == null ? null
                : settleExtra.getJSONObject("result").getJSONObject("record");
        if (extraRec != null) {
            for (String k : extraRec.keySet()) {
                if (!record.containsKey(k)) {
                    record.put(k, extraRec.get(k));
                }
            }
        }

        // game.score（官方结构：detail[7][2] / scoreFactor / score / buff / bp / gp / gpChange / accumulation）
        JSONObject scoreObj = new JSONObject(true);
        JSONArray detail = new JSONArray();
        for (int i = 0; i < 7; i++) {
            JSONArray pair = new JSONArray();
            pair.add(0);
            pair.add(0);
            detail.add(pair);
        }
        scoreObj.put("detail", detail);
        scoreObj.put("scoreFactor", bonusForHistory);
        scoreObj.put("score", score);
        scoreObj.put("buff", 1.0 + bonusForHistory);
        JSONObject bpObj = new JSONObject(true);
        bpObj.put("cnt", Math.max(0, bpTo - bpFrom));
        bpObj.put("from", bpFrom);
        bpObj.put("to", bpTo);
        scoreObj.put("bp", bpObj);
        scoreObj.put("gp", 0);
        JSONArray gpChange = new JSONArray();
        gpChange.add(scoreFrom);
        gpChange.add(scoreTo);
        scoreObj.put("gpChange", gpChange);
        JSONArray accumulation = new JSONArray();
        accumulation.add(scoreFrom);
        accumulation.add(scoreTo);
        scoreObj.put("accumulation", accumulation);

        // game.challenge（items/before/complete/tasks/isHighScore/score）
        JSONObject challengeObj = new JSONObject(true);
        challengeObj.put("items", new JSONArray());
        challengeObj.put("before", false);
        challengeObj.put("complete", false);
        challengeObj.put("isHighScore", false);
        challengeObj.put("score", score);
        JSONArray challengeTasks = new JSONArray();
        JSONObject detailsForTask = themeDetails(settleTheme);
        JSONObject taskData = detailsForTask == null ? null : detailsForTask.getJSONObject("taskData");
        JSONObject innerAddition = brief.getJSONObject("innerMissionProcessAddition");
        if (taskData != null) {
            for (String taskId : taskData.keySet()) {
                JSONObject def = taskData.getJSONObject(taskId);
                int target = def == null ? 1 : Math.max(1, def.getIntValue("cnt"));
                JSONObject t = new JSONObject(true);
                t.put("taskId", taskId);
                JSONArray pr = new JSONArray();
                pr.add(0);
                pr.add(target);
                t.put("progress", pr);
                challengeTasks.add(t);
                if (innerAddition != null && !innerAddition.containsKey(taskId)) {
                    JSONArray ip = new JSONArray();
                    ip.add(0);
                    ip.add(target);
                    innerAddition.put(taskId, ip);
                }
            }
        }
        challengeObj.put("tasks", challengeTasks);
        // record.cntRecruitProfession：按队伍里干员的职业统计（官服是 {TANK:1,CASTER:1,SNIPER:1}）
        JSONObject cntProf = record.getJSONObject("cntRecruitProfession");
        if (cntProf == null || cntProf.isEmpty()) {
            JSONObject cp = new JSONObject(true);
            JSONArray tc = record.getJSONArray("troopChars");
            if (tc != null) {
                for (int i = 0; i < tc.size(); i++) {
                    String cid = tc.getJSONObject(i).getString("charId");
                    String prof = charProfession(cid);
                    if (prof != null) {
                        cp.put(prof, cp.getIntValue(prof) + 1);
                    }
                }
            }
            record.put("cntRecruitProfession", cp);
        }

        JSONObject gameOut = new JSONObject(true);
        gameOut.put("brief", brief);
        gameOut.put("record", record);
        gameOut.put("score", scoreObj);
        gameOut.put("challenge", challengeObj);
        JSONObject result = delta(rl.getJSONObject("current"));
        JSONObject modForMedal = result.getJSONObject("playerDataDelta") == null ? null
                : result.getJSONObject("playerDataDelta").getJSONObject("modified");
        if (modForMedal != null) {
            // 官服 delta.modified.rlv2.outer.<theme> 带着局外结算（bp/buff/bank/collect/record）
            JSONObject rlv2Mod = modForMedal.getJSONObject("rlv2");
            if (rlv2Mod != null && themeOuter != null && settleTheme != null && !settleTheme.isEmpty()) {
                JSONObject outerDelta = new JSONObject(true);
                JSONObject oneTheme = new JSONObject(true);
                oneTheme.put("record", themeOuter.getJSONObject("record"));
                oneTheme.put("bp", themeOuter.getJSONObject("bp"));
                oneTheme.put("buff", themeOuter.getJSONObject("buff"));
                oneTheme.put("bank", themeOuter.getJSONObject("bank"));
                JSONObject collect = themeOuter.getJSONObject("collect");
                if (collect != null) {
                    oneTheme.put("collect", collect);
                }
                oneTheme.put("challenge", themeOuter.getJSONObject("challenge"));
                outerDelta.put(settleTheme, oneTheme);
                rlv2Mod.put("outer", outerDelta);
            }
            // 官服 delta.modified 还带 troop.chars（局内招募干员把经验带回本体）
            if (!modForMedal.containsKey("troop")) {
                JSONObject troopDelta = new JSONObject(true);
                JSONObject charsDelta = new JSONObject(true);
                JSONArray tcList = record.getJSONArray("troopChars");
                if (tcList != null) {
                    for (int i = 0; i < tcList.size(); i++) {
                        JSONObject tc = tcList.getJSONObject(i);
                        String iid = tc.getString("instId");
                        if (iid == null) {
                            continue;
                        }
                        JSONObject ch = new JSONObject(true);
                        ch.put("exp", 0);
                        ch.put("level", tc.getIntValue("level"));
                        charsDelta.put(iid, ch);
                    }
                }
                troopDelta.put("chars", charsDelta);
                modForMedal.put("troop", troopDelta);
            }
            // 官服 delta.modified 里还有 medal（结算面板要读）
            if (!modForMedal.containsKey("medal")) {
                JSONObject medal = new JSONObject(true);
                medal.put("medals", new JSONObject(true));
                modForMedal.put("medal", medal);
            }
        }
        result.put("game", gameOut);
        result.put("outer", outerSummary);
        return result;
    }

    /**
     * 初始化节点升级模块（萨米 rogue_4）。官服实测（B服 syncData）：
     *   module.nodeUpgrade = {"nodeTypeInfoMap":{
     *     "REST":{"tempUpgrade":"temp_update_3",
     *             "upgradeList":["node_update_1",...,"node_update_5"],
     *             "currUpgradeIndex":4}, ...}}
     * 开局 currUpgradeIndex 应为 -1（尚未升级）。
     */
    private static void seedNodeUpgradeModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        JSONObject nu = mods == null ? null : mods.getJSONObject("nodeUpgrade");
        JSONObject map = nu == null ? null : nu.getJSONObject("nodeUpgradeDataMap");
        if (map == null) {
            return;
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject infoMap = new JSONObject(true);
        for (String nodeType : map.keySet()) {
            JSONObject def = map.getJSONObject(nodeType);
            JSONArray perm = def.getJSONArray("permItemList");
            JSONArray upgradeList = new JSONArray();
            if (perm != null) {
                for (int i = 0; i < perm.size(); i++) {
                    upgradeList.add(perm.getJSONObject(i).getString("upgradeId"));
                }
            }
            // 临时升级：官服每局从 tempItemList 里随机挑一个（实测 temp_update_2/5/9 与 temp_update_3/6/9 两种组合）
            JSONArray tempList = def.getJSONArray("tempItemList");
            String tempId = null;
            if (tempList != null && !tempList.isEmpty()) {
                tempId = tempList.getJSONObject(RANDOM.nextInt(tempList.size())).getString("upgradeId");
            }
            JSONObject info = new JSONObject(true);
            info.put("tempUpgrade", tempId);
            info.put("upgradeList", upgradeList);
            info.put("currUpgradeIndex", -1);
            infoMap.put(nodeType, info);
        }
        JSONObject nodeUpgrade = new JSONObject(true);
        nodeUpgrade.put("nodeTypeInfoMap", infoMap);
        module.put("nodeUpgrade", nodeUpgrade);
    }

    /**
     * 初始化灯火值（水月 sanCheck）。官方抓包：createGame 后
     *   module.san = {"sanity":100}，每次战斗结算 -5，事件可能大幅扣（实测 -30）。
     */
    private static void seedSanModule(JSONObject cur, String theme) {
        JSONObject mods = themeModules(theme);
        if (mods == null || mods.getJSONObject("sanCheck") == null) {
            return;
        }
        JSONObject module = cur.getJSONObject("module");
        if (module == null) {
            module = new JSONObject(true);
            cur.put("module", module);
        }
        JSONObject san = new JSONObject(true);
        san.put("sanity", 100);
        module.put("san", san);
    }

    /** 扣灯火值（下限 0），并返回新值 */
    private static int decreaseSanity(JSONObject cur, int amount) {
        JSONObject module = cur.getJSONObject("module");
        JSONObject san = module == null ? null : module.getJSONObject("san");
        if (san == null) {
            return -1;
        }
        int v = Math.max(0, san.getIntValue("sanity") - amount);
        san.put("sanity", v);
        return v;
    }

    /**
     * 从选项描述里解析灯火（san）增减。官方表里没有数值字段，只有文案：
     *   「获得15灯火」「获得5灯火」「获得40灯火」「获得10灯火」
     *   「消耗10灯火，获得招募券」「消耗20灯火，获得5希望」
     * 返回正负增量；无相关文案返回 0。
     */
    private static int parseLampDelta(String raw) {
        if (raw == null) {
            return 0;
        }
        // 表里的文案带标记，例：消耗<@ro2.lost>20</>灯火 / 获得<@ro2.get>15</>灯火
        String text = raw.replaceAll("<[^>]*>", "");
        java.util.regex.Matcher gain = java.util.regex.Pattern
                .compile("获得\\s*(\\d+)\\s*灯火").matcher(text);
        if (gain.find()) {
            return Integer.parseInt(gain.group(1));
        }
        java.util.regex.Matcher cost = java.util.regex.Pattern
                .compile("消耗\\s*(\\d+)\\s*灯火").matcher(text);
        if (cost.find()) {
            return -Integer.parseInt(cost.group(1));
        }
        return 0;
    }

    /** 是否「获得启示」类选项（表里只有 choice_ro2_liar2_4 一处文案） */
    private static boolean grantsInspiration(String title, String desc) {
        String all = (title == null ? "" : title) + (desc == null ? "" : desc);
        return all.contains("获得启示") || all.contains("获得一个启示");
    }

    /**
     * 授予一个启示。灯火明亮（sanity >= 50）给「+」强化版（virtue_8..14），
     * 否则给基础版（virtue_1..7）。这个分档是推断：官方文案说「明亮的灯火会引导你获得强大的启示」。
     */
    private static String grantInspiration(JSONObject cur, String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject pool = details == null ? null : details.getJSONObject("squadBuffData");
        if (pool == null) {
            return null;
        }
        JSONObject buff = cur.getJSONObject("buff");
        if (buff == null) {
            buff = new JSONObject(true);
            buff.put("tmpHP", 0);
            buff.put("capsule", null);
            buff.put("squadBuff", new JSONArray());
            cur.put("buff", buff);
        }
        JSONArray owned = buff.getJSONArray("squadBuff");
        if (owned == null) {
            owned = new JSONArray();
            buff.put("squadBuff", owned);
        }
        java.util.Set<String> have = new java.util.HashSet<>();
        for (int i = 0; i < owned.size(); i++) {
            JSONObject one = owned.getJSONObject(i);
            if (one != null && one.getString("buffId") != null) {
                have.add(one.getString("buffId"));
            }
        }
        int sanity = currentSanity(cur);
        boolean plus = sanity < 0 || sanity >= 50;
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (String k : pool.keySet()) {
            if (have.contains(k)) {
                continue;
            }
            int idx = k.lastIndexOf('_');
            int num = 0;
            try {
                num = Integer.parseInt(k.substring(idx + 1));
            } catch (Exception ignored) {
            }
            boolean isPlus = num >= 8;
            if (isPlus == plus) {
                candidates.add(k);
            }
        }
        if (candidates.isEmpty()) {
            for (String k : pool.keySet()) {
                if (!have.contains(k)) {
                    candidates.add(k);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String pick = candidates.get(RANDOM.nextInt(candidates.size()));
        JSONObject entry = new JSONObject(true);
        entry.put("buffId", pick);
        owned.add(entry);
        ArknightsApplication.LOGGER.info("[rlv2] 获得启示 {}（灯火={}，共 {} 个）", pick, sanity, owned.size());
        return pick;
    }

    /** 当前灯火值（无该模块返回 -1） */
    private static int currentSanity(JSONObject cur) {
        JSONObject module = cur.getJSONObject("module");
        JSONObject san = module == null ? null : module.getJSONObject("san");
        return san == null ? -1 : san.getIntValue("sanity");

}    /**
     * 按灯火值选掷骰组：取 sanRanges 里第一个 sanity >= sanMax 的档位。
     * 表：modules.<theme>.sanCheck.sanRanges = [{sanMax,diceGroupId,description,...}]
     */
    private static String diceGroupBySanity(JSONObject cur, String theme) {
        int sanity = currentSanity(cur);
        if (sanity < 0) {
            return null;
        }
        JSONObject mods = themeModules(theme);
        JSONObject sanCheck = mods == null ? null : mods.getJSONObject("sanCheck");
        com.alibaba.fastjson.JSONArray ranges = sanCheck == null ? null : sanCheck.getJSONArray("sanRanges");
        if (ranges == null) {
            return null;
        }
        for (int i = 0; i < ranges.size(); i++) {
            JSONObject r = ranges.getJSONObject(i);
            if (sanity >= r.getIntValue("sanMax")) {
                return r.getString("diceGroupId");
            }
        }
        return null;
    }
}


