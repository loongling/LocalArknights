package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.voucher.VoucherTable;
import com.hypergryph.arknights.core.work.processArray;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
@RequestMapping({"/depot"})
public class depot {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(value = {"/getVoucherDetail"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getVoucherDetail(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject JsonBody,
                                       HttpServletResponse response,
                                       HttpServletRequest request) {
        LOGGER.info("[/" + ArknightsApplication.getIpAddr(request) + "] /depot/getVoucherDetail");
        String itemId = JsonBody.getString("itemId");
        JSONObject meta = VoucherTable.get(itemId);

        JSONObject result = new JSONObject(true);
        if (meta == null) {
            LOGGER.warn("[getVoucherDetail] 券池表里没有 " + itemId + "，返回空列表");
            result.put("voucherType", "CHAR_VOUCHER");
            result.put("pickNum", 1);
            result.put("voucherBgDec", null);
            result.put("extraDataDic", new JSONObject(true));
            result.put("itemList", new JSONArray());
            result.put("validTimeInfo", validTimeInfo());
            return result;
        }

        String voucherType = meta.getString("voucherType");
        JSONArray itemList = meta.getJSONArray("itemList");
        if (voucherType == null) {
            voucherType = (itemList != null && !itemList.isEmpty()
                    && "CHAR".equals(itemList.getJSONObject(0).getString("type")))
                    ? "CHAR_VOUCHER" : "MATERIAL_VOUCHER";
        }

        result.put("voucherType", voucherType);
        result.put("pickNum", meta.getIntValue("pickNum") == 0 ? 1 : meta.getIntValue("pickNum"));
        result.put("voucherBgDec", meta.get("voucherBgDec"));
        result.put("extraDataDic", new JSONObject(true));
        result.put("itemList", itemList == null ? new JSONArray() : itemList);
        result.put("validTimeInfo", validTimeInfo());
        return result;
    }

    @PostMapping(value = {"/getMaterialVoucherDetail"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getMaterialVoucherDetail(@RequestHeader(value = "secret", required = false) String secret,
                                               @RequestBody JSONObject JsonBody,
                                               HttpServletResponse response,
                                               HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/getMaterialVoucherDetail");
        String itemId = JsonBody.getString("itemId");
        JSONObject meta = VoucherTable.get(itemId);

        JSONObject info = new JSONObject(true);
        info.put("voucherId", itemId);
        info.put("pickNum", meta == null || meta.getIntValue("pickNum") == 0 ? 1 : meta.getIntValue("pickNum"));
        info.put("startTime", -1);
        info.put("endTime", -1);
        info.put("picId", meta == null ? null : meta.getString("picId"));
        JSONArray pool = meta == null ? null : meta.getJSONArray("pool");
        info.put("pool", pool == null ? new JSONArray() : pool);
        if (meta == null) {
            LOGGER.warn("[getMaterialVoucherDetail] 券池表里没有 " + itemId + "，返回空池");
        }

        JSONObject result = new JSONObject(true);
        result.put("info", info);
        return result;
    }


    @PostMapping(value = {"/useMaterialVoucher"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject useMaterialVoucher(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody JSONObject JsonBody,
                                         HttpServletResponse response,
                                         HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/useMaterialVoucher");
        return useVoucher(secret, JsonBody, response, true);
    }

    @PostMapping(value = {"/useOptionVoucher"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject useOptionVoucher(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject JsonBody,
                                       HttpServletResponse response,
                                       HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/useOptionVoucher");
        return useVoucher(secret, JsonBody, response, false);
    }

    private JSONObject useVoucher(String secret, JSONObject JsonBody, HttpServletResponse response, boolean random) {
        String itemId = JsonBody.getString("itemId");
        String instId = JsonBody.getString("instId");
        int count = JsonBody.getIntValue("count");
        if (count <= 0) {
            count = JsonBody.getIntValue("voucherCount");
        }
        if (count <= 0) {
            count = 1;
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
        JSONArray choices = JsonBody.getJSONArray("choices");
        JSONObject applied = applyUseVoucher(UserSyncData, itemId, instId, count, choices, random);
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
        result.put("itemGet", applied.getJSONArray("itemGet"));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    public static JSONObject applyUseVoucher(JSONObject UserSyncData, String itemId, String instId, int count,
                                             JSONArray choices, boolean random) {
        JSONObject out = new JSONObject(true);
        if (count <= 0) {
            count = 1;
        }

        JSONObject consumable = UserSyncData.getJSONObject("consumable");
        JSONObject itemStack = consumable == null ? null : consumable.getJSONObject(itemId);
        JSONObject slot = itemStack == null ? null : itemStack.getJSONObject(instId);
        if (slot == null) {
            out.put("error", "没有这张券: " + itemId + "/" + instId);
            return out;
        }
        int have = slot.getIntValue("count");
        if (have < count) {
            out.put("error", "券数量不足: 有 " + have + "，要用 " + count);
            return out;
        }

        JSONArray grants = new JSONArray();          // [{id,type,count}]
        if (random) {
            JSONArray pool = VoucherTable.pool(itemId);
            if (pool == null || pool.isEmpty()) {
                out.put("error", "券池数据缺失: " + itemId + "（voucher_detail_table.json 里没有 pool）");
                return out;
            }
            // 官方是「随机从池里选一个」，各 group 等概率、group 内等概率
            JSONObject picked = pickFromPool(pool, UserSyncData);
            JSONObject g = new JSONObject(true);
            g.put("type", picked.getString("itemType"));
            g.put("id", picked.getString("itemId"));
            g.put("count", picked.getIntValue("itemNum") == 0 ? 1 : picked.getIntValue("itemNum"));
            grants.add(g);
        } else {
            if (choices == null || choices.isEmpty()) {
                out.put("error", "自选券没有传 choices");
                return out;
            }
            JSONArray allowed = VoucherTable.itemList(itemId);
            for (int i = 0; i < choices.size(); i++) {
                JSONObject c = choices.getJSONObject(i);
                String cid = c.getString("id");
                int ccount = c.getIntValue("count") == 0 ? 1 : c.getIntValue("count");
                if (allowed != null && !allowed.isEmpty() && !containsId(allowed, cid)) {
                    out.put("error", "这个券换不了 " + cid);
                    return out;
                }
                JSONObject g = new JSONObject(true);
                g.put("type", typeOf(allowed, cid));
                g.put("id", cid);
                g.put("count", ccount);
                grants.add(g);
            }
        }

        // ── 扣券 + 发货 ──
        JSONObject modified = new JSONObject(true);
        JSONArray deletedInstIds = new JSONArray();
        int left = have - count;
        JSONObject consumableDelta = new JSONObject(true);
        JSONObject stackDelta = new JSONObject(true);
        if (left > 0) {
            slot.put("count", left);
            stackDelta.put(instId, slot);
            consumableDelta.put(itemId, stackDelta);
        } else {
            slot.put("count", 0);                     // 官方保留 count:0
            stackDelta.put(instId, slot);
            consumableDelta.put(itemId, stackDelta);
        }
        modified.put("consumable", consumableDelta);

        JSONObject inventory = UserSyncData.getJSONObject("inventory");
        if (inventory == null) {
            inventory = new JSONObject(true);
            UserSyncData.put("inventory", inventory);
        }
        JSONObject invDelta = new JSONObject(true);
        for (int i = 0; i < grants.size(); i++) {
            JSONObject g = grants.getJSONObject(i);
            if (!"MATERIAL".equals(g.getString("type"))) {
                continue;
            }
            String id = g.getString("id");
            int now = inventory.getIntValue(id) + g.getIntValue("count");
            inventory.put(id, now);
            invDelta.put(id, now);
        }
        if (!invDelta.isEmpty()) {
            modified.put("inventory", invDelta);
        }

        out.put("itemGet", grants);
        out.put("modified", modified);
        out.put("deleted", deletedInstIds.isEmpty() ? null : deletedInstIds);
        return out;
    }

    public static JSONObject applyVoucherGacha(JSONObject UserSyncData, String itemId, String instId, String charId) {
        JSONObject out = new JSONObject(true);
        JSONArray allowed = VoucherTable.itemList(itemId);
        if (allowed == null || allowed.isEmpty()) {
            out.put("error", "券池数据缺失: " + itemId + "（voucher_detail_table.json 里没有 itemList）");
            return out;
        }
        if (charId == null || !containsId(allowed, charId)) {
            out.put("error", "这个券换不了该干员: " + charId);
            return out;
        }

        JSONObject consumable = UserSyncData.getJSONObject("consumable");
        JSONObject itemStack = consumable == null ? null : consumable.getJSONObject(itemId);
        JSONObject slot = itemStack == null ? null : itemStack.getJSONObject(instId);
        if (slot == null || slot.getIntValue("count") < 1) {
            out.put("error", "没有这张券: " + itemId + "/" + instId);
            return out;
        }

        int left = slot.getIntValue("count") - 1;
        slot.put("count", Math.max(0, left));

        JSONArray granted = processArray.grantChar(UserSyncData, charId, 1);

        JSONObject modified = new JSONObject(true);
        JSONObject consumableDelta = new JSONObject(true);
        JSONObject stackDelta = new JSONObject(true);
        stackDelta.put(instId, slot);
        consumableDelta.put(itemId, stackDelta);
        modified.put("consumable", consumableDelta);

        out.put("items", granted);
        out.put("modified", modified);
        return out;
    }

    @PostMapping(value = {"/voucherGacha"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject voucherGacha(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject JsonBody,
                                   HttpServletResponse response,
                                   HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/voucherGacha");
        String itemId = JsonBody.getString("itemId");
        String instId = JsonBody.getString("instId");
        String charId = JsonBody.getString("charId");

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
        JSONObject applied = applyVoucherGacha(UserSyncData, itemId, instId, charId);
        if (applied.getString("error") != null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", applied.getString("error"));
            return bad;
        }
        userDao.setUserData(uid, UserSyncData);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", applied.getJSONObject("modified"));
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("items", applied.getJSONArray("items"));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static JSONObject validTimeInfo() {
        JSONObject t = new JSONObject(true);
        t.put("startTs", -1);
        t.put("endTs", -1);
        return t;
    }

    private static boolean containsId(JSONArray allowed, String id) {
        for (int i = 0; i < allowed.size(); i++) {
            if (id != null && id.equals(allowed.getJSONObject(i).getString("id"))) {
                return true;
            }
        }
        return false;
    }

    private static String typeOf(JSONArray allowed, String id) {
        if (allowed != null) {
            for (int i = 0; i < allowed.size(); i++) {
                JSONObject e = allowed.getJSONObject(i);
                if (id != null && id.equals(e.getString("id"))) {
                    return e.getString("type");
                }
            }
        }
        return "MATERIAL";
    }

    private static JSONObject pickFromPool(JSONArray pool, JSONObject userData) {
        JSONObject byGroup = new JSONObject(true);
        for (int i = 0; i < pool.size(); i++) {
            JSONObject e = pool.getJSONObject(i);
            String gid = e.getString("groupId") == null ? "1" : e.getString("groupId");
            JSONArray arr = byGroup.getJSONArray(gid);
            if (arr == null) {
                arr = new JSONArray();
                byGroup.put(gid, arr);
            }
            arr.add(e);
        }
        java.util.List<String> gids = new java.util.ArrayList<>(byGroup.keySet());
        String gid = gids.get(ThreadLocalRandom.current().nextInt(gids.size()));
        JSONArray arr = byGroup.getJSONArray(gid);
        return arr.getJSONObject(ThreadLocalRandom.current().nextInt(arr.size()));
    }
}
