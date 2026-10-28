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

    public static JSONObject applyUseItem(JSONObject UserSyncData, String itemId, String instId, int cnt) {
        JSONObject out = new JSONObject(true);
        if (cnt <= 0) {
            cnt = 1;
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
        JSONObject consumableDelta = new JSONObject(true);
        JSONObject stackDelta = new JSONObject(true);
        if (left > 0) {
            slot.put("count", left);
            stackDelta.put(instId, slot);
        } else {
            itemStack.remove(instId);
            if (itemStack.isEmpty()) {
                consumable.remove(itemId);
            }
            deletedInstIds.add(instId);
        }
        if (!stackDelta.isEmpty()) {
            consumableDelta.put(itemId, stackDelta);
            modified.put("consumable", consumableDelta);
        }

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
