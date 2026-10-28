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
    public JSONObject getSocialGoodList() {
        return ArknightsApplication.SocialGoodList;
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

        JSONArray goodList = ArknightsApplication.SocialGoodList.getJSONArray("goodList");
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
}
