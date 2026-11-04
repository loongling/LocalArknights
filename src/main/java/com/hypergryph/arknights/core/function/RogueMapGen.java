package com.hypergryph.arknights.core.function;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 集成战略地图生成（种子驱动）。
 *
 * 抓包实测的布局语法（17 个 zone 样本归纳）：
 *   - 地图是「列」结构的 DAG：列数 4~6（第 1~3 层），深层层数会更多（最多 8）
 *   - 每列节点数 1~5（首列 1~2，末列 1~2 且带 zone_end）
 *   - 相邻列之间每个节点有 1~4 条出边，且下一列每个节点至少有一条入边
 *   - 节点 type 是位掩码：type = 1 << 节点类型下标（类型名单来自
 *     roguelike_topic_table.details.<theme>.nodeTypeData 的键顺序）
 *       战例 rogue_1：BATTLE_NORMAL=1, BATTLE_ELITE=2, BATTLE_BOSS=4, SHOP=8,
 *                     REST=16, INCIDENT=32, TREASURE=64, ENTERTAINMENT=128, UNKNOWN=256
 *
 * 同一个 seed 必定生成同一张图（seed 形如 "<20 位 base62>,<theme>,<modeGrade>"）。
 */
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

    public static int typeBit(String theme, String typeName) {
        List<String> names = nodeTypeNames(theme);
        int idx = names.indexOf(typeName);
        return idx < 0 ? 0 : (1 << idx);
    }

    private static JSONObject themeDetails(String theme) {
        if (ArknightsApplication.roguelikeTopicTable == null) {
            return null;
        }
        JSONObject details = ArknightsApplication.roguelikeTopicTable.getJSONObject("details");
        return details == null ? null : details.getJSONObject(theme);
    }

    private static String stageId(String theme, int level, int seq) {
        String prefix;
        switch (theme == null ? "" : theme) {
            case "rogue_2":
                prefix = "ro2";
                break;
            case "rogue_3":
                prefix = "ro3";
                break;
            case "rogue_4":
                prefix = "ro4";
                break;
            case "rogue_5":
                prefix = "ro5";
                break;
            case "rogue_6":
                prefix = "ro6";
                break;
            default:
                prefix = "ro1";
                break;
        }
        return prefix + "_n_" + level + "_" + seq;
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

    private static JSONObject officialZone(String theme, int level) {
        JSONObject themeZones = zonePool().getJSONObject(theme == null ? "" : theme);
        if (themeZones == null) {
            return null;
        }
        JSONObject z = themeZones.getJSONObject(String.valueOf(1000 + (level - 1)));
        return z == null ? null : JSONObject.parseObject(z.toJSONString());
    }

    private static int deepestOfficiaLevel(String theme) {
        JSONObject themeZones = zonePool().getJSONObject(theme == null ? "" : theme);
        if (themeZones == null) {
            return 0;
        }
        int max = 0;
        for (String k : themeZones.keySet()) {
            try {
                max = Math.max(max, Integer.parseInt(k) - 999);
            } catch (Exception ignored) {
            }
        }
        return max;
    }

    private static JSONObject mirrorZone(JSONObject src, int level) {
        JSONObject out = JSONObject.parseObject(src.toJSONString());
        JSONObject nodes = out.getJSONObject("nodes");
        if (nodes == null) {
            return out;
        }
        // 每列最大 y
        java.util.Map<Integer, Integer> maxY = new java.util.HashMap<>();
        for (String k : nodes.keySet()) {
            JSONObject pos = nodes.getJSONObject(k).getJSONObject("pos");
            int x = pos.getIntValue("x");
            maxY.merge(x, pos.getIntValue("y"), Math::max);
        }
        // 原坐标 → 新坐标
        java.util.Map<String, int[]> mapping = new java.util.HashMap<>();
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            int x = n.getJSONObject("pos").getIntValue("x");
            int y = n.getJSONObject("pos").getIntValue("y");
            int ny = maxY.getOrDefault(x, y) - y;
            mapping.put(x + "," + y, new int[]{x, ny});
            JSONObject pos = new JSONObject(true);
            pos.put("x", x);
            pos.put("y", ny);
            n.put("pos", pos);
            n.remove("fts");
        }
        for (String k : nodes.keySet()) {
            JSONObject n = nodes.getJSONObject(k);
            JSONArray next = n.getJSONArray("next");
            if (next == null) {
                continue;
            }
            for (int i = 0; i < next.size(); i++) {
                JSONObject e = next.getJSONObject(i);
                int[] np = mapping.get(e.getIntValue("x") + "," + e.getIntValue("y"));
                if (np != null) {
                    JSONObject pos = new JSONObject(true);
                    pos.put("x", np[0]);
                    pos.put("y", np[1]);
                    next.set(i, pos);
                }
            }
        }
        out.put("id", "zone_" + level);
        out.put("index", 1000 + (level - 1));
        return out;
    }

    public static JSONObject generateZone(String seed, String theme, int level) {
        // ① 该层有官方抓包拓扑 → 直接用（最高保真）
        JSONObject official = officialZone(theme, level);
        if (official != null) {
            return official;
        }
        // ② 没抓到这一层 → 用已抓最深层的镜像变体
        int deepest = deepestOfficiaLevel(theme);
        if (deepest > 0) {
            JSONObject base = officialZone(theme, deepest);
            if (base != null) {
                // 层数差为偶数用原图，奇数用镜像，避免每层都一样
                JSONObject zone = mirrorZone(base, level);
                if ((level - deepest) % 2 == 0) {
                    zone.put("id", "zone_" + level);
                    zone.put("index", 1000 + (level - 1));
                }
                return zone;
            }
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

        // 连边：每个节点连到下一列 1~2（首列可 2~3）个节点；保证下一列每个节点都有入边
        for (int c = 0; c < columns - 1; c++) {
            int curCount = shape[c].length;
            int nextCount = shape[c + 1].length;
            for (int y = 0; y < curCount; y++) {
                JSONObject node = nodes.getJSONObject(String.valueOf(c * 100 + y));
                JSONArray next = node.getJSONArray("next");
                int links = 1 + rnd.nextInt(c == 0 ? 3 : 2);
                for (int k = 0; k < links; k++) {
                    int targetY = rnd.nextInt(nextCount);
                    JSONObject pos = new JSONObject(true);
                    pos.put("x", c + 1);
                    pos.put("y", targetY);
                    boolean dup = false;
                    for (int i = 0; i < next.size(); i++) {
                        JSONObject e = next.getJSONObject(i);
                        if (e.getIntValue("x") == c + 1 && e.getIntValue("y") == targetY) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        next.add(pos);
                    }
                }
            }
            // 补齐：下一列每个节点至少一条入边
            for (int y = 0; y < nextCount; y++) {
                boolean has = false;
                for (int cy = 0; cy < curCount && !has; cy++) {
                    JSONArray next = nodes.getJSONObject(String.valueOf(c * 100 + cy)).getJSONArray("next");
                    for (int i = 0; i < next.size(); i++) {
                        if (next.getJSONObject(i).getIntValue("y") == y) {
                            has = true;
                            break;
                        }
                    }
                }
                if (!has) {
                    int pick = rnd.nextInt(curCount);
                    JSONArray next = nodes.getJSONObject(String.valueOf(c * 100 + pick)).getJSONArray("next");
                    JSONObject pos = new JSONObject(true);
                    pos.put("x", c + 1);
                    pos.put("y", y);
                    next.add(pos);
                }
            }
        }

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
                        node.put("stage", stageId(theme, level, c * 10 + y + 5));
                    } else {
                        type = names.isEmpty() ? 1 : (1 << rnd.nextInt(names.size()));
                    }
                }
                if (type == 0) {
                    type = 1;
                }
                node.put("type", type);
                if (lastColumn) {
                    node.put("zone_end", true);
                }
            }
        }

        JSONObject zone = new JSONObject(true);
        zone.put("id", "zone_" + level);
        zone.put("index", 1000 + (level - 1));
        zone.put("nodes", nodes);
        zone.put("variation", new JSONArray());
        zone.put("type", 0);
        return zone;
    }
}
