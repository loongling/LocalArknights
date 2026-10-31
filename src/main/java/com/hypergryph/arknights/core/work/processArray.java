package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

import static com.hypergryph.arknights.core.work.consumableNumberG.*;

public class processArray {

    public static JSONArray grantChar(JSONObject user, String charId, int count) {
        JSONArray out = new JSONArray();
        for (int n = 0; n < Math.max(1, count); n++) {
            out.add(grantOneChar(user, charId));
        }
        return out;
    }

    private static JSONObject grantOneChar(JSONObject user, String charId) {
        JSONObject troop = user.getJSONObject("troop");
        JSONObject chars = troop.getJSONObject("chars");

        // 是否已有该角色
        String existInstId = null;
        for (String key : chars.keySet()) {
            JSONObject c = chars.getJSONObject(key);
            if (c != null && charId.equals(c.getString("charId"))) {
                existInstId = key;
                break;
            }
        }

        JSONArray itemGet = new JSONArray();
        int charInstId;
        boolean isNew = existInstId == null;

        if (isNew) {
            // instId 直接用 charId 里的编号（char_151_myrtle → 151），和 gacha 一致
            charInstId = extractInstId(charId, chars);
            chars.put(String.valueOf(charInstId), createNewCharacter(charId, charInstId));
            putCurCharInstId(troop, charInstId);

            // 基建数据
            user.getJSONObject("building").getJSONObject("chars")
                    .put(String.valueOf(charInstId), createBuildingCharacter(charId));

            // 首次获得送 1 张高级凭证
            JSONObject hggShard = new JSONObject(true);
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);
            user.getJSONObject("status").put("hggShard",
                    user.getJSONObject("status").getIntValue("hggShard") + 1);

            // 角色组
            JSONObject charGroup = new JSONObject(true);
            charGroup.put("favorPoint", 0);
            troop.getJSONObject("charGroup").put(charId, charGroup);

            // 潜能材料初始化为 0
            user.getJSONObject("inventory").put("p_" + charId, 0);

            // 图鉴
            putDexNav(user, charId, charInstId, 1);
        } else {
            charInstId = Integer.parseInt(existInstId);
            JSONObject existingChar = chars.getJSONObject(existInstId);
            int potentialRank = existingChar.getIntValue("potentialRank");
            int rarity = ArknightsApplication.getCharRarity(charId);

            // 信物按稀有度折算
            JSONObject shardItem = new JSONObject(true);
            if (rarity <= 3) {
                shardItem.put("type", "LGG_SHD");
                shardItem.put("id", "4005");
                shardItem.put("count", rarity == 3 ? 30 : (rarity == 2 ? 5 : 1));
                user.getJSONObject("status").put("lggShard",
                        user.getJSONObject("status").getIntValue("lggShard")
                                + shardItem.getIntValue("count"));
            } else {
                shardItem.put("type", "HGG_SHD");
                shardItem.put("id", "4004");
                shardItem.put("count", rarity == 5
                        ? (potentialRank < 5 ? 10 : 15)
                        : (potentialRank < 5 ? 5 : 8));
                user.getJSONObject("status").put("hggShard",
                        user.getJSONObject("status").getIntValue("hggShard")
                                + shardItem.getIntValue("count"));
            }
            itemGet.add(shardItem);

            // 潜能材料
            JSONObject potentialItem = new JSONObject(true);
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + charId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);
            user.getJSONObject("inventory").put("p_" + charId,
                    user.getJSONObject("inventory").getIntValue("p_" + charId) + 1);

            // 图鉴获得次数 +1
            putDexNav(user, charId, charInstId, -1);
        }

        JSONObject charGet = new JSONObject(true);
        charGet.put("charInstId", charInstId);
        charGet.put("charId", charId);
        charGet.put("isNew", isNew ? 1 : 0);
        charGet.put("itemGet", itemGet);

        JSONObject item = new JSONObject(true);
        item.put("type", "CHAR");
        item.put("id", charId);
        item.put("count", 1);
        item.put("charGet", charGet);
        return item;
    }

    private static JSONObject createNewCharacter(String charId, int instId) {
        JSONObject charInfo = ArknightsApplication.characterJson.getJSONObject(charId);

        JSONArray skills = new JSONArray();
        JSONArray skilsArray = charInfo.getJSONArray("skills");
        for (int i = 0; i < skilsArray.size(); i++) {
            JSONObject skill = skilsArray.getJSONObject(i);
            JSONObject newSkill = new JSONObject(true);
            newSkill.put("skillId", skill.getString("skillId"));
            newSkill.put("state", 0);
            newSkill.put("specializeLevel", 0);
            newSkill.put("completeUpgradeTime", -1);
            JSONObject unlockCond = skill.getJSONObject("unlockCond");
            String phase = unlockCond != null ? unlockCond.getString("phase") : "";
            // unlockCond.phase 可能是 "PHASE_0"
            newSkill.put("unlock", (phase == null || phase.isEmpty()
                    || ArknightsApplication.getCharPhase(phase) == 0) ? 1 : 0);
            skills.add(newSkill);
        }

        JSONObject charData = new JSONObject(true);
        charData.put("charId", charId);
        charData.put("instId", instId);
        charData.put("level", 1);
        charData.put("exp", 0);
        charData.put("evolvePhase", 0);
        charData.put("favorPoint", 0);
        charData.put("potentialRank", 0);
        charData.put("mainSkillLvl", 1);
        charData.put("skin", charId + "#1");
        charData.put("gainTime", System.currentTimeMillis() / 1000L);
        charData.put("skills", skills);
        charData.put("currentEquip", null);
        charData.put("equip", createEquipSlots(charId));
        // 语音语种跟随干员表（国服是 CN_MANDARIN），不能写死 JP
        JSONObject typeDict = ArknightsApplication.charwordTable.getJSONObject("charDefaultTypeDict");
        charData.put("voiceLan", typeDict == null ? null : typeDict.getString(charId));
        charData.put("defaultSkillIndex", skills.isEmpty() ? -1 : 0);
        // 星标（客户端 PlayerCharacter.starMark，int 枚举）；gacha 的语义：有技能 = 0，没有 = -1
        charData.put("starMark", skills.isEmpty() ? -1 : 0);
        return charData;
    }

    private static JSONObject createBuildingCharacter(String charId) {
        JSONObject buildingChar = new JSONObject(true);
        buildingChar.put("charId", charId);
        buildingChar.put("lastApAddTime", System.currentTimeMillis() / 1000L);
        buildingChar.put("ap", 8640000);
        buildingChar.put("roomSlotId", "");
        buildingChar.put("index", -1);
        buildingChar.put("changeScale", 0);

        JSONObject bubble = new JSONObject(true);
        JSONObject normal = new JSONObject(true);
        normal.put("add", -1);
        normal.put("ts", 0);
        bubble.put("normal", normal);

        JSONObject assist = new JSONObject(true);
        assist.put("add", -1);
        assist.put("ts", 0);
        bubble.put("assist", assist);

        JSONObject privateBubble = new JSONObject(true);
        privateBubble.put("add", -1);
        privateBubble.put("ts", 0);
        bubble.put("private", privateBubble);

        buildingChar.put("bubble", bubble);
        buildingChar.put("workTime", 0);
        buildingChar.put("privateRooms", new JSONArray());
        return buildingChar;
    }

    public static void setEquipHidden(JSONObject charData, boolean hidden) {
        if (charData == null) {
            return;
        }
        int flag = hidden ? 1 : 0;
        applyHidden(charData.getJSONObject("equip"), flag);

        // 模板干员（阿米娅）：每个模板的 equip 也要跟着变
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (tmpl != null) {
            for (String key : tmpl.keySet()) {
                JSONObject patch = tmpl.getJSONObject(key);
                if (patch != null) {
                    applyHidden(patch.getJSONObject("equip"), flag);
                }
            }
        }
    }

    private static void applyHidden(JSONObject equip, int flag) {
        if (equip == null) {
            return;
        }
        for (String slotId : equip.keySet()) {
            JSONObject slot = equip.getJSONObject(slotId);
            if (slot != null) {
                slot.put("hide", flag);
            }
        }
    }

    public static JSONObject activeCharData(JSONObject charData) {
        if (charData == null) {
            return null;
        }
        String currentTmpl = charData.getString("currentTmpl");
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (currentTmpl != null && !currentTmpl.isEmpty() && tmpl != null) {
            JSONObject patch = tmpl.getJSONObject(currentTmpl);
            if (patch != null) {
                return patch;
            }
        }
        return charData;
    }

    public static JSONObject templateNode(JSONObject charData, String requestedTemplateId) {
        if (charData == null) {
            return null;
        }
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (tmpl != null && requestedTemplateId != null && !requestedTemplateId.isEmpty()) {
            JSONObject patch = tmpl.getJSONObject(requestedTemplateId);
            if (patch != null) {
                return patch;
            }
        }
        return activeCharData(charData);
    }

    public static String getSkin(JSONObject charData) {
        if (charData == null) {
            return null;
        }
        String currentTmpl = charData.getString("currentTmpl");
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (currentTmpl != null && !currentTmpl.isEmpty() && tmpl != null) {
            JSONObject patch = tmpl.getJSONObject(currentTmpl);
            if (patch != null) {
                return patch.getString("skinId");
            }
        }
        return charData.getString("skin");
    }

    public static void setSkin(JSONObject charData, String skinId) {
        if (charData == null || skinId == null) {
            return;
        }
        String currentTmpl = charData.getString("currentTmpl");
        JSONObject tmpl = charData.getJSONObject("tmpl");
        if (currentTmpl != null && !currentTmpl.isEmpty() && tmpl != null) {
            JSONObject patch = tmpl.getJSONObject(currentTmpl);
            if (patch != null) {
                patch.put("skinId", skinId);
                return;
            }
        }
        charData.put("skin", skinId);
    }

    public static int getDefaultSkillIndex(JSONObject charData) {
        JSONObject active = activeCharData(charData);
        return active == null ? -1 : active.getIntValue("defaultSkillIndex");
    }

    public static List<JSONArray> allSkillArrays(JSONObject charData) {
        List<JSONArray> result = new ArrayList<>();
        if (charData == null) {
            return result;
        }
        JSONObject tmpl = charData.getJSONObject("tmpl");
        boolean templated = tmpl != null && charData.getString("currentTmpl") != null
                && !charData.getString("currentTmpl").isEmpty();
        if (!templated) {
            JSONArray base = charData.getJSONArray("skills");
            if (base != null) {
                result.add(base);
            }
            return result;
        }
        if (tmpl != null) {
            for (String key : tmpl.keySet()) {
                JSONObject patch = tmpl.getJSONObject(key);
                JSONArray skills = patch == null ? null : patch.getJSONArray("skills");
                if (skills != null) {
                    result.add(skills);
                }
            }
        }
        return result;
    }

    public static void unlockSkillsForPhase(JSONObject charData, String baseCharId, int destPhase) {
        if (charData == null) {
            return;
        }
        JSONObject tmpl = charData.getJSONObject("tmpl");
        boolean templated = tmpl != null && charData.getString("currentTmpl") != null
                && !charData.getString("currentTmpl").isEmpty();

        if (!templated) {
            unlockSkillsOfNode(charData.getJSONArray("skills"), baseCharId, destPhase);
            return;
        }
        if (tmpl != null) {
            for (String templateId : tmpl.keySet()) {
                JSONObject patch = tmpl.getJSONObject(templateId);
                if (patch != null) {
                    unlockSkillsOfNode(patch.getJSONArray("skills"), templateId, destPhase);
                }
            }
        }
    }

    private static void unlockSkillsOfNode(JSONArray charSkills, String tableCharId, int destPhase) {
        if (charSkills == null || tableCharId == null) {
            return;
        }
        JSONArray defs = skillDefsOf(tableCharId);
        if (defs == null) {
            return;
        }
        for (int i = 0; i < charSkills.size(); i++) {
            JSONObject skill = charSkills.getJSONObject(i);
            if (skill == null) {
                continue;
            }
            String skillId = skill.getString("skillId");
            for (int d = 0; d < defs.size(); d++) {
                JSONObject def = defs.getJSONObject(d);
                if (def == null || !skillId.equals(def.getString("skillId"))) {
                    continue;
                }
                JSONObject unlockCond = def.getJSONObject("unlockCond");
                String phase = unlockCond == null ? null : unlockCond.getString("phase");
                int need = phase == null || phase.isEmpty() ? 0 : ArknightsApplication.getCharPhase(phase);
                if (need <= destPhase) {
                    skill.put("unlock", 1);
                }
                break;
            }
        }
    }

    public static JSONObject charDef(String charId) {
        if (charId == null) {
            return null;
        }
        JSONObject patchTable = ArknightsApplication.charPatchTable;
        JSONObject patchChars = patchTable == null ? null : patchTable.getJSONObject("patchChars");
        JSONObject patch = patchChars == null ? null : patchChars.getJSONObject(charId);
        if (patch != null) {
            return patch;
        }
        return ArknightsApplication.characterJson == null
                ? null : ArknightsApplication.characterJson.getJSONObject(charId);
    }

    private static JSONArray skillDefsOf(String charId) {
        JSONObject def = charDef(charId);
        return def == null ? null : def.getJSONArray("skills");
    }

    public static JSONObject buildEquipSlots(String charId) {
        return createEquipSlots(charId);
    }

    private static JSONObject createEquipSlots(String charId) {
        JSONObject equip = new JSONObject(true);
        String itemType = charId.substring(charId.indexOf("_") + 1);
        String itemId = itemType.substring(itemType.indexOf("_") + 1);

        int moduleCount = 0;
        for (int i = 1; i <= 9; i++) {
            if (ArknightsApplication.uniequipTable.containsKey("uniequip_00" + i + "_" + itemId)) {
                moduleCount = i;
            }
        }
        for (int i = 1; i <= moduleCount; i++) {
            JSONObject slot = new JSONObject(true);
            slot.put("hide", 1);
            slot.put("level", 1);
            slot.put("locked", i == 1 ? 0 : 1);
            equip.put("uniequip_00" + i + "_" + itemId, slot);
        }
        return equip;
    }

    private static int extractInstId(String charId, JSONObject chars) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("char_(\\d+)_").matcher(charId);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        ArknightsApplication.LOGGER.warn("[grantChar] charId 里没有编号: {}，退回自增", charId);
        int instId = 1;
        for (String key : chars.keySet()) {
            try {
                instId = Math.max(instId, Integer.parseInt(key) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return instId;
    }

    private static void putCurCharInstId(JSONObject troop, int instId) {
        if (troop.getIntValue("curCharInstId") < instId + 1) {
            troop.put("curCharInstId", instId + 1);
        }
    }

    private static void putDexNav(JSONObject user, String charId, int charInstId, int count) {
        JSONObject dexNav = user.getJSONObject("dexNav");
        if (dexNav == null) {
            return;
        }
        JSONObject dexChar = dexNav.getJSONObject("character");
        if (dexChar == null) {
            dexChar = new JSONObject(true);
            dexNav.put("character", dexChar);
        }
        JSONObject entry = dexChar.getJSONObject(charId);
        if (entry == null) {
            entry = new JSONObject(true);
            entry.put("charInstId", charInstId);
            entry.put("count", Math.max(1, count));
            entry.put("classicCount", 0);
            dexChar.put(charId, entry);
        } else {
            entry.put("count", entry.getIntValue("count") + Math.max(1, count));
        }
    }

    public static JSONArray removeByGoodId(JSONArray array, String id) {
        if (array == null || array.isEmpty() || id == null) {
            return null;
        }

        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) instanceof JSONObject item
                    && Objects.equals(id, item.getString("id"))) {
                array.remove(i);
                return array;
            }
        }
        return null;
    }

    public static JSONObject handleModifiedBuild(JSONObject obj, JSONObject modified, JSONObject user, int i) {
        int count = obj.getInteger("count");
        String id = obj.getString("id");
        String type = obj.getString("type");
        int T = ArknightsApplication.checkCheckInItemType(type);
        JSONObject changedStatus = new JSONObject();
        JSONObject status = user.getJSONObject("status");
        JSONObject inventory = user.getJSONObject("inventory");
        JSONObject inventoryChanged = new JSONObject();
        JSONObject consumable = user.getJSONObject("consumable");
        JSONObject consumableChanged = new JSONObject();

        if (T == 1) {
            int LMD = status.getInteger("gold");
            count = LMD + count;
            changedStatus.put("gold", count);
            mergeNode(modified, "status", changedStatus);
            return modified;
        }
        else if (T == 2) {
            if (inventory.getInteger(id) != null) {
                int counts = inventory.getInteger(id);
                count = count + counts;
                inventory.put(id, count);
                mergeNode(modified, "inventory", inventoryChanged);
                return modified;
            } else {
                inventoryChanged.put(id, count);
                mergeNode(modified, "inventory", inventoryChanged);
                return modified;
            }
        }
        else if (T == 3) {
            // 各种票券：统一映射到 status 里的对应字段
            String ticketField = ticketStatusField(type);
            if (ticketField != null) {
                changedStatus.put(ticketField, status.getIntValue(ticketField) + count);
                mergeNode(modified, "status", changedStatus);
                return modified;
            }
            int TKT = resolveTKT(type);
            if (TKT == 2) {
                if (isExist(id, consumable)){
                    JSONObject A = consumable.getJSONObject(id);
                    JSONObject B = getInnerObject(A);
                    int counts = B.getInteger("count");
                    count = count + counts;
                    int me = getInnerNumber(A);
                    B.put("count", count);
                    JSONObject C = new JSONObject();
                    C.put(String.valueOf(me), B);
                    consumableChanged.put(id, C);
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    String specialDesc = "";
                    long ts = -1;
                    if (i >= 0 && i < ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").size()) {
                        JSONObject item = ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").getJSONObject(i);

                        JSONObject specialItemInfos = item.getJSONObject("specialItemInfos").getJSONObject(id);
                        if (specialItemInfos != null && !specialItemInfos.isEmpty()) {
                            specialDesc = specialItemInfos.getString("specialDesc");
                            ts = parseExpireTs(specialDesc);
                        }
                    }
                    A.put("ts", ts);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    consumableChanged.put(id, B);
                }
                mergeNode(modified, "consumable", consumableChanged);
                return modified;
            }
        } else if(T == 5){
            if (isExist(id, consumable)){
                JSONObject A = consumable.getJSONObject(id);
                JSONObject B = getInnerObject(A);
                int counts = B.getInteger("count");
                count = count + counts;
                int me = getInnerNumber(A);
                B.put("count", count);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), B);
                consumableChanged.put(id, C);
            } else  {
                int me = nextItemNumber(consumable);
                JSONObject A = new JSONObject();
                A.put("count", count);
                A.put("ts", -1);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), A);
                consumableChanged.put(id, C);
            }
            // 两个分支都要写增量（原来"已存在"那条路径直接漏了增量）
            mergeNode(modified, "consumable", consumableChanged);
            return modified;
        } else if(T == 6){
            int andD = status.getInteger("androidDiamond");
            int iosD = status.getInteger("iosDiamond");
            andD = andD + count;
            iosD = iosD + count;
            changedStatus.put("androidDiamond", andD);
            changedStatus.put("iosDiamond", iosD);
            mergeNode(modified, "status", changedStatus);
            return modified;
        }
        else if (T == 7) {
            // 干员：加干员会同时改 troop / building / dexNav / status / inventory，
            // 增量交给调用方在 handleUserDataChange 之后做实际 diff
            return modified;
        }
        else if (T == 8) {
            long nowTs = System.currentTimeMillis() / 1000L;
            String cardId = com.hypergryph.arknights.core.monthlycard.MonthlyCard.currentCardId();
            mergeNode(modified, "status", com.hypergryph.arknights.core.monthlycard.MonthlyCard
                    .planStatusDelta(user, cardId, nowTs));
            mergeNode(modified, "inventory", com.hypergryph.arknights.core.monthlycard.MonthlyCard
                    .planInventoryDelta(user, cardId, nowTs));
            return modified;
        }
        else if(T == 9){
            if(type.equals("HOME_THEME")){
                JSONObject homeTheme = new JSONObject();
                JSONObject theme = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                theme.put(id, add);
                homeTheme.put("themes", theme);
                mergeNode(modified, "homeTheme", homeTheme);
                return modified;
            }
            else if(type.equals("HOME_BACKGROUND")){
                JSONObject background = new JSONObject();
                JSONObject bgs = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                bgs.put(id, add);
                background.put("bgs", bgs);
                mergeNode(modified, "background", background);
                return modified;
            }
            else if(type.equals("PLAYER_AVATAR")){
                JSONObject avatar = new JSONObject();
                JSONObject icon = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("ts", ArknightsApplication.getTimestamp());
                add.put("src", "other");
                icon.put(id, add);
                avatar.put("avatar_icon", icon);
                mergeNode(modified, "avatar", avatar);
                return modified;
            }
        }
        else if(T == 10){
            int o_shd = status.getInteger("diamondShard");
            changedStatus.put("diamondShard", o_shd + count);
            mergeNode(modified, "status", changedStatus);
            return modified;
        }
        return modified;
    }

    private static final Pattern PATTERN =
            Pattern.compile("(\\d{1,2})月(\\d{1,2})日(\\d{1,2}):(\\d{2})");

    public static long parseExpireTs(String text) {
        Matcher m = PATTERN.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("未找到过期时间: " + text);
        }

        int month = Integer.parseInt(m.group(1));
        int day   = Integer.parseInt(m.group(2));
        int hour  = Integer.parseInt(m.group(3));
        int minute = Integer.parseInt(m.group(4));

        int year = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).getYear();

        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, 0);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (ldt.isBefore(now.minusDays(1))) {
            ldt = ldt.plusYears(1);
        }

        return ldt.atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli();
    }

    public static JSONObject handleUserDataChange(JSONObject obj, JSONObject user, long uid, int i) {
        return handleUserDataChange(obj, user, uid, i, null);
    }

    public static JSONObject handleUserDataChange(JSONObject obj, JSONObject user, long uid, int i,
                                                  JSONArray itemsOut) {
        int count = obj.getInteger("count");
        String id = obj.getString("id");
        String type = obj.getString("type");
        int T = ArknightsApplication.checkCheckInItemType(type);
        JSONObject changedStatus = new JSONObject();
        JSONObject status = user.getJSONObject("status");
        JSONObject inventory = user.getJSONObject("inventory");
        JSONObject inventoryChanged = new JSONObject();
        JSONObject consumable = user.getJSONObject("consumable");
        JSONObject consumableChanged = new JSONObject();

        // T == 7 干员（唯一实现在 processArray.grantChar）
        if (T == 7) {
            JSONArray got = grantChar(user, id, count);
            if (itemsOut != null) {
                itemsOut.addAll(got);
            }
            return user;
        }

        if(T == 1) {
            int o_LMD =  status.getInteger("gold");
            user.getJSONObject("status").put("gold", o_LMD + count);
            return user;
        }
        else if(T == 2) {
            if (inventory.getInteger(id) != null) {
                int o_counts = inventory.getInteger(id);
                user.getJSONObject("inventory").put(id, o_counts + count);
                return user;
            } else {
                user.getJSONObject("inventory").put(id, count);
                return user;
            }
        } else if(T == 3) {
            // 各种票券：统一映射到 status 里的对应字段
            String ticketField = ticketStatusField(type);
            if (ticketField != null) {
                user.getJSONObject("status").put(ticketField, status.getIntValue(ticketField) + count);
                return user;
            }
            if ("TKT_GACHA_PRSV".equals(type)) {
                // 采购凭证十连券之类，存在 inventory 里
                user.getJSONObject("inventory").put(id, inventory.getIntValue(id) + count);
                return user;
            }
            int TKT = resolveTKT(type);
            if (TKT == 2) {
                if (isExist(id, consumable)){
                    JSONObject A = consumable.getJSONObject(id);
                    JSONObject B = getInnerObject(A);
                    int counts = B.getInteger("count");
                    count = count + counts;
                    int me = getInnerNumber(A);
                    B.put("count", count);
                    JSONObject C = new JSONObject();
                    C.put(String.valueOf(me), B);
                    user.getJSONObject("consumable").put(id, C);
                    return user;
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    String specialDesc = "";
                    long ts = -1;
                    if (i >= 0 && i < ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").size()) {
                        JSONObject item = ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").getJSONObject(i);

                        JSONObject specialItemInfos = item.getJSONObject("specialItemInfos").getJSONObject(id);
                        if (specialItemInfos != null && !specialItemInfos.isEmpty()) {
                            specialDesc = specialItemInfos.getString("specialDesc");
                            ts = parseExpireTs(specialDesc);
                        }
                    }
                    A.put("ts", ts);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    user.getJSONObject("consumable").put(id, B);
                    return user;
                }
            }
        }
        else if(T == 5) {
            if (isExist(id, consumable)){
                JSONObject A = consumable.getJSONObject(id);
                JSONObject B = getInnerObject(A);
                int counts = B.getInteger("count");
                count = count + counts;
                int me = getInnerNumber(A);
                B.put("count", count);
                if (B.getLongValue("ts") <= 0) {
                    B.put("ts", defaultConsumableTs(id, type));
                }
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), B);
                user.getJSONObject("consumable").put(id, C);
                return user;
            } else  {
                int me = nextItemNumber(consumable);
                JSONObject A = new JSONObject();
                A.put("count", count);
                // 理智药这类时限消耗品必须带过期时间，否则客户端不显示有效期
                A.put("ts", defaultConsumableTs(id, type));
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), A);
                user.getJSONObject("consumable").put(id, C);
                return user;
            }
        }
        else if(T == 6){
            int andD = status.getInteger("androidDiamond");
            int iosD = status.getInteger("iosDiamond");
            andD = andD + count;
            iosD = iosD + count;
            user.getJSONObject("status").put("androidDiamond", andD);
            user.getJSONObject("status").put("iosDiamond", iosD);
            return user;
        }
        else if (T == 8) {
            for (int n = 0; n < Math.max(1, count); n++) {
                com.hypergryph.arknights.core.monthlycard.MonthlyCard
                        .activate(user, System.currentTimeMillis() / 1000L);
            }
            com.hypergryph.arknights.core.monthlycard.MonthlyCard.grantDailyBonus(user,
                    com.hypergryph.arknights.core.monthlycard.MonthlyCard.currentCardId());
            return user;
        }
        else  if(T == 9){
            if(type.equals("HOME_THEME")){
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                user.getJSONObject("homeTheme").getJSONObject("themes").put(id, add);
                return user;
            }
            else if(type.equals("HOME_BACKGROUND")){
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                user.getJSONObject("background").getJSONObject("bgs").put(id, add);
                return  user;
            }
            else if(type.equals("PLAYER_AVATAR")){
                JSONObject add = new JSONObject();
                add.put("ts", ArknightsApplication.getTimestamp());
                add.put("src", "other");
                user.getJSONObject("avatar").getJSONObject("avatar_icon").put(id, add);
                return  user;
            }
        }
        else if(T == 10){
            int o_shd = status.getInteger("diamondShard");
            user.getJSONObject("status").put("diamondShard", o_shd + count);
            return user;
        }
        return user;
    }

    private static void mergeNode(JSONObject modified, String node, JSONObject delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        JSONObject target = modified.getJSONObject(node);
        if (target == null) {
            target = JSONObject.parseObject(delta.toJSONString());
        } else {
            deepMerge(target, delta);
        }
        modified.put(node, target);
    }

    private static void deepMerge(JSONObject dst, JSONObject src) {
        for (String key : src.keySet()) {
            Object sv = src.get(key);
            Object dv = dst.get(key);
            if (sv instanceof JSONObject && dv instanceof JSONObject) {
                deepMerge((JSONObject) dv, (JSONObject) sv);
            } else {
                dst.put(key, sv);
            }
        }
    }

    private static String ticketStatusField(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "TKT_GACHA" -> "gachaTicket";
            case "TKT_GACHA_10" -> "tenGachaTicket";
            case "CLASSIC_TKT_GACHA" -> "classicGachaTicket";
            case "CLASSIC_TKT_GACHA_10" -> "classicTenGachaTicket";
            case "TKT_RECRUIT" -> "recruitLicense";
            case "TKT_INST_FIN" -> "instantFinishTicket";
            case "TKT_TRY" -> "practiceTicket";
            default -> null;
        };
    }

    public static int repairCampaignsV2(JSONObject user) {
        if (user == null) {
            return 0;
        }
        JSONObject c = user.getJSONObject("campaignsV2");
        if (c == null) {
            c = new JSONObject(true);
            user.put("campaignsV2", c);
        }
        JSONObject open = c.getJSONObject("open");
        if (open == null) {
            open = new JSONObject(true);
            c.put("open", open);
        }
        JSONArray per = new JSONArray();
        per.add("camp_01");
        per.add("camp_02");
        per.add("camp_03");
        open.put("permanent", per);
        open.put("rotate", "camp_r_34");
        open.put("rGroup", "group_r_34");
        open.put("tGroup", "group_tr_75");
        open.put("tAllOpen", "group_tr_all_12");
        JSONArray training = new JSONArray();
        for (int i = 1; i <= 33; i++) {
            training.add(String.format("camp_r_%02d", i));
        }
        open.put("training", training);

        JSONObject oldInst = c.getJSONObject("instances");
        JSONObject inst = new JSONObject(true);
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add("camp_01");
        ids.add("camp_02");
        ids.add("camp_03");
        ids.add("camp_r_34");
        for (int i = 1; i <= 33; i++) {
            ids.add(String.format("camp_r_%02d", i));
        }
        for (String id : ids) {
            JSONObject keep = oldInst == null ? null : oldInst.getJSONObject(id);
            if (keep != null) {
                inst.put(id, keep);
            } else {
                JSONObject one = new JSONObject(true);
                one.put("maxKills", 0);
                JSONArray rs = new JSONArray();
                for (int i = 0; i < 8; i++) {
                    rs.add(0);
                }
                one.put("rewardStatus", rs);
                inst.put(id, one);
            }
        }
        c.put("instances", inst);
        if (c.get("campaignTotalFee") == null) {
            c.put("campaignTotalFee", 1200);
        }
        if (c.get("campaignCurrentFee") == null) {
            c.put("campaignCurrentFee", 0);
        }
        if (c.getJSONObject("sweepMaxKills") == null) {
            c.put("sweepMaxKills", new JSONObject(true));
        }
        if (c.getJSONObject("missions") == null) {
            JSONObject m = new JSONObject(true);
            for (int i = 1; i <= 4; i++) {
                m.put("exterminateActivity_" + i, 0);
            }
            c.put("missions", m);
        }
        return 1;
    }

    public static long defaultConsumableTs(String id, String type) {
        boolean timeLimited = "AP_SUPPLY".equalsIgnoreCase(type == null ? "" : type.trim())
                || (id != null && id.startsWith("ap_supply"));
        return timeLimited ? ArknightsApplication.getTimestamp() + 14L * 86400 : -1L;
    }

    public static int repairConsumableTs(JSONObject user) {
        if (user == null) {
            return 0;
        }
        JSONObject consumable = user.getJSONObject("consumable");
        if (consumable == null) {
            consumable = new JSONObject(true);
            user.put("consumable", consumable);
        }
        int fixed = 0;
        long ts = ArknightsApplication.getTimestamp() + 14L * 86400;
        for (String id : new ArrayList<>(consumable.keySet())) {
            if (!id.startsWith("ap_supply")) {
                continue;
            }
            JSONObject bucket = consumable.getJSONObject(id);
            if (bucket == null) {
                continue;
            }
            for (String key : new ArrayList<>(bucket.keySet())) {
                JSONObject entry = bucket.getJSONObject(key);
                if (entry != null && entry.getLongValue("ts") <= 0) {
                    entry.put("ts", ts);
                    entry.put("count", entry.getIntValue("count"));
                    bucket.put(key, entry);
                    fixed++;
                }
            }
        }
        // inventory 里放错的理智药（客户端只认 consumable）→ 搬进来
        JSONObject inventory = user.getJSONObject("inventory");
        if (inventory != null) {
            for (String id : new ArrayList<>(inventory.keySet())) {
                if (!id.startsWith("ap_supply")) {
                    continue;
                }
                int cnt = inventory.getIntValue(id);
                inventory.remove(id);
                if (cnt <= 0) {
                    continue;
                }
                JSONObject bucket = consumable.getJSONObject(id);
                if (bucket == null) {
                    bucket = new JSONObject(true);
                    consumable.put(id, bucket);
                }
                int slot = 1;
                while (bucket.containsKey(String.valueOf(slot))) {
                    slot++;
                }
                JSONObject entry = new JSONObject(true);
                entry.put("count", cnt);
                entry.put("ts", ts);
                bucket.put(String.valueOf(slot), entry);
                fixed++;
                ArknightsApplication.LOGGER.info("[syncData] 把 inventory 里的 {} ×{} 搬到 consumable", id, cnt);
            }
        }
        return fixed;
    }

    private static int resolveTKT(String TKT) {
        if (TKT == null) {
            return -1;
        }
        return switch (TKT.trim().toUpperCase(Locale.ROOT)) {
            case "TKT_GACHA" -> 0;
            case "TKT_GACHA_10" -> 1;
            case "EXCLUSIVE_TKT_GACHA", "EXCLUSIVE_TKT_GACHA_10" -> 2;
            default -> -1;
        };
    }

    public static boolean handleTenGachaCould(JSONArray itemList, JSONObject userData) {
        ArknightsApplication.LOGGER.info("检查itemList" + itemList);
        List<JSONObject> items = new ArrayList<>(2);
        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                Object o = itemList.get(i);
                if (o instanceof Map) {
                    items.add(itemList.getJSONObject(i));
                }
            }
        }
        ArknightsApplication.LOGGER.info("检查items" + items + " size=" + items.size());
        if (items.size() == 2) {
            JSONObject a = items.get(0);
            JSONObject b = items.get(1);
            String aId = a.getString("id");
            String bId = b.getString("id");
            int ACount = a.getInteger("count");
            int BCount = b.getInteger("count");
            return checkIsEnough(aId, ACount, userData) && checkIsEnough(bId, BCount, userData);
        } else if (items.size() == 1) {
            JSONObject only = items.get(0);
            String id = only.getString("id");
            int count = only.getInteger("count");
            return checkIsEnough(id, count, userData);
        }
        return false;
    }

    public static JSONObject handleTenGachaItem(JSONArray itemList,JSONObject userData) {
        List<JSONObject> items = new ArrayList<>(2);
        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                Object o = itemList.get(i);
                if (o instanceof Map) {
                    items.add(itemList.getJSONObject(i));
                }
            }
        }
        if (items.size() == 2) {
            JSONObject a = items.get(0);
            JSONObject b = items.get(1);
            String aId = a.getString("id");
            int ACount = a.getInteger("count");
            userData = costTenGachaItem(aId, ACount, userData);
            String bId = b.getString("id");
            int BCount = b.getInteger("count");
            userData = costTenGachaItem(bId, BCount, userData);
            return userData;
        } else if (items.size() == 1) {
            JSONObject only = items.get(0);
            String id = only.getString("id");
            int count = only.getInteger("count");
            return costTenGachaItem(id, count, userData);
        }
        return userData;
    }

    private static int checkId(String id){
        return switch (id) {
            case "4003" -> 0;
            case "7003" -> 1;
            case "7004" -> 2;
            default -> 3;
        };
    }

    private static final String MARK = "_GACHA";

    public static int parsePullCount(String gachaId) {
        if (gachaId == null) return -1;

        int idx = gachaId.indexOf(MARK);
        if (idx < 0) return -1;

        String rest = gachaId.substring(idx + MARK.length());
        // rest 只可能是: "", "_10", "_4位ID", "_10_4位ID"

        if (rest.equals("_10") || rest.startsWith("_10_")) {
            return 10;   // 十连（有无 4 位 ID 都算）
        }
        if (rest.isEmpty() || (rest.startsWith("_") && rest.length() == 5)) {
            return 1;    // 单抽（无 ID 或带 4 位 ID）
        }
        return -1;       // 格式异常
    }

    public static boolean checkIsEnough(String id, int count, JSONObject userData){
        int type = checkId(id);
        JSONObject status = userData.getJSONObject("status");
        if (type == 0) {
            int D = status.getInteger("diamondShard");
            return D >= count;
        }
        else if (type == 1) {
            int TKT = status.getInteger("gachaTicket");
            return TKT >= count;
        }
        else if (type == 2) {
            int TKT = status.getInteger("tenGachaTicket");
            return TKT >= count;
        }
        else if (type == 3) {
            JSONObject consumable = userData.getJSONObject("consumable");
            JSONObject A = consumable.getJSONObject(id);
            JSONObject TKT = getInnerObject(A);
            int counts = TKT.getInteger("count");
            return counts >= count;
        }
        return false;
    }

    private static JSONObject costTenGachaItem(String id, int count, JSONObject userData){
        int type = checkId(id);
        JSONObject status = userData.getJSONObject("status");
        if (type == 0) {
            int D = status.getInteger("diamondShard");
            userData.getJSONObject("status").put("diamondShard", D - count);
            return userData;
        }
        else if (type == 1) {
            int TKT = status.getInteger("gachaTicket");
            userData.getJSONObject("status").put("gachaTicket", TKT - count);
            return userData;
        }
        else if (type == 2) {
            int TKT = status.getInteger("tenGachaTicket");
            userData.getJSONObject("status").put("tenGachaTicket", TKT - count);
            return userData;
        }
        else if (type == 3) {
            int dropCount = parsePullCount(id);
            JSONObject consumable = userData.getJSONObject("consumable");
            JSONObject A = consumable.getJSONObject(id);
            JSONObject TKT = getInnerObject(A);
            int counts = TKT.getInteger("count");
            long ts  = TKT.getLong("ts");
            int me = getInnerNumber(A);
            JSONObject number = new JSONObject();
            JSONObject TKTc = new  JSONObject();
            if (dropCount == 1){
                number.put("count",counts - count);
                number.put("ts",ts);
                TKTc.put(String.valueOf(me), number);
                userData.getJSONObject("consumable").put(id, TKTc);
                return userData;
            }
            else if (dropCount == 10){
                number.put("count",counts - count);
                number.put("ts",ts);
                TKTc.put(String.valueOf(me), number);
                userData.getJSONObject("consumable").put(id, TKTc);
                return userData;
            }
        }
        return userData;
    }
}
