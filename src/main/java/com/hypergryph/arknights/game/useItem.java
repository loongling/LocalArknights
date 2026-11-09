package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/user"})
public class useItem {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Pattern AP_SUPPLY = Pattern.compile("ap_supply(?:_lt)?_(\\d+)");

    @PostMapping(
            value = {"/useItem"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject useItemApi(@RequestHeader(value = "secret", required = false) String secret,
                                 @RequestBody JSONObject JsonBody,
                                 HttpServletResponse response,
                                 HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + "/" + clientIp + "] /user/useItem");

        String itemId = JsonBody.getString("itemId");
        String instId = JsonBody.getString("instId");
        int cnt = JsonBody.getIntValue("cnt");
        if (cnt <= 0) {
            cnt = 1;
        }

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Long uid = ((Account) Accounts.get(0)).getUid();
        if (((Account) Accounts.get(0)).getBan() == 1L) {
            response.setStatus(500);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "error");
            return banned;
        }

        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
        JSONObject applied = applyUseItem(UserSyncData, itemId, instId, cnt);
        if (applied.getString("error") != null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", applied.getString("error"));
            return bad;
        }
        userDao.setUserData(uid, UserSyncData);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", applied.getJSONObject("modified"));
        JSONObject deleted = new JSONObject(true);
        JSONArray deletedInstIds = applied.getJSONArray("deleted");
        if (deletedInstIds != null && !deletedInstIds.isEmpty()) {
            deleted.put("consumable", deletedInstIds);
        }
        playerDataDelta.put("deleted", deleted);

        JSONObject result = new JSONObject(true);
        result.put("items", applied.getJSONArray("items"));
        result.put("playerDataDelta", playerDataDelta);
        if (applied.getJSONArray("items").isEmpty()) {
            LOGGER.info("[useItem] " + itemId + " ×" + cnt + "（本地还没实现该道具效果，只扣数量）");
        }
        return result;
    }

    /**
     * 官服实际调用的是复数端点（抓包 2026-11-06 12:55 客户端连打两次 404）：
     *   POST /user/useItems {"items":[{"instId":14,"itemId":"ap_supply_lt_120","cnt":1}]}
     *   → {"items":[{"type":"AP_GAMEPLAY","id":"AP_GAMEPLAY","count":120}],
     *      "playerDataDelta":{"modified":{"consumable":{...},"status":{...}},"deleted":{}}}
     * 这里按数组逐个调用 applyUseItem，再把 modified / items 合并。
     */
    @PostMapping(
            value = {"/useItems"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject useItemsApi(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody JSONObject JsonBody,
                                  HttpServletResponse response,
                                  HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + "/" + clientIp + "] /user/useItems body="
                + (JsonBody == null ? "{}" : JsonBody.toJSONString()));

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Long uid = ((Account) Accounts.get(0)).getUid();
        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());

        JSONArray list = JsonBody == null ? null : JsonBody.getJSONArray("items");
        if (list == null || list.isEmpty()) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "items 为空");
            return bad;
        }

        JSONArray outItems = new JSONArray();
        JSONObject mergedConsumable = new JSONObject(true);
        JSONObject mergedStatus = new JSONObject(true);
        for (int i = 0; i < list.size(); i++) {
            JSONObject one = list.getJSONObject(i);
            String itemId = one.getString("itemId");
            String instId = one.getString("instId");
            int cnt = one.getIntValue("cnt");
            JSONObject applied = applyUseItem(UserSyncData, itemId, instId, cnt);
            if (applied.getString("error") != null) {
                JSONObject bad = new JSONObject(true);
                bad.put("result", 1);
                bad.put("error", applied.getString("error"));
                return bad;
            }
            JSONArray got = applied.getJSONArray("items");
            if (got != null) {
                for (int k = 0; k < got.size(); k++) {
                    outItems.add(got.getJSONObject(k));
                }
            }
            JSONObject mod = applied.getJSONObject("modified");
            if (mod != null) {
                JSONObject cons = mod.getJSONObject("consumable");
                if (cons != null) {
                    mergedConsumable.putAll(cons);
                }
                JSONObject st = mod.getJSONObject("status");
                if (st != null) {
                    mergedStatus.putAll(st);
                }
            }
        }
        userDao.setUserData(uid, UserSyncData);

        JSONObject modified = new JSONObject(true);
        if (!mergedConsumable.isEmpty()) {
            modified.put("consumable", mergedConsumable);
        }
        if (!mergedStatus.isEmpty()) {
            modified.put("status", mergedStatus);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());

        JSONObject result = new JSONObject(true);
        result.put("items", outItems);
        result.put("playerDataDelta", playerDataDelta);
        LOGGER.info("[useItems] 使用 {} 种道具，产出 {} 项", list.size(), outItems.size());
        return result;
    }

    public static JSONObject applyUseItem(JSONObject UserSyncData, String itemId, String instId, int cnt) {
        JSONObject out = new JSONObject(true);
        if (cnt <= 0) {
            cnt = 1;
        }
        // 券类道具不能走 /user/useItems（会被静默消耗），必须用仓库的券接口
        JSONObject itemMeta = ArknightsApplication.itemTable == null ? null
                : ArknightsApplication.itemTable.getJSONObject(itemId);
        String itemType = itemMeta == null ? null : itemMeta.getString("itemType");
        if (itemType != null && (itemType.startsWith("VOUCHER") || "OPTIONAL_VOUCHER_PICK".equals(itemType)
                || "ITEM_PACK".equals(itemType) || "MATERIAL_ISSUE_VOUCHER".equals(itemType))) {
            out.put("error", "该道具需要在仓库中使用: " + itemId + " (" + itemType + ")");
            return out;
        }

        JSONObject consumable = UserSyncData.getJSONObject("consumable");
        if (consumable == null) {
            consumable = new JSONObject(true);
            UserSyncData.put("consumable", consumable);
        }
        JSONObject itemStack = consumable.getJSONObject(itemId);
        JSONObject slot = itemStack == null ? null : itemStack.getJSONObject(instId);
        if (slot == null) {
            out.put("error", "没有这个消耗品实例: " + itemId + "/" + instId);
            return out;
        }
        int have = slot.getIntValue("count");
        if (have < cnt) {
            out.put("error", "数量不足: 有 " + have + "，要用 " + cnt);
            return out;
        }

        JSONObject modified = new JSONObject(true);
        JSONArray items = new JSONArray();
        JSONArray deletedInstIds = new JSONArray();

        int left = have - cnt;
        // 官服抓包（004_00 user/useItem）：用光后槽位保留、count 置 0，
        // modified.consumable 带该 itemId 的整个槽位表，deleted 为空。
        slot.put("count", Math.max(0, left));
        JSONObject consumableDelta = new JSONObject(true);
        consumableDelta.put(itemId, itemStack);
        modified.put("consumable", consumableDelta);

        Matcher matcher = AP_SUPPLY.matcher(itemId);
        if (matcher.find()) {
            int apGain = Integer.parseInt(matcher.group(1)) * cnt;
            JSONObject status = UserSyncData.getJSONObject("status");
            int ap = status.getIntValue("ap") + apGain;
            status.put("ap", ap);
            JSONObject statusDelta = new JSONObject(true);
            statusDelta.put("ap", ap);
            modified.put("status", statusDelta);

            JSONObject grant = new JSONObject(true);
            grant.put("type", "AP_GAMEPLAY");
            grant.put("id", "AP_GAMEPLAY");
            grant.put("count", apGain);
            items.add(grant);
        }

        out.put("items", items);
        out.put("modified", modified);
        out.put("deleted", deletedInstIds.isEmpty() ? null : deletedInstIds);
        return out;
    }
}
