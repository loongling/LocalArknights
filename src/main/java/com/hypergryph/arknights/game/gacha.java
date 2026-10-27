package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.pojo.Account;

import java.sql.PseudoColumnUsage;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
import static com.hypergryph.arknights.core.work.consumableNumberG.getInnerNumber;
import static com.hypergryph.arknights.core.work.consumableNumberG.getInnerObject;
import static com.hypergryph.arknights.core.work.processArray.handleTenGachaCould;
import static com.hypergryph.arknights.core.work.processArray.handleTenGachaItem;

@RestController
@RequestMapping("/gacha")
public class gacha {
    private final ArknightsApplication arknightsApplication;

    public gacha(ArknightsApplication arknightsApplication) {
        this.arknightsApplication = arknightsApplication;
    }

    @PostMapping("/syncNormalGacha")
    public JSONObject syncNormalGacha(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/syncNormalGacha");
        ArknightsApplication.LOGGER.info("syncNG" + JsonBody);

        if (checkAccount(secret)) {
            List<Account> accounts = userDao.queryAccountBySecret(secret);

            Account account = accounts.get(0);
            JSONObject userData = JSONObject.parseObject(account.getUser());
            JSONObject result = new JSONObject();
            JSONObject playerDataDelta = new JSONObject();
            JSONObject modified = new JSONObject();
            modified.put("recruit", userData.get("recruit"));
            playerDataDelta.put("modified", modified);
            playerDataDelta.put("deleted", new JSONObject());
            result.put("playerDataDelta", playerDataDelta);
            return result;
        }
        else {
            createError(secret, response);
        }

        return new JSONObject();
    }

    @PostMapping("/normalGacha")
    public JSONObject normalGacha(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/normalGacha");
        ArknightsApplication.LOGGER.info("normalG:" + jsonBody);

        if (checkAccount(secret)) {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            Account account = accounts.get(0);

            // 获取请求参数
            int slotId = jsonBody.getIntValue("slotId");
            JSONArray tagList = jsonBody.getJSONArray("tagList");
            int duration = jsonBody.getIntValue("duration");
            int specialTagId = jsonBody.getIntValue("specialTagId");

            // 读取用户数据
            JSONObject userData = JSONObject.parseObject(account.getUser());
            JSONObject recruitSlot = userData.getJSONObject("recruit")
                    .getJSONObject("normal")
                    .getJSONObject("slots")
                    .getJSONObject(String.valueOf(slotId));

            // 检查招募许可
            int recruitLicense = userData.getJSONObject("status").getIntValue("recruitLicense");
            if (recruitLicense <= 0) {
                response.setStatus(400);
                return createErrorResponse(400, "招募许可不足");
            }

            // 更新招募信息
            long currentTime = System.currentTimeMillis() / 1000;
            recruitSlot.put("state", 2);
            recruitSlot.put("selectTags", createSelectTags(tagList));
            recruitSlot.put("startTs", currentTime);
            recruitSlot.put("durationInSec", duration);
            recruitSlot.put("maxFinishTs", currentTime + duration);
            recruitSlot.put("realFinishTs", currentTime + duration);
            recruitSlot.put("specialTagId", specialTagId);

            // 更新招募许可数量
            userData.getJSONObject("status").put("recruitLicense", recruitLicense - 1);

            // 保存用户数据
            userDao.setUserData(account.getUid(), userData);

            // 构建响应
            JSONObject result = new JSONObject();
            JSONObject playerDataDelta = new JSONObject();
            JSONObject modified = new JSONObject();
            modified.put("recruit", userData.getJSONObject("recruit"));
            modified.put("status", userData.getJSONObject("status"));
            playerDataDelta.put("modified", modified);
            playerDataDelta.put("deleted", new JSONObject());
            result.put("playerDataDelta", playerDataDelta);

            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping("/finishNormalGacha")
    public JSONObject finishNormalGacha(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/finishNormalGacha");
        ArknightsApplication.LOGGER.info("finishNG:" + jsonBody);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        int slotId = jsonBody.getIntValue("slotId");
        JSONObject userData = JSONObject.parseObject(account.getUser());

        JSONObject slots = userData.getJSONObject("recruit").getJSONObject("normal").getJSONObject("slots");
        JSONObject recruitSlot = slots == null ? null : slots.getJSONObject(String.valueOf(slotId));
        if (recruitSlot == null) {
            response.setStatus(400);
            return createErrorResponse(5754, "recruit_slot_not_found: " + slotId);
        }
        int slotState = recruitSlot.getIntValue("state");
        if (slotState != 2 && slotState != 3) {
            response.setStatus(400);
            return createErrorResponse(5754, "recruit_slot_state_error: state=" + slotState);
        }
        JSONArray selectTags = recruitSlot.getJSONArray("selectTags");
        // 招募时长决定稀有度区间；满 9 小时 + 资深/高级资深干员 tag 会触发保底。
        // durationInSec 由 /gacha/normalGacha 写入，异常值按 1 小时处理。
        int durationSec = recruitSlot.getIntValue("durationInSec");
        if (durationSec <= 0) {
            durationSec = 3600;
        }

        JSONObject gachaResult = performNormalGacha(userData, selectTags, durationSec);

        updateUserAfterNormalGacha(userData, gachaResult, slotId);
        userDao.setUserData(account.getUid(), userData);

        // 4) 构建响应
        //    客户端 FinishNormalGachaResponse = { int result; GachaResult charGet; }
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("charGet", gachaResult);

        JSONObject modified = new JSONObject(true);
        modified.put("recruit", userData.getJSONObject("recruit"));
        modified.put("status", userData.getJSONObject("status"));
        modified.put("troop", userData.getJSONObject("troop"));
        // building.chars 也会新增角色，inventory 里有潜能材料，必须一并下发
        modified.put("building", userData.getJSONObject("building"));
        modified.put("inventory", userData.getJSONObject("inventory"));

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);

        ArknightsApplication.LOGGER.info("[gacha] finishNormalGacha slot={} -> {} (isNew={})",
                slotId, gachaResult.getString("charId"), gachaResult.getBooleanValue("isNew"));
        return result;
    }

    @PostMapping("/boostNormalGacha")
    public JSONObject boostNormalGacha(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject jsonBody,
                                       HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + clientIp + "] /gacha/boostNormalGacha");
        ArknightsApplication.LOGGER.info("boostNG:" + jsonBody);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        int slotId = jsonBody.getIntValue("slotId");

        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject slots = userData.getJSONObject("recruit").getJSONObject("normal").getJSONObject("slots");
        JSONObject recruitSlot = slots == null ? null : slots.getJSONObject(String.valueOf(slotId));
        if (recruitSlot == null) {
            response.setStatus(400);
            return createErrorResponse(5754, "recruit_slot_not_found: " + slotId);
        }
        int slotState = recruitSlot.getIntValue("state");
        if (slotState != 2) {
            response.setStatus(400);
            return createErrorResponse(5755, "recruit_slot_not_busy: state=" + slotState);
        }

        boolean lenient = true;
        try {
            lenient = ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug");
        } catch (Exception ignored) {
        }

        JSONObject status = userData.getJSONObject("status");
        int ticket = status.getIntValue("instantFinishTicket");

        if (ticket > 0) {
            status.put("instantFinishTicket", ticket - 1);
        } else if (!lenient) {
            response.setStatus(400);
            return createErrorResponse(5012, "no instant finish ticket");
        } else {
            ArknightsApplication.LOGGER.warn("[gacha] 没有加急许可但 debug 放行加速: slot={}", slotId);
        }

        // 加速：直接进入可领取状态
        long now = ArknightsApplication.getTimestamp();
        recruitSlot.put("state", 3);                 // State.FAST_FINISH
        recruitSlot.put("realFinishTs", now);
        if (recruitSlot.getLongValue("maxFinishTs") > now) {
            recruitSlot.put("maxFinishTs", now);
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject modified = new JSONObject(true);
        modified.put("recruit", userData.getJSONObject("recruit"));
        modified.put("status", status);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[gacha] boostNormalGacha slot={} 完成，剩余加急许可={}",
                slotId, status.getIntValue("instantFinishTicket"));
        return result;
    }

    @PostMapping("/getPoolDetail")
    public JSONObject getPoolDetail(@RequestBody JSONObject jsonBody, HttpServletResponse response) {
        String poolId = jsonBody.getString("poolId");
        String poolPath = "data/gacha/" + poolId + ".json";
        ArknightsApplication.LOGGER.info("getPoolD:" + jsonBody);


        JSONObject poolData = IOTools.ReadJsonFile(poolPath);
        if (poolData == null) {
            response.setStatus(500);
            return createErrorResponse(500, "卡池数据读取失败");
        }

        return poolData;
    }

    @PostMapping("/advancedGacha")
    public JSONObject advancedGacha(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/advancedGacha");
        ArknightsApplication.LOGGER.info("advanceG:" + jsonBody);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        String poolId = jsonBody.getString("poolId");
        int useTkt = jsonBody.getIntValue("useTkt");
        int diamondCost = poolId.startsWith("BOOT") ? 380 : 600;
        JSONObject userdata = JSON.parseObject(account.getUser());
        JSONObject gacha = userdata.getJSONObject("gacha");
        Long uid = account.getUid();
        JSONObject defaultGacha = new JSONObject();
        int cntD = 1;
        int maxCnt = 10;
        int rarity = 4;
        boolean avail = true;
        defaultGacha.put("rarity", rarity);
        defaultGacha.put("maxCnt", maxCnt);
        defaultGacha.put("avail", avail);
        defaultGacha.put("cnt", cntD);
        String itemId = "gachaTicket";
        if (jsonBody.getString("itemId") != null) {
            itemId = jsonBody.getString("itemId");
        }
        if (poolId.startsWith("BOOT")){
            int cnt = gacha.getJSONObject("newbee").getIntValue("cnt");
            gacha.getJSONObject("newbee").put("cnt", cnt -1);
            userDao.setUserData(uid, userdata);
        }

        else if(poolId.startsWith("CLASSIC")){
            gacha.getJSONObject("fesClassic").put(poolId, defaultGacha);
            userdata.put("gacha", gacha);
            userDao.setUserData(uid, userdata);
        } else if(poolId.startsWith("NORM")){
            gacha.getJSONObject("normal").put(poolId, defaultGacha);
            userdata.put("gacha", gacha);
            userDao.setUserData(uid, userdata);
        } else if(poolId.startsWith("GACHA")){}
        return performAdvancedGacha(account, poolId, itemId, diamondCost, 1, useTkt, response, new JSONArray());
    }

    @PostMapping("/tenAdvancedGacha")
    public JSONObject tenAdvancedGacha(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/tenAdvancedGacha");
        ArknightsApplication.LOGGER.info("TAG:" + jsonBody);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        String poolId = jsonBody.getString("poolId");
        int useTkt = jsonBody.getIntValue("useTkt");
        JSONArray itemList = jsonBody.getJSONArray("itemList");
        int diamondCost = poolId.startsWith("BOOT") ? 380 : 600;

        return performAdvancedGacha(account, poolId, "tenGachaTicket", diamondCost, 10, useTkt, response, itemList);
    }

    @GetMapping("/activity")
    public JSONObject activity(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /activity");
        ArknightsApplication.LOGGER.info("/gache/activity:" + jsonBody);
        return new JSONObject();
    }

    private JSONObject createErrorResponse(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("result", code);
        result.put("msg", message);
        result.put("message", "server error");
        return result;
    }

    private JSONArray createSelectTags(JSONArray tagList) {
        JSONArray selectTags = new JSONArray();
        for (int i = 0; i < tagList.size(); i++) {
            JSONObject tag = new JSONObject();
            tag.put("pick", 1);
            tag.put("tagId", tagList.getString(i));
            selectTags.add(tag);
        }
        return selectTags;
    }

    // ────────────────────────────────────────────────────────────
    // 公开招募：tag 表 / 稀有度规则 / 池过滤 / tag 刷新
    // ────────────────────────────────────────────────────────────

    /** 满时长（9 小时）；官方：含「高级资深干员」必得 6★，含「资深干员」必得 5★ */
    private static final int RECRUIT_FULL_DURATION_SEC = 9 * 3600;

    private static final int TAG_TOP_OPERATOR = 11;   // 高级资深干员 -> rarityRank 5（6★）
    private static final int TAG_SENIOR = 14;         // 资深干员     -> rarityRank 4（5★）

    private static JSONObject GACHA_TABLE;

    /** gacha_table.json 懒加载 */
    private static synchronized JSONObject gachaTable() {
        if (GACHA_TABLE == null) {
            try {
                GACHA_TABLE = IOTools.ReadJsonFile(
                        System.getProperty("user.dir") + "/data/excel/gacha_table.json");
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[gacha] 读取 gacha_table.json 失败: {}", e.toString());
            }
            if (GACHA_TABLE == null) {
                GACHA_TABLE = new JSONObject();
            }
        }
        return GACHA_TABLE;
    }

    /** gachaTags（tagId -> tagName） */
    private static JSONArray gachaTags() {
        JSONArray tags = gachaTable().getJSONArray("gachaTags");
        return tags == null ? new JSONArray() : tags;
    }

    /**
     * 满 9 小时 + 勾了特殊 tag 时，强制稀有度。
     * 依据 gacha_table.specialTagRarityTable：{"11":[5], "14":[4]}
     * 高级资深干员优先于资深干员。
     *
     * @return null 表示不强制
     */
    private static Integer forcedRarity(Set<Integer> tagIds, int durationSec) {
        if (tagIds == null || tagIds.isEmpty() || durationSec < RECRUIT_FULL_DURATION_SEC) {
            return null;
        }
        if (tagIds.contains(TAG_TOP_OPERATOR)) {
            return 5;                       // 6★
        }
        if (tagIds.contains(TAG_SENIOR)) {
            return 4;                       // 5★
        }
        return null;
    }

    /**
     * 招募时长决定的稀有度区间。
     * gacha_table.recruitRarityTable 的 key 是**分钟数**（recruitTimeTable 的 timeLength）：
     *   <=230 分钟 -> 0..3   240..450 -> 1..4   460..540 -> 2..4
     *
     * @return {min, max}
     */
    private static int[] rarityRangeByDuration(int durationSec) {
        int minutes = Math.max(10, durationSec / 60);
        JSONObject table = gachaTable().getJSONObject("recruitRarityTable");
        int min = 0;
        int max = 4;
        if (table != null && !table.isEmpty()) {
            JSONObject entry = table.getJSONObject(String.valueOf(minutes));
            if (entry == null) {
                int best = -1;
                for (String k : table.keySet()) {
                    try {
                        int kv = Integer.parseInt(k);
                        if (kv <= minutes && kv > best) {
                            best = kv;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (best > 0) {
                    entry = table.getJSONObject(String.valueOf(best));
                }
            }
            if (entry != null) {
                min = entry.getIntValue("rarityStart");
                max = entry.getIntValue("rarityEnd");
            }
        }
        return new int[]{min, max};
    }

    private static String tagNameOf(int tagId) {
        JSONArray tags = gachaTags();
        for (int i = 0; i < tags.size(); i++) {
            JSONObject t = tags.getJSONObject(i);
            if (t != null && t.getIntValue("tagId") == tagId) {
                return t.getString("tagName");
            }
        }
        return null;
    }

    /** 按稀有度区间过滤招募池；forced != null 时只保留该稀有度 */
    private static JSONArray filterPoolByRarity(JSONArray perAvailList, int min, int max, Integer forced) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < perAvailList.size(); i++) {
            JSONObject bucket = perAvailList.getJSONObject(i);
            int rank = bucket.getIntValue("rarityRank");
            if (forced != null) {
                if (rank != forced) {
                    continue;
                }
            } else if (rank < min || rank > max) {
                continue;
            }
            out.add(bucket);
        }
        return out;
    }

    /** 从 selectTags 里取出 tagId 集合 */
    private static Set<Integer> tagIdSet(JSONArray selectTags) {
        Set<Integer> ids = new HashSet<>();
        if (selectTags != null) {
            for (int i = 0; i < selectTags.size(); i++) {
                JSONObject t = selectTags.getJSONObject(i);
                if (t != null) {
                    ids.add(t.getIntValue("tagId"));
                }
            }
        }
        return ids;
    }

    /** 刷新一个招募位的 5 个 tag（客户端 SlotModel.tags 是 int 数组） */
    private JSONArray rollRecruitTags() {
        JSONArray all = gachaTags();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ids.add(all.getJSONObject(i).getIntValue("tagId"));
        }
        Collections.shuffle(ids);
        JSONArray out = new JSONArray();
        for (int i = 0; i < Math.min(5, ids.size()); i++) {
            out.add(ids.get(i));
        }
        return out;
    }

    /** 职业 tag（1~8）→ character_table.profession */
    private static final Map<Integer, String> PROFESSION_TAG = Map.of(
            1, "WARRIOR",    // 近卫干员
            2, "SNIPER",     // 狙击干员
            3, "TANK",       // 重装干员
            4, "MEDIC",      // 医疗干员
            5, "SUPPORT",    // 辅助干员
            6, "CASTER",     // 术师干员
            7, "SPECIAL",    // 特种干员
            8, "PIONEER");   // 先锋干员

    private static boolean charMatchesTag(String charId, int tagId, String tagName) {
        JSONObject ch = ArknightsApplication.characterJson.getJSONObject(charId);
        if (ch == null) {
            return false;
        }
        String prof = PROFESSION_TAG.get(tagId);
        if (prof != null) {
            return prof.equals(ch.getString("profession"));
        }
        switch (tagId) {
            case 9: {                       // 近战位
                String p = ch.getString("position");
                return "MELEE".equals(p) || "ALL".equals(p);
            }
            case 10: {                      // 远程位
                String p = ch.getString("position");
                return "RANGED".equals(p) || "ALL".equals(p);
            }
            case 11:                        // 高级资深干员 = 6★
                return "TIER_6".equals(ch.getString("rarity"));
            case 14:                        // 资深干员 = 5★
                return "TIER_5".equals(ch.getString("rarity"));
            case 1012:                      // 男性干员：数据里没有 gender，视为不限制
            case 1013:                      // 女性干员
            case 29:                        // 元素：tagList 里没有，视为不限制
                return true;
            default: {
                JSONArray tagList = ch.getJSONArray("tagList");
                return tagList != null && tagName != null && tagList.contains(tagName);
            }
        }
    }

    private JSONArray filterRecruitPool(JSONArray perAvailList, JSONArray selectTags) {
        Set<Integer> wantedIds = tagIdSet(selectTags);
        if (wantedIds.isEmpty()) {
            return perAvailList;
        }
        if (wantedIds.contains(TAG_TOP_OPERATOR)) {
            wantedIds.remove(TAG_SENIOR);
        }

        JSONArray out = new JSONArray();
        int kept = 0;
        for (int i = 0; i < perAvailList.size(); i++) {
            JSONObject bucket = perAvailList.getJSONObject(i);
            JSONArray ids = bucket.getJSONArray("charIdList");
            if (ids == null) {
                continue;
            }
            JSONArray keptIds = new JSONArray();
            for (int j = 0; j < ids.size(); j++) {
                String cid = ids.getString(j);
                boolean ok = true;
                for (Integer tagId : wantedIds) {
                    if (!charMatchesTag(cid, tagId, tagNameOf(tagId))) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    keptIds.add(cid);
                }
            }
            if (keptIds.isEmpty()) {
                continue;
            }
            JSONObject nb = JSONObject.parseObject(bucket.toJSONString());
            nb.put("charIdList", keptIds);
            out.add(nb);
            kept += keptIds.size();
        }
        if (kept == 0) {
            ArknightsApplication.LOGGER.info("[gacha] tag {} 无匹配角色，退回全池", wantedIds);
            return perAvailList;
        }
        ArknightsApplication.LOGGER.info("[gacha] tag {} 过滤后剩 {} 个角色", wantedIds, kept);
        return out;
    }

    private JSONObject performNormalGacha(JSONObject userData, JSONArray selectTags, int durationSec) {
        JSONObject troop = userData.getJSONObject("troop");
        if (troop == null) {
            troop = new JSONObject(true);
            userData.put("troop", troop);
        }
        JSONObject chars = troop.getJSONObject("chars");
        if (chars == null) {
            chars = new JSONObject(true);
            troop.put("chars", chars);
        }

        // 注意：基建干员在 userData.building.chars，
        // 不是 troop.building.chars（那个路径不存在，会 NPE）
        JSONObject building = userData.getJSONObject("building");
        if (building == null) {
            building = new JSONObject(true);
            userData.put("building", building);
        }
        JSONObject buildingChars = building.getJSONObject("chars");
        if (buildingChars == null) {
            buildingChars = new JSONObject(true);
            building.put("chars", buildingChars);
        }
        JSONArray allPool = ArknightsApplication.normalGachaData.getJSONObject("detailInfo")
                .getJSONObject("availCharInfo")
                .getJSONArray("perAvailList");

        // ① 先按勾选的 tag 过滤
        JSONArray tagged = filterRecruitPool(allPool, selectTags);

        // ② 再按「招募时长区间 + 满 9 小时的特殊 tag 保底」过滤稀有度
        Set<Integer> tagIds = tagIdSet(selectTags);
        int[] range = rarityRangeByDuration(durationSec);
        Integer forced = forcedRarity(tagIds, durationSec);
        JSONArray availCharInfo = filterPoolByRarity(tagged, range[0], range[1], forced);

        if (forced != null) {
            ArknightsApplication.LOGGER.info("[gacha] 满 9 小时 + 特殊 tag {} → 保底 rarityRank {}",
                    forced == 5 ? "高级资深干员" : "资深干员", forced);
        } else {
            ArknightsApplication.LOGGER.info("[gacha] 时长 {}s → rarityRank {}-{}", durationSec, range[0], range[1]);
        }

        if (availCharInfo.isEmpty()) {
            // 兜底顺序很重要：
            //   时长决定的稀有度区间是硬规则（1 小时不可能出 6★），
            //   tag 只是玩家的选择 → 先放宽 tag，保留稀有度约束。
            availCharInfo = filterPoolByRarity(allPool, range[0], range[1], forced);
            if (availCharInfo.isEmpty()) {
                availCharInfo = allPool;
                ArknightsApplication.LOGGER.info("[gacha] tag/稀有度过滤后为空，已回退全池");
            } else {
                ArknightsApplication.LOGGER.info("[gacha] tag 过滤后为空，已放宽 tag、保留稀有度（剩 {} 档）",
                        availCharInfo.size());
            }
        }

        // 构建概率数组
        List<JSONObject> randomRankList = new ArrayList<>();
        for (int i = 0; i < availCharInfo.size(); i++) {
            JSONObject charInfo = availCharInfo.getJSONObject(i);
            int totalPercent = (int)(charInfo.getDoubleValue("totalPercent") * 100);
            int rarityRank = charInfo.getIntValue("rarityRank");

            JSONObject rankObj = new JSONObject();
            rankObj.put("rarityRank", rarityRank);
            rankObj.put("index", i);

            for (int j = 0; j < totalPercent; j++) {
                randomRankList.add(rankObj);
            }
        }

        // 随机选择
        Collections.shuffle(randomRankList);
        JSONObject selectedRank = randomRankList.get(new Random().nextInt(randomRankList.size()));
        JSONArray charIdList = availCharInfo.getJSONObject(selectedRank.getIntValue("index"))
                .getJSONArray("charIdList");
        // 注意：charIdList 是 normalGacha.json 里的共享数组，直接 shuffle 会污染全局静态数据
        List<String> charPool = new ArrayList<>(charIdList.size());
        for (int i = 0; i < charIdList.size(); i++) {
            charPool.add(charIdList.getString(i));
        }
        Collections.shuffle(charPool);
        String charId = charPool.get(new Random().nextInt(charPool.size()));

        // 检查是否已有该角色
        int charInstId = 0;
        for (String key : chars.keySet()) {
            if (chars.getJSONObject(key).getString("charId").equals(charId)) {
                charInstId = Integer.parseInt(key);
                break;
            }
        }

        JSONObject result = new JSONObject();
        JSONArray itemGet = new JSONArray();
        boolean isNew = charInstId == 0;

        if (isNew) {
            // 新角色处理
            Pattern pattern = Pattern.compile("char_(\\d+)_");
            Matcher matcher = pattern.matcher(charId);
            ArknightsApplication.LOGGER.info("rewardSId:" + charId);

            int CharId = 0;
            if (matcher.find()) {
                CharId = Integer.parseInt(matcher.group(1));
                ArknightsApplication.LOGGER.info("提取到的数字是: " + CharId);
            } else {
                ArknightsApplication.LOGGER.info("未匹配到数字");
            }
            charInstId = CharId;
            JSONObject newChar = createNewCharacter(charId, charInstId);
            chars.put(String.valueOf(charInstId), newChar);

            // 添加基建数据
            JSONObject buildingChar = createBuildingCharacter(charId);
            buildingChars.put(String.valueOf(charInstId), buildingChar);

            // 添加获得物品
            JSONObject hggShard = new JSONObject();
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);

            // 更新库存
            userData.getJSONObject("status").put("hggShard",
                    userData.getJSONObject("status").getIntValue("hggShard") + 1);

            // 更新角色组
            JSONObject charGroup = new JSONObject();
            charGroup.put("favorPoint", 0);
            userData.getJSONObject("troop").getJSONObject("charGroup").put(charId, charGroup);
        } else {
            // 重复角色处理
            JSONObject existingChar = chars.getJSONObject(String.valueOf(charInstId));
            int rarity = selectedRank.getIntValue("rarityRank");
            int potentialRank = existingChar.getIntValue("potentialRank");

            // 根据稀有度添加物品
            JSONObject shardItem = new JSONObject();
            if (rarity <= 3) {
                shardItem.put("type", "LGG_SHD");
                shardItem.put("id", "4005");
                shardItem.put("count", rarity == 3 ? 30 : (rarity == 2 ? 5 : 1));
                userData.getJSONObject("status").put("lggShard",
                        userData.getJSONObject("status").getIntValue("lggShard") +
                                shardItem.getIntValue("count"));
            } else {
                shardItem.put("type", "HGG_SHD");
                shardItem.put("id", "4004");
                shardItem.put("count", rarity == 5 ?
                        (potentialRank < 5 ? 10 : 15) :
                        (potentialRank < 5 ? 5 : 8));
                userData.getJSONObject("status").put("hggShard",
                        userData.getJSONObject("status").getIntValue("hggShard") +
                                shardItem.getIntValue("count"));
            }
            itemGet.add(shardItem);

            // 添加潜能
            JSONObject potentialItem = new JSONObject();
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + charId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);
            userData.getJSONObject("inventory").put("p_" + charId,
                    userData.getJSONObject("inventory").getIntValue("p_" + charId) + 1);
        }

        // GachaResult.potent = {delta, now}；本实现不自动提升潜能，delta 如实报 0
        JSONObject nowChar = chars.getJSONObject(String.valueOf(charInstId));
        int nowPotential = nowChar == null ? 0 : nowChar.getIntValue("potentialRank");
        JSONObject potent = new JSONObject(true);
        potent.put("delta", 0);
        potent.put("now", nowPotential);

        result.put("charId", charId);
        result.put("charInstId", charInstId);
        result.put("isNew", isNew);          // 客户端 GachaResult.isNew 是 bool
        result.put("itemGet", itemGet);
        result.put("potent", potent);

        return result;
    }

    private JSONObject createNewCharacter(String charId, int instId) {

        JSONArray skilsArray = ArknightsApplication.characterJson.getJSONObject(charId).getJSONArray("skills");
        JSONArray skils = new JSONArray();
        for (int i = 0; i < skilsArray.size(); i++) {
            JSONObject new_skils = new JSONObject(true);
            new_skils.put("skillId", skilsArray.getJSONObject(i).getString("skillId"));
            new_skils.put("state", 0);
            new_skils.put("specializeLevel", 0);
            new_skils.put("completeUpgradeTime", -1);

            if (skilsArray.getJSONObject(i).getJSONObject("unlockCond").getString("phase").equals("PHASE_0")) {
                new_skils.put("unlock", 1);
            } else {
                new_skils.put("unlock", 0);
            }

            skils.add(new_skils);
        }
        JSONObject charData = new JSONObject();
        charData.put("charId", charId);
        charData.put("instId", instId);
        charData.put("level", 1);
        charData.put("exp", 0);
        charData.put("evolvePhase", 0);
        charData.put("favorPoint", 0);
        charData.put("potentialRank", 0);
        charData.put("mainSkillLvl", 1);
        charData.put("skin", charId + "#1");
        charData.put("gainTime", System.currentTimeMillis() / 1000);

        // 初始化技能
        JSONArray skills = new JSONArray();
        JSONObject charInfo = ArknightsApplication.characterJson.getJSONObject(charId);
        for (Object skillObj : charInfo.getJSONArray("skills")) {
            JSONObject skill = (JSONObject) skillObj;
            JSONObject newSkill = new JSONObject();
            JSONObject unlockCond = skill.getJSONObject("unlockCond");

            String phase = unlockCond != null ? unlockCond.getString("phase") : "";
            int unlock = "PHASE_0".equals(phase) ? 1 : 0;
            newSkill.put("skillId", skill.getString("skillId"));
            newSkill.put("state", 0);
            newSkill.put("specializeLevel", 0);
            newSkill.put("completeUpgradeTime", -1);
            newSkill.put("unlock", unlock);
            skills.add(newSkill);
        }
        charData.put("skills", skills);
        charData.put("currentEquip", null);
        JSONObject equip = new JSONObject();
        charData.put("equip", equip);
        // 设置语音语言
        charData.put("voiceLan", "JP");
        charData.put("defaultSkillIndex", -1);
        charData.put("starMark", skils.isEmpty() ? -1 : 0);

        return charData;
    }

    private JSONObject createBuildingCharacter(String charId) {
        JSONObject buildingChar = new JSONObject();
        buildingChar.put("charId", charId);
        buildingChar.put("lastApAddTime", System.currentTimeMillis() / 1000);
        buildingChar.put("ap", 8640000);
        buildingChar.put("roomSlotId", "");
        buildingChar.put("index", -1);
        buildingChar.put("changeScale", 0);
        JSONObject bubble = new JSONObject();
        JSONObject normal = new JSONObject();
        normal.put("add", -1);
        normal.put("ts", 0);
        bubble.put("normal", normal);

        JSONObject assist = new JSONObject();
        assist.put("add", -1);
        assist.put("ts", 0);
        bubble.put("assist", assist);

        buildingChar.put("bubble", bubble);
        buildingChar.put("workTime", 0);

        return buildingChar;
    }

    private void updateUserAfterNormalGacha(JSONObject userData, JSONObject gachaResult, int slotId) {
        // 重置招募位：BUSY/FAST_FINISH -> IDLE，清空计时并刷新一组新 tag
        JSONObject slots = userData.getJSONObject("recruit")
                .getJSONObject("normal")
                .getJSONObject("slots");
        if (slots == null) {
            ArknightsApplication.LOGGER.warn("[gacha] recruit.normal.slots 不存在，跳过重置");
            return;
        }
        JSONObject recruitSlot = slots.getJSONObject(String.valueOf(slotId));
        if (recruitSlot == null) {
            ArknightsApplication.LOGGER.warn("[gacha] 招募位 {} 不存在，跳过重置", slotId);
            return;
        }
        recruitSlot.put("state", 1);                    // State.IDLE
        recruitSlot.put("selectTags", new JSONArray());
        recruitSlot.put("startTs", -1);
        recruitSlot.put("maxFinishTs", -1);
        recruitSlot.put("realFinishTs", -1);
        recruitSlot.put("durationInSec", -1);
        recruitSlot.remove("specialTagId");
        recruitSlot.put("tags", rollRecruitTags());     // 换一组新 tag
    }

    private JSONObject createEmptyPoolResponse() {
        JSONObject result = new JSONObject();
        result.put("result", 1);
        result.put("errMsg", "卡池数据不存在");
        return result;
    }

    private JSONObject performAdvancedGacha(Account account, String poolId,
                                            String ticketType, int diamondCost,
                                            int drawCount, int useTkt,
                                            HttpServletResponse response, JSONArray itemList) {
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject status = userData.getJSONObject("status");

        if (drawCount == 1) {
            if (useTkt == 1 || useTkt == 2) {
                if (status.getIntValue(ticketType) < drawCount) {
                    return createErrorResponse(5771, "gacha_tkt_state_error");
                }
            } else if (useTkt == 4) {
                JSONObject TKT = userData.getJSONObject("consumable").getJSONObject(ticketType);
                JSONObject A = getInnerObject(TKT);
                if (A.getInteger("count") < drawCount) {
                    return createErrorResponse(5771, "gacha_tkt_state_error");
                }
            }
            else {
                if (status.getIntValue("diamondShard") < diamondCost * drawCount) {
                    return createErrorResponse(5647, "not enough diamond shard");
                }
            }
        }
        else if (drawCount == 10) {
            ArknightsApplication.LOGGER.info("10抽");
            if (!handleTenGachaCould(itemList, userData)){
                return createErrorResponse(5771, "gacha_tkt_state_error");
            }
        }

        // 读取卡池数据
        String poolPath = "data/gacha/" + poolId + ".json";
        JSONObject poolData = IOTools.ReadJsonFile(poolPath);
        ArknightsApplication.LOGGER.info("PoolData" + poolData);
        if (poolData == null) {
            poolData = IOTools.ReadJsonFile("data/gacha/DEFAULT.json");
            if (poolData == null) {
                response.setStatus(500);
                return createErrorResponse(500, "卡池数据加载失败");
            }
        }

        // 获取保底数据
        JSONObject UserSyncData = JSON.parseObject(account.getUser());
        JSONObject exGachaData = UserSyncData.getJSONObject("exgachadata");
        if (exGachaData == null) {
            exGachaData = new JSONObject();
        }

        String gachaType = poolId.startsWith("CLASSIC") ? "CLASSIC" :
                poolId.startsWith("BOOT") ? "BOOT" : "NORM";
        int gachaCount = exGachaData.getIntValue(gachaType);

        JSONArray gachaResults = new JSONArray();
        JSONObject chars = userData.getJSONObject("troop").getJSONObject("chars");
        JSONObject buildingChars = userData.getJSONObject("building").getJSONObject("chars");
        JSONArray availCharInfo = poolData.getJSONObject("detailInfo")
                .getJSONObject("availCharInfo")
                .getJSONArray("perAvailList");

        for (int i = 0; i < drawCount; i++) {
            // 执行单次抽卡
            JSONObject gachaResult = performSingleGacha(userData, chars, buildingChars,
                    availCharInfo, gachaCount, poolId);
            gachaResults.add(gachaResult);

            // 更新保底计数
            if (gachaResult.getIntValue("rarity") == 5) {
                gachaCount = 0;
            } else {
                gachaCount++;
            }
        }
        Long uid = account.getUid();

        // 保存保底数据
        exGachaData.put(gachaType, gachaCount);

        // 扣除资源
        if (drawCount == 1) {
            if (useTkt == 1 || useTkt == 2) {
                status.put(ticketType, status.getIntValue(ticketType) - drawCount);
            }
            else if (useTkt == 4) {
                JSONObject TKT = userData.getJSONObject("consumable").getJSONObject(ticketType);
                JSONObject A = getInnerObject(TKT);
                JSONObject TKY = new JSONObject();
                int me = getInnerNumber(TKT);
                int o_count = A.getIntValue("count");
                A.put("count", o_count - 1);
                TKY.put(String.valueOf(me), A);
                userData.getJSONObject("consumable").put(ticketType, TKY);
            }
            else {
                status.put("diamondShard", status.getIntValue("diamondShard") - diamondCost * drawCount);
            }
        }
        else if(drawCount == 10) {
            userData = handleTenGachaItem(itemList, userData);
        }

        //处理 dexNav
        JSONObject dexNav0 = performDexNav(gachaResults, userData);
        JSONObject dexNav = dexNav0.getJSONObject("dexNav");

        // 保存用户数据
        userDao.setUserData(account.getUid(), userData);

        // 构建响应
        JSONObject result = new JSONObject();
        result.put("result", 0);

        if (drawCount == 1) {
            result.put("charGet", gachaResults.getJSONObject(0));
        } else {
            result.put("gachaResultList", gachaResults);
        }
        JSONObject gacha = userData.getJSONObject("gacha");
        if(poolId.startsWith("BOOT")) {
            int cnt = gacha.getJSONObject("newbee").getIntValue("cnt");
            if(cnt - drawCount < 0){
                return createErrorResponse(400, "新手池抽卡数不足");
            }
            else {
                gacha.getJSONObject("newbee").put("cnt", gacha.getJSONObject("newbee").getIntValue("cnt") - drawCount);
                userDao.setUserData(uid, userData);
            }
        }

        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        modified.put("status", status);
        modified.put("troop", userData.getJSONObject("troop"));
        modified.put("inventory", userData.getJSONObject("inventory"));
        modified.put("consumable", userData.getJSONObject("consumable"));
        modified.put("gacha", gacha);
        modified.put("dexNav", dexNav);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("result" + result);
        return result;
    }

    private JSONObject performSingleGacha(JSONObject userData, JSONObject chars,
                                          JSONObject buildingChars, JSONArray availCharInfo,
                                          int gachaCount, String poolId) {
        // 构建概率数组
        List<JSONObject> randomRankList = new ArrayList<>();
        for (int i = 0; i < availCharInfo.size(); i++) {
            JSONObject charInfo = availCharInfo.getJSONObject(i);
            int totalPercent = (int)(charInfo.getDoubleValue("totalPercent") * 100);
            int rarityRank = charInfo.getIntValue("rarityRank");

            // 保底加成
            if (rarityRank == 5) {
                totalPercent += (gachaCount + 50) / 50 * 2;
            }

            JSONObject rankObj = new JSONObject();
            rankObj.put("rarityRank", rarityRank);
            rankObj.put("index", i);

            for (int j = 0; j < totalPercent; j++) {
                randomRankList.add(rankObj);
            }
        }

        // 随机选择
        Collections.shuffle(randomRankList);
        JSONObject selectedRank = randomRankList.get(new Random().nextInt(randomRankList.size()));
        JSONArray charIdList = availCharInfo.getJSONObject(selectedRank.getIntValue("index"))
                .getJSONArray("charIdList");
        JSONObject poolData = IOTools.ReadJsonFile("data/gacha/" + poolId + ".json");

        // 处理UP角色
        if (!poolId.startsWith("BOOT")) {
            try {
                JSONArray upCharInfo = poolData.getJSONObject("detailInfo")
                        .getJSONObject("upCharInfo")
                        .getJSONArray("perCharList");
                for (int i = 0; i < upCharInfo.size(); i++) {
                    JSONObject upChar = upCharInfo.getJSONObject(i);
                    if (upChar.getIntValue("rarityRank") == selectedRank.getIntValue("rarityRank")) {
                        int upRate = (int)(upChar.getDoubleValue("percent") * 100) - 15;
                        JSONArray upCharIds = upChar.getJSONArray("charIdList");
                        for (int j = 0; j < upCharIds.size(); j++) {
                            String upCharId = upCharIds.getString(j);
                            for (int k = 0; k < upRate; k++) {
                                charIdList.add(upCharId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("处理UP角色异常", e);
            }
        }

        Collections.shuffle(charIdList);
        String charId = charIdList.getString(new Random().nextInt(charIdList.size()));

        // 检查是否已有角色
        Pattern pattern = Pattern.compile("char_(\\d+)_");
        Matcher matcher = pattern.matcher(charId);
        ArknightsApplication.LOGGER.info("rewardSId:" + charId);

        int CharId = 0;
        if (matcher.find()) {
            CharId = Integer.parseInt(matcher.group(1));
            ArknightsApplication.LOGGER.info("提取到的数字是: " + CharId);
        } else {
            ArknightsApplication.LOGGER.info("未匹配到数字");
        }
        boolean isNew = true;
        int charInstId = CharId;
        for (String key : chars.keySet()) {
            if (chars.getJSONObject(key).getString("charId").equals(charId)) {
                isNew = false;
                charInstId = Integer.parseInt(key);
                break;
            }
        }

        // 构建结果
        JSONObject result = new JSONObject();
        result.put("charId", charId);
        result.put("rarity", selectedRank.getIntValue("rarityRank"));
        JSONArray itemGet = new JSONArray();

        if (isNew) {
            // 新角色处理
            charInstId = CharId;
            JSONObject newChar = createNewCharacter(charId, charInstId);
            chars.put(String.valueOf(charInstId), newChar);

            // 添加基建数据
            JSONObject buildingChar = createBuildingCharacter(charId);
            buildingChars.put(String.valueOf(charInstId), buildingChar);

            // 添加角色组
            JSONObject charGroup = new JSONObject();
            charGroup.put("favorPoint", 0);
            userData.getJSONObject("troop").getJSONObject("charGroup").put(charId, charGroup);

            // 添加获得物品
            JSONObject hggShard = new JSONObject();
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);
            userData.getJSONObject("status").put("hggShard",
                    userData.getJSONObject("status").getIntValue("hggShard") + 1);

            // 初始化潜能
            userData.getJSONObject("inventory").put("p_" + charId, 0);
        } else {
            // 重复角色处理
            JSONObject existingChar = chars.getJSONObject(String.valueOf(charInstId));
            int rarity = selectedRank.getIntValue("rarityRank");
            int potentialRank = existingChar.getIntValue("potentialRank");

            // 根据稀有度添加物品
            JSONObject shardItem = new JSONObject();
            if (rarity <= 3) {
                shardItem.put("type", "LGG_SHD");
                shardItem.put("id", "4005");
                shardItem.put("count", rarity == 3 ? 30 : (rarity == 2 ? 5 : 1));
                userData.getJSONObject("status").put("lggShard",
                        userData.getJSONObject("status").getIntValue("lggShard") +
                                shardItem.getIntValue("count"));
            } else {
                shardItem.put("type", "HGG_SHD");
                shardItem.put("id", "4004");
                shardItem.put("count", rarity == 5 ?
                        (potentialRank < 5 ? 10 : 15) :
                        (potentialRank < 5 ? 5 : 8));
                userData.getJSONObject("status").put("hggShard",
                        userData.getJSONObject("status").getIntValue("hggShard") +
                                shardItem.getIntValue("count"));
            }
            itemGet.add(shardItem);

            // 添加潜能
            JSONObject potentialItem = new JSONObject();
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + charId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);
            userData.getJSONObject("inventory").put("p_" + charId,
                    userData.getJSONObject("inventory").getIntValue("p_" + charId) + 1);
        }

        result.put("charInstId", charInstId);
        result.put("isNew", isNew ? 1 : 0);
        result.put("itemGet", itemGet);

        return result;
    }

    private JSONObject performDexNav(JSONArray gachaResultList,JSONObject userData) {
        JSONObject result = new JSONObject();
        JSONObject dexNav = new JSONObject();
        JSONObject character = new JSONObject();
        JSONObject id = new JSONObject();
        JSONObject add = new JSONObject();
        if (gachaResultList != null && !gachaResultList.isEmpty()) {
            for (int i = 0; i < gachaResultList.size(); i++) {
                if (gachaResultList.get(i) instanceof JSONObject item) {
                    String charId = item.getString("charId");
                    int isNew = item.getIntValue("isNew");
                    int charInstId  = item.getIntValue("charInstId");
                    if  (isNew == 1) {
                        add.put("count", 1);
                        add.put("charInstId", charInstId);
                        add.put("classicCount", 0);
                        id.put(charId, add);
                        character.put("character", id);
                        dexNav.put("dexNav", character);
                        userData.getJSONObject("dexNav").getJSONObject("character").put(charId, add);
                        result.put("dexNav", dexNav);
                    } else if (isNew == 0) {
                        int count = userData.getJSONObject("dexNav").getJSONObject("character").getJSONObject(charId).getIntValue("count");
                        add.put("count", count);
                        add.put("charInstId", charInstId);
                        add.put("classicCount", 0);
                        id.put(charId, add);
                        character.put("character", id);
                        dexNav.put("dexNav", character);
                        userData.getJSONObject("dexNav").getJSONObject("character").getJSONObject(charId).put("count", count + 1);
                        result.put("dexNav", dexNav);
                    }
                }
            }
        }
        return result;
    }
}