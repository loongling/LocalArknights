package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import static com.hypergryph.arknights.ArknightsApplication.LOGGER;
import static com.hypergryph.arknights.core.work.consumableNumberG.*;
import static com.hypergryph.arknights.core.work.getTodayRewards.getTodayReward;

@RestController
@RequestMapping("/activity")
public class activity {
    public  activity() {
    }

    @PostMapping(
            value = {"/getChainLogInReward"},
            produces = {"application/json;charset=UTF-8"}
    )
    public static JSONObject getChainLogInReward(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getChainLogInReward");
        LOGGER.info("getloginchain" + JsonBody);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        int index = JsonBody.getInteger("index");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account) Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account) Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                }
                else {
                    UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
                    JSONObject result = new JSONObject();
                    JSONObject reward = new JSONObject();
                    JSONObject playerDataDelta = new JSONObject();
                    playerDataDelta.put("deleted", new JSONObject());
                    JSONObject modified = new JSONObject();
                    JSONObject todayRewards = getTodayReward(1, index);
                    String itemType = todayRewards.getJSONObject("item").getString("itemType");
                    int count = todayRewards.getJSONObject("item").getInteger("count");
                    String itemId  = todayRewards.getJSONObject("item").getString("itemId");
                    JSONObject openServer = UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin");
                    reward.put("type", itemType);
                    reward.put("count", count);
                    reward.put("id", itemId);
                    result.put("reward", reward);
                    JSONArray history = openServer.getJSONArray("history");
                    history.remove(index);
                    history.add(0);
                    history.add(1);
                    int T = ArknightsApplication.checkCheckInItemType(itemType);
                    JSONObject chainLogin =  new JSONObject();
                    JSONObject changedOS = new JSONObject();
                    if (index == 6) {
                        chainLogin.put("isAvailable", false);
                        chainLogin.put("nowIndex", 14);
                        chainLogin.put("finalReward", 1);
                    } else {
                        chainLogin.put("isAvailable", true);
                        chainLogin.put("nowIndex", index);
                        chainLogin.put("finalReward", 0);
                    }
                    chainLogin.put("history", history);
                    changedOS.put("chainLogin", chainLogin);
                    modified.put("openServer", changedOS);
                    if (T == 6) {
                        JSONObject changedStatus = new JSONObject();
                        JSONObject status = UserSyncData.getJSONObject("status");
                        int andD = status.getInteger("androidDiamond");
                        int iosD = status.getInteger("iosDiamond");
                        iosD = iosD + count;
                        andD = andD + count;
                        status.put("androidDiamond", andD);
                        status.put("iosDiamond", iosD);
                        UserSyncData.put("status", status);
                        changedStatus.put("iosDiamond", iosD);
                        changedStatus.put("androidDiamond", andD);
                        modified.put("status", changedStatus);
                        UserSyncData.put("status", status);
                        playerDataDelta.put("modified", modified);
                        result.put("playerDataDelta", playerDataDelta);
                        UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                        userDao.setUserData(uid, UserSyncData);
                        return result;
                    }
                    else if(T == 3) {
                        int TP = resolveTKT(itemType);
                        JSONObject changedStatus = new JSONObject();
                        JSONObject status = UserSyncData.getJSONObject("status");
                        if(TP == 1){
                           int gachaTicket = status.getInteger("gachaTicket");
                           gachaTicket = gachaTicket + count;
                           changedStatus.put("gachaTicket", gachaTicket);
                           modified.put("status", changedStatus);
                           UserSyncData.put("status", status);
                            playerDataDelta.put("modified", modified);
                            result.put("playerDataDelta", playerDataDelta);
                            UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                            userDao.setUserData(uid, UserSyncData);
                            return result;
                        } else if(TP == 2){
                            int classicGachaTicket = status.getInteger("classicGachaTicket");
                            classicGachaTicket = classicGachaTicket + count;
                            changedStatus.put("classicGachaTicket", classicGachaTicket);
                            modified.put("status", changedStatus);
                            UserSyncData.put("status", status);
                            playerDataDelta.put("modified", modified);
                            result.put("playerDataDelta", playerDataDelta);
                            UserSyncData.getJSONObject("openServer").put("chainLogin", chainLogin);
                            userDao.setUserData(uid, UserSyncData);
                            return result;
                        }
                    }
                    else if(T == 5){
                        JSONObject changedCons = new JSONObject();
                        JSONObject consumable = UserSyncData.getJSONObject("consumable");
                        if(isExist(itemId, consumable)){
                            JSONObject consumableData = getInnerObject(consumable.getJSONObject(itemId));
                            int num = consumableData.getInteger("count");
                            count = num + count;
                            int me = getInnerNumber(consumable.getJSONObject(itemId));
                            JSONObject A = new JSONObject();
                            A.put("count", count);
                            A.put("ts", -1);
                            JSONObject B = new JSONObject();
                            B.put(String.valueOf(me), A);
                            consumable.put(itemId, B);
                            JSONObject Change = new JSONObject();
                            Change.put(itemId, B);
                            UserSyncData.put("consumable", consumable);
                            userDao.setUserData(uid, UserSyncData);
                            modified.put("consumable", Change);
                            playerDataDelta.put("modified", modified);
                            result.put("playerDataDelta", playerDataDelta);
                            return result;
                        }
                        else {
                            int me = nextItemNumber(consumable);
                            JSONObject A = new JSONObject();
                            A.put("count", count);
                            A.put("ts", -1);
                            JSONObject B = new JSONObject();
                            B.put(String.valueOf(me), A);
                            JSONObject consumableData = new JSONObject();
                            consumable.put(itemId, B);
                            JSONObject Change = new JSONObject();
                            Change.put(itemId, consumableData);
                            UserSyncData.put("consumable", consumable);
                            userDao.setUserData(uid, UserSyncData);
                            modified.put("consumable", Change);
                            playerDataDelta.put("modified", modified);
                            result.put("playerDataDelta", playerDataDelta);
                            return result;
                        }
                    }
                }
            }
        }
        return JsonBody;
    }

    private static int resolveTKT(String TKT){
        return switch (TKT) {
            case "TKT_GACHA" -> 1;
            case "CLASSIC_TKT_GACHA_10" -> 2;
            default -> 2;
        };
    }

    @PostMapping(
            value = {"/getOpenServerCheckInReward"},
            produces = {"application/json;charset=UTF-8"}
    )
    public static JSONObject getOpenServerCheckInReward(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getOpenServerCheckInReward");
        LOGGER.info("getloginchain" + JsonBody);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account) Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account) Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                }
            }
        }
        return new JSONObject(true);
    }
}
