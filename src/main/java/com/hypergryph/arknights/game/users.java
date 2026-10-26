package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.orderIdDao;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.hypergryph.arknights.ArknightsApplication.*;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;

@RestController
@RequestMapping({"/user"})
public class users {
    private static final Logger LOGGER = LogManager.getLogger();

    public users() {
    }

    @PostMapping(
            value = {"bindNickName"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject bindNickName(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/bindNickName");
        if (checkAccount(secret)) {
            String nickName = JsonBody.getString("nickName");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData;
            if (nickName.length() > 8) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("result", 1);
                return UserSyncData;
            } else if (nickName.indexOf("/") != -1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("result", 2);
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
                String nickNumber = String.format("%04d", userDao.queryNickName(nickName).size() + 1);
                UserSyncData.getJSONObject("status").put("nickNumber", nickNumber);
                UserSyncData.getJSONObject("status").put("uid", uid);
                UserSyncData.getJSONObject("status").put("nickName", nickName);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                playerDataDelta.put("deleted", new JSONObject(true));
                JSONObject modified = new JSONObject(true);
                JSONObject status = new JSONObject(true);
                status.put("nickName", nickName);
                modified.put("status", status);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/rebindNickName"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject reBindNickName(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/rebindNickName");
        if (checkAccount(secret)) {
            String nickName = JsonBody.getString("nickName");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            UserSyncData.getJSONObject("status").put("nickName", nickName);
            UserSyncData.getJSONObject("inventory").put("renamingCard", UserSyncData.getJSONObject("inventory").getIntValue("renamingCard") - 1);
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            JSONObject inventory = new JSONObject(true);
            inventory.put("renamingCard", UserSyncData.getJSONObject("inventory").getIntValue("renamingCard"));
            status.put("nickName", nickName);
            modified.put("status", status);
            modified.put("inventory", inventory);
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/bindBirthday"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject bindBirthday(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/bindBirthday");
        if (checkAccount(secret)) {
            LOGGER.info(JsonBody);
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/exchangeDiamondShard"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject exchangeDiamondShard(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/exchangeDiamondShard");
        if (checkAccount(secret)) {
            int count = JsonBody.getIntValue("count");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            JSONObject result;
            if (UserSyncData.getJSONObject("status").getIntValue("androidDiamond") < count) {
                result = new JSONObject(true);
                result.put("result", 1);
                result.put("errMsg", "剩余源石无法兑换合成玉");
                return result;
            } else {
                UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") - count);
                UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") - count);
                UserSyncData.getJSONObject("status").put("diamondShard", UserSyncData.getJSONObject("status").getIntValue("diamondShard") + count * 180);
                userDao.setUserData(uid, UserSyncData);
                result = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                playerDataDelta.put("deleted", new JSONObject(true));
                JSONObject modified = new JSONObject(true);
                JSONObject status = new JSONObject(true);
                status.put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond"));
                status.put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond"));
                status.put("diamondShard", UserSyncData.getJSONObject("status").getIntValue("diamondShard"));
                modified.put("status", status);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/changeResume"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeResume(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/changeResume");
        if (checkAccount(secret)) {
            String resume = JsonBody.getString("resume");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            UserSyncData.getJSONObject("status").put("resume", resume);
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            status.put("resume", UserSyncData.getJSONObject("status").getString("resume"));
            modified.put("status", status);
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/changeSecretary"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeSecretary(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/changeSecretary");
        if (checkAccount(secret)) {
            int charInstId = JsonBody.getIntValue("charInstId");
            String skinId = JsonBody.getString("skinId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            UserSyncData.getJSONObject("status").put("secretary", UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId)).getString("charId"));
            UserSyncData.getJSONObject("status").put("secretarySkinId", skinId);
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            status.put("secretary", UserSyncData.getJSONObject("status").getString("secretary"));
            status.put("secretarySkinId", UserSyncData.getJSONObject("status").getString("secretarySkinId"));
            modified.put("status", status);
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/buyAp"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyAp(HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/buyAp");
        if (checkAccount(secret)) {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData;
            UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            int nowTime = (int) ((new Date()).getTime() / 1000L);
            int addAp = (nowTime - UserSyncData.getJSONObject("status").getIntValue("lastApAddTime")) / 360;
            if (UserSyncData.getJSONObject("status").getIntValue("ap") < UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                if (UserSyncData.getJSONObject("status").getIntValue("ap") + addAp >= UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                } else if (addAp != 0) {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + addAp);
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                }
            }

            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") - 1);
            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") - 1);
            UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + UserSyncData.getJSONObject("status").getIntValue("maxAp"));
            UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
            UserSyncData.getJSONObject("status").put("buyApRemainTimes", UserSyncData.getJSONObject("status").getIntValue("buyApRemainTimes"));
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            status.put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond"));
            status.put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond"));
            status.put("ap", UserSyncData.getJSONObject("status").getIntValue("ap"));
            status.put("lastApAddTime", UserSyncData.getJSONObject("status").getIntValue("lastApAddTime"));
            status.put("buyApRemainTimes", UserSyncData.getJSONObject("status").getIntValue("buyApRemainTimes"));
            modified.put("status", status);
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            return result;
        } else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/changeAvatar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeAvatar(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] /user/changeAvatar");
        if (checkAccount(secret)) {
            String id = JsonBody.getString("id");
            String type = JsonBody.getString("type");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            UserSyncData.getJSONObject("status").getJSONObject("avatar").put("id", id);
            UserSyncData.getJSONObject("status").getJSONObject("avatar").put("type", type);
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            status.put("avatar", UserSyncData.getJSONObject("status").getJSONObject("avatar"));
            modified.put("status", status);
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/checkIn"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject checkIn(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        LOGGER.info("[/" + clientIp + "] 签到 /user/checkIn");
        LOGGER.info("checkIn:" + JsonBody);
        if (checkAccount(secret)) {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            Account account = accounts.get(0);
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            JSONObject userData = JSONObject.parseObject(account.getUser());
            JSONObject checkInU = userData.getJSONObject("checkIn");
            int showCount = checkInU.getInteger("showCount");
            showCount = showCount + 1;
            JSONObject status = userData.getJSONObject("status");
            JSONObject medal = userData.getJSONObject("medal");
            JSONObject inventory = userData.getJSONObject("inventory");
            JSONArray checkInHistory = checkInU.getJSONArray("checkInHistory");
            int checkInRewardIndex = checkInU.getInteger("checkInRewardIndex");
            checkInRewardIndex = checkInRewardIndex + 1;
            JSONArray signInRewards = new JSONArray();
            String checkInGroupId = checkInU.getString("checkInGroupId");
            JSONArray checkInTable = checkIn.getJSONObject(checkInGroupId).getJSONArray("items");
            long ts = ArknightsApplication.getTimestamp();
            int n = checkInHistory.size() + 1;
            JSONObject nextRecord = null;
            if (checkInU != null && n >= 1 && n <= checkInTable.size()) {
                nextRecord = checkInTable.getJSONObject(n - 1);
            }
            if (nextRecord == null || nextRecord.getString("itemType") == null) {
                nextRecord = new JSONObject();
                nextRecord.put("itemId", "4001");
                nextRecord.put("itemType", "GOLD");
                nextRecord.put("count", 2000);
            }
            String itemType = nextRecord.getString("itemType");
            int type = checkCheckInItemType(itemType);
            checkInHistory.add(0);
            int medalC;
            JSONObject medalCJ = new JSONObject();
            if (showCount < 2001) {
                medalC = 1;
                JSONArray inner_1 = new JSONArray();
                JSONArray inner_2 = new JSONArray();
                JSONArray inner_3 = new JSONArray();
                inner_1.add(showCount);
                inner_2.add(showCount);
                inner_3.add(showCount);
                inner_1.add(360);
                inner_2.add(1000);
                inner_3.add(2000);
                JSONArray val1 = new JSONArray();
                JSONArray val2 = new JSONArray();
                JSONArray val3 = new JSONArray();
                val1.add(inner_1);
                val2.add(inner_2);
                val3.add(inner_3);
                JSONObject medal_player_login_01 = new JSONObject();
                JSONObject medal_player_login_02 = new JSONObject();
                JSONObject medal_player_login_03 = new JSONObject();
                medal_player_login_01.put("id", "medal_player_login_01");
                medal_player_login_01.put("val", val1);
                medal_player_login_01.put("fts", -1);
                medal_player_login_01.put("rts", -1);
                medal_player_login_02.put("id", "medal_player_login_02");
                medal_player_login_02.put("val", val2);
                medal_player_login_02.put("fts", -1);
                medal_player_login_02.put("rts", -1);
                medal_player_login_03.put("id", "medal_player_login_03");
                medal_player_login_03.put("val", val3);
                medal_player_login_03.put("fts", -1);
                medal_player_login_03.put("rts", -1);
                if (showCount < 360) {
                    medalCJ.put("medal_player_login_01", medal_player_login_01);
                    medalCJ.put("medal_player_login_02", medal_player_login_02);
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                } else if (showCount == 360) {
                    medal_player_login_01.put("id", "medal_player_login_01");
                    medal_player_login_01.put("val", val1);
                    medal_player_login_01.put("fts", ts);
                    medal_player_login_01.put("rts", -1);
                    medalCJ.put("medal_player_login_01", medal_player_login_01);
                    medalCJ.put("medal_player_login_02", medal_player_login_02);
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                } else if (showCount <= 999) {
                    medalCJ.put("medal_player_login_02", medal_player_login_02);
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                } else if (showCount == 1000) {
                    medal_player_login_02.put("id", "medal_player_login_02");
                    medal_player_login_02.put("val", val2);
                    medal_player_login_02.put("fts", ts);
                    medal_player_login_02.put("rts", -1);
                    medalCJ.put("medal_player_login_02", medal_player_login_02);
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                } else if (showCount <= 1999) {
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                } else {
                    medal_player_login_03.put("id", "medal_player_login_03");
                    medal_player_login_03.put("val", val3);
                    medal_player_login_03.put("fts", ts);
                    medal_player_login_03.put("rts", -1);
                    medalCJ.put("medal_player_login_03", medal_player_login_03);
                }
            } else {
                medalC = 0;
            }
            userData.getJSONObject("checkIn").put("canCheckIn", 0);
            userData.getJSONObject("checkIn").put("checkInHistory", checkInHistory);
            userData.getJSONObject("checkIn").put("showCount", showCount);
            userData.getJSONObject("checkIn").put("checkInRewardIndex", checkInRewardIndex);
            JSONObject changedStatus = new JSONObject();
            JSONObject changedSignIn = new JSONObject();
            changedSignIn.put("canCheckIn", 0);
            changedSignIn.put("checkInHistory", checkInHistory);
            changedSignIn.put("showCount", showCount + 1);
            String typeI = nextRecord.getString("itemType");
            String itemId = nextRecord.getString("itemId");
            int counts = Integer.parseInt(nextRecord.getString("count"));
            JSONObject item = new JSONObject();
            item.put("id", itemId);
            item.put("type", typeI);
            item.put("count", counts);
            signInRewards.add(item);
            result.put("signInRewards", signInRewards);
            result.put("subscriptionRewards", new JSONArray());  //TODO 月卡检查
            playerDataDelta.put("deleted", new JSONObject(true));
            JSONObject modified = new JSONObject();
            modified.put("checkIn", changedSignIn);
            if (type == 1) {
                int count = Integer.parseInt(nextRecord.getString("count"));
                int money = status.getInteger("gold");
                int total = money + count;
                userData.getJSONObject("status").put("gold", total);
                changedStatus.put("gold", total);
                modified.put("status", changedStatus);
                if (medalC == 0) {
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                } else {
                    JSONObject medals = new JSONObject();
                    JSONObject medalM = new JSONObject();
                    medals.put("medals", medalCJ);
                    medalM.put("medal", medals);
                    modified.put("medal", medalM);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    medal.put("medals", medalCJ);
                    userData.put("medal", medal);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                }
            } else if (type == 2) {
                JSONObject inventoryU = new JSONObject();
                if (inventory.getInteger(itemId) != null) {
                    int count = inventory.getInteger(itemId);
                    count = count + counts;
                    inventory.put(itemId, count);
                    userData.put("inventory", inventory);
                    inventoryU.put(itemId, count);
                } else {
                    inventory.put(itemId, counts);
                    userData.put("inventory", inventory);
                    inventoryU.put(itemId, counts);
                }
                modified.put("inventory", inventoryU);
                if (medalC == 0) {
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                } else {
                    JSONObject medals = new JSONObject();
                    JSONObject medalM = new JSONObject();
                    medals.put("medals", medalCJ);
                    medalM.put("medal", medals);
                    modified.put("medal", medalM);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    medal.put("medals", medalCJ);
                    userData.put("medal", medal);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                }
            } else if (type == 3) {
                int solvedTKT = resolveTKT(typeI);
                if (solvedTKT == 1) {
                    int count = status.getInteger("instantFinishTicket");
                    count = count + counts;
                    status.put("instantFinishTicket", count);
                    userData.put("status", status);
                    changedStatus.put("instantFinishTicket", count);
                    modified.put("status", changedStatus);
                } else if (solvedTKT == 2) {
                    int count = status.getInteger("recruitLicense");
                    count = count + counts;
                    status.put("recruitLicense", count);
                    userData.put("status", status);
                    changedStatus.put("recruitLicense", count);
                } else if (solvedTKT == 3) {
                    int count = status.getInteger("gachaTicket");
                    count = count + counts;
                    status.put("gachaTicket", count);
                    userData.put("status", status);
                    changedStatus.put("gachaTicket", count);
                }
                if (medalC == 0) {
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                } else {
                    JSONObject medals = new JSONObject();
                    JSONObject medalM = new JSONObject();
                    medals.put("medals", medalCJ);
                    medalM.put("medal", medals);
                    modified.put("medal", medalM);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    medal.put("medals", medalCJ);
                    userData.put("medal", medal);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                }
            } else if (type == 4) {
                int solvedSHD = resolveSHD(typeI);
                if (solvedSHD == 1) {
                    int count = status.getInteger("LGG_SHD");
                    count = count + counts;
                    status.put("LGG_SHD", count);
                    userData.put("status", status);
                    changedStatus.put("LGG_SHD", count);
                    modified.put("status", changedStatus);
                } else if (solvedSHD == 2) {
                    int count = status.getInteger("HGG_SHD");
                    count = count + counts;
                    status.put("HGG_SHD", count);
                    userData.put("status", status);
                    changedStatus.put("HGG_SHD", count);
                    modified.put("status", changedStatus);
                }
                if (medalC == 0) {
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                } else {
                    JSONObject medals = new JSONObject();
                    JSONObject medalM = new JSONObject();
                    medals.put("medals", medalCJ);
                    medalM.put("medal", medals);
                    modified.put("medal", medalM);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    medal.put("medals", medalCJ);
                    userData.put("medal", medal);
                    userDao.setUserData(account.getUid(), userData);
                    return result;
                }
            }
        }
        else {
            createError(secret, response);
        }
        return new JSONObject();
    }

    private int resolveTKT(String TKT){
        return switch (TKT) {
            case "TKT_INST_FIN" -> 1;
            case "TKT_RECRUIT" -> 2;
            case "TKT_GACHA" -> 3;
            default -> -1;
        };
    }

    private int resolveSHD(String SHD){
        return switch (SHD) {
            case "LGG_SHD" -> 1;
            case "HGG_SHD" -> 2;
            default -> -1;
        };
    }

    @PostMapping(
            value = "/pay/order/v2/create/app_product",
            produces = "application/json;charset=UTF-8"
    )
    public JSONObject createAppProduct(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/pay/order/v2/create/app_product");
        LOGGER.info("createAppProduct:" + JsonBody);
        String secret = JsonBody.getString("token");
        if (checkAccount(secret)) {
            JSONObject result = new JSONObject(true);
            JSONObject data = new JSONObject();
            JSONArray payment = new JSONArray();
            JSONObject alipay = new JSONObject();
            JSONObject wechat = new JSONObject();
            String orderId = orderIdDao.queryOrderIdBtSecret(secret);
            JSONObject order = orderIdDao.queryAllBySecret(secret);
            int price = order.getInteger("price");
            String productName = order.getString("product_name");
            data.put("orderId", orderId);
            data.put("amount", price);
            data.put("productName", productName);
            alipay.put("key", "alipay");
            alipay.put("recommend", true);
            alipay.put("discount", false);
            wechat.put("key", "wechat");
            wechat.put("recommend", false);
            wechat.put("discount", false);
            payment.add(alipay);
            payment.add(wechat);
            data.put("payment", payment);
            data.put("displayMode", 0);
            result.put("data", data);
            result.put("msg", "ok");
            result.put("status", 0);
            result.put("type", "A");
            return result;
        }
        else {
            return createError(secret, response);
        }
    }

    @PostMapping(
            value = "/pay/order/v2/create/app_product/alipay",
            produces = "application/json;charset=UTF-8"
    )
    public JSONObject createBillByAlipay(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/pay/order/v2/create/app_product/alipay");
        String secret = JsonBody.getString("token");
        if (checkAccount(secret)) {
            String qrCodeURL = "https://www.bilibili.com/video/BV1GJ411x7h7?vd_source=20f25237f2fb61a571818a58c8769fc9";
            JSONObject result = new JSONObject();
            JSONObject data = new JSONObject();
            JSONObject extension = new JSONObject();
            String orderId = orderIdDao.queryOrderIdBtSecret(JsonBody.getString("token"));
            extension.put("qrCodeUrl", qrCodeURL);
            data.put("extension", extension);
            data.put("billOrderId", orderId);
            data.put("expireTime", 600);
            result.put("data", data);
            result.put("msg", "ok");
            result.put("status", 0);
            result.put("type", "A");
            return result;
        }
        else {
            return createError(secret, response);
        }
    }

    @PostMapping(
            value = "/pay/order/v2/create/app_product/wechat",
            produces = "application/json;charset=UTF-8"
    )
    public JSONObject createBillByWeChat(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/pay/order/v2/create/app_product/wechat");
        String secret = JsonBody.getString("token");
        if (checkAccount(secret)) {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Long uid = ((Account) Accounts.get(0)).getUid();
            String qrCodeURL = "https://www.bilibili.com/video/BV1GJ411x7h7?vd_source=20f25237f2fb61a571818a58c8769fc9";
            JSONObject result = new JSONObject();
            JSONObject data = new JSONObject();
            JSONObject extension = new JSONObject();
            String orderId = orderIdDao.queryOrderIdBtSecret(JsonBody.getString("token"));
            extension.put("qrCodeUrl", qrCodeURL);
            data.put("extension", extension);
            data.put("billOrderId", orderId);
            data.put("expireTime", 600);
            result.put("data", data);
            result.put("msg", "ok");
            result.put("status", 0);
            result.put("type", "A");
            return result;
        }
        else {
            return createError(secret, response);
        }
    }
}
