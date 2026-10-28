package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/charRotation")
public class charRotation {
    
    private static final int FLAG_PRESET_NAME = 1;          // 名字
    private static final int FLAG_HOME_BACKGROUND = 2;      // 背景
    private static final int FLAG_HOME_THEME = 4;           // 主题
    private static final int FLAG_PRESET_SECRETARY = 8;     // 秘书
    private static final int FLAG_PRESET_SLOTS = 16;        // 槽位

    @PostMapping("/updatePreset")
    public JSONObject updatePreset(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charRotation/updatePreset");
        ArknightsApplication.LOGGER.info("updatePreset:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONObject userData = holder.getJSONObject("user");

        String instId = jsonBody.getString("instId");
        int flag = jsonBody.getIntValue("flag");
        JSONObject data = jsonBody.getJSONObject("data");

        if (data == null) {
            response.setStatus(400);
            return createErrorResponse(400, "missing data");
        }

        JSONObject charRotation = ensureCharRotation(userData);
        JSONObject presets = charRotation.getJSONObject("preset");

        // instId 缺省时落到当前预设
        if (instId == null || instId.isEmpty()) {
            instId = charRotation.getString("current");
        }
        JSONObject preset = instId == null ? null : presets.getJSONObject(instId);
        if (preset == null) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.warn("[charRotation] 预设 {} 不存在，现有预设: {}",
                    instId, presets.keySet());
            return createErrorResponse(404, "preset_not_found: " + instId);
        }

        // 官方 updatePreset 只回**被改动的那几个字段**，所以先拍快照再 diff
        JSONObject snapshot = JSONObject.parseObject(preset.toJSONString());
        applyPresetUpdate(preset, flag, data);
        JSONObject updated = diffPreset(snapshot, preset);
        userDao.setUserData(account.getUid(), userData);

        ArknightsApplication.LOGGER.info("[charRotation] updatePreset instId={} flag={} 改动字段={}",
                instId, flag, updated.keySet());
        return deltaPresetUpdate(instId, updated);
    }

    private static final int MAX_PRESETS = 10;
    private static final String DEFAULT_PRESET_NAME = "未命名界面配置";

    @PostMapping("/createPreset")
    public JSONObject createPreset(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody(required = false) JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charRotation/createPreset");

        JSONObject userData = new JSONObject();
        Account account = auth(secret, response, userData);
        if (account == null) {
            return (JSONObject) userData.remove("__error");
        }
        userData = userData.getJSONObject("user");

        JSONObject charRotation = ensureCharRotation(userData);
        JSONObject presets = charRotation.getJSONObject("preset");
        if (presets.size() >= MAX_PRESETS) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.warn("[charRotation] 预设数量已达上限 {}/{}", presets.size(), MAX_PRESETS);
            return createErrorResponse(400, "preset_limit_reached: " + MAX_PRESETS);
        }

        int maxId = 0;
        for (String key : presets.keySet()) {
            try {
                maxId = Math.max(maxId, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        String newId = String.valueOf(maxId + 1);

        JSONObject preset = defaultPreset(presets, charRotation);
        presets.put(newId, preset);

        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[charRotation] createPreset -> instId={} 共 {} 个预设",
                newId, presets.size());

        JSONObject presetMap = new JSONObject(true);
        presetMap.put(newId, preset);
        JSONObject charRotationDelta = new JSONObject(true);
        charRotationDelta.put("preset", presetMap);
        JSONObject modified = new JSONObject(true);
        modified.put("charRotation", charRotationDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("instId", newId);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping({"/setCurrent", "/setCurrentPreset"})
    public JSONObject setCurrent(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody JSONObject jsonBody,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charRotation/setCurrent");
        ArknightsApplication.LOGGER.info("setCurrent:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONObject userData = holder.getJSONObject("user");

        String instId = jsonBody.getString("instId");
        JSONObject charRotation = ensureCharRotation(userData);
        JSONObject presets = charRotation.getJSONObject("preset");
        JSONObject preset = instId == null ? null : presets.getJSONObject(instId);
        if (preset == null) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.warn("[charRotation] setCurrent 预设 {} 不存在，现有: {}",
                    instId, presets.keySet());
            return createErrorResponse(404, "preset_not_found: " + instId);
        }

        charRotation.put("current", instId);

        // 连带同步主页展示字段
        String profile = preset.getString("profile");
        String secretary = profile;
        if (profile != null && profile.contains("#")) {
            secretary = profile.substring(0, profile.indexOf('#'));
        }
        boolean profileSp = preset.getBooleanValue("profileSp");
        String background = preset.getString("background");
        String homeTheme = preset.getString("homeTheme");

        JSONObject status = userData.getJSONObject("status");
        if (status != null) {
            if (secretary != null) {
                status.put("secretary", secretary);
            }
            if (profile != null) {
                status.put("secretarySkinId", profile);
            }
            status.put("secretarySkinSp", profileSp);
        }
        JSONObject backgroundNode = userData.getJSONObject("background");
        if (backgroundNode != null && background != null) {
            backgroundNode.put("selected", background);
        }
        JSONObject homeThemeNode = userData.getJSONObject("homeTheme");
        if (homeThemeNode != null && homeTheme != null) {
            homeThemeNode.put("selected", homeTheme);
        }

        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[charRotation] 当前预设 -> {}（秘书={} 背景={} 主题={}）",
                instId, secretary, background, homeTheme);

        JSONObject crDelta = new JSONObject(true);
        crDelta.put("current", instId);
        JSONObject modified = new JSONObject(true);
        modified.put("charRotation", crDelta);

        if (status != null) {
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("secretary", secretary);
            statusDelta.put("secretarySkinId", profile);
            statusDelta.put("secretarySkinSp", profileSp);
            modified.put("status", statusDelta);
        }
        if (background != null) {
            JSONObject bgDelta = new JSONObject(true);
            bgDelta.put("selected", background);
            modified.put("background", bgDelta);
        }
        if (homeTheme != null) {
            JSONObject themeDelta = new JSONObject(true);
            themeDelta.put("selected", homeTheme);
            modified.put("homeTheme", themeDelta);
        }

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping("/deletePreset")
    public JSONObject deletePreset(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charRotation/deletePreset");
        ArknightsApplication.LOGGER.info("deletePreset:" + jsonBody);

        JSONObject userData = new JSONObject();
        Account account = auth(secret, response, userData);
        if (account == null) {
            return (JSONObject) userData.remove("__error");
        }
        userData = userData.getJSONObject("user");

        String instId = jsonBody.getString("instId");
        JSONObject charRotation = ensureCharRotation(userData);
        JSONObject presets = charRotation.getJSONObject("preset");
        if (instId == null || !presets.containsKey(instId)) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.warn("[charRotation] deletePreset 预设 {} 不存在，现有: {}",
                    instId, presets.keySet());
            return createErrorResponse(404, "preset_not_found: " + instId);
        }
        if (presets.size() <= 1) {
            response.setStatus(400);
            return createErrorResponse(400, "cannot_delete_last_preset");
        }

        if (instId.equals(charRotation.getString("current"))) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.warn("[charRotation] 拒绝删除当前预设 {}", instId);
            return createErrorResponse(5847, "charRotation deletePreset can not delete current");
        }

        presets.remove(instId);

        userDao.setUserData(account.getUid(), userData);
        ArknightsApplication.LOGGER.info("[charRotation] deletePreset {} 完成，剩 {} 个，deleted 已下发",
                instId, presets.size());
        return deltaWithDeletedPreset(charRotation, instId, false);
    }

    private Account auth(String secret, HttpServletResponse response, JSONObject holder) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            holder.put("__error", createErrorResponse(400, "服务器维护中"));
            return null;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            holder.put("__error", createErrorResponse(2, "账户验证失败"));
            return null;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            holder.put("__error", createErrorResponse(403, "账号已被封禁"));
            return null;
        }
        holder.put("user", JSONObject.parseObject(account.getUser()));
        return account;
    }

    private static JSONObject ensureCharRotation(JSONObject userData) {
        JSONObject charRotation = userData.getJSONObject("charRotation");
        if (charRotation == null) {
            charRotation = new JSONObject(true);
            userData.put("charRotation", charRotation);
        }
        if (charRotation.getJSONObject("preset") == null) {
            charRotation.put("preset", new JSONObject(true));
        }
        return charRotation;
    }

    private static JSONObject defaultPreset(JSONObject presets, JSONObject charRotation) {
        String current = charRotation.getString("current");
        JSONObject base = current == null ? null : presets.getJSONObject(current);
        if (base == null && !presets.isEmpty()) {
            base = presets.getJSONObject(presets.keySet().iterator().next());
        }
        JSONObject preset = base != null
                ? JSONObject.parseObject(base.toJSONString())
                : new JSONObject(true);

        preset.put("name", DEFAULT_PRESET_NAME);
        if (!preset.containsKey("background")) {
            preset.put("background", "bg_rhodes_day");
        }
        if (!preset.containsKey("homeTheme")) {
            preset.put("homeTheme", "tm_rhodes_day");
        }
        if (!preset.containsKey("profile")) {
            preset.put("profile", "char_002_amiya#1");
        }
        if (!preset.containsKey("profileInst")) {
            preset.put("profileInst", 1);
        }
        if (!preset.containsKey("slots")) {
            JSONArray slots = new JSONArray();
            JSONObject slot = new JSONObject(true);
            slot.put("charId", "char_002_amiya");
            slot.put("skinId", "char_002_amiya#1");
            slots.add(slot);
            preset.put("slots", slots);
        }
        return preset;
    }

    private static JSONObject delta(JSONObject charRotation) {
        JSONObject modified = new JSONObject(true);
        modified.put("charRotation", charRotation);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static JSONObject diffPreset(JSONObject before, JSONObject after) {
        JSONObject updated = new JSONObject(true);
        for (String key : after.keySet()) {
            Object now = after.get(key);
            Object prev = before.get(key);
            boolean changed = prev == null ? now != null : !prev.equals(now);
            if (changed) {
                updated.put(key, now);
            }
        }
        return updated;
    }

    private static JSONObject deltaPresetUpdate(String instId, JSONObject updated) {
        JSONObject presetMap = new JSONObject(true);
        presetMap.put(instId, updated);
        JSONObject cr = new JSONObject(true);
        cr.put("preset", presetMap);
        JSONObject modified = new JSONObject(true);
        modified.put("charRotation", cr);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static JSONObject deltaWithDeletedPreset(JSONObject charRotation, String deletedInstId,
                                                     boolean currentChanged) {
        JSONObject modified = new JSONObject(true);
        if (currentChanged) {
            JSONObject cr = new JSONObject(true);
            cr.put("current", charRotation.getString("current"));
            modified.put("charRotation", cr);
        }

        JSONArray presetDelete = new JSONArray();
        presetDelete.add(deletedInstId);
        JSONObject charRotationDelete = new JSONObject(true);
        charRotationDelete.put("preset", presetDelete);

        JSONObject deleted = new JSONObject(true);
        deleted.put("charRotation", charRotationDelete);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", deleted);

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private void applyPresetUpdate(JSONObject preset, int flag, JSONObject data) {
        boolean singleBit = flag == FLAG_PRESET_NAME || flag == FLAG_HOME_BACKGROUND
                || flag == FLAG_HOME_THEME || flag == FLAG_PRESET_SECRETARY
                || flag == FLAG_PRESET_SLOTS;
        if (!singleBit) {
            applyFlagMask(preset, flag, data);
            return;
        }
        switch (flag) {
            case FLAG_PRESET_NAME:
                copyField(preset, data, "name");
                break;

            case FLAG_HOME_BACKGROUND:
                copyField(preset, data, "background");
                break;

            case FLAG_HOME_THEME:
                copyField(preset, data, "homeTheme");
                break;

            case FLAG_PRESET_SECRETARY:
                copyField(preset, data, "profile");
                copyIntField(preset, data, "profileInst");
                copyField(preset, data, "profileSp");
                copyRenamed(preset, data, "secretarySkinId", "profile");
                copyRenamed(preset, data, "secretaryCharInstId", "profileInst");
                copyRenamed(preset, data, "secretaryShowSpDynIllust", "profileSp");
                break;

            case FLAG_PRESET_SLOTS: {
                JSONArray slots = data.getJSONArray("slots");
                if (slots != null) {
                    preset.put("slots", normalizeSlots(slots));
                }
                break;
            }

            default:
                ArknightsApplication.LOGGER.warn("[charRotation] 未知 flag={}，按白名单整体合并", flag);
                for (String key : new String[]{"name", "background", "homeTheme", "profile", "profileSp"}) {
                    copyField(preset, data, key);
                }
                copyIntField(preset, data, "profileInst");
                JSONArray slots = data.getJSONArray("slots");
                if (slots != null) {
                    preset.put("slots", normalizeSlots(slots));
                }
                break;
        }
    }

    private void applyFlagMask(JSONObject preset, int flag, JSONObject data) {
        boolean changed = false;

        if ((flag & FLAG_PRESET_NAME) != 0) {
            changed |= copyIfNonNull(preset, data, "name");
        }
        if ((flag & FLAG_HOME_BACKGROUND) != 0) {
            changed |= copyIfNonNull(preset, data, "background");
        }
        if ((flag & FLAG_HOME_THEME) != 0) {
            changed |= copyIfNonNull(preset, data, "homeTheme");
        }
        if ((flag & FLAG_PRESET_SECRETARY) != 0) {
            changed |= copyIfNonNull(preset, data, "profile");
            changed |= copyIntIfNonNull(preset, data, "profileInst");
            changed |= copyBoolIfNonNull(preset, data, "profileSp");
        }
        if ((flag & FLAG_PRESET_SLOTS) != 0) {
            JSONArray slots = data.getJSONArray("slots");
            if (slots != null) {
                preset.put("slots", normalizeSlots(slots));
                changed = true;
            }
        }

        if (!changed) {
            ArknightsApplication.LOGGER.warn("[charRotation] flag={} 没有任何可更新字段（data 是否全为 null？）", flag);
        }
    }

    private static boolean copyIfNonNull(JSONObject dst, JSONObject src, String key) {
        if (!src.containsKey(key)) {
            return false;
        }
        Object v = src.get(key);
        if (v == null) {
            return false;
        }
        dst.put(key, v);
        return true;
    }

    private static boolean copyIntIfNonNull(JSONObject dst, JSONObject src, String key) {
        if (!src.containsKey(key)) {
            return false;
        }
        Object v = src.get(key);
        if (v == null) {
            return false;
        }
        if (v instanceof Number) {
            dst.put(key, ((Number) v).intValue());
            return true;
        }
        try {
            dst.put(key, Integer.parseInt(String.valueOf(v).trim()));
            return true;
        } catch (NumberFormatException e) {
            ArknightsApplication.LOGGER.warn("[charRotation] {} 不是数字: {}", key, v);
            return false;
        }
    }

    private static boolean copyBoolIfNonNull(JSONObject dst, JSONObject src, String key) {
        if (!src.containsKey(key)) {
            return false;
        }
        Object v = src.get(key);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            dst.put(key, v);
        } else if (v instanceof Number) {
            dst.put(key, ((Number) v).intValue() != 0);
        } else {
            String s = String.valueOf(v).trim();
            dst.put(key, "1".equals(s) || "true".equalsIgnoreCase(s));
        }
        return true;
    }

    private static void copyField(JSONObject dst, JSONObject src, String key) {
        if (src.containsKey(key) && src.get(key) != null) {
            dst.put(key, src.get(key));
        }
    }

    private static void copyRenamed(JSONObject dst, JSONObject src, String srcKey, String dstKey) {
        if (src.containsKey(srcKey)) {
            dst.put(dstKey, src.get(srcKey));
        }
    }

    private static void copyIntField(JSONObject dst, JSONObject src, String key) {
        if (!src.containsKey(key) || src.get(key) == null) {
            return;
        }
        Object v = src.get(key);
        if (v instanceof Number) {
            dst.put(key, ((Number) v).intValue());
        } else {
            try {
                dst.put(key, Integer.parseInt(String.valueOf(v).trim()));
            } catch (NumberFormatException e) {
                ArknightsApplication.LOGGER.warn("[charRotation] {} 不是数字: {}", key, v);
            }
        }
    }

    private static JSONArray normalizeSlots(JSONArray slots) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < slots.size(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            if (slot == null || slot.getString("charId") == null) {
                continue;
            }
            JSONObject clean = new JSONObject(true);
            clean.put("charId", slot.getString("charId"));
            String skinId = slot.getString("skinId");
            clean.put("skinId", skinId != null ? skinId : slot.getString("charId") + "#1");
            // 客户端发的是 0/1，存档里统一成 bool
            Object sp = slot.get("skinSp");
            if (sp instanceof Boolean) {
                clean.put("skinSp", sp);
            } else if (sp instanceof Number) {
                clean.put("skinSp", ((Number) sp).intValue() != 0);
            } else if (sp != null) {
                clean.put("skinSp", "1".equals(String.valueOf(sp).trim()));
            } else {
                clean.put("skinSp", false);
            }
            out.add(clean);
        }
        return out;
    }

    private JSONObject createErrorResponse(int code, String message) {
        JSONObject result = new JSONObject(true);
        result.put("result", code);
        result.put("msg", message);
        result.put("message", "server error");
        return result;
    }
}
