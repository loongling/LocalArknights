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

public final class MissionHelper {

    private static JSONObject TABLE;

    private MissionHelper() {
    }

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
        JSONObject e = missions == null ? null : missions.getJSONObject(missionId);
        if (e != null) {
            return e;
        }
        // 新服开启活动 V3 的见习任务（newguide_*）不在 mission_table，而是在 open_server_table 里
        return openServerMission(missionId);
    }

    private static JSONObject OPEN_SERVER_V3_MISSIONS;

    private static synchronized JSONObject openServerMissions() {
        if (OPEN_SERVER_V3_MISSIONS == null) {
            String path = System.getProperty("user.dir") + "/data/excel/open_server_table.json";
            try {
                JSONObject table = JSONObject.parseObject(
                        new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                                java.nio.charset.StandardCharsets.UTF_8));
                JSONObject data = table.getJSONObject("openServerV3Data");
                OPEN_SERVER_V3_MISSIONS = data == null ? new JSONObject(true) : data.getJSONObject("missionData");
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[MissionHelper] 读取 open_server_table.json 失败", e);
            }
            if (OPEN_SERVER_V3_MISSIONS == null) {
                OPEN_SERVER_V3_MISSIONS = new JSONObject(true);
            }
        }
        return OPEN_SERVER_V3_MISSIONS;
    }

    private static JSONObject openServerMission(String missionId) {
        JSONObject m = openServerMissions().getJSONObject(missionId);
        if (m == null) {
            return null;
        }
        if (m.getString("type") == null) {
            m.put("type", "GUIDE_V3");
        }
        return m;
    }

    public static String typeOf(String missionId) {
        JSONObject e = entry(missionId);
        return e == null ? null : e.getString("type");
    }


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

    public static JSONObject nodeOf(JSONObject user, String missionId) {
        String type = typeOf(missionId);
        if (type == null) {
            return null;
        }
        JSONObject bucket = missionsOfType(user, type, false);
        return bucket == null ? null : bucket.getJSONObject(missionId);
    }

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

    public static JSONObject setClaimable(JSONObject user, String missionId) {
        return setProgress(user, missionId, 1, 1);
    }

    public static JSONObject setInProgress(JSONObject user, String missionId) {
        return setProgress(user, missionId, 1, 0);
    }

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

    public static void ensureNewbieSubMissions(JSONObject user) {
        JSONObject sub = missionsOfType(user, "SUB", false);
        if (sub != null && !sub.isEmpty()) {
            return;
        }
        ensureSubMissionGroup(user, "sub_g_1");
    }

    private static Map<String, List<String>> SUB_GROUPS;

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
            grantOneReward(r, user, items);
        }
        return items;
    }

    private static void grantOneReward(JSONObject r, JSONObject user, JSONArray items) {
        String type = r.getString("type");
        String id = r.getString("id");
        int count = r.getIntValue("count");
        JSONObject status = user.getJSONObject("status");
        JSONObject inventory = user.getJSONObject("inventory");

        // 见习笔记：不进背包，直接累计到 V3 里程碑积分
        if ("OPEN_SERVER_CREDIT".equals(type)) {
            JSONObject v3 = user.getJSONObject("openServerV3");
            if (v3 == null) {
                v3 = new JSONObject(true);
                user.put("openServerV3", v3);
            }
            JSONObject missionNode = v3.getJSONObject("mission");
            if (missionNode == null) {
                missionNode = new JSONObject(true);
                v3.put("mission", missionNode);
            }
            JSONObject milestone = missionNode.getJSONObject("milestone");
            if (milestone == null) {
                milestone = new JSONObject(true);
                milestone.put("groupId", "new_bp_g_1");
                milestone.put("point", 0);
                milestone.put("got", new JSONArray());
                missionNode.put("milestone", milestone);
            }
            milestone.put("point", milestone.getIntValue("point") + count);
            JSONObject shown = new JSONObject(true);
            shown.put("type", type);
            shown.put("id", id);
            shown.put("count", count);
            items.add(shown);
            ArknightsApplication.LOGGER.info("[MissionHelper] 见习笔记 +{} → V3 积分 {}", count,
                    milestone.getIntValue("point"));
            return;
        }

        // 物资补给箱：展开内容物
        if ("ITEM_PACK".equals(type)) {
            JSONObject pack = itemPackInfos() == null ? null : itemPackInfos().getJSONObject(id);
            JSONArray content = pack == null ? null : pack.getJSONArray("content");
            if (content != null) {
                for (int t = 0; t < Math.max(1, count); t++) {
                    for (int i = 0; i < content.size(); i++) {
                        grantOneReward(content.getJSONObject(i), user, items);
                    }
                }
                return;
            }
            ArknightsApplication.LOGGER.warn("[MissionHelper] 未知礼包 {}", id);
            return;
        }

        // 干员：交给 processArray（T==7 会往 items 里塞 charGet）
        if ("CHAR".equals(type)) {
            JSONObject grant = new JSONObject(true);
            grant.put("type", type);
            grant.put("id", id);
            grant.put("count", count);
            com.hypergryph.arknights.core.work.processArray.handleUserDataChange(grant, user, 0L, -1, items);
            return;
        }

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

    private static JSONObject ITEM_PACK_INFOS;

    private static synchronized JSONObject itemPackInfos() {
        if (ITEM_PACK_INFOS == null) {
            String path = System.getProperty("user.dir") + "/data/excel/item_table.json";
            try {
                JSONObject itemTable = JSONObject.parseObject(
                        new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                                java.nio.charset.StandardCharsets.UTF_8));
                ITEM_PACK_INFOS = itemTable.getJSONObject("itemPackInfos");
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[MissionHelper] 读取 item_table.json 失败", e);
            }
            if (ITEM_PACK_INFOS == null) {
                ITEM_PACK_INFOS = new JSONObject(true);
            }
        }
        return ITEM_PACK_INFOS;
    }

    /**
     * 自愈：确保玩家存档里有 V3 见习任务（GUIDE_V3）与里程碑积分节点。
     * 这些任务的条件在表里只有文字描述（没有 template/param），私服按「可直接完成」处理，
     * 领奖后会通过 OPEN_SERVER_CREDIT 累加里程碑积分。
     */
    public static boolean ensureGuideV3(JSONObject user) {
        if (user == null) {
            return false;
        }
        JSONObject root = user.getJSONObject("mission");
        if (root == null) {
            root = new JSONObject(true);
            user.put("mission", root);
        }
        JSONObject missions = root.getJSONObject("missions");
        if (missions == null) {
            missions = new JSONObject(true);
            root.put("missions", missions);
        }
        JSONObject bucket = missions.getJSONObject("GUIDE_V3");
        boolean changed = false;
        if (bucket == null) {
            bucket = new JSONObject(true);
            missions.put("GUIDE_V3", bucket);
            changed = true;
        }
        JSONObject data = openServerMissions();
        for (String id : data.keySet()) {
            if (bucket.containsKey(id)) {
                continue;
            }
            JSONObject node = new JSONObject(true);
            node.put("state", 1);            // 先按未解锁建，随后 refreshGuideV3 按条件解锁
            JSONArray progress = new JSONArray();
            JSONObject p = new JSONObject(true);
            p.put("target", 1);
            p.put("value", 0);
            progress.add(p);
            node.put("progress", progress);
            bucket.put(id, node);
            changed = true;
        }
        // 里程碑积分节点
        JSONObject v3 = user.getJSONObject("openServerV3");
        if (v3 == null) {
            v3 = new JSONObject(true);
            user.put("openServerV3", v3);
            changed = true;
        }
        JSONObject missionNode = v3.getJSONObject("mission");
        if (missionNode == null) {
            missionNode = new JSONObject(true);
            v3.put("mission", missionNode);
            changed = true;
        }
        JSONObject milestone = missionNode.getJSONObject("milestone");
        if (milestone == null) {
            milestone = new JSONObject(true);
            milestone.put("groupId", "new_bp_g_1");
            milestone.put("point", 0);
            milestone.put("got", new JSONArray());
            missionNode.put("milestone", milestone);
            changed = true;
        }
        JSONObject checkIn = v3.getJSONObject("checkIn");
        if (checkIn == null) {
            checkIn = new JSONObject(true);
            checkIn.put("groupId", "new_login_g_1");
            checkIn.put("history", new JSONArray());
            v3.put("checkIn", checkIn);
            changed = true;
        }
        if (checkIn.getString("groupId") == null || checkIn.getString("groupId").isEmpty()) {
            checkIn.put("groupId", "new_login_g_1");
            changed = true;
        }
        JSONArray historyArr = checkIn.getJSONArray("history");
        if (historyArr == null) {
            historyArr = new JSONArray();
            checkIn.put("history", historyArr);
            changed = true;
        }
        // 补齐时给「第 1 天可领」的初始状态（history: 0=已领, 1=未领）
        if (historyArr.isEmpty()) {
            historyArr.add(1);
            changed = true;
        }
        if (v3.getJSONObject("fullOpen") == null) {
            JSONObject fo = new JSONObject(true);
            fo.put("today", false);
            fo.put("remain", 0);
            v3.put("fullOpen", fo);
            changed = true;
        }
        if (refreshGuideV3(user)) {
            changed = true;
        }
        return changed;
    }

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
            // param = [_, "stageA^stageB^...", 需要的通关评价]
            // 评价 2 = 通关，3 = 三星通关（如「在资源收集中以3星评价完成…」）。
            // 达成条件：列表里任意一关达到该评价即可（客户端显示 0/1）。
            String joined = param.getString(1);
            int needState = intOrDefault(param.getString(2), 2);
            if (needState > 3) {
                needState = 3;
            }
            if (joined != null) {
                for (String s : joined.split("\\^")) {
                    if (stageStateOf(user, s) >= needState) {
                        return 1;
                    }
                }
            }
            return 0;
        }

        if ("HasChar".equals(template) && param.size() >= 5) {
            // param = [_, 需要数量, ?, 需要的等级, 需要的星级, 职业(ALL=不限)]
            // 例：将任意一个3星干员提升到10级 → [0,1,0,10,3,ALL]
            int needCount = Math.max(1, intOrDefault(param.getString(1), 1));
            int needLevel = intOrDefault(param.getString(3), 1);
            int needRarity = intOrDefault(param.getString(4), 0);
            String profession = param.size() > 5 ? param.getString(5) : "ALL";
            JSONObject chars = user.getJSONObject("troop") == null ? null : user.getJSONObject("troop").getJSONObject("chars");
            if (chars == null) {
                return 0;
            }
            int matched = 0;
            for (String key : chars.keySet()) {
                JSONObject c = chars.getJSONObject(key);
                if (c == null || c.getIntValue("level") < needLevel) {
                    continue;
                }
                String charId = c.getString("charId");
                JSONObject cfg = ArknightsApplication.characterJson.getJSONObject(charId);
                if (cfg == null) {
                    continue;
                }
                int rarity = rarityOf(cfg.getString("rarity"));
                if (rarity < needRarity) {
                    continue;
                }
                if (profession != null && !"ALL".equals(profession)
                        && !profession.equals(cfg.getString("profession"))) {
                    continue;
                }
                matched++;
                if (matched >= needCount) {
                    return 1;
                }
            }
            return 0;
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
        String template = e == null ? null : e.getString("template");
        if ("CompleteAnyStage".equals(template) || "HasChar".equals(template)) {
            // 这两种模板是「满足即 1」，target 归一成 1，避免历史存档里 target=3 导致永远完不成
            target = 1;
            p.put("target", 1);
        }
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

    private static int rarityOf(String rarity) {
        if (rarity == null) {
            return 0;
        }
        int idx = rarity.lastIndexOf('_');
        try {
            return Integer.parseInt(rarity.substring(idx + 1));
        } catch (Exception e) {
            return 0;
        }
    }
    private static int intOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

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

    public static JSONArray addProgress(JSONObject user, String template, int delta) {
        JSONArray touched = new JSONArray();
        if (user == null || template == null || delta <= 0) {
            return touched;
        }
        JSONObject root = user.getJSONObject("mission");
        if (root == null) {
            return touched;
        }
        JSONObject missionsTable = table().getJSONObject("missions");
        if (missionsTable == null) {
            return touched;
        }
        for (String missionId : missionsTable.keySet()) {
            JSONObject e = missionsTable.getJSONObject(missionId);
            if (e == null || !template.equals(e.getString("template"))) {
                continue;
            }
            JSONObject node = nodeOf(user, missionId);
            if (node == null || node.getIntValue("state") != 2) {
                continue;
            }
            JSONArray progress = node.getJSONArray("progress");
            if (progress == null || progress.isEmpty()) {
                continue;
            }
            JSONObject p = progress.getJSONObject(0);
            int target = p.getIntValue("target");
            int value = p.getIntValue("value");
            if (target <= 0 || value >= target) {
                continue;
            }
            int nv = Math.min(target, value + delta);
            p.put("value", nv);
            if (nv >= target) {
                touched.add(missionId);
                ArknightsApplication.LOGGER.info("[MissionHelper] {} 达成（{} {}/{}）", missionId, template, nv, target);
            }
        }
        return touched;
    }

    public static JSONArray grantPointRewards(JSONObject user, String type) {
        JSONArray gained = new JSONArray();
        if (user == null || type == null) {
            return gained;
        }
        JSONObject root = user.getJSONObject("mission");
        if (root == null) {
            return gained;
        }
        JSONObject mr = root.getJSONObject("missionRewards");
        if (mr == null) {
            mr = new JSONObject(true);
            root.put("missionRewards", mr);
        }
        String pointKey = "DAILY".equals(type) ? "dailyPoint" : "weeklyPoint";
        int points = mr.getIntValue(pointKey);
        JSONObject rewardsNode = mr.getJSONObject("rewards");
        if (rewardsNode == null) {
            rewardsNode = new JSONObject(true);
            mr.put("rewards", rewardsNode);
        }
        JSONObject bucket = rewardsNode.getJSONObject(type);
        if (bucket == null) {
            bucket = new JSONObject(true);
            rewardsNode.put(type, bucket);
        }

        JSONObject rewardTable = "DAILY".equals(type) ? table().getJSONObject("periodicalRewards")
                : table().getJSONObject("weeklyRewards");
        if (rewardTable == null) {
            return gained;
        }
        // 只按「玩家存档里已初始化的那一期」的档位来算（bucket 的 key 就是当期档位 id），
        // 否则会把历史上所有期的档位混在一起，累计阈值立刻爆掉。
        List<JSONObject> ladder = new ArrayList<>();
        for (String id : bucket.keySet()) {
            JSONObject r = rewardTable.getJSONObject(id);
            if (r == null || !type.equals(r.getString("type"))) {
                continue;
            }
            ladder.add(r);
        }
        ladder.sort((a, b) -> Integer.compare(a.getIntValue("sortIndex"), b.getIntValue("sortIndex")));

        long now = System.currentTimeMillis() / 1000L;
        for (JSONObject r : ladder) {
            String id = r.getString("id");
            long endTime = r.getLongValue("endTime");
            if (endTime > 0 && now > endTime) {
                continue;    // 这一期已过期
            }
            if (bucket.getIntValue(id) == 1) {
                continue;
            }
            // 每一档消耗自己的 periodicalPointCost，领完就扣（够就扣，不够也照发）
            int cost = r.getIntValue("periodicalPointCost");
            points = Math.max(0, points - cost);
            JSONArray rs = r.getJSONArray("rewards");
            if (rs != null) {
                for (int i = 0; i < rs.size(); i++) {
                    JSONObject one = rs.getJSONObject(i);
                    JSONObject grant = new JSONObject(true);
                    grant.put("id", one.getString("id"));
                    grant.put("type", one.getString("type"));
                    grant.put("count", one.getIntValue("count"));
                    com.hypergryph.arknights.core.work.processArray.handleUserDataChange(grant, user, 0L, -1, null);
                    gained.add(grant);
                }
            }
            bucket.put(id, 1);
            mr.put(pointKey, points);
            ArknightsApplication.LOGGER.info("[MissionHelper] {} 点数里程碑 {} 已发放（剩余点数 {}）", type, id, points);
        }
        mr.put(pointKey, points);
        return gained;
    }

    public static JSONArray exchangePointRewards(JSONObject user, String type, JSONArray wanted) {
        JSONArray gained = new JSONArray();
        JSONObject root = user == null ? null : user.getJSONObject("mission");
        if (root == null || type == null || wanted == null) {
            return gained;
        }
        JSONObject mr = root.getJSONObject("missionRewards");
        if (mr == null) {
            return gained;
        }
        String pointKey = "DAILY".equals(type) ? "dailyPoint" : "weeklyPoint";
        int points = mr.getIntValue(pointKey);
        JSONObject rewardsNode = mr.getJSONObject("rewards");
        if (rewardsNode == null) {
            rewardsNode = new JSONObject(true);
            mr.put("rewards", rewardsNode);
        }
        JSONObject bucket = rewardsNode.getJSONObject(type);
        if (bucket == null) {
            bucket = new JSONObject(true);
            rewardsNode.put(type, bucket);
        }
        JSONObject rewardTable = "DAILY".equals(type) ? table().getJSONObject("periodicalRewards")
                : table().getJSONObject("weeklyRewards");
        if (rewardTable == null) {
            return gained;
        }
        // 当期档位 = 存档里已初始化的 key ∪ 客户端点名要的 rewardId（后者可能还没初始化）
        java.util.Set<String> ladderIds = new java.util.LinkedHashSet<>(bucket.keySet());
        for (int i = 0; i < wanted.size(); i++) {
            Object w = wanted.get(i);
            if (w != null && !(w instanceof Number)) {
                ladderIds.add(String.valueOf(w));
            }
        }
        List<JSONObject> ladder = new ArrayList<>();
        for (String id : ladderIds) {
            JSONObject r = rewardTable.getJSONObject(id);
            if (r != null && type.equals(r.getString("type"))) {
                ladder.add(r);
                if (!bucket.containsKey(id)) {
                    bucket.put(id, 0);      // 补一条当期记录
                }
            }
        }
        ladder.sort((a, b) -> Integer.compare(a.getIntValue("sortIndex"), b.getIntValue("sortIndex")));

        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        for (int i = 0; i < wanted.size(); i++) {
            Object w = wanted.get(i);
            if (w instanceof Number) {
                int idx = ((Number) w).intValue();
                if (idx >= 0 && idx < ladder.size()) {
                    targets.add(ladder.get(idx).getString("id"));
                }
            } else if (w != null) {
                targets.add(String.valueOf(w));
            }
        }
        if (targets.isEmpty()) {
            return gained;
        }

        long now = System.currentTimeMillis() / 1000L;
        for (JSONObject r : ladder) {
            String id = r.getString("id");
            long endTime = r.getLongValue("endTime");
            if (endTime > 0 && now > endTime) {
                continue;
            }
            if (!targets.contains(id)) {
                continue;
            }
            if (bucket.getIntValue(id) == 1) {
                // 已经领过：把奖励内容原样回显到 items（不重复发货），
                // 否则客户端会因为「items 为空」判定领取失败而一直重试刷「获取物资」。
                JSONArray echo = r.getJSONArray("rewards");
                if (echo != null) {
                    for (int i = 0; i < echo.size(); i++) {
                        JSONObject one = echo.getJSONObject(i);
                        JSONObject shown = new JSONObject(true);
                        shown.put("id", one.getString("id"));
                        shown.put("type", one.getString("type"));
                        shown.put("count", one.getIntValue("count"));
                        gained.add(shown);
                    }
                }
                continue;
            }
            // 每档消耗自己的点数（够就扣，不够也照发 —— 私服不卡点数，
            // 否则客户端会一直重试同一个档位导致「获取物资」弹窗刷屏）
            int cost = r.getIntValue("periodicalPointCost");
            points = Math.max(0, points - cost);
            JSONArray rs = r.getJSONArray("rewards");
            if (rs != null) {
                for (int i = 0; i < rs.size(); i++) {
                    JSONObject one = rs.getJSONObject(i);
                    JSONObject grant = new JSONObject(true);
                    grant.put("id", one.getString("id"));
                    grant.put("type", one.getString("type"));
                    grant.put("count", one.getIntValue("count"));
                    com.hypergryph.arknights.core.work.processArray.handleUserDataChange(grant, user, 0L, -1, null);
                    gained.add(grant);
                }
            }
            bucket.put(id, 1);
            mr.put(pointKey, points);
            ArknightsApplication.LOGGER.info("[MissionHelper] 兑换 {} 档位 {}（消耗 {}，剩余 {}）", type, id, cost, points);
        }
        mr.put(pointKey, points);
        return gained;
    }

    public static int addPoint(JSONObject user, String missionId) {
        JSONObject e = entry(missionId);
        int point = e == null ? 0 : e.getIntValue("periodicalPoint");
        if (point <= 0) {
            return 0;
        }
        JSONObject root = user.getJSONObject("mission");
        JSONObject mr = root.getJSONObject("missionRewards");
        if (mr == null) {
            mr = new JSONObject(true);
            root.put("missionRewards", mr);
        }
        String type = e.getString("type");
        String pointKey = "DAILY".equals(type) ? "dailyPoint" : "weeklyPoint";
        mr.put(pointKey, mr.getIntValue(pointKey) + point);
        return point;
    }

    public static long todayResetTs() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 4);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long ts = c.getTimeInMillis() / 1000L;
        long now = System.currentTimeMillis() / 1000L;
        if (ts > now) {
            ts -= 86400L;
        }
        return ts;
    }

    public static long weekResetTs() {
        long day = todayResetTs();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(day * 1000L);
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK);   // 周日=1 … 周六=7
        int back = (dow == java.util.Calendar.SUNDAY) ? 6 : dow - java.util.Calendar.MONDAY;
        return day - back * 86400L;
    }

    public static int guideV3Value(JSONObject user, JSONObject def) {
        if (user == null || def == null) {
            return 0;
        }
        String jumpType = def.getString("jumpType");
        String place = def.getString("jumpPlace");
        String desc = def.getString("description") == null ? "" : def.getString("description");
        JSONObject dungeon = user.getJSONObject("dungeon");
        JSONObject stages = dungeon == null ? null : dungeon.getJSONObject("stages");

        if ("MAIN_STAGE".equals(jumpType)) {
            if (stages == null) {
                return 0;
            }
            if (desc.contains("突袭")) {
                for (String id : stages.keySet()) {
                    if (id.startsWith("main_00-") && id.endsWith("#s")
                            && stages.getJSONObject(id).getIntValue("state") >= 2) {
                        return 1;
                    }
                }
                return 0;
            }
            return stageState(stages, place) >= 2 ? 1 : 0;
        }

        if ("NONE".equals(jumpType)) {
            int need = parseIntAfter(desc, "博士等级达到", "级");
            if (need > 0) {
                return user.getJSONObject("status").getIntValue("level") >= need ? 1 : 0;
            }
            return 0;
        }

        if ("WEEKLY".equals(jumpType)) {
            if (stages == null) {
                return 0;
            }
            if (desc.contains("芯片")) {
                for (String id : stages.keySet()) {
                    if (id.startsWith("pro_") && stages.getJSONObject(id).getIntValue("state") >= 2) {
                        return 1;
                    }
                }
                return 0;
            }
            int diff = parseIntAfter(desc, "难度", "的");
            if (diff > 0) {
                String[] prefixes = {"wk_melee_", "wk_armor_", "wk_kc_", "wk_toxic_", "wk_fly_",
                        "pro_a_", "pro_b_", "pro_c_", "pro_d_"};
                for (String p : prefixes) {
                    String id = p + diff;
                    if (stages.containsKey(id) && stages.getJSONObject(id).getIntValue("state") >= 2) {
                        return 1;
                    }
                }
            }
            return 0;
        }

        if ("SHOP".equals(jumpType)) {
            JSONObject shop = user.getJSONObject("shop");
            if (shop == null) {
                return 0;
            }
            java.util.Map<String, Integer> bought = new java.util.HashMap<>();
            collectGoodsBought(shop, bought);
            for (Integer v : bought.values()) {
                if (v != null && v > 0) {
                    return 1;
                }
            }
            return 0;
        }

        if ("BUILDING".equals(jumpType)) {
            JSONObject building = user.getJSONObject("building");
            if (building == null) {
                return 0;
            }
            if (desc.contains("专精")) {
                JSONObject chars = user.getJSONObject("troop") == null ? null
                        : user.getJSONObject("troop").getJSONObject("chars");
                if (chars == null) {
                    return 0;
                }
                for (String k : chars.keySet()) {
                    JSONArray skills = chars.getJSONObject(k).getJSONArray("skills");
                    if (skills == null) {
                        continue;
                    }
                    for (int i = 0; i < skills.size(); i++) {
                        if (skills.getJSONObject(i).getIntValue("specializeLevel") > 0) {
                            return 1;
                        }
                    }
                }
                return 0;
            }
            int need = parseIntAfter(desc, "中枢升级至LV.", "");
            if (need > 0) {
                JSONObject slots = building.getJSONObject("roomSlots");
                if (slots == null) {
                    return 0;
                }
                for (String id : slots.keySet()) {
                    JSONObject slot = slots.getJSONObject(id);
                    if ("CONTROL".equals(slot.getString("roomId")) && slot.getIntValue("level") >= need) {
                        return 1;
                    }
                }
            }
            return 0;
        }

        if ("CAMPAIGN_STAGE".equals(jumpType)) {
            JSONObject v2 = user.getJSONObject("campaignsV2");
            JSONObject instances = v2 == null ? null : v2.getJSONObject("instances");
            if (instances == null) {
                return 0;
            }
            for (String campId : instances.keySet()) {
                JSONObject one = instances.getJSONObject(campId);
                if (one == null) {
                    continue;
                }
                if (desc.contains("100名")) {
                    if (one.getIntValue("maxKills") >= 100) {
                        return 1;
                    }
                } else if (desc.contains("全部进度奖励")) {
                    JSONArray status = one.getJSONArray("rewardStatus");
                    if (status != null && !status.isEmpty()) {
                        boolean all = true;
                        for (int i = 0; i < status.size(); i++) {
                            if (status.getIntValue(i) != 1) {
                                all = false;
                                break;
                            }
                        }
                        if (all) {
                            return 1;
                        }
                    }
                }
            }
            return 0;
        }

        if ("CHAR_REPO".equals(jumpType)) {
            JSONObject chars = user.getJSONObject("troop") == null ? null
                    : user.getJSONObject("troop").getJSONObject("chars");
            if (chars == null) {
                return 0;
            }
            int needCount = Math.max(1, parseIntAfter(desc, "任意", "名"));
            int needLevel = parseIntAfter(desc, "等级提升至", "级");
            int needRank = parseIntAfter(desc, "Rank", "");
            int eliteStage = parseIntAfter(desc, "精英", "阶段");
            int eliteLevel = parseIntAfter(desc, "阶段", "级");
            int matched = 0;
            for (String k : chars.keySet()) {
                JSONObject c = chars.getJSONObject(k);
                if (c == null) {
                    continue;
                }
                int level = c.getIntValue("level");
                int evolve = c.getIntValue("evolvePhase");
                boolean ok;
                if (needRank > 0) {
                    ok = c.getIntValue("mainSkillLvl") >= needRank;
                } else if (eliteLevel > 0) {
                    ok = evolve >= Math.max(1, eliteStage) && level >= eliteLevel;
                } else if (desc.contains("精英化")) {
                    ok = evolve >= 1;
                } else if (needLevel > 0) {
                    ok = level >= needLevel;
                } else {
                    ok = false;
                }
                if (ok) {
                    matched++;
                    if (matched >= needCount) {
                        return 1;
                    }
                }
            }
            return 0;
        }
        return 0;
    }

    private static void collectGoodsBought(Object node, java.util.Map<String, Integer> out) {
        if (node == null) {
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.size(); i++) {
                Object o = arr.get(i);
                if (o instanceof JSONObject && ((JSONObject) o).getString("id") != null) {
                    out.merge(((JSONObject) o).getString("id"), ((JSONObject) o).getIntValue("count"), Integer::sum);
                } else {
                    collectGoodsBought(o, out);
                }
            }
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String k : obj.keySet()) {
                Object v = obj.get(k);
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    collectGoodsBought(v, out);
                }
            }
        }
    }

    private static int stageState(JSONObject stages, String stageId) {
        if (stages == null || stageId == null) {
            return 0;
        }
        JSONObject s = stages.getJSONObject(stageId);
        if (s == null) {
            return 0;
        }
        return s.getIntValue("state");
    }

    private static int parseIntAfter(String desc, String prefix, String suffix) {
        try {
            int i = desc.indexOf(prefix);
            if (i < 0) {
                return 0;
            }
            int from = i + prefix.length();
            int end = desc.length();
            if (suffix != null && !suffix.isEmpty()) {
                int j = desc.indexOf(suffix, from);
                if (j > from) {
                    end = j;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (int k = from; k < end; k++) {
                char c = desc.charAt(k);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                } else if (sb.length() > 0) {
                    break;
                }
            }
            return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean refreshGuideV3(JSONObject user) {
        if (user == null) {
            return false;
        }
        ArknightsApplication.LOGGER.info("[V3见习] refresh 被调用");
        JSONObject bucket = missionsOfType(user, "GUIDE_V3", false);
        if (bucket == null || bucket.isEmpty()) {
            return false;
        }
        JSONObject defs = openServerMissions();
        // 计算每组的 NORMAL 任务是否已全部领取
        java.util.Map<String, Boolean> groupClaimed = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        for (String id : defs.keySet()) {
            JSONObject def = defs.getJSONObject(id);
            String g = def.getString("missionGroup");
            groups.computeIfAbsent(g, k -> new java.util.ArrayList<>()).add(id);
        }
        java.util.List<String> groupOrder = new java.util.ArrayList<>(groups.keySet());
        groupOrder.sort(String::compareTo);

        boolean changed = false;
        java.util.Map<String, Integer> groupDone = new java.util.HashMap<>();
        for (String g : groupOrder) {
            boolean allClaimed = true;
            for (String id : groups.get(g)) {
                JSONObject def = defs.getJSONObject(id);
                if (!"NORMAL".equals(def.getString("openServerMissionType"))) {
                    continue;
                }
                JSONObject node = bucket.getJSONObject(id);
                if (node == null || node.getIntValue("state") != 3) {
                    allClaimed = false;
                    break;
                }
            }
            groupDone.put(g, allClaimed ? 1 : 0);
        }

        boolean prevDone = true;
        for (String g : groupOrder) {
            for (String id : groups.get(g)) {
                JSONObject node = bucket.getJSONObject(id);
                if (node == null || node.getIntValue("state") == 3) {
                    continue;
                }
                JSONObject def = defs.getJSONObject(id);
                int value = guideV3Value(user, def);
                int newState = prevDone ? 2 : 1;
                JSONArray progress = node.getJSONArray("progress");
                if (progress == null || progress.isEmpty()) {
                    progress = new JSONArray();
                    JSONObject p = new JSONObject(true);
                    p.put("target", 1);
                    p.put("value", 0);
                    progress.add(p);
                    node.put("progress", progress);
                    changed = true;
                }
                JSONObject p = progress.getJSONObject(0);
                if (p.getIntValue("target") != 1) {
                    p.put("target", 1);
                    changed = true;
                }
                int oldValue = p.getIntValue("value");
                if (oldValue != value) {
                    p.put("value", value);
                    changed = true;
                    ArknightsApplication.LOGGER.info("[V3见习] {} 进度 {} → {}", id, oldValue, value);
                }
                if (node.getIntValue("state") != newState) {
                    node.put("state", newState);
                    changed = true;
                }
            }
            prevDone = prevDone && groupDone.getOrDefault(g, 0) == 1;
        }
        return changed;
    }
    public static boolean dailyCheckInV3(JSONObject user) {
        if (user == null) {
            return false;
        }
        JSONObject v3 = user.getJSONObject("openServerV3");
        if (v3 == null) {
            return false;
        }
        JSONObject checkIn = v3.getJSONObject("checkIn");
        if (checkIn == null) {
            return false;
        }
        JSONArray history = checkIn.getJSONArray("history");
        if (history == null) {
            history = new JSONArray();
            checkIn.put("history", history);
        }
        String groupId = checkIn.getString("groupId");
        if (groupId == null || groupId.isEmpty()) {
            groupId = "new_login_g_1";
            checkIn.put("groupId", groupId);
        }
        int total = 14;
        try {
            JSONObject table = JSONObject.parseObject(new String(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                            System.getProperty("user.dir") + "/data/excel/open_server_table.json")),
                    java.nio.charset.StandardCharsets.UTF_8));
            JSONArray list = table.getJSONObject("openServerV3Data")
                    .getJSONObject("checkInData").getJSONObject(groupId).getJSONArray("checkInItemList");
            if (list != null && !list.isEmpty()) {
                total = list.size();
            }
        } catch (Exception ignored) {
        }
        if (history.size() >= total) {
            return false;
        }
        boolean hasUnclaimed = false;
        for (int i = 0; i < history.size(); i++) {
            Integer v = history.getInteger(i);
            if (v != null && v == 1) {
                hasUnclaimed = true;
                break;
            }
        }
        if (hasUnclaimed) {
            return false;
        }
        history.add(1);
        ArknightsApplication.LOGGER.info("[MissionHelper] V3 签到解锁第 {} 天（未领取）", history.size());
        return true;
    }
    public static void forceResetGroup(JSONObject user, String type) {
        resetGroup(user, type);
    }
    private static void resetGroup(JSONObject user, String type) {
        JSONObject bucket = missionsOfType(user, type, false);
        if (bucket == null) {
            return;
        }
        for (String id : new ArrayList<>(bucket.keySet())) {
            JSONObject node = bucket.getJSONObject(id);
            if (node == null) {
                continue;
            }
            JSONArray progress = node.getJSONArray("progress");
            int target = 1;
            if (progress != null && !progress.isEmpty()) {
                target = Math.max(1, progress.getJSONObject(0).getIntValue("target"));
            } else {
                JSONObject e = entry(id);
                JSONArray param = e == null ? null : e.getJSONArray("param");
                if (param != null && param.size() >= 2) {
                    target = Math.max(1, intOrDefault(param.getString(1), 1));
                }
            }
            JSONArray fresh = new JSONArray();
            JSONObject p = new JSONObject(true);
            p.put("target", target);
            p.put("value", 0);
            fresh.add(p);
            node.put("progress", fresh);
            // 前置没做完的保持「未解锁(1)」，否则会把整条每周任务链一次性全放开
            node.put("state", isUnlocked(user, id) ? 2 : 1);
        }
        // 点数与里程碑领取状态一起清空
        JSONObject root = user.getJSONObject("mission");
        JSONObject mr = root.getJSONObject("missionRewards");
        if (mr != null) {
            mr.put("DAILY".equals(type) ? "dailyPoint" : "weeklyPoint", 0);
            JSONObject rewards = mr.getJSONObject("rewards");
            if (rewards != null) {
                JSONObject group = rewards.getJSONObject(type);
                if (group != null) {
                    for (String rid : new ArrayList<>(group.keySet())) {
                        group.put(rid, 0);
                    }
                }
            }
        }
    }

    public static boolean resetPeriodicalMissions(JSONObject user) {
        JSONObject root = user == null ? null : user.getJSONObject("mission");
        if (root == null) {
            return false;
        }
        boolean changed = false;
        long today = todayResetTs();
        long lastDaily = root.getLongValue("lastDailyReset");
        if (lastDaily < today) {
            resetGroup(user, "DAILY");
            root.put("lastDailyReset", today);
            changed = true;
            ArknightsApplication.LOGGER.info("[MissionHelper] 每日任务已重置（本地 {}）", today);
        }
        long week = weekResetTs();
        long lastWeekly = root.getLongValue("lastWeeklyReset");
        if (lastWeekly < week) {
            resetGroup(user, "WEEKLY");
            root.put("lastWeeklyReset", week);
            changed = true;
            ArknightsApplication.LOGGER.info("[MissionHelper] 每周任务已重置（本地 {}）", week);
        }
        return changed;
    }
}
