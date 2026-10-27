package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

@RestController
@RequestMapping("/user")
public class yostar_user {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(
            value = {"/quick-login"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject quickLogin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) JSONObject body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /user/quick-login (YoStar)");
        LOGGER.info("Authorization: " + authorization);
        if (body != null) {
            LOGGER.info("Received JSON: " + body);
        }

        JSONObject result = new JSONObject(true);

        String token = null;
        String uid = null;
        try {
            JSONObject auth = JSON.parseObject(authorization);
            if (auth != null) {
                JSONObject head = auth.getJSONObject("Head");
                if (head != null) {
                    token = head.getString("Token");
                    uid = head.getString("UID");
                }
                if (token == null) {
                    token = auth.getString("Token");
                }
                if (token == null) {
                    token = auth.getString("token");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[quick-login] Authorization 头解析失败: {}", e.toString());
        }

        LOGGER.info("[quick-login] 提取到 Token={} UID={}", token, uid);

        String nickName = "";
        String phone = userDao.queryPhoneBySecret(token);
        long ts = ArknightsApplication.getTimestamp();
        nickName = userDao.queryNickNameBySecret(token);
        JSONObject Key_json = new JSONObject();
        JSONArray keys = new JSONArray();
        JSONObject Data = new JSONObject();
        Key_json.put("ID", uid);
        Key_json.put("Type", "yostar");
        Key_json.put("Key", phone);
        Key_json.put("NickName", "LocalArknights");
        Key_json.put("CreatedAt", 1793082926);
        keys.add(Key_json);
        Data.put("Keys", keys);
        Data.put("AgeVerifyMethod", 0);
        Data.put("Destroy", null);
        Data.put("IsTestAccount", false);
        Data.put("ServerNowAt", ts);
        JSONObject userInfo = new JSONObject();
        JSONObject yostar = new JSONObject();
        userInfo.put("ID", uid);
        userInfo.put("UID2", 0);
        userInfo.put("PID", "US-ARKNIGHTS");
        userInfo.put("Token", token);
        userInfo.put("Birthday", "");
        userInfo.put("RegChannel", "googleplay");
        userInfo.put("TransCode", "");
        userInfo.put("State", 1);
        userInfo.put("DeviceID", "");
        userInfo.put("CreatedAt", 1793082926);
        yostar.put("ID", "Ark" + uid);
        yostar.put("Country", "US");
        yostar.put("Nickname", nickName);
        yostar.put("Picture", "");
        yostar.put("State", 1);
        yostar.put("AgreeAd", 0);
        yostar.put("CreatedAt", 1793082926);
        yostar.put("YostarDestroy", null);
        Data.put("UserInfo", userInfo);
        Data.put("Yostar", yostar);
        result.put("Data", Data);
        result.put("Code", 200);
        result.put("Msg", "OK");
        return  result;
    }

    @PostMapping(
            value = {"/login"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject login(@RequestBody(required = false) JSONObject login,
                            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /user/login (YoStar)");
        JSONObject result = new JSONObject(true);
        LOGGER.info("Received JSON: " + login);
        String account = login.getString("OpenID");
        String token = login.getString("Token");
        JSONObject data = new JSONObject();
        JSONObject yostar = new JSONObject();
        JSONObject userInfo = new JSONObject();
        if (!userDao.queryAccountByPhone(account).isEmpty()) {
            String nickName = "";
            if (!userDao.queryNickNameBySecret(token).isEmpty()) {
                nickName = userDao.queryNickNameBySecret(token);
            }
            result.put("Code", 200);
            result.put("Msg", "OK");
            long uid = userDao.queryUidBySecret(token);
            data.put("AgeVerifyMethod", 0);
            data.put("IsNew", 0);
            userInfo.put("ID", uid);
            userInfo.put("UID2", 0);
            userInfo.put("PID", "US-ARKNIGHTS");
            userInfo.put("Token", token);
            userInfo.put("Birthday", "");
            userInfo.put("RegChannel", "googleplay");
            userInfo.put("TransCode", "");
            userInfo.put("State", 1);
            userInfo.put("DeviceID", "");
            userInfo.put("CreatedAt", 1793082926);
            yostar.put("ID", "Ark" + uid);
            yostar.put("Country", "US");
            yostar.put("Nickname", nickName);
            yostar.put("Picture", "");
            yostar.put("State", 1);
            yostar.put("AgreeAd", 0);
            yostar.put("CreatedAt", 1793082926);
            data.put("UserInfo", userInfo);
            data.put("Yostar", yostar);
            result.put("Data", data);
            return result;
        }
        userDao.RegisterAccount(account, token, token);
        result.put("result", 0);
        return result;
    }

    @PostMapping(
            value = {"/detail"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject userDetail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestBody(required = false) JSONObject userDetail,
                                 HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /user/detail (YoStar)");
        JSONObject result = new JSONObject(true);
        LOGGER.info("Received JSON: " + userDetail);

        String token = null;
        String uid = null;
        try {
            JSONObject auth = JSON.parseObject(authorization);
            if (auth != null) {
                JSONObject head = auth.getJSONObject("Head");
                if (head != null) {
                    token = head.getString("Token");
                    uid = head.getString("UID");
                }
                if (token == null) {
                    token = auth.getString("Token");
                }
                if (token == null) {
                    token = auth.getString("token");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[user detail] Authorization 头解析失败: {}", e.toString());
        }

        LOGGER.info("[user detail] 提取到 Token={} UID={}", token, uid);
        if (userDao.queryAccountBySecret(token).isEmpty()) {
            response.setStatus(401);
            result.put("Code", 400);
            return result;
        }
        long ts = ArknightsApplication.getTimestamp();
        String phone = userDao.queryPhoneBySecret(token);
        String nickName = "";
        nickName = userDao.queryNickNameBySecret(token);
        JSONObject Key_json = new JSONObject();
        JSONArray keys = new JSONArray();
        JSONObject Data = new JSONObject();
        Key_json.put("ID", uid);
        Key_json.put("Type", "yostar");
        Key_json.put("Key", phone);
        Key_json.put("NickName", "LocalArknights");
        Key_json.put("CreatedAt", 1793082926);
        keys.add(Key_json);
        Data.put("Keys", keys);
        Data.put("AgeVerifyMethod", 0);
        Data.put("Destroy", null);
        Data.put("IsTestAccount", false);
        Data.put("ServerNowAt", ts);
        JSONObject userInfo = new JSONObject();
        JSONObject yostar = new JSONObject();
        userInfo.put("ID", uid);
        userInfo.put("UID2", 0);
        userInfo.put("PID", "US-ARKNIGHTS");
        userInfo.put("Token", token);
        userInfo.put("Birthday", "");
        userInfo.put("RegChannel", "googleplay");
        userInfo.put("TransCode", "");
        userInfo.put("State", 1);
        userInfo.put("DeviceID", "");
        userInfo.put("CreatedAt", 1793082926);
        yostar.put("ID", "Ark" + uid);
        yostar.put("Country", "US");
        yostar.put("Nickname", nickName);
        yostar.put("Picture", "");
        yostar.put("State", 1);
        yostar.put("AgreeAd", 0);
        yostar.put("CreatedAt", 1793082926);
        yostar.put("YostarDestroy", null);
        Data.put("UserInfo", userInfo);
        Data.put("Yostar", yostar);
        result.put("Data", Data);
        result.put("Code", 200);
        result.put("Msg", "OK");
        return  result;
    }

    @PostMapping(
            value = {"/set"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject userSet(HttpServletRequest request, HttpServletResponse response){
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /user/set (YoStar)");
        JSONObject result = new JSONObject(true);
        result.put("Code", 200);
        result.put("Msg", "OK");
        result.put("Data", new JSONObject());
        return result;
    }
}
