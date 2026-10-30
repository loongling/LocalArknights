package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.Admin;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.hypergryph.arknights.core.monthlycard.MonthlyCard.*;
import static com.hypergryph.arknights.core.shop.GPGoodGrant.grant;
import static com.hypergryph.arknights.core.work.processArray.handleModifiedBuild;
import static com.hypergryph.arknights.core.work.processArray.handleUserDataChange;

@RestController
@RequestMapping({"/shop"})
public class shop {
    public shop() {
    }

    @PostMapping(
            value = {"/getSkinGoodList"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getSkinGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject JsonBody) {
        JSONArray goodList = new JSONArray();
        for (Map.Entry<String, Object> entry : ArknightsApplication.skinTable.entrySet()) {
            String skinId = entry.getKey();
            if (skinId.indexOf("@") == -1) {
                continue;
            }
            JSONObject skinData = ArknightsApplication.skinTable.getJSONObject(skinId);
            if (skinData == null || !skinData.getBooleanValue("isBuySkin")) {
                continue;
            }
            JSONObject displaySkin = skinData.getJSONObject("displaySkin");
            if (displaySkin == null) {
                continue;
            }
            int price = skinPriceOfSkinId(skinId);
            if (price <= 0) {
                price = DEFAULT_SKIN_PRICE;
            }
            JSONObject good = new JSONObject(true);
            good.put("charId", skinData.getString("charId"));
            good.put("skinId", skinId);
            good.put("goodId", "SS_" + skinId);
            good.put("slotId", displaySkin.getIntValue("sortId"));
            good.put("skinName", displaySkin.getString("skinName"));
            good.put("currencyUnit", "DIAMOND");
            good.put("originPrice", price);
            good.put("price", price);
            good.put("discount", 0);
            good.put("desc1", (Object) null);
            good.put("desc2", (Object) null);
            good.put("startDateTime", -1);
            good.put("endDateTime", -1);
            good.put("isRedeem", false);
            goodList.add(good);
        }

        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        result.put("goodList", goodList);
        result.put("gachaGoodList", new JSONArray());
        ArknightsApplication.LOGGER.info("[shop] getSkinGoodList 返回 {} 个皮肤（全部标记可购买）", goodList.size());
        return result;
    }
    
    private static final int DEFAULT_SKIN_PRICE = 15;

    private static String deriveSkinId(String goodId) {
        if (goodId == null) {
            return "";
        }
        String skinId = goodId.startsWith("SS_") ? goodId.substring(3) : goodId;
        int revIdx = skinId.lastIndexOf("_r");
        if (revIdx > 0 && revIdx + 2 < skinId.length()
                && skinId.substring(revIdx + 2).matches("\\d+")) {
            skinId = skinId.substring(0, revIdx);
        }
        return skinId;
    }

    private java.util.Set<String> ownedSkins(String secret) {
        java.util.Set<String> owned = new java.util.HashSet<>();
        try {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            if (accounts.size() != 1) {
                return owned;
            }
            JSONObject userData = JSONObject.parseObject(accounts.get(0).getUser());
            JSONObject skin = userData.getJSONObject("skin");
            JSONObject characterSkins = skin == null ? null : skin.getJSONObject("characterSkins");
            if (characterSkins != null) {
                for (String key : characterSkins.keySet()) {
                    if (characterSkins.getIntValue(key) > 0) {
                        owned.add(key);
                    }
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[shop] 读取皮肤拥有状态失败: {}", e.toString());
        }
        return owned;
    }

    private static int skinPriceOfSkinId(String skinId) {
        JSONObject shop = ArknightsApplication.skinGoodList;
        JSONArray list = shop == null ? null : shop.getJSONArray("goodList");
        if (list == null) {
            return 0;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject good = list.getJSONObject(i);
            if (good != null && skinId.equals(deriveSkinId(good.getString("goodId")))) {
                return good.getIntValue("price");
            }
        }
        return 0;
    }

    @PostMapping(
            value = {"/buyGPGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyGPGood(@RequestHeader(value = "secret", required = false) String secret,
                                @RequestBody JSONObject JsonBody,
                                HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buyGPGood");
        ArknightsApplication.LOGGER.info("buyGPGood:" + JsonBody);

        String goodId = JsonBody.getString("goodId");
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Account account = Accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "account banned");
            return banned;
        }

        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject good = findGood(goodId);
        if (good == null) {
            response.setStatus(400);
            JSONObject miss = new JSONObject(true);
            miss.put("code", 5847);
            miss.put("msg", "invalid parameter");
            miss.put("info", "good not found: " + goodId);
            miss.put("message", "server error");
            return miss;
        }

        String[] gpPath = gpShopPath(goodId);
        if (gpPath != null) {
            String soldOut = checkAvailCount(good, userData, gpPath, goodId, 1);
            if (soldOut != null) {
                return badRequest(response, soldOut);
            }
        }

        JSONObject modified = new JSONObject(true);
        JSONArray items = grant(userData, good, account.getUid(), modified);
        userDao.setUserData(account.getUid(), userData);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[shop] buyGPGood uid={} goodId={} modified={}",
                account.getUid(), goodId, modified.toJSONString());
        return result;
    }

    private static void mergeNode(JSONObject modified, String node, JSONObject delta) {
        com.hypergryph.arknights.core.shop.ShopPurchase.mergeNode(modified, node, delta);
    }

    private static java.util.Map<String, String> snapshotUserNodes(JSONObject userData) {
        java.util.Map<String, String> snapshot = new java.util.HashMap<>();
        for (String node : new String[]{"status", "inventory", "troop", "skin",
                "building", "dexNav", "consumable"}) {
            JSONObject obj = userData.getJSONObject(node);
            if (obj != null) {
                snapshot.put(node, obj.toJSONString());
            }
        }
        return snapshot;
    }

    private static void mergeNodeDiff(JSONObject modified, JSONObject userData,
                                      java.util.Map<String, String> before) {
        for (java.util.Map.Entry<String, String> e : before.entrySet()) {
            JSONObject now = userData.getJSONObject(e.getKey());
            if (now == null) {
                continue;
            }
            JSONObject old = JSONObject.parseObject(e.getValue());
            if (!now.equals(old)) {
                modified.put(e.getKey(), now);
            }
        }
    }

    private static void recordPurchase(JSONObject modified, JSONObject userData, String[] path,
                                       String goodId, int count, String costField, int costDelta) {
        com.hypergryph.arknights.core.shop.ShopPurchase.recordPurchase(modified, userData, path,
                goodId, count, costField, costDelta);
    }

    private static int boughtCount(JSONObject userData, String[] path, String goodId) {
        return com.hypergryph.arknights.core.shop.ShopPurchase.boughtCount(userData, path, goodId);
    }

    private static String checkAvailCount(JSONObject good, JSONObject userData, String[] path,
                                          String goodId, int count) {
        return com.hypergryph.arknights.core.shop.ShopPurchase
                .checkAvailCount(good, userData, path, goodId, count);
    }

    private static String[] gpShopPath(String goodId) {
        return com.hypergryph.arknights.core.shop.ShopPurchase.gpShopPath(goodId);
    }

    private static void setCurGroupId(JSONObject modified, JSONObject userData, String[] path, String groupId) {
        com.hypergryph.arknights.core.shop.ShopPurchase.setCurGroupId(modified, userData, path, groupId);
    }

    @PostMapping(
            value = {"/buySkinGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buySkinGood(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buySkinGood");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String goodId = JsonBody.getString("goodId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());

                    String skinId = deriveSkinId(goodId);
                    JSONObject skinNode = UserSyncData.getJSONObject("skin");
                    JSONObject characterSkins = skinNode.getJSONObject("characterSkins");
                    JSONObject skinTs = skinNode.getJSONObject("skinTs");

                    if (characterSkins.containsKey(skinId)) {
                        response.setStatus(400);
                        JSONObject dup = new JSONObject(true);
                        dup.put("code", 5847);
                        dup.put("msg", "invalid parameter");
                        dup.put("info", "skin already owned: " + skinId);
                        dup.put("message", "server error");
                        return dup;
                    }

                    int price = skinPriceOfSkinId(skinId);
                    if (price <= 0) {
                        price = DEFAULT_SKIN_PRICE;
                    }
                    JSONObject statusNode = UserSyncData.getJSONObject("status");
                    int androidDiamond = statusNode.getIntValue("androidDiamond");
                    int iosDiamond = statusNode.getIntValue("iosDiamond");
                    if (androidDiamond < price || iosDiamond < price) {
                        response.setStatus(400);
                        JSONObject poor = new JSONObject(true);
                        poor.put("code", 5847);
                        poor.put("msg", "invalid parameter");
                        poor.put("info", "not enough diamond: need " + price + " in both pools"
                                + ", have android=" + androidDiamond + " ios=" + iosDiamond);
                        poor.put("message", "server error");
                        return poor;
                    }

                    statusNode.put("androidDiamond", androidDiamond - price);
                    statusNode.put("iosDiamond", iosDiamond - price);

                    long nowTs = System.currentTimeMillis() / 1000L;
                    characterSkins.put(skinId, 1);
                    skinTs.put(skinId, nowTs);
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject skinDelta = new JSONObject(true);
                    JSONObject skinsDelta = new JSONObject(true);
                    skinsDelta.put(skinId, 1);
                    JSONObject tsDelta = new JSONObject(true);
                    tsDelta.put(skinId, nowTs);
                    skinDelta.put("characterSkins", skinsDelta);
                    skinDelta.put("skinTs", tsDelta);

                    JSONObject statusDelta = new JSONObject(true);
                    statusDelta.put("androidDiamond", statusNode.getIntValue("androidDiamond"));
                    statusDelta.put("iosDiamond", statusNode.getIntValue("iosDiamond"));

                    JSONObject modified = new JSONObject(true);
                    modified.put("skin", skinDelta);
                    modified.put("status", statusDelta);
                    recordPurchase(modified, UserSyncData, new String[]{"CASH"}, goodId, 1, null, 0);
                    userDao.setUserData(uid, UserSyncData);

                    JSONObject playerDataDelta = new JSONObject(true);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    playerDataDelta.put("modified", modified);

                    JSONObject result = new JSONObject(true);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("result", 0);

                    ArknightsApplication.LOGGER.info("[shop] uid={} 购买皮肤 {} 花费 {} 源石（android/ios 各扣一份，余额 {}/{}）",
                            uid, skinId, price, statusNode.getIntValue("androidDiamond"),
                            statusNode.getIntValue("iosDiamond"));
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/buyLowGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyLowGood(@RequestHeader(value = "secret", required = false)String secret,
                                 @RequestBody JSONObject JsonBody,
                                 HttpServletResponse response, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("[" + ArknightsApplication.getIpAddr(request) + "] /shop/buyLowGood");
        return buyShardGood(secret, JsonBody, response,
                ArknightsApplication.LowGoodList.getJSONArray("goodList"), "lggShard", false);
    }

    @PostMapping(
            value = {"/buyHighGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyHighGood(@RequestHeader(value = "secret", required = false)String secret,
                                  @RequestBody JSONObject JsonBody,
                                  HttpServletResponse response, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("[" + ArknightsApplication.getIpAddr(request) + "] /shop/buyHighGood");
        return buyShardGood(secret, JsonBody, response,
                ArknightsApplication.HighGoodList.getJSONArray("goodList"), "hggShard", false);
    }

    @PostMapping(
            value = {"/buyExtraGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyExtraGood(@RequestHeader(value = "secret", required = false)String secret,
                                   @RequestBody JSONObject JsonBody,
                                   HttpServletResponse response, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("[" + ArknightsApplication.getIpAddr(request) + "] /shop/buyExtraGood");
        return buyShardGood(secret, JsonBody, response,
                ArknightsApplication.ExtraGoodList.getJSONArray("goodList"), "4006", true);
    }

    @PostMapping(
            value = {"/decomposePotentialItem"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject decomposePotentialItem(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /shop/decomposePotentialItem");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            JSONArray charInstIdList = JsonBody.getJSONArray("charInstIdList");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    JSONArray itemGet = new JSONArray();

                    JSONObject status;
                    for(int i = 0; i < charInstIdList.size(); ++i) {
                        int lggShard = UserSyncData.getJSONObject("status").getIntValue("lggShard");
                        int hggShard = UserSyncData.getJSONObject("status").getIntValue("hggShard");
                        status = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstIdList.get(i)));
                        String CharId = status.getString("charId");
                        int pcount = UserSyncData.getJSONObject("inventory").getIntValue("p_" + CharId);
                        UserSyncData.getJSONObject("inventory").put("p_" + CharId, 0);
                        int rarity = ArknightsApplication.getCharRarity(CharId);
                        JSONObject item = new JSONObject(true);
                        if (rarity == 0) {
                            item.put("type", "LGG_SHD");
                            item.put("id", "4005");
                            item.put("count", pcount * 1);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("lggShard", lggShard + pcount * 1);
                        } else if (rarity == 1) {
                            item.put("type", "LGG_SHD");
                            item.put("id", "4005");
                            item.put("count", pcount * 1);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("lggShard", lggShard + pcount * 1);
                        } else if (rarity == 2) {
                            item.put("type", "LGG_SHD");
                            item.put("id", "4005");
                            item.put("count", pcount * 5);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("lggShard", lggShard + pcount * 5);
                        } else if (rarity == 3) {
                            item.put("type", "HGG_SHD");
                            item.put("id", "4004");
                            item.put("count", pcount * 1);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("hggShard", hggShard + pcount * 1);
                        } else if (rarity == 4) {
                            item.put("type", "HGG_SHD");
                            item.put("id", "4004");
                            item.put("count", pcount * 5);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("hggShard", hggShard + pcount * 5);
                        } else if (rarity == 5) {
                            item.put("type", "HGG_SHD");
                            item.put("id", "4004");
                            item.put("count", pcount * 10);
                            itemGet.add(item);
                            UserSyncData.getJSONObject("status").put("hggShard", hggShard + pcount * 10);
                        }
                    }

                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    status = new JSONObject(true);
                    status.put("lggShard", UserSyncData.getJSONObject("status").getIntValue("lggShard"));
                    status.put("hggShard", UserSyncData.getJSONObject("status").getIntValue("hggShard"));
                    modified.put("status", status);
                    modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    result.put("items", itemGet);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @RequestMapping({"/getGoodPurchaseState"})
    public JSONObject getGoodPurchaseState() {
        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        result.put("result", new JSONObject(true));
        return result;
    }

    @RequestMapping({"/getCashGoodList"})
    public JSONObject getCashGoodList() {
        JSONObject result = new JSONObject();
        JSONArray CashGoodList = ArknightsApplication.CashGoodList;
        result.put("goodList", CashGoodList);
        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @RequestMapping({"/getGPGoodList"})
    public JSONObject getGPGoodList(@RequestHeader(value = "secret", required = false) String secret) {
        return withAvailableCounts(ArknightsApplication.GPGoodList, loadUserData(secret), goodId -> gpShopPath(goodId));
    }

    @RequestMapping({"/getLowGoodList"})
    public JSONObject getLowGoodList(@RequestHeader(value = "secret", required = false) String secret) {
        return withAvailableCounts(ArknightsApplication.LowGoodList, loadUserData(secret), goodId -> new String[]{"LS"});
    }

    @RequestMapping({"/getHighGoodList"})
    public JSONObject getHighGoodList(@RequestHeader(value = "secret", required = false) String secret) {
        return withAvailableCounts(ArknightsApplication.HighGoodList, loadUserData(secret), goodId -> new String[]{"HS"});
    }

    @RequestMapping({"/getExtraGoodList"})
    public JSONObject getExtraGoodList(@RequestHeader(value = "secret", required = false) String secret) {
        return withAvailableCounts(ArknightsApplication.ExtraGoodList, loadUserData(secret), goodId -> new String[]{"ES"});
    }

    @RequestMapping({"/getLMTGSGoodList"})
    public JSONObject getLMTGSGoodList() {
        return ArknightsApplication.LMTGSGoodList;
    }

    @RequestMapping({"/getEPGSGoodList"})
    public JSONObject getEPGSGoodList() {
        return ArknightsApplication.EPGSGoodList;
    }

    @RequestMapping({"/getRepGoodList"})
    public JSONObject getRepGoodList() {
        return ArknightsApplication.RepGoodList;
    }

    @RequestMapping({"/getFurniGoodList"})
    public JSONObject getFurniGoodList() {
        return ArknightsApplication.FurniGoodList;
    }

    @RequestMapping({"/getSocialGoodList"})
    public JSONObject getSocialGoodList(@RequestHeader(value = "secret", required = false) String secret) {
        JSONObject userData = null;
        String uid = null;
        try {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            if (accounts.size() == 1) {
                Account account = accounts.get(0);
                uid = String.valueOf(account.getUid());
                userData = JSONObject.parseObject(account.getUser());
            }
        } catch (Exception ignored) {
        }
        return buildSocialGoodList(userData, uid);
    }

    private static volatile java.util.List<Object[]> CHAR_UNLOCK_CACHE = null;
    private static volatile java.util.Map<String, String> CHAR_NAME_CACHE = null;

    private static void loadCharUnlocks() {
        if (CHAR_UNLOCK_CACHE != null && CHAR_NAME_CACHE != null) {
            return;
        }
        synchronized (shop.class) {
            if (CHAR_UNLOCK_CACHE != null && CHAR_NAME_CACHE != null) {
                return;
            }
            java.util.List<Object[]> list = new java.util.ArrayList<>();
            java.util.Map<String, String> names = new java.util.HashMap<>();
            try {
                String base = System.getProperty("user.dir") + "/data/excel/";
                JSONObject st = JSONObject.parseObject(new String(
                        java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(base + "shop_client_table.json")),
                        java.nio.charset.StandardCharsets.UTF_8));
                JSONObject groups = st.getJSONObject("creditUnlockGroup");
                if (groups != null) {
                    // 两个组的 charDict 合并（同一干员取最小 unlockNum），按累计消费解锁
                    java.util.Map<String, Integer> merged = new java.util.LinkedHashMap<>();
                    for (String gid : groups.keySet()) {
                        JSONObject g = groups.getJSONObject(gid);
                        JSONArray dict = g == null ? null : g.getJSONArray("charDict");
                        if (dict == null) {
                            continue;
                        }
                        for (int i = 0; i < dict.size(); i++) {
                            JSONObject e = dict.getJSONObject(i);
                            if (e == null) {
                                continue;
                            }
                            String cid = e.getString("charId");
                            int need = e.getIntValue("unlockNum");
                            Integer old = merged.get(cid);
                            if (old == null || need < old) {
                                merged.put(cid, need);
                            }
                        }
                    }
                    for (java.util.Map.Entry<String, Integer> en : merged.entrySet()) {
                        list.add(new Object[]{en.getKey(), en.getValue()});
                    }
                }
                // 干员名字
                JSONObject chars = JSONObject.parseObject(new String(
                        java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(base + "character_table.json")),
                        java.nio.charset.StandardCharsets.UTF_8));
                if (chars != null) {
                    for (String cid : chars.keySet()) {
                        JSONObject c = chars.getJSONObject(cid);
                        if (c != null) {
                            names.put(cid, c.getString("name"));
                        }
                    }
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[shop] 读取信用商店干员解锁失败: {}", e.toString());
            }
            CHAR_UNLOCK_CACHE = list;
            CHAR_NAME_CACHE = names;
            ArknightsApplication.LOGGER.info("[shop] 信用商店干员解锁 {} 条", list.size());
        }
    }

    private static volatile java.util.List<Object[]> SOCIAL_POOL_CACHE = null;

    private static java.util.List<Object[]> socialPool() {
        if (SOCIAL_POOL_CACHE != null) {
            return SOCIAL_POOL_CACHE;
        }
        synchronized (shop.class) {
            if (SOCIAL_POOL_CACHE != null) {
                return SOCIAL_POOL_CACHE;
            }
            java.util.List<Object[]> pool = new java.util.ArrayList<>();
            pool.add(new Object[]{"4001", "GOLD", "龙门币", "T1", "gold_2", 100, 1800});
            pool.add(new Object[]{"7002", "TKT_INST_FIN", "加急许可", "T1", "speed_3", 160, 1});
            pool.add(new Object[]{"3401", "MATERIAL", "家具零件", "T1", "complexcoin_3", 160, 20});
            try {
                String path = System.getProperty("user.dir") + "/data/excel/item_table.json";
                String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                        java.nio.charset.StandardCharsets.UTF_8);
                JSONObject table = JSONObject.parseObject(content);
                JSONObject items = table.getJSONObject("items");
                if (items != null) {
                    for (String id : items.keySet()) {
                        JSONObject it = items.getJSONObject(id);
                        if (it == null) {
                            continue;
                        }
                        String rarity = it.getString("rarity");
                        if (rarity == null || !rarity.startsWith("TIER_")) {
                            continue;
                        }
                        int tier;
                        try {
                            tier = Integer.parseInt(rarity.substring(5));
                        } catch (Exception e) {
                            continue;
                        }
                        if (tier < 1 || tier > 4) {
                            continue;
                        }
                        if (!"MATERIAL".equals(it.getString("itemType"))) {
                            continue;
                        }
                        String name = it.getString("name");
                        if (name == null || name.isEmpty()) {
                            continue;
                        }
                        // 只收普通材料（3xxxx/3xxx）和经验卡（2001~2004），排除干员信物 p_char_*、芯片 32xx
                        boolean isMaterial = id.matches("^3\\d{3,4}$") && !id.startsWith("32");
                        boolean isExpCard = id.matches("^200[1-4]$");
                        if (!isMaterial && !isExpCard) {
                            continue;
                        }
                        int price = tier <= 1 ? 80 : (tier == 2 ? 120 : 160);
                        int count = tier <= 1 ? 2 : (tier == 2 ? 2 : 1);
                        String type = isExpCard ? "CARD_EXP" : "MATERIAL";
                        pool.add(new Object[]{id, type, name, "T" + tier, "goods_" + id, price, count});
                    }
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("[shop] 读 item_table 失败，信用商店用小池子: {}", e.toString());
            }
            if (pool.size() < 12) {
                pool.add(new Object[]{"30011", "MATERIAL", "源岩", "T1", "goods_2", 80, 2});
                pool.add(new Object[]{"30051", "MATERIAL", "双酮", "T1", "goods_13", 120, 2});
                pool.add(new Object[]{"30061", "MATERIAL", "破损装置", "T1", "goods_17", 160, 2});
                pool.add(new Object[]{"2001", "CARD_EXP", "基础作战记录", "T1", "exp_2", 100, 9});
            }
            ArknightsApplication.LOGGER.info("[shop] 信用商店候选池 {} 种商品", pool.size());
            SOCIAL_POOL_CACHE = pool;
            return pool;
        }
    }

    private static JSONObject buildSocialGoodList(JSONObject userData, String uid) {
        java.time.ZonedDateTime nowCn = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        String day = String.format("%04d%02d%02d", nowCn.getYear(), nowCn.getMonthValue(), nowCn.getDayOfMonth());
        String shopId = "SOCIAL" + day;

        JSONObject socialShop = null;
        if (userData != null) {
            JSONObject shopNode = userData.getJSONObject("shop");
            socialShop = shopNode == null ? null : shopNode.getJSONObject("SOCIAL");
        }
        int spent = 0;
        JSONArray info = new JSONArray();
        JSONObject charPurchase = new JSONObject(true);
        if (socialShop != null) {
            if (shopId.equals(socialShop.getString("curShopId"))) {
                spent = socialShop.getIntValue("costSocialPoint");
                JSONArray old = socialShop.getJSONArray("info");
                if (old != null) {
                    info = old;
                }
            }
            JSONObject cp = socialShop.getJSONObject("charPurchase");
            if (cp != null) {
                charPurchase = cp;
            }
        }

        java.util.List<Object[]> pool = socialPool();
        java.util.Random rnd = new java.util.Random(Long.parseLong(day));
        java.util.List<Object[]> shuffled = new java.util.ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, rnd);
        JSONArray goodList = new JSONArray();
        int slot = 1;
        // 先放已解锁的干员商品（官服：slot 1 起，price 100，availCount 1，goodId=SOCIAL{day}_char_{charId}）
        loadCharUnlocks();
        java.util.List<Object[]> unlocks = CHAR_UNLOCK_CACHE;
        if (unlocks != null) {
            for (Object[] u : unlocks) {
                if (slot > 10) {
                    break;
                }
                String charId = (String) u[0];
                int unlockNum = (Integer) u[1];
                if (spent < unlockNum) {
                    continue;
                }
                // charPurchase 里存的是剩余可购买次数：买过（<=0）就不再上架
                if (charPurchase.containsKey(charId) && charPurchase.getIntValue(charId) <= 0) {
                    continue;
                }
                JSONObject item = new JSONObject(true);
                item.put("id", charId);
                item.put("count", 1);
                item.put("type", "CHAR");
                JSONObject good = new JSONObject(true);
                good.put("goodId", "SOCIAL" + day + "_" + charId);
                good.put("displayName", CHAR_NAME_CACHE == null ? charId : CHAR_NAME_CACHE.getOrDefault(charId, charId));
                good.put("originPrice", 100);
                good.put("price", 100);
                good.put("discount", 0);
                good.put("slotId", slot);
                good.put("availCount", 1);
                good.put("item", item);
                goodList.add(good);
                slot++;
            }
        }
        for (; slot <= 10; slot++) {
            Object[] def = shuffled.get((slot - 1) % shuffled.size());
            int originPrice = (Integer) def[5];
            double[] discounts = {0.5, 0.75, 0.0};
            double discount = discounts[rnd.nextInt(discounts.length)];
            int price = discount <= 0 ? originPrice : Math.max(10, (int) Math.round(originPrice * discount));
            JSONObject item = new JSONObject(true);
            item.put("id", def[0]);
            item.put("count", def[6]);
            item.put("type", def[1]);
            JSONObject good = new JSONObject(true);
            good.put("goodId", "SOCIAL" + day + "_" + def[3] + "_" + def[4] + "_" + slot);
            good.put("displayName", def[2]);
            good.put("originPrice", originPrice);
            good.put("price", price);
            good.put("discount", discount);
            good.put("slotId", slot);
            good.put("availCount", 1);
            good.put("item", item);
            goodList.add(good);
        }

        JSONObject socialDelta = new JSONObject(true);
        socialDelta.put("curShopId", shopId);
        socialDelta.put("info", info);
        socialDelta.put("charPurchase", charPurchase);
        JSONObject shopDelta = new JSONObject(true);
        shopDelta.put("SOCIAL", socialDelta);
        JSONObject modified = new JSONObject(true);
        modified.put("shop", shopDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("goodList", goodList);
        result.put("charPurchase", charPurchase);
        result.put("costSocialPoint", spent);
        result.put("creditGroup", spent >= 4000 ? "creditGroup2" : "creditGroup1");
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private static JSONObject findSocialGood(JSONObject userData, String uid, String goodId) {
        JSONObject list = buildSocialGoodList(userData, uid);
        JSONArray goodList = list.getJSONArray("goodList");
        if (goodList == null || goodId == null) {
            return null;
        }
        for (int i = 0; i < goodList.size(); i++) {
            JSONObject g = goodList.getJSONObject(i);
            if (g != null && goodId.equals(g.getString("goodId"))) {
                return g;
            }
        }
        return null;
    }


    @RequestMapping({"/getClassicGoodList"})
    public JSONObject getClassicGoodList() {
        return ArknightsApplication.ClassicGoodList;
    }

    private JSONObject buyShardGood(String secret, JSONObject JsonBody, HttpServletResponse response,
                                    JSONArray goodList, String currencyField, boolean currencyInInventory) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject closed = new JSONObject(true);
            closed.put("statusCode", 400);
            closed.put("error", "Bad Request");
            closed.put("message", "server is close");
            return closed;
        }

        String goodId = JsonBody.getString("goodId");
        int count = Math.max(1, JsonBody.getIntValue("count"));

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Account account = Accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "account banned");
            return banned;
        }

        JSONObject good = null;
        if (goodList != null && goodId != null) {
            for (int i = 0; i < goodList.size(); i++) {
                JSONObject g = goodList.getJSONObject(i);
                if (g != null && goodId.equals(g.getString("goodId"))) {
                    good = g;
                    break;
                }
            }
        }
        if (good == null) {
            return badRequest(response, "good not found: " + goodId);
        }
        JSONObject itemDef = good.getJSONObject("item");
        if (itemDef == null) {
            return badRequest(response, "good has no item: " + goodId);
        }

        int price = good.getIntValue("price") * count;
        JSONObject userData = JSONObject.parseObject(account.getUser());

        String[] shopPath = currencyInInventory ? new String[]{"ES"}
                : ("lggShard".equals(currencyField) ? new String[]{"LS"} : new String[]{"HS"});
        String soldOut = checkAvailCount(good, userData, shopPath, goodId, count);
        if (soldOut != null) {
            return badRequest(response, soldOut);
        }

        JSONObject status = userData.getJSONObject("status");
        JSONObject inventory = userData.getJSONObject("inventory");

        int balance = currencyInInventory ? inventory.getIntValue(currencyField)
                : status.getIntValue(currencyField);
        if (balance < price) {
            return badRequest(response, "not enough " + currencyField
                    + ": need " + price + ", have " + balance);
        }

        JSONObject modified = new JSONObject(true);
        JSONObject currencyDelta = new JSONObject(true);
        currencyDelta.put(currencyField, balance - price);
        if (currencyInInventory) {
            inventory.put(currencyField, balance - price);
            mergeNode(modified, "inventory", currencyDelta);
        } else {
            status.put(currencyField, balance - price);
            mergeNode(modified, "status", currencyDelta);
        }

        JSONObject grant = new JSONObject(true);
        grant.put("id", itemDef.getString("id"));
        grant.put("type", itemDef.getString("type"));
        grant.put("count", itemDef.getIntValue("count") * count);

        JSONArray items = new JSONArray();
        if ("CHAR".equals(grant.getString("type"))) {
            ArknightsApplication.LOGGER.info("[shop] uid={} 购买干员 {} x{}",
                    account.getUid(), grant.getString("id"), grant.getIntValue("count"));
            java.util.Map<String, String> before = snapshotUserNodes(userData);
            userData = handleUserDataChange(grant, userData, account.getUid(), 0, items);
            mergeNodeDiff(modified, userData, before);
        } else {
            modified = handleModifiedBuild(grant, modified, userData, 0);
            userData = handleUserDataChange(grant, userData, account.getUid(), 0);
            items.add(grant);
        }

        userDao.setUserData(account.getUid(), userData);

        recordPurchase(modified, userData, shopPath, goodId, count,
                "lggShard".equals(currencyField) ? "lggCostTotal" : null,
                "lggShard".equals(currencyField) ? price : 0);
        userDao.setUserData(account.getUid(), userData);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        result.put("items", items);
        result.put("result", 0);
        ArknightsApplication.LOGGER.info("[shop] uid={} 购买 {} x{} 花费 {}{}，modified={}",
                account.getUid(), goodId, count, price, currencyField, modified.toJSONString());
        return result;
    }

    private static JSONObject loadUserData(String secret) {
        try {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            if (accounts.size() != 1) {
                return null;
            }
            return JSONObject.parseObject(accounts.get(0).getUser());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[shop] 读取玩家存档失败: {}", e.toString());
            return null;
        }
    }

    private static JSONObject withAvailableCounts(JSONObject source, JSONObject userData,
                                                  java.util.function.Function<String, String[]> pathResolver) {
        if (source == null) {
            return new JSONObject(true);
        }
        if (userData == null) {
            return source;
        }
        JSONObject copy = JSONObject.parseObject(source.toJSONString());
        subtractBoughtRecursive(copy, userData, pathResolver);
        return copy;
    }

    private static void subtractBoughtRecursive(Object node, JSONObject userData,
                                                java.util.function.Function<String, String[]> pathResolver) {
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.size(); i++) {
                subtractBoughtRecursive(arr.get(i), userData, pathResolver);
            }
            return;
        }
        if (!(node instanceof JSONObject)) {
            return;
        }
        JSONObject obj = (JSONObject) node;
        String goodId = obj.getString("goodId");
        if (goodId != null && obj.containsKey("availCount")) {
            String[] path = pathResolver.apply(goodId);
            if (path != null) {
                int left = obj.getIntValue("availCount") - boughtCount(userData, path, goodId);
                obj.put("availCount", Math.max(0, left));
            }
        }
        for (String key : new java.util.ArrayList<>(obj.keySet())) {
            subtractBoughtRecursive(obj.get(key), userData, pathResolver);
        }
    }

    private static JSONObject badRequest(HttpServletResponse response, String info) {
        response.setStatus(400);
        JSONObject r = new JSONObject(true);
        r.put("code", 5847);
        r.put("msg", "invalid parameter");
        r.put("info", info);
        r.put("message", "server error");
        return r;
    }

    @PostMapping({"/buySocialGood"})
    public JSONObject buySocialGood(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject JsonBody,
                                    HttpServletResponse response,
                                    HttpServletRequest request) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject closed = new JSONObject(true);
            closed.put("statusCode", 400);
            closed.put("error", "Bad Request");
            closed.put("message", "server is close");
            return closed;
        }

        String goodId = JsonBody.getString("goodId");
        int count = Math.max(1, JsonBody.getIntValue("count"));

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Account account = (Account) Accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "account banned");
            return banned;
        }

        JSONObject good = null;
        {
            JSONObject u0 = JSONObject.parseObject(account.getUser());
            good = findSocialGood(u0, String.valueOf(account.getUid()), goodId);
        }
        if (good == null) {
            return badRequest(response, "good not found: " + goodId);
        }
        JSONObject itemDef = good.getJSONObject("item");
        if (itemDef == null) {
            return badRequest(response, "good has no item: " + goodId);
        }

        int price = good.getIntValue("price") * count;
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject status = userData.getJSONObject("status");
        int balance = status.getIntValue("socialPoint");
        if (balance < price) {
            return badRequest(response, "not enough socialPoint: need " + price + ", have " + balance);
        }

        // 干员商品：受 charPurchase 剩余次数限制
        String charId = "CHAR".equals(itemDef.getString("type")) ? itemDef.getString("id") : null;
        if (charId != null) {
            JSONObject shopNode = userData.getJSONObject("shop");
            JSONObject socialShop = shopNode == null ? null : shopNode.getJSONObject("SOCIAL");
            JSONObject charPurchase = socialShop == null ? null : socialShop.getJSONObject("charPurchase");
            int left = charPurchase != null && charPurchase.get(charId) != null
                    ? charPurchase.getIntValue(charId)
                    : 1;
            if (left < count) {
                return badRequest(response, "char sold out: " + charId + " left=" + left);
            }
        }

        JSONObject modified = new JSONObject(true);
        status.put("socialPoint", balance - price);
        JSONObject statusDelta = new JSONObject(true);
        statusDelta.put("socialPoint", balance - price);
        modified.put("status", statusDelta);

        JSONObject grant = new JSONObject(true);
        grant.put("id", itemDef.getString("id"));
        grant.put("type", itemDef.getString("type"));
        grant.put("count", itemDef.getIntValue("count") * count);

        JSONArray items = new JSONArray();
        java.util.Map<String, String> before = snapshotUserNodes(userData);
        userData = handleUserDataChange(grant, userData, account.getUid(), 0, items);
        mergeNodeDiff(modified, userData, before);
        if (items.isEmpty()) {
            items.add(grant);
        }

        // shop.SOCIAL.info 记账（已买过的 good 累加数量）
        JSONObject shopNode = userData.getJSONObject("shop");
        if (shopNode == null) {
            shopNode = new JSONObject(true);
            userData.put("shop", shopNode);
        }
        JSONObject socialShop = shopNode.getJSONObject("SOCIAL");
        if (socialShop == null) {
            socialShop = new JSONObject(true);
            shopNode.put("SOCIAL", socialShop);
        }
        JSONArray info = socialShop.getJSONArray("info");
        if (info == null) {
            info = new JSONArray();
            socialShop.put("info", info);
        }
        boolean merged = false;
        for (int i = 0; i < info.size(); i++) {
            JSONObject e = info.getJSONObject(i);
            if (goodId.equals(e.getString("id"))) {
                e.put("count", e.getIntValue("count") + count);
                merged = true;
                break;
            }
        }
        if (!merged) {
            JSONObject e = new JSONObject(true);
            e.put("id", goodId);
            e.put("count", count);
            info.add(e);
        }
        // 当日已消费信用点累计（官服 costSocialPoint）
        socialShop.put("costSocialPoint", socialShop.getIntValue("costSocialPoint") + price);
        socialShop.put("curShopId", "SOCIAL" + java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));

        // 干员商品扣 charPurchase
        if (charId != null) {
            JSONObject charPurchase = socialShop.getJSONObject("charPurchase");
            if (charPurchase == null) {
                charPurchase = new JSONObject(true);
                socialShop.put("charPurchase", charPurchase);
            }
            int left = charPurchase.get(charId) != null ? charPurchase.getIntValue(charId) : 1;
            charPurchase.put(charId, Math.max(0, left - count));
        }

        userDao.setUserData(account.getUid(), userData);

        JSONObject socialShopDelta = new JSONObject(true);
        socialShopDelta.put("info", info);
        socialShopDelta.put("costSocialPoint", socialShop.getIntValue("costSocialPoint"));
        socialShopDelta.put("curShopId", socialShop.getString("curShopId"));
        if (charId != null) {
            socialShopDelta.put("charPurchase", socialShop.getJSONObject("charPurchase"));
        }
        JSONObject shopDelta = new JSONObject(true);
        shopDelta.put("SOCIAL", socialShopDelta);
        modified.put("shop", shopDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("items", items);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[shop] uid={} 信用商店买 {} x{} 花费 {}，余额 {}",
                account.getUid(), goodId, count, price, balance - price);
        return result;
    }

    private static JSONObject loadShopFile(String name) {
        String path = System.getProperty("user.dir") + "/data/shop/" + name + ".json";
        try {
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    java.nio.charset.StandardCharsets.UTF_8);
            return JSONObject.parseObject(content);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.error("[shop] 读取 {} 失败: {}", path, e.toString());
            return null;
        }
    }

    private static JSONObject serveShopList(String file, String path, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /shop/" + path);
        JSONObject data = loadShopFile(file);
        JSONObject result = new JSONObject(true);
        if (data == null) {
            JSONObject delta = new JSONObject(true);
            delta.put("modified", new JSONObject(true));
            delta.put("deleted", new JSONObject(true));
            result.put("playerDataDelta", delta);
            return result;
        }
        for (String key : data.keySet()) {
            if ("playerDataDelta".equals(key)) {
                continue;
            }
            result.put(key, data.get(key));
        }
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }

    @PostMapping(value = {"/getCashGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getCashGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("CashGoodList", "getCashGoodList", request);
    }

    @PostMapping(value = {"/getLowGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getLowGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("LowGoodList", "getLowGoodList", request);
    }

    @PostMapping(value = {"/getHighGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getHighGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("HighGoodList", "getHighGoodList", request);
    }

    @PostMapping(value = {"/getExtraGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getExtraGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("ExtraGoodList", "getExtraGoodList", request);
    }

    @PostMapping(value = {"/getSocialGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getSocialGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("SocialGoodList", "getSocialGoodList", request);
    }

    @PostMapping(value = {"/getClassicGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getClassicGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                         @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("ClassicGoodList", "getClassicGoodList", request);
    }

    @PostMapping(value = {"/getLMTGSGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getLMTGSGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("LMTGSGoodList", "getLMTGSGoodList", request);
    }

    @PostMapping(value = {"/getEPGSGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getEPGSGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("EPGSGoodList", "getEPGSGoodList", request);
    }

    @PostMapping(value = {"/getRepGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getRepGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("RepGoodList", "getRepGoodList", request);
    }

    @PostMapping(value = {"/getGPGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getGPGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("GPGoodList", "getGPGoodList", request);
    }

    @PostMapping(value = {"/getGoodPurchaseState"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getGoodPurchaseState(@RequestHeader(value = "secret", required = false) String secret,
                                           @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        return serveShopList("GoodPurchaseState", "getGoodPurchaseState", request);
    }

    private static JSONObject FURNI_GOOD_TABLE;

    private static synchronized JSONObject furniGoodTable() {
        if (FURNI_GOOD_TABLE != null) {
            return FURNI_GOOD_TABLE;
        }
        String path = System.getProperty("user.dir") + "/data/shop/FurniGoodList.json";
        try {
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    java.nio.charset.StandardCharsets.UTF_8);
            FURNI_GOOD_TABLE = JSONObject.parseObject(content);
            ArknightsApplication.LOGGER.info("[furni] 家具商店加载完成: goods={} groups={}",
                    FURNI_GOOD_TABLE.getJSONArray("goods").size(), FURNI_GOOD_TABLE.getJSONArray("groups").size());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.error("[furni] 读取 {} 失败: {}", path, e.toString());
            FURNI_GOOD_TABLE = new JSONObject(true);
        }
        return FURNI_GOOD_TABLE;
    }

    private static JSONObject findFurniGood(String goodId) {
        JSONArray goods = furniGoodTable().getJSONArray("goods");
        if (goods != null) {
            for (int i = 0; i < goods.size(); i++) {
                JSONObject g = goods.getJSONObject(i);
                if (goodId.equals(g.getString("goodId"))) {
                    return g;
                }
            }
        }
        JSONArray groups = furniGoodTable().getJSONArray("groups");
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                JSONArray list = groups.getJSONObject(i).getJSONArray("goodList");
                if (list == null) {
                    continue;
                }
                for (int k = 0; k < list.size(); k++) {
                    String id = list.getJSONObject(k).getString("goodId");
                    if (goodId.equals(id)) {
                        JSONObject hit = new JSONObject(true);
                        hit.put("goodId", id);
                        // 套组内单件价格表里没有，用 0 兜底（官方也只允许整包买）
                        hit.put("furniId", null);
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    @PostMapping(value = {"/getFurniGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getFurniGoodList(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject jsonBody,
                                       HttpServletRequest request, HttpServletResponse response) {
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /shop/getFurniGoodList");
        JSONObject table = furniGoodTable();
        JSONObject result = new JSONObject(true);
        result.put("goods", table.getJSONArray("goods") == null ? new JSONArray() : table.getJSONArray("goods"));
        result.put("groups", table.getJSONArray("groups") == null ? new JSONArray() : table.getJSONArray("groups"));
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }

    /** 家具入库：building.furniture.count += n、solution.furnitureTs[id] = now */
    private static JSONObject addFurniToSave(JSONObject userData, String furniId, int count, long now) {
        JSONObject building = userData.getJSONObject("building");
        JSONObject furni = building.getJSONObject("furniture");
        if (furni == null) {
            furni = new JSONObject(true);
            building.put("furniture", furni);
        }
        JSONObject entry = furni.getJSONObject(furniId);
        if (entry == null) {
            entry = new JSONObject(true);
            entry.put("count", 0);
            entry.put("inUse", 0);
        }
        entry.put("count", entry.getIntValue("count") + count);
        furni.put(furniId, entry);

        JSONObject solution = building.getJSONObject("solution");
        if (solution == null) {
            solution = new JSONObject(true);
            building.put("solution", solution);
        }
        JSONObject ts = solution.getJSONObject("furnitureTs");
        if (ts == null) {
            ts = new JSONObject(true);
            solution.put("furnitureTs", ts);
        }
        ts.put(furniId, now);

        JSONObject entryOut = new JSONObject(true);
        entryOut.put("count", entry.getIntValue("count"));
        entryOut.put("inUse", entry.getIntValue("inUse"));
        return entryOut;
    }

    private static int bumpFurniShopCount(JSONObject userData, String goodId, int add) {
        JSONObject shop = userData.getJSONObject("shop");
        if (shop == null) {
            shop = new JSONObject(true);
            userData.put("shop", shop);
        }
        JSONObject furniShop = shop.getJSONObject("FURNI");
        if (furniShop == null) {
            furniShop = new JSONObject(true);
            shop.put("FURNI", furniShop);
        }
        JSONArray info = furniShop.getJSONArray("info");
        if (info == null) {
            info = new JSONArray();
            furniShop.put("info", info);
        }
        for (int i = 0; i < info.size(); i++) {
            JSONObject it = info.getJSONObject(i);
            if (goodId.equals(it.getString("id"))) {
                it.put("count", it.getIntValue("count") + add);
                return it.getIntValue("count");
            }
        }
        JSONObject fresh = new JSONObject(true);
        fresh.put("id", goodId);
        fresh.put("count", add);
        info.add(fresh);
        return add;
    }

    @PostMapping(value = {"/buyFurniGood"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject buyFurniGood(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String goodId = jsonBody.getString("goodId");
        int buyCount = Math.max(1, jsonBody.getIntValue("buyCount"));
        String costType = jsonBody.getString("costType");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /shop/buyFurniGood goodId=" + goodId + " x" + buyCount + " costType=" + costType);

        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 400);
            return result;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            return result;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());

        JSONObject good = findFurniGood(goodId);
        if (good == null || good.getString("furniId") == null) {
            result.put("result", 1);
            result.put("error", "未知家具商品: " + goodId);
            return result;
        }
        String furniId = good.getString("furniId");
        int priceCoin = good.getIntValue("priceCoin");
        int priceDia = good.getIntValue("priceDia");

        JSONObject inventory = userData.getJSONObject("inventory");
        JSONObject status = userData.getJSONObject("status");
        JSONObject modified = new JSONObject(true);

        boolean payDia = priceDia > 0 && !"COIN_FURN".equals(costType);
        if (payDia) {
            int cost = priceDia * buyCount;
            int have = status.getIntValue("androidDiamond");
            if (have < cost) {
                result.put("result", 1);
                result.put("error", "至纯源石不足: 需要 " + cost + "，只有 " + have);
                return result;
            }
            JSONObject sd = new JSONObject(true);
            sd.put("androidDiamond", have - cost);
            status.put("androidDiamond", have - cost);
            modified.put("status", sd);
        } else {
            int cost = priceCoin * buyCount;
            int have = inventory.getIntValue("3401");
            if (have < cost) {
                result.put("result", 1);
                result.put("error", "家具零件不足: 需要 " + cost + "，只有 " + have);
                return result;
            }
            JSONObject inv = new JSONObject(true);
            inv.put("3401", have - cost);
            inventory.put("3401", have - cost);
            modified.put("inventory", inv);
        }

        JSONObject entryOut = addFurniToSave(userData, furniId, buyCount, ArknightsApplication.getTimestamp());
        int newShopCount = bumpFurniShopCount(userData, goodId, buyCount);

        JSONObject buildingDelta = new JSONObject(true);
        JSONObject furniDelta = new JSONObject(true);
        furniDelta.put(furniId, entryOut);
        buildingDelta.put("furniture", furniDelta);
        JSONObject solutionDelta = new JSONObject(true);
        JSONObject tsDelta = new JSONObject(true);
        tsDelta.put(furniId, ArknightsApplication.getTimestamp());
        solutionDelta.put("furnitureTs", tsDelta);
        buildingDelta.put("solution", solutionDelta);
        modified.put("building", buildingDelta);

        JSONObject shopDelta = new JSONObject(true);
        JSONObject furniShopDelta = new JSONObject(true);
        JSONArray infoDelta = new JSONArray();
        JSONObject infoItem = new JSONObject(true);
        infoItem.put("id", goodId);
        infoItem.put("count", newShopCount);
        infoDelta.add(infoItem);
        furniShopDelta.put("info", infoDelta);
        shopDelta.put("FURNI", furniShopDelta);
        modified.put("shop", shopDelta);

        JSONObject carouselDelta = new JSONObject(true);
        JSONObject furniCarousel = new JSONObject(true);
        JSONObject goodsMark = new JSONObject(true);
        goodsMark.put(goodId, 1);
        furniCarousel.put("goods", goodsMark);
        carouselDelta.put("furnitureShop", furniCarousel);
        modified.put("carousel", carouselDelta);

        userData.put("carousel", mergeCarousel(userData.getJSONObject("carousel"), goodId, null));

        userDao.setUserData(account.getUid(), userData);

        JSONArray items = new JSONArray();
        JSONObject item = new JSONObject(true);
        item.put("type", "FURN");
        item.put("id", furniId);
        item.put("count", buyCount);
        items.add(item);

        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        result.put("items", items);
        result.put("playerDataDelta", delta);
        ArknightsApplication.LOGGER.info("[shop] uid={} 买家具 {} x{} → {}", account.getUid(), goodId, buyCount, furniId);
        return result;
    }

    private static JSONObject mergeCarousel(JSONObject carousel, String goodId, String groupId) {
        JSONObject out = carousel == null ? new JSONObject(true) : carousel;
        JSONObject furniShop = out.getJSONObject("furnitureShop");
        if (furniShop == null) {
            furniShop = new JSONObject(true);
            out.put("furnitureShop", furniShop);
        }
        JSONObject goods = furniShop.getJSONObject("goods");
        if (goods == null) {
            goods = new JSONObject(true);
            furniShop.put("goods", goods);
        }
        if (goodId != null) {
            goods.put(goodId, 1);
        }
        if (groupId != null) {
            JSONObject groups = furniShop.getJSONObject("groups");
            if (groups == null) {
                groups = new JSONObject(true);
                furniShop.put("groups", groups);
            }
            groups.put(groupId, 1);
        }
        return out;
    }

    @PostMapping(value = {"/buyFurniGroup"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject buyFurniGroup(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject jsonBody,
                                    HttpServletRequest request, HttpServletResponse response) {
        String groupId = jsonBody.getString("groupId");
        JSONArray goodsReq = jsonBody.getJSONArray("goods");
        String costType = jsonBody.getString("costType");
        ArknightsApplication.LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request)
                + "] /shop/buyFurniGroup groupId=" + groupId + " goods=" + goodsReq + " costType=" + costType);

        JSONObject result = new JSONObject(true);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            result.put("result", 2);
            return result;
        }
        if (goodsReq == null || goodsReq.isEmpty()) {
            result.put("result", 1);
            result.put("error", "goods 不能为空");
            return result;
        }
        Account account = accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject status = userData.getJSONObject("status");

        int totalDia = 0;
        JSONArray items = new JSONArray();
        StringBuilder err = new StringBuilder();
        for (int i = 0; i < goodsReq.size(); i++) {
            JSONObject g = goodsReq.getJSONObject(i);
            String goodId = g.getString("id");
            int cnt = Math.max(1, g.getIntValue("count"));
            JSONObject def = findFurniGood(goodId);
            if (def == null || def.getString("furniId") == null) {
                err.append("未知商品 ").append(goodId).append("; ");
                continue;
            }
            totalDia += def.getIntValue("priceDia") * cnt;
            JSONObject item = new JSONObject(true);
            item.put("type", "FURN");
            item.put("id", def.getString("furniId"));
            item.put("count", cnt);
            items.add(item);
        }
        if (err.length() > 0) {
            result.put("result", 1);
            result.put("error", err.toString());
            return result;
        }

        JSONObject modified = new JSONObject(true);
        if (totalDia > 0) {
            int have = status.getIntValue("androidDiamond");
            if (have < totalDia) {
                result.put("result", 1);
                result.put("error", "至纯源石不足: 需要 " + totalDia + "，只有 " + have);
                return result;
            }
            status.put("androidDiamond", have - totalDia);
            JSONObject sd = new JSONObject(true);
            sd.put("androidDiamond", have - totalDia);
            modified.put("status", sd);
        }

        JSONObject furniDelta = new JSONObject(true);
        JSONObject tsDelta = new JSONObject(true);
        long now = ArknightsApplication.getTimestamp();
        JSONObject shopInfo = new JSONObject(true);
        for (int i = 0; i < goodsReq.size(); i++) {
            JSONObject g = goodsReq.getJSONObject(i);
            String goodId = g.getString("id");
            int cnt = Math.max(1, g.getIntValue("count"));
            JSONObject def = findFurniGood(goodId);
            String furniId = def.getString("furniId");
            furniDelta.put(furniId, addFurniToSave(userData, furniId, cnt, now));
            tsDelta.put(furniId, now);
            shopInfo.put(goodId, bumpFurniShopCount(userData, goodId, cnt));
        }

        JSONObject buildingDelta = new JSONObject(true);
        buildingDelta.put("furniture", furniDelta);
        JSONObject solutionDelta = new JSONObject(true);
        solutionDelta.put("furnitureTs", tsDelta);
        buildingDelta.put("solution", solutionDelta);
        modified.put("building", buildingDelta);

        JSONObject shopDelta = new JSONObject(true);
        JSONObject furniShopDelta = new JSONObject(true);
        JSONArray infoDelta = new JSONArray();
        for (Map.Entry<String, Object> e : shopInfo.entrySet()) {
            JSONObject it = new JSONObject(true);
            it.put("id", e.getKey());
            it.put("count", e.getValue());
            infoDelta.add(it);
        }
        furniShopDelta.put("info", infoDelta);
        shopDelta.put("FURNI", furniShopDelta);
        modified.put("shop", shopDelta);

        JSONObject carouselDelta = new JSONObject(true);
        JSONObject furniCarousel = new JSONObject(true);
        JSONObject groupsMark = new JSONObject(true);
        groupsMark.put(groupId, 1);
        furniCarousel.put("groups", groupsMark);
        carouselDelta.put("furnitureShop", furniCarousel);
        modified.put("carousel", carouselDelta);

        userData.put("carousel", mergeCarousel(userData.getJSONObject("carousel"), null, groupId));
        userDao.setUserData(account.getUid(), userData);

        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        result.put("items", items);
        result.put("playerDataDelta", delta);
        ArknightsApplication.LOGGER.info("[shop] uid={} 买家具套组 {} → {} 件，花 {} 源石",
                account.getUid(), groupId, items.size(), totalDia);
        return result;
    }
}
