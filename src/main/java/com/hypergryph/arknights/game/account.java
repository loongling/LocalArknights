package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/login");
        LOGGER.info("Received JSON: " + JsonBody.toJSONString());
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
            Long uid = ((Account) Accounts.get(0)).getUid();
            JSONObject result;
            if (((Account) Accounts.get(0)).getBan() == 1L) {
                result = new JSONObject(true);
                result.put("result", 1);
                result.put("error", "您已被此服务器封禁");
                return result;
            } else if (!clientVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("android").getString("clientVersion"))) {
                result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "客户端版本需要更新");
                return result;
            } else if (!assetsVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("android").getString("resVersion"))) {
                result = new JSONObject(true);
                result.put("result", 4);
                result.put("error", "资源需要更新");
                return result;
            } else {
                if (((Account) Accounts.get(0)).getUser().equals("{}")) {
                    ArknightsApplication.DefaultSyncData.getJSONObject("status").put("registerTs", (new Date()).getTime() / 1000L);
                    ArknightsApplication.DefaultSyncData.getJSONObject("status").put("lastApAddTime", (new Date()).getTime() / 1000L);
                    userDao.setUserData(uid, ArknightsApplication.DefaultSyncData);
                }
                ArknightsApplication.addSecretForIP(clientIp, secret);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("result", 0);
                data.put("uid", uid);
                data.put("secret", secret);
                data.put("serviceLicenseVersion", 0);

                result = new JSONObject(true);
                result.put("data", data);

                return result;
            }
        }
    }

    @PostMapping(
            value = {"/syncData"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncData(HttpServletRequest request,
                               HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncData");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        long uid = 0;
        List<Account> Accounts = userDao.queryAccountBySecret(String.valueOf(uid));

        // **检查服务器状态**
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "server is close");
        }

        if (secret == null) {
            LOGGER.warn("请求失败");
            return createErrorResponse(2, "请先登录");
        }

        // **查询数据库**
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            LOGGER.warn("数据库找不到 `secret`: " + secret);
            return createErrorResponse(2, "无法查询到此账户");
        }

        // **获取用户数据**
        Account user = accounts.get(0);
        if (user.getBan() == 1L) {
            LOGGER.warn("用户被封禁, UID: " + uid);
            response.setStatus(403);
            return createErrorResponse(403, "error");
        }

        // **解析用户数据**
        JSONObject UserSyncData;
        try {
            UserSyncData = JSONObject.parseObject(user.getUser());
            if (UserSyncData == null) {
                throw new Exception("解析用户数据失败");
            }
        } catch (Exception e) {
            LOGGER.error("解析用户数据失败, UID: " + uid, e);
            return createErrorResponse(2, "用户数据损坏");
        }

        Long ts = ArknightsApplication.getTimestamp();
        UserSyncData.getJSONObject("status").put("lastOnlineTs", System.currentTimeMillis() / 1000L);
        UserSyncData.getJSONObject("status").put("lastRefreshTs", ts);

        try {
            userDao.setUserData(uid, UserSyncData);
        } catch (Exception e) {
            LOGGER.error("写入用户数据失败, UID: " + uid, e);
            return createErrorResponse(2, "数据存储失败");
        }

        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("deleted", new JSONObject(true));  // 空对象而非null
        playerDataDelta.put("modified", new JSONObject(true)); // 空对象而非null
        result.put("playerDataDelta", playerDataDelta);
        result.put("result", 0);
        result.put("ts", ts);
        result.put("user", UserSyncData);
        LOGGER.info("用户数据同步成功, UID: " + uid);
        return result;
    }

    private JSONObject createErrorResponse(int code, String message) {
        JSONObject result = new JSONObject(true);
        result.put("result", code);
        result.put("error", message);
        return result;
    }

    @PostMapping(
            value = {"/syncStatus"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncStatus(HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncStatus");
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
                } else {
                    UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
                    UserSyncData.getJSONObject("status").put("lastOnlineTs", (new Date()).getTime() / 1000L);
                    UserSyncData.getJSONObject("status").put("lastRefreshTs", ArknightsApplication.getTimestamp());
                    UserSyncData.getJSONObject("pushFlags").put("hasGifts", 0);
                    UserSyncData.getJSONObject("pushFlags").put("hasFriendRequest", 0);
                    JSONArray listMailBox = JSONArray.parseArray(((Account) Accounts.get(0)).getMails());

                    for (int i = 0; i < listMailBox.size(); ++i) {
                        if (listMailBox.getJSONObject(i).getIntValue("state") == 0) {
                            if ((new Date()).getTime() / 1000L <= listMailBox.getJSONObject(i).getLongValue("expireAt")) {
                                UserSyncData.getJSONObject("pushFlags").put("hasGifts", 1);
                                break;
                            }

                            listMailBox.getJSONObject(i).put("remove", 1);
                        }
                    }

                    JSONArray FriendRequest = JSONObject.parseObject(((Account) Accounts.get(0)).getFriend()).getJSONArray("request");
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
                    result_announcement.put("4", ArknightsApplication.serverConfig.getJSONObject("announce").getJSONObject("status"));
                    result.put("result", result_announcement);
                    result.put("ts", ArknightsApplication.getTimestamp());
                    return result;
                }
            }
        }
    }

    @PostMapping({"/syncPushMessage"})
    public JSONObject SyncPushMessage() {
        JSONObject json = new JSONObject();
        json.put("code", 200);
        json.put("msg", "OK");
        return json;
    }
}
