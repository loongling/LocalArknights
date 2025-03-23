package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
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

@RestController
@RequestMapping({"/user"})
public class user {
    private static final Logger LOGGER = LogManager.getLogger();
    public user() {}
    @PostMapping(
            value = {"bindNickName"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject bindNickName(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request){
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/bindNickName");
        if (!ArKnightsApplication.enableServer){
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        }
        else{
            String nickName = JsonBody.getString("nickName");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1){
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            }
            else{
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                }
                else if (nickName.length() > 8) {
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("result", 1);
                    return UserSyncData;
                }
                else if (nickName.indexOf("/") != -1) {
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("result", 2);
                    return UserSyncData;
                }
                else {
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
        }
    }

    @PostMapping(
            value = {"/rebindNickName"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject reBindNickName(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/rebindNickName");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String nickName = JsonBody.getString("nickName");
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
            }
        }
    }

    @PostMapping(
            value = {"/exchangeDiamondShard"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject exchangeDiamondShard(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/exchangeDiamondShard");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
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
            }
        }
    }

    @PostMapping(
            value = {"/changeResume"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeResume(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/changeResume");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String resume = JsonBody.getString("resume");
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
            }
        }
    }

    @PostMapping(
            value = {"/changeSecretary"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeSecretary(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/changeSecretary");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            int charInstId = JsonBody.getIntValue("charInstId");
            String skinId = JsonBody.getString("skinId");
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
            }
        }
    }

    @PostMapping(
            value = {"/buyAp"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject buyAp(@RequestHeader("secret") String secret, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/buyAp");
        if (!ArKnightsApplication.enableServer) {
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
                    int nowTime = (int)((new Date()).getTime() / 1000L);
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
                }
            }
        }
    }

    @PostMapping(
            value = {"/changeAvatar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeAvatar(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /user/changeAvatar");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String id = JsonBody.getString("id");
            String type = JsonBody.getString("type");
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
            }
        }
    }
}
