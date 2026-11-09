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
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/useOptionVoucher body="
                + (JsonBody == null ? "{}" : JsonBody.toJSONString()));
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
        JSONArray gotItems = applied.getJSONArray("itemGet");
        result.put("itemGet", gotItems);
        // 官方干员类券（depot/voucherGacha 抓包）用的是 items 键，材料券用 itemGet；
        // 两个都带上，客户端取哪个都能弹「获得干员」
        boolean hasChar = false;
        for (int i = 0; i < gotItems.size(); i++) {
            if ("CHAR".equals(gotItems.getJSONObject(i).getString("type"))) {
                hasChar = true;
                break;
            }
        }
        if (hasChar) {
            result.put("items", gotItems);
        }
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
        JSONObject troopDelta = new JSONObject(true);
        JSONObject buildingDelta = new JSONObject(true);
        JSONObject skinDelta = new JSONObject(true);
        JSONObject skinTsDelta = new JSONObject(true);
        for (int i = grants.size() - 1; i >= 0; i--) {
            JSONObject g = grants.getJSONObject(i);
            String type = g.getString("type");
            String id = g.getString("id");
            if ("MATERIAL".equals(type)) {
                int now = inventory.getIntValue(id) + g.getIntValue("count");
                inventory.put(id, now);
                invDelta.put(id, now);
            } else if ("SKIN".equals(type)) {
                // 时装自选凭证：官服 user.skin.characterSkins 是扁平表 {skinId: 1}
                JSONObject skin = UserSyncData.getJSONObject("skin");
                if (skin == null) {
                    skin = new JSONObject(true);
                    UserSyncData.put("skin", skin);
                }
                JSONObject characterSkins = skin.getJSONObject("characterSkins");
                if (characterSkins == null) {
                    characterSkins = new JSONObject(true);
                    skin.put("characterSkins", characterSkins);
                }
                JSONObject skinTs = skin.getJSONObject("skinTs");
                if (skinTs == null) {
                    skinTs = new JSONObject(true);
                    skin.put("skinTs", skinTs);
                }
                long skinNow = ArknightsApplication.getTimestamp();
                characterSkins.put(id, 1);
                skinTs.put(id, skinNow);
                skinDelta.put(id, 1);
                skinTsDelta.put(id, skinNow);
            } else if ("CHAR".equals(type) || "OPERATOR".equals(type)) {
                // 干员调用凭证：必须真的入库，并把 troop / building / status 变化带回去。
                // itemGet 用 grantChar 的完整产出（CHAR + 首次获得的高凭 HGG_SHD），
                // 官服的发货列表就是这个形状，客户端靠它弹「获得干员」。
                int hggBefore = UserSyncData.getJSONObject("status").getIntValue("hggShard");
                JSONArray got = com.hypergryph.arknights.core.work.processArray.grantChar(UserSyncData, id, g.getIntValue("count"));
                grants.remove(i);
                int grantedInstId = 0;
                for (int q = 0; q < got.size(); q++) {
                    JSONObject one2 = got.getJSONObject(q);
                    JSONObject cg = one2.getJSONObject("charGet");
                    if (cg != null) {
                        grantedInstId = cg.getIntValue("charInstId");
                    }
                    grants.add(one2);
                }
                int hggAfter = UserSyncData.getJSONObject("status").getIntValue("hggShard");
                if (hggAfter != hggBefore) {
                    JSONObject st = new JSONObject(true);
                    st.put("hggShard", hggAfter);
                    modified.put("status", st);
                }
                // 官服发货 delta 还带 dexNav.character（图鉴/干员获得界面靠它，
                // 抓包 parsed_1205/004_10 depot/voucherGacha：modified 键 = consumable,dexNav,troop,building,status,medal）
                JSONObject dexNav = UserSyncData.getJSONObject("dexNav");
                if (dexNav != null) {
                    JSONObject dexChars = dexNav.getJSONObject("character");
                    if (dexChars == null) {
                        dexChars = new JSONObject(true);
                        dexNav.put("character", dexChars);
                    }
                    JSONObject entry = dexChars.getJSONObject(id);
                    if (entry == null) {
                        entry = new JSONObject(true);
                        entry.put("charInstId", grantedInstId);
                        entry.put("count", 1);
                        entry.put("classicCount", 0);
                        dexChars.put(id, entry);
                    } else if (entry.getIntValue("charInstId") == 0) {
                        entry.put("charInstId", grantedInstId);
                    }
                    JSONObject dexDelta = new JSONObject(true);
                    dexDelta.put(id, entry);
                    JSONObject dexMod = new JSONObject(true);
                    dexMod.put("character", dexDelta);
                    modified.put("dexNav", dexMod);
                }
                JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");
                for (String k : chars.keySet()) {
                    JSONObject c = chars.getJSONObject(k);
                    if (c != null && id.equals(c.getString("charId"))) {
                        troopDelta.put(k, c);
                    }
                }
                JSONObject bChars = UserSyncData.getJSONObject("building").getJSONObject("chars");
                for (String k : troopDelta.keySet()) {
                    JSONObject b = bChars.getJSONObject(k);
                    if (b != null) {
                        buildingDelta.put(k, b);
                    }
                }
            }
        }
        if (!invDelta.isEmpty()) {
            modified.put("inventory", invDelta);
        }
        if (!troopDelta.isEmpty()) {
            JSONObject troopMod = new JSONObject(true);
            troopMod.put("chars", troopDelta);
            modified.put("troop", troopMod);
        }
        if (!buildingDelta.isEmpty()) {
            JSONObject buildingMod = new JSONObject(true);
            buildingMod.put("chars", buildingDelta);
            modified.put("building", buildingMod);
        }
        if (!skinDelta.isEmpty()) {
            JSONObject skinMod = new JSONObject(true);
            skinMod.put("characterSkins", skinDelta);
            skinMod.put("skinTs", skinTsDelta);
            modified.put("skin", skinMod);
        }

        out.put("itemGet", grants);
        out.put("modified", modified);
        out.put("deleted", deletedInstIds.isEmpty() ? null : deletedInstIds);
        return out;
    }

    public static JSONObject applyVoucherGacha(JSONObject UserSyncData, String itemId, String instId, String charId) {
        JSONArray allowed = VoucherTable.itemList(itemId);
        if (allowed == null || allowed.isEmpty()) {
            JSONObject out = new JSONObject(true);
            out.put("error", "券池数据缺失: " + itemId + "（voucher_detail_table.json 里没有 itemList）");
            return out;
        }
        if (charId == null || !containsId(allowed, charId)) {
            JSONObject out = new JSONObject(true);
            out.put("error", "这个券换不了该干员: " + charId);
            return out;
        }
        // 直接复用自选券的发货逻辑：干员入库 + troop/building/dexNav/status 增量 + charGet 形状
        JSONArray choices = new JSONArray();
        JSONObject one = new JSONObject(true);
        one.put("id", charId);
        one.put("count", 1);
        choices.add(one);
        return applyUseVoucher(UserSyncData, itemId, instId, 1, choices, false);
    }

    @PostMapping(value = {"/voucherGacha"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject voucherGacha(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject JsonBody,
                                   HttpServletResponse response,
                                   HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /depot/voucherGacha body="
                + (JsonBody == null ? "{}" : JsonBody.toJSONString()));
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

        JSONObject modified = applied.getJSONObject("modified");
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("items", applied.getJSONArray("itemGet"));
        result.put("itemGet", applied.getJSONArray("itemGet"));
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
