package com.hypergryph.arknights.core.chartemplate;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.work.processArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿米娅的三套模板（char_patch_table.json）。
 *
 * <pre>
 * char_002_amiya     术师  sortId 0  默认模板
 * char_1001_amiya2   近卫  sortId 1  通关 main_08-16（JT8-2）解锁
 * char_1037_amiya3   医疗  sortId 2  通关 main_14-20（14-22）解锁
 * </pre>
 *
 * 解锁时要做两件事：
 *   1. 把「当前生效的那套数据」搬进 troop.chars[N].tmpl[旧模板]，基节点腾空（skin/defaultSkillIndex/skills）
 *   2. 给新模板建 patch（皮肤 / 技能 / 模组槽），并把 currentTmpl 指向它
 */
public final class AmiyaTemplate {

    public static final String BASE_CHAR_ID = "char_002_amiya";

    private AmiyaTemplate() {
    }

    /** 该关卡的首次通关会解锁哪些阿米娅模板（条件来自 unlockConds） */
    public static List<String> unlockedBy(String stageId) {
        List<String> result = new ArrayList<>();
        JSONObject table = ArknightsApplication.charPatchTable;
        JSONObject conds = table == null ? null : table.getJSONObject("unlockConds");
        if (conds == null || stageId == null) {
            return result;
        }
        for (String tmplId : conds.keySet()) {
            // 只处理阿米娅的模板，其它干员的 patch 先不管
            if (!tmplId.contains("amiya")) {
                continue;
            }
            JSONArray list = conds.getJSONObject(tmplId).getJSONArray("conds");
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                JSONObject cond = list.getJSONObject(i);
                if (cond != null && stageId.equals(cond.getString("stageId"))) {
                    result.add(tmplId);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 按关卡解锁模板（幂等）。
     *
     * @return 实际解锁/切换的模板数量
     */
    public static int unlockByStage(JSONObject userData, String stageId) {
        int unlocked = 0;
        for (String tmplId : unlockedBy(stageId)) {
            if (unlock(userData, tmplId)) {
                unlocked++;
                ArknightsApplication.LOGGER.info("[amiya] 模板 {} 已解锁并切换（stage={}）", tmplId, stageId);
            }
        }
        return unlocked;
    }

    /** 找存档里阿米娅的 instId（没有就返回 null） */
    public static String findInstId(JSONObject userData) {
        if (userData == null) {
            return null;
        }
        JSONObject troop = userData.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        if (chars == null) {
            return null;
        }
        for (String key : chars.keySet()) {
            JSONObject c = chars.getJSONObject(key);
            if (c != null && BASE_CHAR_ID.equals(c.getString("charId"))) {
                return key;
            }
        }
        return null;
    }

    /**
     * 建立模板 patch 并切换过去；已经存在则只切换。
     *
     * @return userData 是否有改动
     */
    public static boolean unlock(JSONObject userData, String tmplId) {
        if (userData == null || tmplId == null) {
            return false;
        }
        JSONObject troop = userData.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        if (chars == null) {
            return false;
        }

        String instId = null;
        for (String key : chars.keySet()) {
            JSONObject c = chars.getJSONObject(key);
            if (c != null && BASE_CHAR_ID.equals(c.getString("charId"))) {
                instId = key;
                break;
            }
        }
        if (instId == null) {
            ArknightsApplication.LOGGER.warn("[amiya] 存档里没有阿米娅，跳过模板解锁");
            return false;
        }

        JSONObject charData = chars.getJSONObject(instId);
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (tmpl == null) {
            tmpl = new JSONObject(true);
        }
        boolean changed = false;

        // 第一次切换：把当前生效数据（术师）搬进基模板 patch
        if (!tmpl.containsKey(BASE_CHAR_ID)) {
            tmpl.put(BASE_CHAR_ID, snapshotBasePatch(charData));
            changed = true;
        }

        // 新模板的 patch
        if (!tmpl.containsKey(tmplId)) {
            JSONObject patch = buildPatch(tmplId);
            if (patch == null) {
                return false;
            }
            tmpl.put(tmplId, patch);
            changed = true;
        }

        charData.put("tmpl", tmpl);
        if (!tmplId.equals(charData.getString("currentTmpl"))) {
            charData.put("currentTmpl", tmplId);
            changed = true;
        }

        // 新模板的模组槽可见性要跟随当前精英阶段：
        // 已经精二的玩家通关 main_14-20 解锁医疗时，槽位应当是 hide=0 而不是 1
        processArray.setEquipHidden(charData, charData.getIntValue("evolvePhase") < 2);

        // 当前生效的数据由 tmpl 提供，基节点整体腾空 —— 官方实测：
        // skin=null、defaultSkillIndex=-1、skills=[]、currentEquip=null、equip={}
        charData.put("skin", (Object) null);
        charData.put("defaultSkillIndex", -1);
        charData.put("skills", new JSONArray());
        charData.put("currentEquip", (Object) null);
        charData.put("equip", new JSONObject(true));
        return changed;
    }

    /** 把基节点当前的数据复制成 patch（切走之前先把它存下来） */
    private static JSONObject snapshotBasePatch(JSONObject charData) {
        JSONObject base = new JSONObject(true);
        base.put("skinId", charData.getString("skin"));
        base.put("defaultSkillIndex", charData.getIntValue("defaultSkillIndex"));
        JSONArray skills = charData.getJSONArray("skills");
        base.put("skills", skills == null ? new JSONArray() : JSONObject.parseArray(skills.toJSONString()));
        base.put("currentEquip", charData.get("currentEquip"));
        JSONObject equip = charData.getJSONObject("equip");
        base.put("equip", equip == null ? new JSONObject(true) : JSONObject.parseObject(equip.toJSONString()));
        return base;
    }

    /**
     * 建一个模板的 patch：皮肤、技能（unlock 由 unlockCond.phase 决定）、模组槽。
     * 数据全部来自 char_patch_table.patchChars，不写死技能 id。
     */
    public static JSONObject buildPatch(String tmplId) {
        JSONObject table = ArknightsApplication.charPatchTable;
        JSONObject patchChars = table == null ? null : table.getJSONObject("patchChars");
        JSONObject def = patchChars == null ? null : patchChars.getJSONObject(tmplId);
        if (def == null) {
            ArknightsApplication.LOGGER.warn("[amiya] char_patch_table.patchChars 里没有 {}", tmplId);
            return null;
        }

        JSONArray skills = new JSONArray();
        JSONArray defSkills = def.getJSONArray("skills");
        if (defSkills != null) {
            for (int i = 0; i < defSkills.size(); i++) {
                JSONObject defSkill = defSkills.getJSONObject(i);
                if (defSkill == null) {
                    continue;
                }
                JSONObject skill = new JSONObject(true);
                skill.put("skillId", defSkill.getString("skillId"));
                skill.put("unlock", unlockOf(defSkill));
                skill.put("state", 0);
                skill.put("specializeLevel", 0);
                skill.put("completeUpgradeTime", -1);
                skills.add(skill);
            }
        }

        JSONObject patch = new JSONObject(true);
        // 模板皮肤固定是 #2（近卫/医疗都是），术师才是 #1
        patch.put("skinId", tmplId + "#2");
        patch.put("defaultSkillIndex", skills.isEmpty() ? -1 : 0);
        patch.put("skills", skills);
        patch.put("currentEquip", (Object) null);
        patch.put("equip", processArray.buildEquipSlots(tmplId));
        return patch;
    }

    /** unlockCond.phase == PHASE_0 的技能初始解锁 */
    private static int unlockOf(JSONObject defSkill) {
        JSONObject unlockCond = defSkill.getJSONObject("unlockCond");
        String phase = unlockCond == null ? null : unlockCond.getString("phase");
        if (phase == null || phase.isEmpty()) {
            return 1;
        }
        return ArknightsApplication.getCharPhase(phase) == 0 ? 1 : 0;
    }
}
