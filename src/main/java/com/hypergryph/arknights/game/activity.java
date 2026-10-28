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
import static com.hypergryph.arknights.ArknightsApplication.LOGGER;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
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
    public JSONObject getChainLogInReward(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getChainLogInReward");
        LOGGER.info("getloginchain" + JsonBody);
        int index = JsonBody.getInteger("index");
        if(checkAccount(secret)){
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData;
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
        } else {
            return createError(secret, response);
        }
        return new JSONObject();
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
    public JSONObject getOpenServerCheckInReward(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /activity/getOpenServerCheckInReward");
        LOGGER.info("getloginchain" + JsonBody);
        if (checkAccount(secret)) {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            JSONObject result = new JSONObject();
            JSONObject reward = new JSONObject();
            JSONObject playerDataDelta = new JSONObject();
            playerDataDelta.put("deleted", new JSONObject());
            JSONObject modified = new JSONObject();
            JSONObject openServer = UserSyncData.getJSONObject("openServer").getJSONObject("checkIn");
            JSONArray history = openServer.getJSONArray("history");
            int index = history.size() - 1;
            history.remove(index);
            history.add(0);
            JSONObject todayRewards = getTodayReward(2, index);
            String itemType = todayRewards.getJSONObject("item").getString("itemType");
            int count = todayRewards.getJSONObject("item").getInteger("count");
            String itemId  = todayRewards.getJSONObject("item").getString("itemId");
            reward.put("type", itemType);
            reward.put("count", count);
            reward.put("id", itemId);
            result.put("reward", reward);
            openServer.put("history", history);
            JSONObject CI = new JSONObject();
            CI.put("checkIn", openServer);
            modified.put("openServer", CI);
            UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").put("history", history);
            int T = ArknightsApplication.checkCheckInItemType(itemType);
            if(T == 5){
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
            }else if (T == 1){
                JSONObject status = UserSyncData.getJSONObject("status");
                JSONObject changedStatus = new JSONObject();
                int num = status.getInteger("gold");
                count = num + count;
                status.put("gold", count);
                changedStatus.put("gold", count);
                UserSyncData.put("status", status);
                userDao.setUserData(uid, UserSyncData);
                modified.put("status", changedStatus);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
            else if (T == 2) {
                JSONObject inventory = UserSyncData.getJSONObject("inventory");
                JSONObject inventoryU = new JSONObject();
                if (inventory.getInteger(itemId) != null) {
                    int counts = inventory.getInteger(itemId);
                    count = counts + count;
                    inventory.put(itemId, count);
                    UserSyncData.put("inventory", inventory);
                    inventoryU.put(itemId, count);
                } else {
                    inventory.put(itemId, count);
                    UserSyncData.put("inventory", inventory);
                    inventoryU.put(itemId, count);
                }
                modified.put("inventory", inventoryU);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                userDao.setUserData(uid, UserSyncData);
                return result;
            }
            else if (T == 3) {
                JSONObject status = UserSyncData.getJSONObject("status");
                JSONObject changedStatus = new JSONObject();
                int num = status.getInteger("instantFinishTicket");
                count = num + count;
                status.put("instantFinishTicket", count);
                changedStatus.put("instantFinishTicket", count);
                UserSyncData.put("status", status);
                userDao.setUserData(uid, UserSyncData);
                modified.put("status", changedStatus);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        } else {
            return createError(secret, response);
        }
        return new JSONObject(true);
    }

}