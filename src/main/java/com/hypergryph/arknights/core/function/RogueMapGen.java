package com.hypergryph.arknights.core.function;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RogueMapGen {
    
    private static final String SEED_ALPHABET = "0123456789AEIOUaeiou";

    private RogueMapGen() {
    }

    // 生成 20 位字符集随机串
    public static String randomSeedPart() {
        Random r = new Random();
        return randomSeedPart(r);
    }

    // 用指定随机源生成 20 位 seed（供地图生成复用同一序列）
    private static String randomSeedPart(Random r) {
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(SEED_ALPHABET.charAt(r.nextInt(SEED_ALPHABET.length())));
        }
        return sb.toString();
    }

    // 校验 seed 是否合法（20 位官方字符集 + 可选 ,theme,grade 后缀）
    public static boolean isValidSeed(String seed) {
        if (seed == null || seed.isEmpty()) {
            return false;
        }
        String part = seed.contains(",") ? seed.substring(0, seed.indexOf(',')) : seed;
        if (part.length() != 20) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            if (SEED_ALPHABET.indexOf(part.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    // 完整 seed：<20 位>,<theme>,<modeGrade>
    public static String makeSeed(String theme, int modeGrade) {
        return randomSeedPart() + "," + theme + "," + modeGrade;
    }

    public static List<String> nodeTypeNames(String theme) {
        List<String> out = new ArrayList<>();
        JSONObject details = themeDetails(theme);
        JSONObject nodeTypeData = details == null ? null : details.getJSONObject("nodeTypeData");
        if (nodeTypeData == null) {
            out.add("BATTLE_NORMAL");
            out.add("BATTLE_ELITE");
            out.add("BATTLE_BOSS");
            out.add("SHOP");
            out.add("REST");
            out.add("INCIDENT");
            out.add("TREASURE");
            return out;
        }
        out.addAll(nodeTypeData.keySet());
        return out;
    }

    /**
     * rogue_6 节点位值 —— 以客户端 GA（global-metadata.dat v29）中
     * Torappu.RoguelikeEventType 枚举常量为准（metadata 自定义 varint+zigzag
     * 默认值编码已解码，30 个单值与官服抓包表逐项吻合）：
     *   0 NONE, 1 BATTLE_NORMAL, 2 BATTLE_ELITE, 4 BATTLE_BOSS, 8 SHOP, 16 REST,
     *   32 INCIDENT, 64 TREASURE, 128 ENTERTAINMENT, 256 UNKNOWN, 512 WISH,
     *   1024 SACRIFICE, 2048 EXPEDITION, 4096 BATTLE_SHOP, 8192 PORTAL,
     *   16384 MISSION, 32768 STORY, 65536 STORY_HIDDEN, 131072 ALCHEMY,
     *   262144 DUEL, 524288 STASHED_RECRUIT, 1048576 SPECIAL_ZONE,
     *   2097152 SCRAP_SHOP, 4194304 DOOR, 8388608 FINAL, 16777216 EVACUATE,
     *   33554432 EMPLOY, 67108864 LIGHT, 134217728 BATTLE_SAVAGE, 268435456 EMPTY
     * 枚举内组合位（同样从 metadata 解出）：
     *   BATTLES = 0x08000007 (BN|BE|BB|BATTLE_SAVAGE)
     *   CHOICES = 0x019DEEF0
     *   EVENTS  = 0x17FDFEF8
     *   ALL     = 0x1FFDFEFF (全量 - ALCHEMY - UNKNOWN)
     */
    private static final java.util.Map<String, Integer> ROGUE6_BITS = new java.util.HashMap<>();
    static {
        ROGUE6_BITS.put("NONE", 0);
        ROGUE6_BITS.put("BATTLE_NORMAL", 1);
        ROGUE6_BITS.put("BATTLE_ELITE", 2);
        ROGUE6_BITS.put("BATTLE_BOSS", 4);
        ROGUE6_BITS.put("SHOP", 8);
        ROGUE6_BITS.put("REST", 16);
        ROGUE6_BITS.put("INCIDENT", 32);
        ROGUE6_BITS.put("TREASURE", 64);
        ROGUE6_BITS.put("ENTERTAINMENT", 128);
        ROGUE6_BITS.put("UNKNOWN", 256);
        ROGUE6_BITS.put("BATTLE", 256); // 旧名兼容，正式枚举名是 UNKNOWN
        ROGUE6_BITS.put("WISH", 512);
        ROGUE6_BITS.put("SACRIFICE", 1024);
        ROGUE6_BITS.put("EXPEDITION", 2048);
        ROGUE6_BITS.put("BATTLE_SHOP", 4096);
        ROGUE6_BITS.put("PORTAL", 8192);
        ROGUE6_BITS.put("MISSION", 16384);
        ROGUE6_BITS.put("STORY", 32768);
        ROGUE6_BITS.put("STORY_HIDDEN", 65536);
        ROGUE6_BITS.put("ALCHEMY", 131072);
        ROGUE6_BITS.put("DUEL", 262144);
        ROGUE6_BITS.put("STASHED_RECRUIT", 524288);
        ROGUE6_BITS.put("SPECIAL_ZONE", 1048576);
        ROGUE6_BITS.put("SCRAP_SHOP", 2097152);
        ROGUE6_BITS.put("DOOR", 4194304);
        ROGUE6_BITS.put("FINAL", 8388608);
        ROGUE6_BITS.put("EVACUATE", 16777216);
        ROGUE6_BITS.put("EMPLOY", 33554432);
        ROGUE6_BITS.put("LIGHT", 67108864);
        ROGUE6_BITS.put("BATTLE_SAVAGE", 134217728);
        ROGUE6_BITS.put("EMPTY", 268435456);
    }

    public static int typeBit(String theme, String typeName) {
        if ("rogue_6".equals(theme) && ROGUE6_BITS.containsKey(typeName)) {
            return ROGUE6_BITS.get(typeName);
        }
        List<String> names = nodeTypeNames(theme);
        int idx = names.indexOf(typeName);
        return idx < 0 ? 0 : (1 << idx);
    }

    /** 该主题的节点是否带 refresh（刷新）信息：目前只有萨米（rogue_4）观测到 */
    private static boolean refreshEnabled(String theme) {
        return "rogue_4".equals(theme);
    }

    private static JSONObject themeDetails(String theme) {
        if (ArknightsApplication.roguelikeTopicTable == null) {
            return null;
        }
        JSONObject details = ArknightsApplication.roguelikeTopicTable.getJSONObject("details");
        return details == null ? null : details.getJSONObject(theme);
    }

    private static String stagePrefix(String theme) {
        switch (theme == null ? "" : theme) {
            case "rogue_2":
                return "ro2";
            case "rogue_3":
                return "ro3";
            case "rogue_4":
                return "ro4";
            case "rogue_5":
                return "ro5";
            case "rogue_6":
                return "ro6";
            default:
                return "ro1";
        }
    }

    /**
     * 官方该主题、该层的战斗关卡池（ro6_n_1_2 / ro6_e_1_1 这种），按尾号排序。
     * 官方每层 n/e 关卡数（抓包统计）：rogue_6 = 4/6/7/9/9/2/2，rogue_1 = 5/5/8/8/8/2。
     * 之前直接拼 ro6_n_3_15 这种不存在的 id，客户端开局找不到关卡。
     */
    private static java.util.List<String> stagePool(String theme, int level, boolean elite) {
        java.util.List<String> out = new java.util.ArrayList<>();
        JSONObject details = themeDetails(theme);
        JSONObject stages = details == null ? null : details.getJSONObject("stages");
        if (stages == null) {
            return out;
        }
        String prefix = stagePrefix(theme) + (elite ? "_e_" : "_n_") + level + "_";
        java.util.TreeMap<Integer, String> bySeq = new java.util.TreeMap<>();
        for (String id : stages.keySet()) {
            if (!id.startsWith(prefix)) {
                continue;
            }
            String tail = id.substring(prefix.length());
            if (!tail.matches("\\d+")) {
                continue;
            }
            try {
                bySeq.put(Integer.parseInt(tail), id);
            } catch (Exception ignored) {
            }
        }
        out.addAll(bySeq.values());
        return out;
    }

    private static String stageId(String theme, int level, int seq) {
        return pickStage(theme, level, seq, false);
    }

    private static String pickStage(String theme, int level, int seq, boolean elite) {
        java.util.List<String> pool = stagePool(theme, level, elite);
        if (pool.isEmpty() && elite) {
            pool = stagePool(theme, level, false);
        }
        if (pool.isEmpty()) {
            return stagePrefix(theme) + (elite ? "_e_" : "_n_") + level + "_" + seq;
        }
        return pool.get(Math.floorMod(seq - 1, pool.size()));
    }

    private static JSONObject ZONE_POOL;

    private static synchronized JSONObject zonePool() {
        if (ZONE_POOL == null) {
            String path = System.getProperty("user.dir") + "/data/rlv2_zones.json";
            try {
                ZONE_POOL = JSONObject.parseObject(
                        new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                                java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[rlv2] 读取 rlv2_zones.json 失败: {}", e.toString());
                ZONE_POOL = new JSONObject(true);
            }
        }
        return ZONE_POOL;
    }

    /**
     * 该主题的**正常**区域模板，按序号排序：zone_1, zone_2, zone_3 ...
     * zone_portal_normal_* 是传送门子区域，不属于关卡进度序列，必须排除
     * （之前按 1000+level-1 直接取，导致第 4 关拿到传送门图、后面整体错位）
     */
    private static java.util.List<JSONObject> normalTemplates(String theme) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        JSONObject themeZones = zonePool().getJSONObject(theme == null ? "" : theme);
        if (themeZones == null) {
            return out;
        }
        java.util.TreeMap<Integer, JSONObject> byOrder = new java.util.TreeMap<>();
        for (String k : themeZones.keySet()) {
            JSONObject z = themeZones.getJSONObject(k);
            String id = z == null ? null : z.getString("id");
            if (id == null || !id.matches("zone_\\d+")) {
                continue;
            }
            int order;
            try {
                order = Integer.parseInt(id.substring("zone_".length()));
            } catch (Exception e) {
                continue;
            }
            byOrder.put(order, z);
        }
        for (java.util.Map.Entry<Integer, JSONObject> e : byOrder.entrySet()) {
            out.add(JSONObject.parseObject(e.getValue().toJSONString()));
        }
        return out;
    }

    private static JSONObject officialZone(String theme, int level) {
        java.util.List<JSONObject> list = normalTemplates(theme);
        if (level < 1 || level > list.size()) {
            return null;
        }
        JSONObject zone = list.get(level - 1);
        // 抓包模板个别主题（如 rogue_1）里存在斜边，客户端会画成斜线 → 有斜边就按四邻接重建
        sanitizeEdges(zone);
        return zone;
    }

    /** 模板里若有非四邻接的边，按四邻接重建（干净模板原样不动，保证与官服一致） */
    private static void sanitizeEdges(JSONObject zone) {
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n == null ? null : n.getJSONObject("pos");
            JSONArray next = n == null ? null : n.getJSONArray("next");
            if (pos == null || next == null) {
                continue;
            }
            for (int i = 0; i < next.size(); i++) {
                JSONObject t = next.getJSONObject(i);
                int d = Math.abs(t.getIntValue("x") - pos.getIntValue("x"))
                        + Math.abs(t.getIntValue("y") - pos.getIntValue("y"));
                if (d != 1) {
                    ArknightsApplication.LOGGER.info("[rlv2] 模板 {} 存在斜边，按四邻接重建", zone.getString("id"));
                    linkOrthogonal(nodes);
                    return;
                }
            }
        }
    }

    private static int deepestOfficiaLevel(String theme) {
        return normalTemplates(theme).size();
    }

    /** 官方该主题一共几个区域（rogue_1=6 / rogue_2=7 / rogue_3=7 / rogue_4=8 / rogue_5=8 / rogue_6=7） */
    public static int officialZoneCount(String theme) {
        JSONObject details = themeDetails(theme);
        JSONObject zones = details == null ? null : details.getJSONObject("zones");
        if (zones == null) {
            return 0;
        }
        int n = 0;
        for (String k : zones.keySet()) {
            if (k.matches("zone_\\d+")) {
                n++;
            }
        }
        return n;
    }

    /**
     * 官服的网格地图：`next` 只连**上下左右相邻**的格子（四邻接），不会斜着连。
     * 实测样本（rogue_3 首层 8 节点）：
     *   (0,0)→(1,0)；(0,1)→(1,1)；(1,0)→(0,0),(1,1)；(1,1)→(0,1),(1,0)
     * 这里统一按四邻接重建所有 next。
     */
    private static void linkOrthogonal(JSONObject nodes) {
        java.util.Set<String> alive = new java.util.HashSet<>();
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n == null ? null : n.getJSONObject("pos");
            if (pos != null) {
                alive.add(pos.getIntValue("x") + "," + pos.getIntValue("y"));
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            int x = pos.getIntValue("x");
            int y = pos.getIntValue("y");
            JSONArray next = new JSONArray();
            for (int[] d : dirs) {
                if (alive.contains((x + d[0]) + "," + (y + d[1]))) {
                    JSONObject p = new JSONObject(true);
                    p.put("x", x + d[0]);
                    p.put("y", y + d[1]);
                    next.add(p);
                }
            }
            n.put("next", next);
        }
    }

    /** EMPTY 节点位（官服 nodeTypeData 里 EMPTY 实测 268435456） */
    private static final int EMPTY_BIT = 268435456;

    /**
     * 官服抓包的区域是"打了一半"的快照：可能整个缺起点节点，或者 x=0 的节点是 EMPTY（空洞）。
     * 客户端把玩家放在空洞节点上就会报「非法位置」，地图整块不渲染。
     * 这里保证 x=0 至少有一个非 EMPTY 节点；没有就把节点整体右移一列，补一个真正的入口节点。
     */
    /**
     * 官服抓包的区域是"打了一半"的快照：常常整块缺起点节点，x=0 的节点还是 EMPTY 空洞。
     * 客户端（RL06DataUtil.ParseRoute）会按 next 逐列算行走路径，
     * 空洞/后向边/断链都会导致 "[rl06] _CalculateWalkPath fail, path find path"→ 无法移动。
     * 这里重建成合法的前向网格：
     *   ① 丢掉 EMPTY 空洞节点
     *   ② 剩余节点整体左移到 x=1 起（保持列内顺序）
     *   ③ next 只保留「正前方一列且目标存在」的边；空的话连到前一列最近节点
     *   ④ 补入口 index="0" pos=(0,0) type=BATTLE_NORMAL + stageId，next=第 1 列全部节点
     */
    private static JSONObject ensureLegalEntry(JSONObject zone, String theme, int level) {
        JSONObject nodes = zone == null ? null : zone.getJSONObject("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return zone;
        }
        // ① 去掉 EMPTY 空洞
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            if (n == null || n.getIntValue("type") == EMPTY_BIT) {
                drop.add(k);
            }
        }
        for (String k : drop) {
            nodes.remove(k);
        }
        // 抓包快照里的 fts 是「别人打到一半」的痕迹，新局必须清掉，否则地图一进来就有已访问节点
        for (String k : nodes.keySet()) {
            nodes.getJSONObject(k).remove("fts");
        }
        if (nodes.isEmpty()) {
            return zone;
        }
        // ② 整体左移到 x=1 起
        int minX = Integer.MAX_VALUE;
        for (String k : nodes.keySet()) {
            minX = Math.min(minX, nodes.getJSONObject(k).getJSONObject("pos").getIntValue("x"));
        }
        int shift = 1 - minX;
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            pos.put("x", pos.getIntValue("x") + shift);
        }
        // ③ 四邻接连边（官服只连上下左右，不斜连）
        linkOrthogonal(nodes);
        // ④ 入口节点
        java.util.List<JSONObject> colOne = null;
        for (String nk : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(nk);
            JSONObject pos = n.getJSONObject("pos");
            if (pos.getIntValue("x") == 1 && pos.getIntValue("y") == 0) {
                if (colOne == null) {
                    colOne = new java.util.ArrayList<>();
                }
                colOne.add(n);
            }
            if (pos.getIntValue("x") == 1) {
                n.put("visibility", 1);
            }
        }
        JSONArray entryNext = new JSONArray();
        JSONObject east = new JSONObject(true);
        east.put("x", 1);
        east.put("y", 0);
        // 入口在 (0,0)：只连正右方 (1,0)（若存在），符合四邻接
        if (nodes.getJSONObject("100") != null) {
            entryNext.add(east);
        }
        JSONObject entry = new JSONObject(true);
        entry.put("index", "0");
        JSONObject epos = new JSONObject(true);
        epos.put("x", 0);
        epos.put("y", 0);
        entry.put("pos", epos);
        entry.put("next", entryNext);
        entry.put("type", typeBit(theme, "BATTLE_NORMAL"));
        entry.put("visibility", 1);
        entry.put("stage", stageId(theme, level, 1));
        nodes.put("0", entry);
        linkOrthogonal(nodes);
        ArknightsApplication.LOGGER.info("[rlv2] 区域重建为四邻接网格（去空洞 {} 个）", drop.size());
        return zone;
    }

    private static JSONObject mirrorZone(JSONObject src, int level) {
        JSONObject out = JSONObject.parseObject(src.toJSONString());
        JSONObject nodes = out.getJSONObject("nodes");
        if (nodes == null) {
            return out;
        }
        // 原实现按「每列自己的 maxY」翻 y，同一列内的边还好，跨列的边因为两列 maxY 不同
        // 就被翻成了斜的（(0,0)→(1,1) 这种）。改成刚体反射：全图统一 maxY。
        int maxY = 0;
        for (String k : nodes.keySet()) {
            JSONObject pos = nodes.getJSONObject(k).getJSONObject("pos");
            maxY = Math.max(maxY, pos.getIntValue("y"));
        }
        JSONObject rebuilt = new JSONObject(true);
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONObject pos = n.getJSONObject("pos");
            int x = pos.getIntValue("x");
            int ny = maxY - pos.getIntValue("y");
            pos.put("y", ny);
            n.remove("fts");
            // index 必须和 pos 对得上（官服 index = x*100 + y），否则客户端按 index 找不到格子
            String index = String.valueOf(x * 100 + ny);
            n.put("index", index);
            rebuilt.put(index, n);
        }
        // 边跟着反射一起翻，之后按四邻接重建（顺带清掉指向不存在格子的悬空边）
        for (String k : rebuilt.keySet()) {
            JSONObject n = rebuilt.getJSONObject(k);
            JSONArray next = n.getJSONArray("next");
            if (next == null) {
                continue;
            }
            for (int i = 0; i < next.size(); i++) {
                JSONObject e = next.getJSONObject(i);
                e.put("y", maxY - e.getIntValue("y"));
            }
        }
        out.put("nodes", rebuilt);
        linkOrthogonal(rebuilt);
        out.put("id", "zone_" + level);
        out.put("index", 1000 + (level - 1));
        return out;
    }

    /**
     * 「局外 buff 没解锁就不该生成」的节点类型（数据来自 roguelike_topic_table 的
     * customizeData.<theme>.commonDevelopment.developments[*].rawDesc「探险中会出现“XX”节点」）：
     *   rogue_3_outbuff_5 平衡互惠   → SACRIFICE（失与得）
     *   rogue_3_outbuff_6 灵性经验   → PORTAL（树篱之途）
     *   rogue_3_outbuff_7 正相观察   → EXPEDITION（先行一步）
     *   rogue_4_outbuff_7 通解玄秘   → PORTAL（思维边界）
     *   rogue_4_outbuff_8 卷曲裂隙   → EXPEDITION（先行一步）
     *   rogue_4_outbuff_9 置换现实   → SACRIFICE（失与得）
     *   rogue_5_outbuff_7《先忧篇》  → EXPEDITION（先行一步）
     *   rogue_5_outbuff_8《论道篇》  → DUEL（狭路相逢）
     *   rogue_6_outbuff_7 育儿袋     → PORTAL（误入奇境）
     *   rogue_6_outbuff_9 眼睛       → EXPEDITION（先行一步）
     * rogue_1 / rogue_2 没有这套局外节点解锁（buffDesc 为空、developments 里也没有「会出现」描述）。
     */
    private static java.util.Map<String, java.util.Map<String, String>> gatedNodeTable() {
        java.util.Map<String, java.util.Map<String, String>> t = new java.util.HashMap<>();
        java.util.Map<String, String> r3 = new java.util.HashMap<>();
        r3.put("SACRIFICE", "rogue_3_outbuff_5");
        r3.put("PORTAL", "rogue_3_outbuff_6");
        r3.put("EXPEDITION", "rogue_3_outbuff_7");
        t.put("rogue_3", r3);
        java.util.Map<String, String> r4 = new java.util.HashMap<>();
        r4.put("PORTAL", "rogue_4_outbuff_7");
        r4.put("EXPEDITION", "rogue_4_outbuff_8");
        r4.put("SACRIFICE", "rogue_4_outbuff_9");
        t.put("rogue_4", r4);
        java.util.Map<String, String> r5 = new java.util.HashMap<>();
        r5.put("EXPEDITION", "rogue_5_outbuff_7");
        r5.put("DUEL", "rogue_5_outbuff_8");
        t.put("rogue_5", r5);
        java.util.Map<String, String> r6 = new java.util.HashMap<>();
        r6.put("PORTAL", "rogue_6_outbuff_7");
        r6.put("EXPEDITION", "rogue_6_outbuff_9");
        t.put("rogue_6", r6);
        return t;
    }

    /** 该主题下「已解锁」的门控节点类型名集合 */
    private static java.util.Set<String> allowedGatedTypes(String theme, java.util.Set<String> unlockedBuffs) {
        java.util.Map<String, String> gated = gatedNodeTable().get(theme == null ? "" : theme);
        java.util.Set<String> allowed = new java.util.HashSet<>();
        if (gated == null || gated.isEmpty()) {
            return allowed;
        }
        for (java.util.Map.Entry<String, String> e : gated.entrySet()) {
            if (unlockedBuffs != null && unlockedBuffs.contains(e.getValue())) {
                allowed.add(e.getKey());
            }
        }
        return allowed;
    }

    /**
     * 去掉「局外 buff 未解锁」的节点类型：
     * 模板是官服（已有进度）的快照，里面可能有 PORTAL / EXPEDITION 等；没解锁就改成普通作战
     * （有 stage 保留，没有就按层补一个真实关卡），并保持四邻接结构不变。
     */
    private static void stripGatedNodes(JSONObject zone, String theme, int level,
            java.util.Set<String> unlockedBuffs) {
        if (zone == null) {
            return;
        }
        java.util.Map<String, String> gated = gatedNodeTable().get(theme == null ? "" : theme);
        if (gated == null || gated.isEmpty()) {
            return;
        }
        java.util.Set<String> allowed = allowedGatedTypes(theme, unlockedBuffs);
        java.util.Set<Integer> gatedBits = new java.util.HashSet<>();
        for (String name : gated.keySet()) {
            if (allowed.contains(name)) {
                continue;
            }
            int bit = typeBit(theme, name);
            if (bit != 0) {
                gatedBits.add(bit);
            }
        }
        if (gatedBits.isEmpty()) {
            return;
        }
        JSONObject nodes = zone.getJSONObject("nodes");
        if (nodes == null) {
            return;
        }
        int battleBit = typeBit(theme, "BATTLE_NORMAL");
        int incidentBit = typeBit(theme, "INCIDENT");
        int fixed = 0;
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            if (n == null || !gatedBits.contains(n.getIntValue("type"))) {
                continue;
            }
            // 有 stage（先行一步会带关卡）→ 当普通作战；没有 → 当不期而遇
            String stage = n.getString("stage");
            if (stage == null || stage.isEmpty()) {
                n.put("type", incidentBit != 0 ? incidentBit : battleBit);
                n.remove("stage");
            } else {
                n.put("type", battleBit != 0 ? battleBit : 1);
            }
            fixed++;
        }
        if (fixed > 0) {
            ArknightsApplication.LOGGER.info("[rlv2] {} 第 {} 层：{} 个节点因局外 buff 未解锁被替换（已解锁={}）",
                    theme, level, fixed, allowed);
        }
    }

    public static JSONObject generateZone(String seed, String theme, int level) {
        return generateZone(seed, theme, level, null);
    }

    /**
     * @param unlockedBuffs 玩家局外成长里已解锁的 buff id（user.rlv2.outer.&lt;theme&gt;.buff.unlocked 的键）；
     *                      null = 不做门控
     */
    public static JSONObject generateZone(String seed, String theme, int level,
            java.util.Set<String> unlockedBuffs) {
        // 官方该主题总层数上限：超过就夹回去（别真的无限往下盖图）
        int zoneCount = officialZoneCount(theme);
        if (zoneCount > 0 && level > zoneCount) {
            ArknightsApplication.LOGGER.info("[rlv2] {} 只有 {} 层，请求第 {} 层 → 夹到第 {} 层",
                    theme, zoneCount, level, zoneCount);
            level = zoneCount;
        }
        // ① 该层有官方抓包拓扑 → 直接用（最高保真）
        JSONObject official = officialZone(theme, level);
        if (official != null) {
            stripGatedNodes(official, theme, level, unlockedBuffs);
            return official;
        }
        // ② 没抓到这一层 → 拿「最深的正常区域」做刚体镜像变体
        //    层间交替取最深/次深，避免千层一面
        java.util.List<JSONObject> bases = normalTemplates(theme);
        if (!bases.isEmpty()) {
            int offset = level - bases.size();
            int idx = bases.size() - 1 - (offset % 2 == 1 ? 1 : 0);
            idx = Math.max(0, Math.min(bases.size() - 1, idx));
            JSONObject mirrored = mirrorZone(bases.get(idx), level);
            stripGatedNodes(mirrored, theme, level, unlockedBuffs);
            return mirrored;
        }

        long state = (seed == null ? 12345L : seed.hashCode()) * 31L + level * 7919L;
        Random rnd = new Random(state);

        // 列数/规模按官服实测（rogue_1）：
        //   第1层 4 列 6~7 节点（每列 1~2）
        //   第2层 4 列 8~9 节点（每列 2~3）
        //   第3层 6 列 12~13 节点（每列 2~3）
        //   第4层起 6~8 列，节点随层数增长
        int columns;
        int minPerCol;
        int maxPerCol;
        if (level <= 1) {
            columns = 4;
            minPerCol = 1;
            maxPerCol = 2;
        } else if (level == 2) {
            columns = 4;
            minPerCol = 2;
            maxPerCol = 3;
        } else if (level == 3) {
            columns = 6;
            minPerCol = 2;
            maxPerCol = 3;
        } else {
            columns = Math.min(8, 5 + (level - 4));
            minPerCol = 2;
            maxPerCol = 3;
        }
        int[][] shape = new int[columns][];
        for (int c = 0; c < columns; c++) {
            int n;
            if (c == 0) {
                n = 1 + rnd.nextInt(Math.max(1, maxPerCol - 1));    // 首列 1~2
            } else if (c == columns - 1) {
                n = 1 + rnd.nextInt(2);                             // 末列 1~2（带终点）
            } else {
                n = minPerCol + rnd.nextInt(maxPerCol - minPerCol + 1);
            }
            shape[c] = new int[n];
        }

        List<String> names = nodeTypeNames(theme);
        int battleBit = typeBit(theme, "BATTLE_NORMAL");
        int eliteBit = typeBit(theme, "BATTLE_ELITE");
        int bossBit = typeBit(theme, "BATTLE_BOSS");
        int shopBit = typeBit(theme, "SHOP");
        int incidentBit = typeBit(theme, "INCIDENT");
        int restBit = typeBit(theme, "REST");

        JSONObject nodes = new JSONObject(true);
        // 每列每个节点的坐标与索引（index = x*100 + y，和官服一致）
        for (int c = 0; c < columns; c++) {
            for (int y = 0; y < shape[c].length; y++) {
                String index = String.valueOf(c * 100 + y);
                JSONObject node = new JSONObject(true);
                node.put("index", index);
                JSONObject pos = new JSONObject(true);
                pos.put("x", c);
                pos.put("y", y);
                node.put("pos", pos);
                node.put("next", new JSONArray());
                node.put("type", 0);
                if (c == 0) {
                    node.put("type", battleBit == 0 ? 1 : battleBit);
                    node.put("stage", stageId(theme, level, 1 + y));
                }
                nodes.put(index, node);
            }
        }

        // 连边：四邻接（官服网格只能走上下左右，不斜连）
        linkOrthogonal(nodes);

        // 分配类型：中间列随机（保证至少一场战斗和一个事件/商店），末列 zone_end
        for (int c = 1; c < columns; c++) {
            for (int y = 0; y < shape[c].length; y++) {
                JSONObject node = nodes.getJSONObject(String.valueOf(c * 100 + y));
                boolean lastColumn = c == columns - 1;
                int type;
                if (lastColumn) {
                    type = bossBit != 0 ? bossBit : (eliteBit != 0 ? eliteBit : 1);
                } else {
                    // 权重参考官服样本：战斗 ~35%、事件 ~25%、商店/安全角各 ~10%、紧急 ~10%、其它 ~10%
                    int roll = rnd.nextInt(100);
                    if (roll < 35) {
                        type = battleBit;
                        node.put("stage", stageId(theme, level, c * 10 + y));
                    } else if (roll < 60) {
                        type = incidentBit;
                    } else if (roll < 70) {
                        type = shopBit;
                    } else if (roll < 80) {
                        type = restBit;
                    } else if (roll < 90) {
                        type = eliteBit;
                        node.put("stage", pickStage(theme, level, c * 10 + y + 5, true));
                    } else {
                        java.util.List<String> pickable = new java.util.ArrayList<>(names);
                        java.util.Map<String, String> gatedHere = gatedNodeTable().get(theme == null ? "" : theme);
                        if (gatedHere != null && !gatedHere.isEmpty()) {
                            java.util.Set<String> allowed = allowedGatedTypes(theme, unlockedBuffs);
                            for (String gname : gatedHere.keySet()) {
                                if (!allowed.contains(gname)) {
                                    pickable.remove(gname);
                                }
                            }
                        }
                        type = pickable.isEmpty() ? 1 : typeBit(theme, pickable.get(rnd.nextInt(pickable.size())));
                    }
                }
                if (type == 0) {
                    type = 1;
                }
                node.put("type", type);
                if (lastColumn) {
                    node.put("zone_end", true);
                } else if (refreshEnabled(theme)) {
                    // 官服（萨米）中间节点带刷新信息：{"usedCount":0,"count":1,"cost":1}
                    JSONObject refresh = new JSONObject(true);
                    refresh.put("usedCount", 0);
                    refresh.put("count", 1);
                    refresh.put("cost", 1);
                    node.put("refresh", refresh);
                }
            }
        }

        // 一次性节点（官服 gameConst.onceNodeTypeList，如 rogue_2 = ["PORTAL","MISSION"]）：
        // 每层放 1 个到中间列，替换掉一个普通事件/商店节点，保证委托/传送门能被走到。
        JSONObject details = themeDetails(theme);
        JSONObject gameConst = details == null ? null : details.getJSONObject("gameConst");
        JSONArray onceList = gameConst == null ? null : gameConst.getJSONArray("onceNodeTypeList");
        if (onceList != null && !onceList.isEmpty() && columns >= 3) {
            java.util.Set<String> used = new java.util.HashSet<>();
            for (int oi = 0; oi < onceList.size(); oi++) {
                String onceName = onceList.getString(oi);
                int onceBit = typeBit(theme, onceName);
                if (onceBit == 0) {
                    continue;
                }
                // 每种 once 节点各占一个中间列节点（PORTAL=传送门、MISSION=地区委托）
                for (int attempt = 0; attempt < 8; attempt++) {
                    int col = 1 + rnd.nextInt(Math.max(1, columns - 2));
                    int ry = rnd.nextInt(shape[col].length);
                    String idx = String.valueOf(col * 100 + ry);
                    if (used.contains(idx)) {
                        continue;
                    }
                    JSONObject onceNode = nodes.getJSONObject(idx);
                    if (onceNode == null || onceNode.getBooleanValue("zone_end")) {
                        continue;
                    }
                    onceNode.put("type", onceBit);
                    onceNode.remove("stage");
                    used.add(idx);
                    break;
                }
            }
        }

        JSONObject zone = new JSONObject(true);
        zone.put("id", "zone_" + level);
        zone.put("index", 1000 + (level - 1));
        zone.put("nodes", nodes);
        zone.put("variation", new JSONArray());
        zone.put("type", 0);
        stripGatedNodes(zone, theme, level, unlockedBuffs);
        return zone;
    }

    /**
     * rogue_6（沉沦者的黑流树海）专用网格地图生成器（嵌在 MapGen 里的独立生成逻辑）。
     *
     * 依据（三源闭环，2026-11-11）：
     *  ① 官服抓包 zone_1000~1006 构成统计（37/30/23/8/5/2/2 份快照，
     *     扫描脚本 work/tools/_scratch/scan_r6_layouts.py）；
     *  ② PRTS「沉沦者的黑流树海」节点出场层限制（密道成对/羽瞰点每层1个/恶敌层无其它安全出口）；
     *  ③ GA metadata Torappu.RoguelikeEventType 位值（全部常量实证）。
     *
     * 官服地图形态：近满矩形随机去格（~30-40% 林间空地+缺格），四邻接双向边，
     * 入口 = x=0 的 EMPTY 格（vis 0），出口带 zone_end=true；
     * 未定型格 type=256（vis 1，靠近后由服务端 resolveFrontierNodes 定型）。
     */
    public static class R6MapGen {

        // —— 位值（GA metadata 实证）——
        public static final int BATTLE_NORMAL = 1;
        public static final int BATTLE_ELITE = 2;
        public static final int BATTLE_BOSS = 4;
        public static final int SHOP = 8;
        public static final int REST = 16;
        public static final int INCIDENT = 32;
        public static final int UNKNOWN = 256;             // 未定型占位
        public static final int WISH = 512;
        public static final int SACRIFICE = 1024;
        public static final int EXPEDITION = 2048;
        public static final int BATTLE_SHOP = 4096;
        public static final int PORTAL = 8192;
        public static final int STORY = 32768;
        public static final int STORY_HIDDEN = 65536;      // 命运所指
        public static final int ALCHEMY = 131072;
        public static final int DUEL = 262144;
        public static final int SCRAP_SHOP = 2097152;
        public static final int DOOR = 4194304;            // 曲折密道（成对传送）
        public static final int FINAL = 8388608;           // 险路尽头
        public static final int EVACUATE = 16777216;       // 险路小径
        public static final int EMPLOY = 33554432;
        public static final int LIGHT = 67108864;          // 羽瞰点
        public static final int BATTLE_SAVAGE = 134217728; // 险路恶敌
        public static final int EMPTY = 268435456;         // 林间空地/空洞

        /** 局外成长门控（roguelike_topic_table customizeData：未解锁不出现） */
        private static final String GATE_PORTAL = "rogue_6_outbuff_7";     // 育儿袋 → 误入奇境
        private static final String GATE_EXPEDITION = "rogue_6_outbuff_9"; // 眼睛 → 先行一步

        private R6MapGen() {
        }

        /** 每层配方（抓包统计，★=几乎必出）：cols×rows / 节点数 / UNK 数 / 空洞数 */
        private static final class Spec {
            final int cols;
            final int rows;
            final int minNodes;
            final int maxNodes;
            final int minUnk;
            final int maxUnk;
            final int minHoles;
            final int maxHoles;
            /** 固定特殊节点（每层必放的类型，DOOR 出现 2 次即成对） */
            final int[] fixed;
            /** 出口类型（FINAL / BATTLE_BOSS / BATTLE_SAVAGE=恶敌层无其它安全出口） */
            final int exitType;
            /** 出口数量 */
            final int exits;
            /** 险路小径 EVACUATE 个数 */
            final int evacuates;
            /** 其余格子的加权池 */
            final String[] pool;

            Spec(int cols, int rows, int minNodes, int maxNodes, int minUnk, int maxUnk,
                    int minHoles, int maxHoles, int[] fixed, int exitType, int exits,
                    int evacuates, String[] pool) {
                this.cols = cols;
                this.rows = rows;
                this.minNodes = minNodes;
                this.maxNodes = maxNodes;
                this.minUnk = minUnk;
                this.maxUnk = maxUnk;
                this.minHoles = minHoles;
                this.maxHoles = maxHoles;
                this.fixed = fixed;
                this.exitType = exitType;
                this.exits = exits;
                this.evacuates = evacuates;
                this.pool = pool;
            }
        }

        /*
         * 每层规格（官方快照实测）：
         *  L1 1000: 5×3   11~13 格  FIN×2(出口)   无 LIGHT/DOOR              空洞 3~4
         *  L2 1001: 6×5   16~20 格  FIN×2(出口)   LIGHT 1                    空洞 5~6
         *  L3 1002: 8×6   27~31 格  BB×1(出口)    DOOR 2 + LIGHT 1 + BSHOP 1 空洞 8~11
         *  L4 1003: 8×5   28~32 格  FIN×3(出口)   DOOR 2 + LIGHT 1 + REST 1  空洞 9~11
         *  L5 1004: 8×5   36~40 格  SAVG×1(出口)  DOOR 2 + LIGHT 1 + EVAC 1  空洞 12~16
         *  L6 1005: 10×5  38~40 格  SAVG×1(出口)  DOOR 2 + LIGHT 1 + PORT 1 + EMP 1 + BB 1 空洞 14~16
         *  L7 1006: 4×4    9~11 格  FIN×1(出口)   小型结局层                 空洞 4~5
         * 出场层限制（PRTS）：REST≠I；DUEL/PORT/DOOR≠I、II、VI；SACR/EMP≠I、VI；EXPD≠I、V、VI
         * （L6 抓包实测含 PORT/DUEL，以抓包为准保留低权重项）
         */
        private static Spec spec(int level) {
            switch (level) {
                case 2:
                    return new Spec(6, 5, 16, 20, 2, 6, 4, 5,
                            new int[]{LIGHT}, FINAL, 2, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL",
                                "INCIDENT", "INCIDENT", "BATTLE_SHOP", "SCRAP_SHOP",
                                "WISH", "SACRIFICE", "REST", "BATTLE_BOSS"});
                case 3:
                    return new Spec(8, 6, 27, 31, 5, 9, 7, 10,
                            new int[]{DOOR, LIGHT, BATTLE_SHOP}, BATTLE_BOSS, 1, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_ELITE",
                                "INCIDENT", "INCIDENT", "WISH", "SACRIFICE", "EXPEDITION",
                                "PORTAL", "DUEL", "SCRAP_SHOP", "EMPLOY", "ALCHEMY", "REST"});
                case 4:
                    return new Spec(8, 5, 28, 32, 5, 10, 8, 10,
                            new int[]{DOOR, LIGHT, REST}, FINAL, 3, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "INCIDENT", "INCIDENT",
                                "WISH", "SACRIFICE", "EXPEDITION", "PORTAL",
                                "SCRAP_SHOP", "EMPLOY", "DUEL"});
                case 5:
                    return new Spec(8, 5, 36, 40, 8, 16, 11, 15,
                            new int[]{DOOR, LIGHT, EVACUATE}, BATTLE_SAVAGE, 1, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_ELITE",
                                "BATTLE_BOSS", "INCIDENT", "INCIDENT", "WISH", "WISH",
                                "SACRIFICE", "PORTAL", "DUEL", "EMPLOY", "SCRAP_SHOP",
                                "BATTLE_SHOP"});
                case 6:
                    return new Spec(10, 5, 38, 40, 8, 13, 13, 15,
                            new int[]{DOOR, LIGHT, PORTAL, EMPLOY, BATTLE_BOSS},
                            BATTLE_SAVAGE, 1, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_ELITE", "INCIDENT",
                                "DUEL", "WISH"});
                case 7:
                    return new Spec(4, 4, 9, 11, 2, 3, 3, 4,
                            new int[0], FINAL, 1, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL",
                                "INCIDENT"});
                case 1:
                default:
                    return new Spec(5, 3, 11, 13, 1, 3, 2, 3,
                            new int[0], FINAL, 2, 0,
                            new String[]{"BATTLE_NORMAL", "BATTLE_NORMAL", "BATTLE_NORMAL",
                                "BATTLE_ELITE", "INCIDENT", "INCIDENT",
                                "BATTLE_SHOP", "WISH"});
            }
        }

        /**
         * 生成 rogue_6 第 level 层。
         *
         * @param seed          局种子（只影响随机序列，保证同一局可复现）
         * @param level         层号 1..7
         * @param unlockedBuffs 玩家局外成长已解锁 buff id；null = 全部按已解锁处理
         */
        public static JSONObject generateZone(String seed, int level,
                java.util.Set<String> unlockedBuffs) {
            return generateZone(seed, level, unlockedBuffs, java.util.Collections.emptySet());
        }

        /**
         * 带结局持有物感知的生成（PRTS 层级判定）：
         *   第Ⅴ层持【沙盘α+β】→ 追加 1 个「命运所指」（结局2 的「修正结果」选项）；
         *   第Ⅵ层恒追加 1 个「命运所指」（结局3/4 的「险路恶敌」选项）。
         *
         * @param finalRelics 本局已获 final 系收藏品 id（rlv2.rogue6FinalRelics）
         */
        public static JSONObject generateZone(String seed, int level,
                java.util.Set<String> unlockedBuffs, java.util.Set<String> finalRelics) {
            int lv = Math.max(1, Math.min(7, level));
            Spec sp = spec(lv);
            boolean sandbox = finalRelics != null
                    && finalRelics.contains("rogue_6_relic_final_1")
                    && finalRelics.contains("rogue_6_relic_final_2");
            if (lv == 5 && sandbox) {
                int[] old = sp.fixed;
                int[] grown = new int[old.length + 1];
                System.arraycopy(old, 0, grown, 0, old.length);
                grown[old.length] = STORY_HIDDEN;   // 第Ⅴ层「命运所指」（结局2）
                sp = new Spec(sp.cols, sp.rows, sp.minNodes, sp.maxNodes, sp.minUnk, sp.maxUnk,
                        sp.minHoles, sp.maxHoles, grown, sp.exitType, sp.exits, sp.evacuates,
                        sp.pool);
            } else if (lv == 6) {
                int[] old = sp.fixed;
                int[] grown = new int[old.length + 1];
                System.arraycopy(old, 0, grown, 0, old.length);
                grown[old.length] = STORY_HIDDEN;   // 第Ⅵ层「命运所指」（结局3/4）
                sp = new Spec(sp.cols, sp.rows, sp.minNodes, sp.maxNodes, sp.minUnk, sp.maxUnk,
                        sp.minHoles, sp.maxHoles, grown, sp.exitType, sp.exits, sp.evacuates,
                        sp.pool);
            }
            java.util.Random rnd = new java.util.Random(
                    (seed == null ? 0L : seed.hashCode()) * 97L + lv * 7919L);

            // ── 1. 官方形态：近满矩形随机去格（保持连通） ──
            boolean[][] cell = new boolean[sp.rows][sp.cols];
            for (int y = 0; y < sp.rows; y++) {
                for (int x = 0; x < sp.cols; x++) {
                    cell[y][x] = true;
                }
            }
            int entryY = sp.rows / 2;
            int target = sp.minNodes + rnd.nextInt(sp.maxNodes - sp.minNodes + 1);
            target = Math.min(target, sp.cols * sp.rows - sp.exits - sp.evacuates - 1);
            int toRemove = sp.cols * sp.rows - target;
            java.util.List<int[]> cand = new java.util.ArrayList<>();
            for (int y = 0; y < sp.rows; y++) {
                for (int x = 0; x < sp.cols; x++) {
                    if (x == 0 && y == entryY) {
                        continue;   // 入口永不删
                    }
                    cand.add(new int[]{x, y});
                }
            }
            // 倾向先删角落/边缘（邻居少的格子），官方 blob 边缘参差
            cand.sort(java.util.Comparator
                    .comparingInt((int[] c) -> neighborCount(cell, c[0], c[1])));
            java.util.Collections.shuffle(cand, rnd);
            for (int[] c : cand) {
                if (toRemove <= 0) {
                    break;
                }
                cell[c[1]][c[0]] = false;
                if (!connected(cell, 0, entryY)) {
                    cell[c[1]][c[0]] = true;   // 会断图 → 恢复
                } else {
                    toRemove--;
                }
            }

            java.util.List<int[]> cells = new java.util.ArrayList<>();
            for (int y = 0; y < sp.rows; y++) {
                for (int x = 0; x < sp.cols; x++) {
                    if (cell[y][x]) {
                        cells.add(new int[]{x, y});
                    }
                }
            }

            // ── 2. 选出口：最右列（不够再向左找）；EVACUATE 放最左列 ──
            java.util.Set<String> used = new java.util.LinkedHashSet<>();
            used.add(key(0, entryY));

            java.util.List<int[]> exits = new java.util.ArrayList<>();
            for (int x = sp.cols - 1; x >= 0 && exits.size() < sp.exits; x--) {
                java.util.List<Integer> colYs = new java.util.ArrayList<>();
                for (int y = 0; y < sp.rows; y++) {
                    if (cell[y][x] && !used.contains(key(x, y))) {
                        colYs.add(y);
                    }
                }
                java.util.Collections.shuffle(colYs, rnd);
                while (!colYs.isEmpty() && exits.size() < sp.exits) {
                    int y = colYs.remove(0);
                    exits.add(new int[]{x, y});
                    used.add(key(x, y));
                }
            }

            java.util.List<int[]> evacCells = new java.util.ArrayList<>();
            for (int i = 0; i < sp.evacuates; i++) {
                java.util.List<Integer> colYs = new java.util.ArrayList<>();
                for (int y = 0; y < sp.rows; y++) {
                    if (y != entryY && cell[y][0] && !used.contains(key(0, y))) {
                        colYs.add(y);
                    }
                }
                java.util.Collections.shuffle(colYs, rnd);
                if (!colYs.isEmpty()) {
                    int y = colYs.get(0);
                    evacCells.add(new int[]{0, y});
                    used.add(key(0, y));
                }
            }

            // ── 3. 剩余自由格 ──
            java.util.List<int[]> free = new java.util.ArrayList<>();
            for (int[] c : cells) {
                if (!used.contains(key(c[0], c[1]))) {
                    free.add(c);
                }
            }
            java.util.Collections.shuffle(free, rnd);

            // 空洞 / UNK 配额（不够就夹到剩余格数）
            int holes = sp.minHoles + rnd.nextInt(sp.maxHoles - sp.minHoles + 1);
            int unk = sp.minUnk + rnd.nextInt(sp.maxUnk - sp.minUnk + 1);
            int budget = free.size();
            unk = Math.min(unk, Math.max(0, budget / 3));
            holes = Math.max(0, Math.min(holes, budget - unk));

            // ── 4. 组装节点 ──
            JSONObject nodes = new JSONObject(true);
            nodes.put(key(0, entryY), node(0, entryY, EMPTY, 0));
            for (int[] c : exits) {
                nodes.put(key(c[0], c[1]), node(c[0], c[1], sp.exitType, 0, true));
            }
            for (int[] c : evacCells) {
                nodes.put(key(c[0], c[1]), node(c[0], c[1], EVACUATE, 0, true));
            }

            // 固定特殊节点（DOOR 成对：第一个随便放，第二个取相距最远的自由格）
            java.util.ArrayDeque<Integer> fx = new java.util.ArrayDeque<>();
            for (int t : sp.fixed) {
                if (t == PORTAL && !gateOpen(unlockedBuffs, GATE_PORTAL)) {
                    continue;
                }
                fx.add(t);
            }
            while (!fx.isEmpty() && !free.isEmpty()) {
                int t = fx.poll();
                int[] spot = free.remove(0);
                nodes.put(key(spot[0], spot[1]), node(spot[0], spot[1], t, 0));
                if (t == DOOR && !free.isEmpty()) {
                    int[] far = free.get(0);
                    int bestD = -1;
                    for (int[] c : free) {
                        int d = Math.abs(c[0] - spot[0]) + Math.abs(c[1] - spot[1]);
                        if (d > bestD) {
                            bestD = d;
                            far = c;
                        }
                    }
                    free.remove(far);
                    nodes.put(key(far[0], far[1]), node(far[0], far[1], DOOR, 0));
                }
            }

            // 空洞（vis 0）与未定型（vis 1，靠近后由 resolveFrontierNodes 定型）
            for (int i = 0; i < holes && !free.isEmpty(); i++) {
                int[] c = free.remove(0);
                nodes.put(key(c[0], c[1]), node(c[0], c[1], EMPTY, 0));
            }
            for (int i = 0; i < unk && !free.isEmpty(); i++) {
                int[] c = free.remove(0);
                nodes.put(key(c[0], c[1]), node(c[0], c[1], UNKNOWN, 1));
            }
            // 稀有类容量（官服每层 0~1，商店类 0~2），先统计已放的固定节点
            java.util.Map<Integer, Integer> placed = new java.util.HashMap<>();
            for (String k : nodes.keySet()) {
                placed.merge(nodes.getJSONObject(k).getIntValue("type"), 1, Integer::sum);
            }
            // 剩余按池加权填充
            for (int[] c : free) {
                String name = sp.pool[rnd.nextInt(sp.pool.length)];
                if ("PORTAL".equals(name) && !gateOpen(unlockedBuffs, GATE_PORTAL)) {
                    name = "INCIDENT";
                }
                if ("EXPEDITION".equals(name) && !gateOpen(unlockedBuffs, GATE_EXPEDITION)) {
                    name = "INCIDENT";
                }
                int bit = bitOf(name);
                int cap = capOf(bit);
                for (int t = 0; t < 4 && placed.getOrDefault(bit, 0) >= cap; t++) {
                    name = "INCIDENT";
                    bit = INCIDENT;
                }
                placed.merge(bit, 1, Integer::sum);
                nodes.put(key(c[0], c[1]), node(c[0], c[1], bit, 0));
            }

            // ── 5. 四邻接双向连边（官服 next 就是全部相邻格） ──
            linkOrthogonal(nodes);

            JSONObject zone = new JSONObject(true);
            zone.put("id", "zone_" + lv);
            zone.put("index", 1000 + lv - 1);
            zone.put("nodes", nodes);
            zone.put("variation", new JSONArray());
            zone.put("type", 0);
            return zone;
        }

        // ───────────────────────── helpers ─────────────────────────

        private static boolean gateOpen(java.util.Set<String> unlockedBuffs, String buff) {
            return unlockedBuffs == null || unlockedBuffs.contains(buff);
        }

        private static int bitOf(String name) {
            if ("BATTLE_NORMAL".equals(name)) {
                return BATTLE_NORMAL;
            }
            if ("BATTLE_ELITE".equals(name)) {
                return BATTLE_ELITE;
            }
            if ("BATTLE_BOSS".equals(name)) {
                return BATTLE_BOSS;
            }
            if ("SHOP".equals(name)) {
                return SHOP;
            }
            if ("REST".equals(name)) {
                return REST;
            }
            if ("WISH".equals(name)) {
                return WISH;
            }
            if ("SACRIFICE".equals(name)) {
                return SACRIFICE;
            }
            if ("EXPEDITION".equals(name)) {
                return EXPEDITION;
            }
            if ("BATTLE_SHOP".equals(name)) {
                return BATTLE_SHOP;
            }
            if ("PORTAL".equals(name)) {
                return PORTAL;
            }
            if ("STORY".equals(name)) {
                return STORY;
            }
            if ("STORY_HIDDEN".equals(name)) {
                return STORY_HIDDEN;
            }
            if ("ALCHEMY".equals(name)) {
                return ALCHEMY;
            }
            if ("DUEL".equals(name)) {
                return DUEL;
            }
            if ("SCRAP_SHOP".equals(name)) {
                return SCRAP_SHOP;
            }
            if ("EMPLOY".equals(name)) {
                return EMPLOY;
            }
            return INCIDENT;
        }

        /** 单层稀有类上限（官服统计） */
        private static int capOf(int bit) {
            if (bit == REST || bit == WISH || bit == SACRIFICE || bit == EMPLOY
                    || bit == DUEL || bit == EXPEDITION || bit == PORTAL) {
                return 1;
            }
            if (bit == BATTLE_SHOP || bit == SCRAP_SHOP || bit == ALCHEMY) {
                return 2;
            }
            return Integer.MAX_VALUE;
        }

        private static String key(int x, int y) {
            return String.valueOf(x * 100 + y);
        }

        private static JSONObject node(int x, int y, int type, int visibility) {
            return node(x, y, type, visibility, false);
        }

        private static JSONObject node(int x, int y, int type, int visibility, boolean zoneEnd) {
            JSONObject n = new JSONObject(true);
            n.put("index", key(x, y));
            JSONObject pos = new JSONObject(true);
            pos.put("x", x);
            pos.put("y", y);
            n.put("pos", pos);
            n.put("next", new JSONArray());
            n.put("type", type);
            n.put("visibility", visibility);
            if (zoneEnd) {
                n.put("zone_end", true);
            }
            return n;
        }

        /** 四邻接双向连边 */
        private static void linkOrthogonal(JSONObject nodes) {
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (String k : nodes.keySet()) {
                JSONObject n = nodes.getJSONObject(k);
                JSONObject pos = n.getJSONObject("pos");
                int x = pos.getIntValue("x");
                int y = pos.getIntValue("y");
                JSONArray next = new JSONArray();
                for (int[] d : dirs) {
                    String tk = key(x + d[0], y + d[1]);
                    if (nodes.containsKey(tk)) {
                        JSONObject p = new JSONObject(true);
                        p.put("x", x + d[0]);
                        p.put("y", y + d[1]);
                        next.add(p);
                    }
                }
                n.put("next", next);
            }
        }

        private static int neighborCount(boolean[][] cell, int x, int y) {
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int c = 0;
            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx >= 0 && nx < cell[0].length && ny >= 0 && ny < cell.length && cell[ny][nx]) {
                    c++;
                }
            }
            return c;
        }

        /** 从 (sx,sy) BFS，判断现存格是否全部连通 */
        private static boolean connected(boolean[][] cell, int sx, int sy) {
            int rows = cell.length;
            int cols = cell[0].length;
            int total = 0;
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (cell[y][x]) {
                        total++;
                    }
                }
            }
            if (!cell[sy][sx]) {
                return total == 0;
            }
            boolean[][] seen = new boolean[rows][cols];
            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
            q.add(new int[]{sx, sy});
            seen[sy][sx] = true;
            int got = 0;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!q.isEmpty()) {
                int[] c = q.poll();
                got++;
                for (int[] d : dirs) {
                    int nx = c[0] + d[0];
                    int ny = c[1] + d[1];
                    if (nx >= 0 && nx < cols && ny >= 0 && ny < rows
                            && cell[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true;
                        q.add(new int[]{nx, ny});
                    }
                }
            }
            return got == total;
        }
    }
}
