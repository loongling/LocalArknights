package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.Admin;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/shop"})
public class shop {
    public shop() {
    }

    @PostMapping(
            value = {"/getSkinGoodList"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getSkinGoodList(@RequestBody JSONObject JsonBody) {
        JSONArray charIdList = JsonBody.getJSONArray("charIdList");
        JSONArray goodList = new JSONArray();
        if (charIdList.size() == 0) {
            return ArKnightsApplication.skinGoodList;
        } else {
            Iterator var4 = ArKnightsApplication.skinTable.entrySet().iterator();

            while(var4.hasNext()) {
                Map.Entry<String, Object> entry = (Map.Entry)var4.next();
                String skinId = (String)entry.getKey();
                if (skinId.indexOf(charIdList.getString(0)) != -1 && skinId.indexOf("@") != -1) {
                    JSONObject SkinData = ArKnightsApplication.skinTable.getJSONObject(skinId);
                    JSONObject SkinGood = new JSONObject(true);
                    SkinGood.put("charId", SkinData.getString("charId"));
                    SkinGood.put("skinId", SkinData.getString("skinId"));
                    SkinGood.put("goodId", "SS_" + SkinData.getString("skinId"));
                    SkinGood.put("slotId", SkinData.getJSONObject("displaySkin").getIntValue("sortId"));
                    SkinGood.put("skinName", SkinData.getJSONObject("displaySkin").getString("skinName"));
                    SkinGood.put("discount", 0);
                    SkinGood.put("originPrice", 18);
                    SkinGood.put("price", 18);
                    SkinGood.put("startDateTime", -1);
                    SkinGood.put("endDateTime", -1);
                    SkinGood.put("desc1", (Object)null);
                    SkinGood.put("desc2", (Object)null);
                    SkinGood.put("currencyUnit", "DIAMOND");
                    goodList.add(SkinGood);
                }
            }

            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("modified", new JSONObject(true));
            playerDataDelta.put("deleted", new JSONObject(true));
            result.put("playerDataDelta", playerDataDelta);
            result.put("goodList", goodList);
            return result;
        }
    }

    @PostMapping(
            value = {"/buySkinGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buySkinGood(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buySkinGood");
        if (!ArKnightsApplication.enableServer) {
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
                    UserSyncData.getJSONObject("skin").getJSONObject("characterSkins").put(goodId.substring(3), 1);
                    UserSyncData.getJSONObject("skin").getJSONObject("skinTs").put(goodId.substring(3), (new Date()).getTime() / 1000L);
                    UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") - 18);
                    UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") - 18);
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject status = new JSONObject(true);
                    status.put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond"));
                    status.put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond"));
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("status", status);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/buyLowGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyLowGood(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buyLowGood");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String goodId = JsonBody.getString("goodId");
            int count = JsonBody.getIntValue("count");
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
                    JSONArray items = new JSONArray();

                    JSONObject lowGood;
                    for(int i = 0; i < ArKnightsApplication.LowGoodList.getJSONArray("goodList").size(); ++i) {
                        lowGood = ArKnightsApplication.LowGoodList.getJSONArray("goodList").getJSONObject(i);
                        if (lowGood.getString("goodId").equals(goodId)) {
                            UserSyncData.getJSONObject("status").put("lggShard", UserSyncData.getJSONObject("status").getIntValue("lggShard") - lowGood.getIntValue("price") * count);
                            String reward_id = lowGood.getJSONObject("item").getString("id");
                            String reward_type = lowGood.getJSONObject("item").getString("type");
                            int reward_count = lowGood.getJSONObject("item").getIntValue("count") * count;
                            Admin.GM_GiveItem(UserSyncData, reward_id, reward_type, reward_count, items);
                            break;
                        }
                    }

                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    lowGood = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("status", UserSyncData.getJSONObject("status"));
                    modified.put("shop", UserSyncData.getJSONObject("shop"));
                    modified.put("troop", UserSyncData.getJSONObject("troop"));
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    lowGood.put("deleted", new JSONObject(true));
                    lowGood.put("modified", modified);
                    result.put("playerDataDelta", lowGood);
                    result.put("items", items);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/buyHighGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyHighGood(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buyHighGood");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String goodId = JsonBody.getString("goodId");
            int count = JsonBody.getIntValue("count");
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
                    JSONArray items = new JSONArray();

                    JSONObject HighGood;
                    for(int i = 0; i < ArKnightsApplication.HighGoodList.getJSONArray("goodList").size(); ++i) {
                        HighGood = ArKnightsApplication.HighGoodList.getJSONArray("goodList").getJSONObject(i);
                        if (HighGood.getString("goodId").equals(goodId)) {
                            UserSyncData.getJSONObject("status").put("hggShard", UserSyncData.getJSONObject("status").getIntValue("hggShard") - HighGood.getIntValue("price") * count);
                            String reward_id = HighGood.getJSONObject("item").getString("id");
                            String reward_type = HighGood.getJSONObject("item").getString("type");
                            int reward_count = HighGood.getJSONObject("item").getIntValue("count") * count;
                            Admin.GM_GiveItem(UserSyncData, reward_id, reward_type, reward_count, items);
                            break;
                        }
                    }

                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    HighGood = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("status", UserSyncData.getJSONObject("status"));
                    modified.put("shop", UserSyncData.getJSONObject("shop"));
                    modified.put("troop", UserSyncData.getJSONObject("troop"));
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    HighGood.put("deleted", new JSONObject(true));
                    HighGood.put("modified", modified);
                    result.put("playerDataDelta", HighGood);
                    result.put("items", items);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/buyExtraGood"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyExtraGood(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /shop/buyExtraGood");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String goodId = JsonBody.getString("goodId");
            int count = JsonBody.getIntValue("count");
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
                    JSONArray items = new JSONArray();

                    JSONObject ExtraGood;
                    for(int i = 0; i < ArKnightsApplication.ExtraGoodList.getJSONArray("goodList").size(); ++i) {
                        ExtraGood = ArKnightsApplication.ExtraGoodList.getJSONArray("goodList").getJSONObject(i);
                        if (ExtraGood.getString("goodId").equals(goodId)) {
                            UserSyncData.getJSONObject("inventory").put("4006", UserSyncData.getJSONObject("inventory").getIntValue("4006") - ExtraGood.getIntValue("price") * count);
                            String reward_id = ExtraGood.getJSONObject("item").getString("id");
                            String reward_type = ExtraGood.getJSONObject("item").getString("type");
                            int reward_count = ExtraGood.getJSONObject("item").getIntValue("count") * count;
                            Admin.GM_GiveItem(UserSyncData, reward_id, reward_type, reward_count, items);
                            break;
                        }
                    }

                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    ExtraGood = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("status", UserSyncData.getJSONObject("status"));
                    modified.put("shop", UserSyncData.getJSONObject("shop"));
                    modified.put("troop", UserSyncData.getJSONObject("troop"));
                    modified.put("skin", UserSyncData.getJSONObject("skin"));
                    modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    ExtraGood.put("deleted", new JSONObject(true));
                    ExtraGood.put("modified", modified);
                    result.put("playerDataDelta", ExtraGood);
                    result.put("items", items);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/decomposePotentialItem"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject decomposePotentialItem(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /shop/decomposePotentialItem");
        if (!ArKnightsApplication.enableServer) {
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
                        int rarity = ArKnightsApplication.characterJson.getJSONObject(CharId).getIntValue("rarity");
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
        return ArKnightsApplication.CashGoodList;
    }

    @RequestMapping({"/getGPGoodList"})
    public JSONObject getGPGoodList() {
        return ArKnightsApplication.GPGoodList;
    }

    @RequestMapping({"/getLowGoodList"})
    public JSONObject getLowGoodList() {
        return ArKnightsApplication.LowGoodList;
    }

    @RequestMapping({"/getHighGoodList"})
    public JSONObject getHighGoodList() {
        return ArKnightsApplication.HighGoodList;
    }

    @RequestMapping({"/getExtraGoodList"})
    public JSONObject getExtraGoodList() {
        return ArKnightsApplication.ExtraGoodList;
    }

    @RequestMapping({"/getLMTGSGoodList"})
    public JSONObject getLMTGSGoodList() {
        return ArKnightsApplication.LMTGSGoodList;
    }

    @RequestMapping({"/getEPGSGoodList"})
    public JSONObject getEPGSGoodList() {
        return ArKnightsApplication.EPGSGoodList;
    }

    @RequestMapping({"/getRepGoodList"})
    public JSONObject getRepGoodList() {
        return ArKnightsApplication.RepGoodList;
    }

    @RequestMapping({"/getFurniGoodList"})
    public JSONObject getFurniGoodList() {
        return ArKnightsApplication.FurniGoodList;
    }

    @RequestMapping({"/getSocialGoodList"})
    public JSONObject getSocialGoodList() {
        return ArKnightsApplication.SocialGoodList;
    }
}
