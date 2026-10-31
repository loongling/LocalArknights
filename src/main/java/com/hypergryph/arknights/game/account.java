package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.mailInstanceDao;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import static com.hypergryph.arknights.core.shop.PeriodicShopReset.resetIfNeeded;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
import static com.hypergryph.arknights.core.work.checkActLogin.needAddIndex;
import static com.hypergryph.arknights.core.work.checkActLogin.needResetIndex;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.canCheckIn;
import static com.hypergryph.arknights.core.work.checkNeedChangeGroup.needChange;
import static com.hypergryph.arknights.core.work.checkNeedChangeGroup.nextGroupId;

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

        // 按 Host 头区分国服 / 国际服：例如 gs.arknights.global:8443 就是国际服。
        // 国际服的 clientVersion / resVersion 跟国服配置对不上，不能套用同一套版本校验。
        String host = request.getHeader("Host");
        if (host == null || host.isEmpty()) {
            host = request.getHeader("host");
        }
        if (host == null || host.isEmpty()) {
            host = request.getHeader("X-Forwarded-Host");
        }
        if (host == null || host.isEmpty()) {
            host = request.getServerName();
        }
        boolean isGlobal = host != null && host.toLowerCase().contains("global");

        String secret = JsonBody.getString("token");
        int platform = JsonBody.getInteger("platform");
        String assetsVersion = JsonBody.getString("assetsVersion");
        String clientVersion = JsonBody.getString("clientVersion");
        LOGGER.info("[login] host={} isGlobal={} platform={} clientVersion={} resVersion={}",
                host, isGlobal, platform, clientVersion, assetsVersion);

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        JSONObject result = new JSONObject(true);
        if (checkAccount(secret)) {
            Long uid = ((Account) Accounts.get(0)).getUid();
            if (!isGlobal) {
                if (!clientVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("Windows").getString("clientVersion"))) {
                    result.put("result", 2);
                    result.put("error", "客户端版本需要更新");
                    return result;
                } else if (platform == 2 && !assetsVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("Windows").getString("resVersion"))) {
                    result.put("result", 4);
                    result.put("error", "资源需要更新");
                    return result;
                } else if (platform == 1 && !assetsVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("Android").getString("resVersion"))) {
                    result.put("result", 4);
                    result.put("error", "资源需要更新");
                    return result;
                }
            } else {
                if (!clientVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("global_Android").getString("clientVersion"))) {
                    result.put("result", 2);
                    result.put("error", "客户端版本需要更新");
                    return result;
                } else if (platform == 2 && !assetsVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("global_Windows").getString("resVersion"))) {
                    result.put("result", 4);
                    result.put("error", "资源需要更新");
                    return result;
                } else if (platform == 1 && !assetsVersion.equals(ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("global_Android").getString("resVersion"))) {
                    result.put("result", 4);
                    result.put("error", "资源需要更新");
                    return result;
                }
            }

            if (((Account) Accounts.get(0)).getUser().equals("{}")) {
                JSONObject userData = ArknightsApplication.DefaultSyncData;
                userData.getJSONObject("status").put("registerTs", (new Date()).getTime() / 1000L);
                userData.getJSONObject("status").put("lastApAddTime", (new Date()).getTime() / 1000L);
                userDao.setUserData(uid, userData);
            }

            try {
                List<Account> fresh = userDao.queryAccountBySecret(secret);
                JSONObject loginUserData = JSONObject.parseObject(fresh.get(0).getUser());
                if (resetIfNeeded(loginUserData)) {
                    userDao.setUserData(uid, loginUserData);
                    LOGGER.info("[login] uid={} 周/月礼包商店已按周期重置", uid);
                }
            } catch (Exception e) {
                LOGGER.warn("[login] 周/月礼包商店重置失败: {}", e.toString());
            }
            //ArknightsApplication.addSecretForIP(clientIp, secret);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("result", 0);
            data.put("uid", uid);
            data.put("secret", secret);
            data.put("token", secret);
            data.put("serviceLicenseVersion", 0);
            result.put("result", 0);
            result.put("uid", uid);
            result.put("token", secret);
            result.put("secret", secret);
            result.put("serviceLicenseVersion", 0);
            result.put("data", data);
        }
        else {
            createError(secret, response);
        }
        return result;
    }

    @PostMapping(
            value = {"/syncData"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncData(@RequestHeader(value = "secret", required = false)String secret,HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncData");
        //String secret = ArknightsApplication.getSecretByIP(clientIp);
        long uid = userDao.queryUidBySecret(secret);
        if (checkAccount(secret)) {
            // **查询数据库**
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            // **获取用户数据**
            Account user = accounts.get(0);
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
            if (UserSyncData.containsKey("monthlySub")) {
                UserSyncData.remove("monthlySub");
                LOGGER.info("[syncData] 已清掉不兼容的 monthlySub（uid=" + uid + "）");
            }
            try {
                int fixed = com.hypergryph.arknights.core.work.processArray.repairConsumableTs(UserSyncData);
                if (fixed > 0) {
                    userDao.setUserData(uid, UserSyncData);
                    LOGGER.info("[syncData] 修补了 " + fixed + " 条时限消耗品的过期时间");
                }
            } catch (Exception e) {
                LOGGER.warn("[syncData] 修补消耗品过期时间失败: " + e.toString());
            }
            Long ts = ArknightsApplication.getTimestamp();
            Long lastLogin = UserSyncData.getJSONObject("status").getLong("lastOnlineTs");
            boolean canCheck = canCheckIn(lastLogin, ts);
            if (canCheck) {
                if (UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getBoolean("isAvailable")) {
                    int index = UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getInteger("nowIndex");
                    JSONArray history = UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history");
                    boolean indexAdd = needAddIndex(lastLogin, index, history);
                    boolean resetIndex = needResetIndex(lastLogin);
                    if (indexAdd) {
                        UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").put("nowIndex", index + 1);
                        UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history").add(1);
                    } else {
                        if (UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history") == null || UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history").isEmpty()) {
                            UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history").add(1);
                        }
                    }
                    if (resetIndex) {
                        UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").put("nowIndex", 0);
                    }
                }
                if (UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").getBoolean("isAvailable")) {
                    JSONArray history = UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").getJSONArray("history");
                    boolean hasOne = false;
                    if (history != null) {
                        for (int i = 0; i < history.size(); i++) {
                            Integer v = history.getInteger(i);
                            if (v != null && v == 1) {
                                hasOne = true;
                                break;
                            }
                        }
                    }
                    if (!hasOne) {
                        UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").getJSONArray("history").add(1);
                    }
                }
            }
            if (canCheck) {
                UserSyncData.getJSONObject("checkIn").put("canCheckIn", 1);
            }
            UserSyncData.getJSONObject("status").put("lastOnlineTs", System.currentTimeMillis() / 1000L);
            UserSyncData.getJSONObject("status").put("lastRefreshTs", ts);
            String id = UserSyncData.getJSONObject("checkIn").getString("checkInGroupId");
            boolean needChange = needChange(lastLogin, ts);
            if (needChange) {
                String next_id = nextGroupId(id);
                UserSyncData.getJSONObject("checkIn").put("checkInGroupId", next_id);
            }

            JSONArray missionBecame = com.hypergryph.arknights.core.function.MissionHelper
                    .refreshActiveMissions(UserSyncData);
            if (!missionBecame.isEmpty()) {
                LOGGER.info("[MissionHelper] syncData 自愈任务: " + missionBecame.toJSONString());
            }

            try {
                com.hypergryph.arknights.game.building.grantTutorialKit(UserSyncData);
            } catch (Exception ignored) {
            }

            try {
                int fixedRooms = com.hypergryph.arknights.game.building.repairAutoRooms(UserSyncData);
                if (fixedRooms > 0) {
                    LOGGER.info("[building] syncData 修复自动房间槽 {} 个", fixedRooms);
                }
            } catch (Exception ignored) {
            }

            try {
                if (resetIfNeeded(UserSyncData)) {
                    LOGGER.info("[syncData] uid={} 周/月礼包商店已按周期重置", uid);
                }
            } catch (Exception e) {
                LOGGER.warn("[syncData] 周/月礼包商店重置失败: {}", e.toString());
            }

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
        return new JSONObject();
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
    public JSONObject SyncStatus(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncStatus");
        LOGGER.info("SyncS" + JsonBody);
        //String secret = ArknightsApplication.getSecretByIP(clientIp);
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
                    // 邮件已经落 mail_instance 表：顺手清过期、按未领取附件算 hasGifts
                    long mailNow = (new Date()).getTime() / 1000L;
                    mailInstanceDao.deleteExpired(uid, mailNow);
                    if (mailInstanceDao.countUnreceived(uid, mailNow) > 0) {
                        UserSyncData.getJSONObject("pushFlags").put("hasGifts", 1);
                    }

                    JSONArray FriendRequest = JSONObject.parseObject(((Account) Accounts.get(0)).getFriend()).getJSONArray("request");
                    if (FriendRequest.size() != 0) {
                        UserSyncData.getJSONObject("pushFlags").put("hasFriendRequest", 1);
                    }

                    JSONObject modified = new JSONObject(true);

                    if (canCheckIn(UserSyncData.getJSONObject("status").getLong("lastOnlineTs"), ArknightsApplication.getTimestamp())) {
                        Long ts = ArknightsApplication.getTimestamp();
                        Long lastLogin = UserSyncData.getJSONObject("status").getLong("lastOnlineTs");
                        boolean canCheck = canCheckIn(lastLogin, ts);
                        if (canCheck) {
                            UserSyncData.getJSONObject("checkIn").put("canCheckIn", 1);
                            JSONObject checkIn = new JSONObject(true);
                            checkIn.put("canCheckIn", 1);
                            modified.put("checkIn", checkIn);
                        }
                    }
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
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
                    result.put("result", result_announcement);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/syncPushMessage"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SyncPushMessage(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /account/syncStatus");
        LOGGER.info("syncPM:" + JsonBody);
        JSONObject result = new JSONObject();
        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
