package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

        result.put("result", 0);
        result.put("token", token);
        return result;
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
        long ts = ArknightsApplication.getTimestamp();
        if (!userDao.queryAccountByPhone(account).isEmpty()) {
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
            userInfo.put("CreatedAt", ts);
            yostar.put("ID", "Ark" + uid);
            yostar.put("Country", "US");
            yostar.put("Nickname", "US");
            return result;
        }
        userDao.RegisterAccount(account, token, token);
        result.put("result", 0);
        return result;
    }
}
