package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
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
@RequestMapping({"/account"})
public class account {
    private static final Logger LOGGER = LogManager.getLogger();

    public account() {
    }

    @PostMapping(
            value = {"/login"},
            produces = {"application/json; charset=utf-8"}
    )
    public JSONObject Login(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/login");
        String secret = JsonBody.getString("token");
        String assetsVersion = JsonBody.getString("assetsVersion");
        String clientVersion = JsonBody.getString("clientVersion");
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        } else {
            Long uid = ((Account)Accounts.get(0)).getUid();
            JSONObject result;
            if (((Account)Accounts.get(0)).getBan() == 1L) {
                result = new JSONObject(true);
                result.put("result", 1);
                result.put("error", "您已被此服务器封禁");
                return result;
            } else if (!clientVersion.equals(ArKnightsApplication.serverConfig.getJSONObject("version").getJSONObject("android").getString("clientVersion"))) {
                result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "客户端版本需要更新");
                return result;
            } else if (!assetsVersion.equals(ArKnightsApplication.serverConfig.getJSONObject("version").getJSONObject("android").getString("resVersion"))) {
                result = new JSONObject(true);
                result.put("result", 4);
                result.put("error", "资源需要更新");
                return result;
            } else {
                if (((Account)Accounts.get(0)).getUser().equals("{}")) {
                    ArKnightsApplication.DefaultSyncData.getJSONObject("status").put("registerTs", (new Date()).getTime() / 1000L);
                    ArKnightsApplication.DefaultSyncData.getJSONObject("status").put("lastApAddTime", (new Date()).getTime() / 1000L);
                    userDao.setUserData(uid, ArKnightsApplication.DefaultSyncData);
                }

                result = new JSONObject(true);
                result.put("result", 0);
                result.put("uid", uid);
                result.put("secret", secret);
                result.put("serviceLicenseVersion", 0);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/syncData"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncData(@RequestHeader("secret") String secret, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncData");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            Long ts = ArKnightsApplication.getTimestamp();
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
                    UserSyncData.getJSONObject("status").put("lastOnlineTs", (new Date()).getTime() / 1000L);
                    UserSyncData.getJSONObject("status").put("lastRefreshTs", ts);
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    result.put("result", 0);
                    result.put("user", UserSyncData);
                    result.put("ts", ts);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/syncStatus"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncStatus(@RequestHeader("secret") String secret, HttpServletResponse response, HttpServletRequest request) {
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
                    UserSyncData.getJSONObject("status").put("lastOnlineTs", (new Date()).getTime() / 1000L);
                    UserSyncData.getJSONObject("status").put("lastRefreshTs", ArKnightsApplication.getTimestamp());
                    UserSyncData.getJSONObject("pushFlags").put("hasGifts", 0);
                    UserSyncData.getJSONObject("pushFlags").put("hasFriendRequest", 0);
                    JSONArray listMailBox = JSONArray.parseArray(((Account)Accounts.get(0)).getMails());

                    for(int i = 0; i < listMailBox.size(); ++i) {
                        if (listMailBox.getJSONObject(i).getIntValue("state") == 0) {
                            if ((new Date()).getTime() / 1000L <= listMailBox.getJSONObject(i).getLongValue("expireAt")) {
                                UserSyncData.getJSONObject("pushFlags").put("hasGifts", 1);
                                break;
                            }

                            listMailBox.getJSONObject(i).put("remove", 1);
                        }
                    }

                    JSONArray FriendRequest = JSONObject.parseObject(((Account)Accounts.get(0)).getFriend()).getJSONArray("request");
                    if (FriendRequest.size() != 0) {
                        UserSyncData.getJSONObject("pushFlags").put("hasFriendRequest", 1);
                    }

                    userDao.setMailsData(uid, listMailBox);
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    modified.put("status", UserSyncData.getJSONObject("status"));
                    modified.put("gacha", UserSyncData.getJSONObject("gacha"));
                    modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    modified.put("pushFlags", UserSyncData.getJSONObject("pushFlags"));
                    modified.put("consumable", UserSyncData.getJSONObject("consumable"));
                    modified.put("rlv2", UserSyncData.getJSONObject("rlv2"));
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    result.put("playerDataDelta", playerDataDelta);
                    JSONObject result_announcement = new JSONObject(true);
                    result_announcement.put("4", ArKnightsApplication.serverConfig.getJSONObject("announce").getJSONObject("status"));
                    result.put("result", result_announcement);
                    result.put("ts", ArKnightsApplication.getTimestamp());
                    return result;
                }
            }
        }
    }
}
