package com.hypergryph.arknights.core.function;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务（mission）辅助工具。
 *
 * 状态约定（与官服一致）：
 *   进行中：{"state":2, "progress":[{"target":1,"value":0}]}
 *   可领取：{"state":2, "progress":[{"target":1,"value":1}]}   value >= target 时客户端出「领取」按钮
 *   已领取：{"state":3}                                        没有 progress 字段
 *
 * 解锁条件（严格按 mission_table.json）：
 *   1. preMissionIds   —— 列出的前置任务必须都已领取（state == 3）
 *   2. unlockCondition —— 目前只有 "StageCondition"，unlockParam = [minState, stageId]
 *                        要求 dungeon.stages[stageId].state >= minState（2 = 已通关）
 *   3. 两者都为 null   —— 无额外条件
 */
public final class MissionHelper {

    private static JSONObject TABLE;

    private MissionHelper() {
    }

    // ────────────────────────────────────────────────────────────
    // mission_table
    // ────────────────────────────────────────────────────────────

    public static synchronized JSONObject table() {
        if (TABLE == null) {
            String path = System.getProperty("user.dir") + "/data/excel/mission_table.json";
            try {
                String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                TABLE = JSONObject.parseObject(content);
            } catch (IOException e) {
                ArknightsApplication.LOGGER.error("[MissionHelper] 读取 {} 失败: {}", path, e.toString());
                TABLE = new JSONObject();
            }
        }
        return TABLE;
    }

    public static JSONObject entry(String missionId) {
        if (missionId == null) {
            return null;
        }
        JSONObject missions = table().getJSONObject("missions");
        return missions == null ? null : missions.getJSONObject(missionId);
    }

    public static String typeOf(String missionId) {
        JSONObject e = entry(missionId);
        return e == null ? null : e.getString("type");
    }

    // ────────────────────────────────────────────────────────────
    // 玩家数据
    // ────────────────────────────────────────────────────────────

    private static JSONObject missionRoot(JSONObject user) {
        return user.getJSONObject("mission");
    }

    private static JSONObject missionsOfType(JSONObject user, String type, boolean create) {
        JSONObject missions = missionRoot(user).getJSONObject("missions");
        JSONObject bucket = missions.getJSONObject(type);
        if (bucket == null && create) {
            bucket = new JSONObject(true);
            missions.put(type, bucket);
        }
        return bucket;
    }

    /** 读某条任务在玩家数据里的节点（可能为 null） */
    public static JSONObject nodeOf(JSONObject user, String missionId) {
        String type = typeOf(missionId);
        if (type == null) {
            return null;
        }
        JSONObject bucket = missionsOfType(user, type, false);
        return bucket == null ? null : bucket.getJSONObject(missionId);
    }

    /** 读某条任务的状态：不存在算 0 */
    public static int stateOf(JSONObject user, String missionId) {
        JSONObject node = nodeOf(user, missionId);
        return node == null ? 0 : node.getIntValue("state");
    }

    private static int stageStateOf(JSONObject user, String stageId) {
        JSONObject dungeon = user.getJSONObject("dungeon");
        if (dungeon == null) {
            return 0;
        }
        JSONObject stages = dungeon.getJSONObject("stages");
        if (stages == null || !stages.containsKey(stageId)) {
            return 0;
        }
        return stages.getJSONObject(stageId).getIntValue("state");
    }

    // ────────────────────────────────────────────────────────────
    // 解锁条件判定
    // ────────────────────────────────────────────────────────────

    /** 严格按 mission_table 判断：该任务的解锁条件是否已满足（不看是否已领取） */
    public static boolean isUnlocked(JSONObject user, String missionId) {
        JSONObject e = entry(missionId);
        if (e == null) {
            return false;
        }

        // 1) 前置任务必须都已领取
        JSONArray pre = e.getJSONArray("preMissionIds");
        if (pre != null) {
            for (int i = 0; i < pre.size(); i++) {
                if (stateOf(user, pre.getString(i)) != 3) {
                    return false;
                }
            }
        }

        // 2) 关卡条件
        String cond = e.getString("unlockCondition");
        if ("StageCondition".equals(cond)) {
            JSONArray p = e.getJSONArray("unlockParam");
            if (p == null || p.size() < 2) {
                return true;
            }
            int minState = p.getIntValue(0);
            String stageId = p.getString(1);
            if (stageStateOf(user, stageId) < minState) {
                return false;
            }
        }
        // 未识别的 unlockCondition 不阻塞；null 表示无条件
        return true;
    }

    // ────────────────────────────────────────────────────────────
    // 状态写入
    // ────────────────────────────────────────────────────────────

    public static JSONObject setProgress(JSONObject user, String missionId, int target, int value) {
        String type = typeOf(missionId);
        if (type == null) {
            type = "MAIN";
        }

        JSONObject node = new JSONObject(true);
        node.put("state", 2);

        JSONObject p = new JSONObject(true);
        p.put("target", target);
        p.put("value", value);
        JSONArray progress = new JSONArray();
        progress.add(p);
        node.put("progress", progress);

        missionsOfType(user, type, true).put(missionId, node);
        markGroup(user, missionId, 0);
        return node;
    }

    /** 可领取：state=2 + progress=[{target:1, value:1}] */
    public static JSONObject setClaimable(JSONObject user, String missionId) {
        return setProgress(user, missionId, 1, 1);
    }

    /** 进行中：state=2 + progress=[{target:1, value:0}] */
    public static JSONObject setInProgress(JSONObject user, String missionId) {
        return setProgress(user, missionId, 1, 0);
    }

    /**
     * 已领取：state=3。
     *
     * 【重要】必须保留 progress，并把 value 拉满到 target。
     * 官服已领取任务是 {"state":3,"progress":[{"target":1,"value":1}]}，
     * 如果写成 {"state":3}（没有 progress），客户端 MissionModel._DealWithMissionData
     * 读 progress[0] 就会 ArgumentOutOfRangeException，整个任务页 OnStart 崩溃。
     */
    public static void setClaimed(JSONObject user, String missionId) {
        String type = typeOf(missionId);
        if (type == null) {
            type = "MAIN";
        }
        JSONObject node = new JSONObject(true);
        node.put("state", 3);

        JSONObject old = nodeOf(user, missionId);
        JSONArray oldProgress = old == null ? null : old.getJSONArray("progress");

        JSONArray progress = new JSONArray();
        if (oldProgress != null && !oldProgress.isEmpty()) {
            for (int i = 0; i < oldProgress.size(); i++) {
                JSONObject p = oldProgress.getJSONObject(i);
                JSONObject np = new JSONObject(true);
                np.put("target", p.getIntValue("target"));
                np.put("value", p.getIntValue("target"));   // 拉满
                progress.add(np);
            }
        } else {
            JSONObject p = new JSONObject(true);
            p.put("target", 1);
            p.put("value", 1);
            progress.add(p);
        }
        node.put("progress", progress);

        missionsOfType(user, type, true).put(missionId, node);
        markGroup(user, missionId, 1);
    }

    /**
     * 修复历史数据：state==3 但没有 progress 的任务补上 [{target:1,value:1}]。
     * （早期的 setClaimed 会把 progress 删掉，导致任务页越界崩溃）
     *
     * @return 被修复的任务数
     */
    public static int repairClaimedMissions(JSONObject user) {
        JSONObject root = user.getJSONObject("mission");
        if (root == null) {
            return 0;
        }
        JSONObject missions = root.getJSONObject("missions");
        if (missions == null) {
            return 0;
        }
        int fixed = 0;
        for (String type : missions.keySet()) {
            JSONObject bucket = missions.getJSONObject(type);
            if (bucket == null) {
                continue;
            }
            for (String id : new ArrayList<>(bucket.keySet())) {
                JSONObject node = bucket.getJSONObject(id);
                if (node == null || node.getIntValue("state") != 3) {
                    continue;
                }
                JSONArray progress = node.getJSONArray("progress");
                if (progress != null && !progress.isEmpty()) {
                    continue;
                }
                JSONObject p = new JSONObject(true);
                p.put("target", 1);
                p.put("value", 1);
                JSONArray arr = new JSONArray();
                arr.add(p);
                node.put("progress", arr);
                fixed++;
            }
        }
        if (fixed > 0) {
            ArknightsApplication.LOGGER.info("[MissionHelper] 修复 {} 条已领取任务缺失的 progress", fixed);
        }
        return fixed;
    }

    /** 写任意 state（1=锁定，2=进行中），progress 固定 [{target:1,value:0}] */
    public static void setState(JSONObject user, String missionId, int state) {
        String type = typeOf(missionId);
        if (type == null) {
            type = "SUB";
        }
        JSONObject node = new JSONObject(true);
        node.put("state", state);
        JSONObject p = new JSONObject(true);
        p.put("target", 1);
        p.put("value", 0);
        JSONArray progress = new JSONArray();
        progress.add(p);
        node.put("progress", progress);
        missionsOfType(user, type, true).put(missionId, node);
    }

    /**
     * 初始化「新手任务」SUB 组（官服行为）。
     *
     * 官服在领取 main_1 时会一次性创建 sub_g_1 的 6 条任务：
     *   sub_1        → {"state":2,"progress":[{"target":1,"value":0}]}   激活第一环
     *   sub_2..sub_6 → {"state":1,...}                                   锁定
     * 并且 missionGroups["sub_g_1"] = 0
     *
     * 不创建的话，任务页渲染 SUB 列表时会 list[index] 越界：
     *   ArgumentOutOfRangeException at MissionModel._DealWithMissionData
     *   → MissionPage.OnStart() 崩溃 → 卡在任务界面
     */
    public static void ensureSubMissionGroup(JSONObject user, String groupId) {
        JSONObject missions = table().getJSONObject("missions");
        if (missions == null || groupId == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String id : missions.keySet()) {
            JSONObject e = missions.getJSONObject(id);
            if (e != null && "SUB".equals(e.getString("type")) && groupId.equals(e.getString("missionGroup"))) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        ids.sort((a, b) -> {
            int na = suffixOf(a);
            int nb = suffixOf(b);
            return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
        });

        JSONObject bucket = missionsOfType(user, "SUB", true);
        boolean firstActivated = false;
        for (String id : ids) {
            JSONObject existing = bucket.getJSONObject(id);
            if (existing != null) {
                if (existing.getIntValue("state") >= 2) {
                    firstActivated = true;
                }
                continue;
            }
            if (!firstActivated) {
                setState(user, id, 2);
                firstActivated = true;
            } else {
                setState(user, id, 1);
            }
        }
        markGroup(user, ids.get(0), 0);
        ArknightsApplication.LOGGER.info("[MissionHelper] 初始化新手任务组 {}: {}", groupId, ids);
    }

    /** SUB 桶为空时，补上最初的 sub_g_1（登录自愈和领取时都会调用） */
    public static void ensureNewbieSubMissions(JSONObject user) {
        JSONObject sub = missionsOfType(user, "SUB", false);
        if (sub != null && !sub.isEmpty()) {
            return;
        }
        ensureSubMissionGroup(user, "sub_g_1");
    }

    // ────────────────────────────────────────────────────────────
    // SUB 组逐组解锁（按 mission_table 的 unlockCondition）
    // ────────────────────────────────────────────────────────────

    private static Map<String, List<String>> SUB_GROUPS;

    /** 所有 SUB 组：group -> 按数字后缀排序的 missionId 列表（懒加载） */
    private static synchronized Map<String, List<String>> subGroups() {
        if (SUB_GROUPS == null) {
            Map<String, List<String>> map = new HashMap<>();
            JSONObject missions = table().getJSONObject("missions");
            if (missions != null) {
                for (String id : missions.keySet()) {
                    JSONObject e = missions.getJSONObject(id);
                    if (e == null || !"SUB".equals(e.getString("type"))) {
                        continue;
                    }
                    String g = e.getString("missionGroup");
                    if (g == null) {
                        g = "(none)";
                    }
                    map.computeIfAbsent(g, k -> new ArrayList<>()).add(id);
                }
                for (List<String> list : map.values()) {
                    list.sort((a, b) -> {
                        int na = suffixOf(a);
                        int nb = suffixOf(b);
                        return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
                    });
                }
            }
            SUB_GROUPS = map;
            ArknightsApplication.LOGGER.info("[MissionHelper] SUB 组共 {} 个", map.size());
        }
        return SUB_GROUPS;
    }

    /**
     * 按 mission_table 的解锁条件逐组解锁「新手任务」SUB。
     *
     * 规则（以组内第一条任务为准）：
     *   - 该组已有任意一条 → 跳过（幂等）
     *   - 第一条任务既无 unlockCondition 又无 preMissionIds → 不自动创建
     *     （无门槛的组由 haveSubMissionToUnlock / 具体玩法触发，
     *       否则首次登录就会一次冒出几十个组，任务页会爆）
     *   - 有门槛且条件已满足 → 按官服形态创建整组
     *
     * 例：
     *   sub_g_1  无门槛             → 由领取 main_1 时创建
     *   sub_g_2  head=sub_7 需 main_00-04 通关 → 打完 0-4 自动出现
     *   sub_g_9  head=sub_34 HasChar 无门槛    → 不自动创建
     *
     * @return 本次新建的组名
     */
    public static JSONArray unlockSubGroups(JSONObject user) {
        JSONArray created = new JSONArray();

        for (Map.Entry<String, List<String>> groupEntry : subGroups().entrySet()) {
            String group = groupEntry.getKey();
            List<String> ids = groupEntry.getValue();
            if (ids == null || ids.isEmpty()) {
                continue;
            }

            String headId = ids.get(0);

            // 组已存在 → 跳过（每轮重新取，因为上一轮可能刚创建了 SUB 桶）
            JSONObject bucket = missionsOfType(user, "SUB", false);
            if (bucket != null && bucket.containsKey(headId)) {
                continue;
            }

            JSONObject head = entry(headId);
            if (head == null) {
                continue;
            }

            String cond = head.getString("unlockCondition");
            JSONArray pre = head.getJSONArray("preMissionIds");
            boolean gated = (cond != null) || (pre != null && !pre.isEmpty());
            if (!gated) {
                continue;   // 无门槛组交给别处触发
            }
            if (!isUnlocked(user, headId)) {
                continue;
            }

            ensureSubMissionGroup(user, group);
            refreshMission(user, headId);
            created.add(group);
            ArknightsApplication.LOGGER.info("[MissionHelper] 解锁 SUB 组 {}（{} {}）",
                    group, cond, head.getJSONArray("unlockParam"));
        }
        return created;
    }

    /** 统一入口：补基础 SUB + 按条件解锁新组（登录/领取/战斗中都会调用，幂等） */
    public static void syncSubMissions(JSONObject user) {
        ensureNewbieSubMissions(user);
        unlockSubGroups(user);
    }

    private static void markGroup(JSONObject user, String missionId, int value) {
        JSONObject root = missionRoot(user);
        JSONObject groups = root.getJSONObject("missionGroups");
        if (groups == null) {
            groups = new JSONObject(true);
            root.put("missionGroups", groups);
        }
        JSONObject e = entry(missionId);
        String group = (e == null || e.getString("missionGroup") == null) ? missionId : e.getString("missionGroup");
        groups.put(group, value);
    }

    // ────────────────────────────────────────────────────────────
    // nextMission（带解锁条件检查）
    // ────────────────────────────────────────────────────────────

    /**
     * 下一个任务（同前缀、数字后缀 +1），并且必须满足 mission_table 的解锁条件。
     * 不满足 → 返回 null（绝不越过条件硬激活）。
     */
    public static String nextMissionId(JSONObject user, String missionId) {
        if (missionId == null) {
            return null;
        }
        JSONObject e = entry(missionId);
        String type = e == null ? null : e.getString("type");

        // 1) 表里若显式给了 nextMissionIds，优先用
        if (e != null) {
            JSONArray explicit = e.getJSONArray("nextMissionIds");
            if (explicit != null && !explicit.isEmpty()) {
                String id = explicit.getString(0);
                return isUnlocked(user, id) ? id : null;
            }
        }

        // 2) 数字后缀 +1
        int us = missionId.lastIndexOf('_');
        if (us < 0) {
            return null;
        }
        String prefix = missionId.substring(0, us + 1);
        int n;
        try {
            n = Integer.parseInt(missionId.substring(us + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        String candidate = prefix + (n + 1);
        JSONObject ce = entry(candidate);
        if (ce == null) {
            return null;
        }
        if (type != null && !type.equals(ce.getString("type"))) {
            return null;
        }
        if (stateOf(user, candidate) == 3) {
            return null;   // 已领取
        }
        return isUnlocked(user, candidate) ? candidate : null;
    }

    /**
     * 扫描同类型任务，返回第一个「未领取 且 解锁条件已满足」的任务。
     * 教程需要一个可领取任务时可用来兜底。
     */
    public static String firstUnlockedUnclaimed(JSONObject user, String type) {
        JSONObject missions = table().getJSONObject("missions");
        if (missions == null) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String id : missions.keySet()) {
            JSONObject e = missions.getJSONObject(id);
            if (e != null && type.equals(e.getString("type"))) {
                ids.add(id);
            }
        }
        ids.sort((a, b) -> {
            int na = suffixOf(a);
            int nb = suffixOf(b);
            return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
        });
        for (String id : ids) {
            if (stateOf(user, id) == 3) {
                continue;
            }
            if (isUnlocked(user, id)) {
                return id;
            }
        }
        return null;
    }

    private static int suffixOf(String id) {
        int us = id.lastIndexOf('_');
        if (us < 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(id.substring(us + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    // ────────────────────────────────────────────────────────────
    // 奖励
    // ────────────────────────────────────────────────────────────

    /** 发放奖励并返回 items（客户端响应里的 items），同时写入 inventory / status */
    public static JSONArray grantRewards(JSONObject user, String missionId) {
        JSONArray items = new JSONArray();
        JSONObject e = entry(missionId);
        if (e == null) {
            return items;
        }
        JSONArray rewards = e.getJSONArray("rewards");
        if (rewards == null) {
            return items;
        }

        JSONObject inventory = user.getJSONObject("inventory");
        JSONObject status = user.getJSONObject("status");

        for (int i = 0; i < rewards.size(); i++) {
            JSONObject r = rewards.getJSONObject(i);
            String type = r.getString("type");
            String id = r.getString("id");
            int count = r.getIntValue("count");

            JSONObject item = new JSONObject(true);
            item.put("type", type);
            item.put("id", id);
            item.put("count", count);
            items.add(item);

            if ("GOLD".equals(type)) {
                status.put("gold", status.getLongValue("gold") + count);
            } else if ("DIAMOND".equals(type) || "DIAMOND_SHD".equals(type)) {
                status.put("diamondShard", status.getIntValue("diamondShard") + count);
            } else {
                if (inventory == null) {
                    inventory = new JSONObject(true);
                    user.put("inventory", inventory);
                }
                inventory.put(id, inventory.getIntValue(id) + count);
            }
        }
        return items;
    }

    // ────────────────────────────────────────────────────────────
    // 组合操作
    // ────────────────────────────────────────────────────────────

    /** 领取并激活下一个（受解锁条件约束）。返回 {"claimed","next","items"} */
    public static JSONObject claimAndAdvance(JSONObject user, String missionId) {
        setClaimed(user, missionId);
        JSONArray items = grantRewards(user, missionId);

        String next = nextMissionId(user, missionId);
        if (next != null) {
            // 官服行为：下一个任务变「进行中」，progress=[{target:1, value:0}]
            setInProgress(user, next);
            // 若该任务的达成条件此刻已满足（比如关卡早就打过），立即推进到可领取
            refreshMission(user, next);
        }

        // 领取主线任务时按官服初始化「新手任务」SUB（haveSubMissionToUnlock）
        JSONObject claimedEntry = entry(missionId);
        if (claimedEntry != null && claimedEntry.getBooleanValue("haveSubMissionToUnlock")) {
            syncSubMissions(user);
        }

        JSONObject out = new JSONObject(true);
        out.put("claimed", missionId);
        out.put("next", next);
        out.put("items", items);
        return out;
    }

    // ────────────────────────────────────────────────────────────
    // 任务进度求值（按 template/param，和 mission_table 一致）
    // ────────────────────────────────────────────────────────────

    /**
     * 按 mission_table 的 template/param 计算该任务当前应有的 value。
     * 返回 -1 表示这个模板不支持自动求值（保持原样）。
     *
     *   CompleteMainStage  param = [_, stageId, count]        → 该关 completeTimes，封顶 count
     *   CompleteAnyStage   param = [_, "a^b^c", count]        → 这些关 completeTimes 之和，封顶 count
     *   HasRoom            param = [_, _, level, roomType]    → 有 roomType 且 level >= level 则 1
     */
    public static int evaluateValue(JSONObject user, JSONObject entry) {
        if (entry == null) {
            return -1;
        }
        String template = entry.getString("template");
        JSONArray param = entry.getJSONArray("param");
        if (template == null || param == null) {
            return -1;
        }

        if ("CompleteMainStage".equals(template) && param.size() >= 3) {
            String stageId = param.getString(1);
            int target = intOrDefault(param.getString(2), 1);
            return Math.min(stageCompleteTimes(user, stageId), target);
        }

        if ("CompleteAnyStage".equals(template) && param.size() >= 3) {
            String joined = param.getString(1);
            int target = intOrDefault(param.getString(2), 1);
            int sum = 0;
            if (joined != null) {
                for (String s : joined.split("\\^")) {
                    sum += stageCompleteTimes(user, s);
                }
            }
            return Math.min(sum, target);
        }

        if ("HasRoom".equals(template) && param.size() >= 4) {
            int needLevel = intOrDefault(param.getString(2), 1);
            String roomType = param.getString(3);
            JSONObject slots = user.getJSONObject("building").getJSONObject("roomSlots");
            for (String slotId : slots.keySet()) {
                JSONObject slot = slots.getJSONObject(slotId);
                if (roomType.equals(slot.getString("roomId"))
                        && slot.getIntValue("level") >= needLevel
                        && slot.getIntValue("state") == 2) {
                    return 1;
                }
            }
            return 0;
        }

        return -1;
    }

    /**
     * 重新求值并写入某个任务的进度（只在「该任务处于 state==2 且未达成」时才动它）。
     *
     * @return true 表示求值后已达到可领取
     */
    public static boolean refreshMission(JSONObject user, String missionId) {
        JSONObject node = nodeOf(user, missionId);
        if (node == null || node.getIntValue("state") != 2) {
            return false;
        }
        JSONObject e = entry(missionId);
        int value = evaluateValue(user, e);
        if (value < 0) {
            return false;   // 模板不支持，保持原样
        }

        JSONArray progress = node.getJSONArray("progress");
        if (progress == null || progress.isEmpty()) {
            return false;
        }
        JSONObject p = progress.getJSONObject(0);
        int target = p.getIntValue("target");
        p.put("value", Math.min(value, target));
        return p.getIntValue("value") >= target;
    }

    private static int stageCompleteTimes(JSONObject user, String stageId) {
        JSONObject dungeon = user.getJSONObject("dungeon");
        if (dungeon == null) {
            return 0;
        }
        JSONObject stages = dungeon.getJSONObject("stages");
        if (stages == null || !stages.containsKey(stageId)) {
            return 0;
        }
        return stages.getJSONObject(stageId).getIntValue("completeTimes");
    }

    private static int intOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 自愈：按当前玩家数据重新求值所有「进行中(state==2)」的任务，
     * 让任务进度始终跟真实进度一致（官服就是这么做的）。
     *
     * 只遍历玩家自己的任务桶，最多几十条，开销可忽略。
     *
     * @return 本次被推进到「可领取」的任务 id 列表
     */
    public static JSONArray refreshActiveMissions(JSONObject user) {
        JSONArray became = new JSONArray();
        JSONObject root = user.getJSONObject("mission");
        if (root == null) {
            return became;
        }
        JSONObject missions = root.getJSONObject("missions");
        if (missions == null) {
            return became;
        }

        // 老存档可能没有 SUB 新手任务，导致任务页 OnStart 越界崩溃 —— 登录时补齐
        // 并按 unlockCondition 逐组解锁（sub_g_2 需 main_00-04 通关 之类）
        repairClaimedMissions(user);
        syncSubMissions(user);

        for (String type : missions.keySet()) {
            JSONObject bucket = missions.getJSONObject(type);
            if (bucket == null) {
                continue;
            }
            // 先收集，避免边遍历边改
            List<String> ids = new ArrayList<>(bucket.keySet());
            for (String id : ids) {
                JSONObject node = bucket.getJSONObject(id);
                if (node == null || node.getIntValue("state") != 2) {
                    continue;
                }
                if (!isUnlocked(user, id)) {
                    continue;
                }
                if (refreshMission(user, id)) {
                    became.add(id);
                }
            }
        }
        return became;
    }

    /**
     * 通关某关时自动推进对应任务（查表驱动，不写死 main_1）。
     *
     * 支持模板：
     *   CompleteMainStage  param = [_, stageId, count]
     *   CompleteAnyStage   param = [_, "stageA^stageB^...", count]
     *
     * value = min(completeTimes, count)，达到 count 即可领取。
     * 跳过：已领取(state==3)、解锁条件未满足。
     *
     * @return 被推进到「可领取」的任务 id 列表
     */
    public static JSONArray onStageCleared(JSONObject user, String stageId, int completeTimes) {
        JSONArray touched = new JSONArray();
        JSONObject missions = table().getJSONObject("missions");
        if (missions == null || stageId == null) {
            return touched;
        }

        for (String missionId : missions.keySet()) {
            JSONObject e = missions.getJSONObject(missionId);
            if (e == null) {
                continue;
            }
            String template = e.getString("template");
            JSONArray param = e.getJSONArray("param");
            if (param == null || param.size() < 3) {
                continue;
            }

            boolean hit = false;
            if ("CompleteMainStage".equals(template)) {
                hit = stageId.equals(param.getString(1));
            } else if ("CompleteAnyStage".equals(template)) {
                String joined = param.getString(1);
                if (joined != null) {
                    for (String s : joined.split("\\^")) {
                        if (stageId.equals(s)) {
                            hit = true;
                            break;
                        }
                    }
                }
            } else {
                continue;
            }
            if (!hit) {
                continue;
            }

            if (stateOf(user, missionId) == 3) {
                continue;   // 已领取
            }
            if (!isUnlocked(user, missionId)) {
                continue;   // 解锁条件未满足
            }

            int target;
            try {
                target = Integer.parseInt(param.getString(2));
            } catch (Exception ex) {
                target = 1;
            }
            int value = Math.min(completeTimes, target);
            setProgress(user, missionId, target, value);
            if (value >= target) {
                touched.add(missionId);
                ArknightsApplication.LOGGER.info("[MissionHelper] {} 达成（{} {}/{}）", missionId, stageId, value, target);
            }
        }

        // 关卡推进后可能满足某些 SUB 组的 unlockCondition（StageCondition）→ 逐组解锁
        syncSubMissions(user);

        return touched;
    }
}
