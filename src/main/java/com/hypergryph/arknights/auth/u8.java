package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.dao.userDao;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/u8"})
public class u8 {
    public u8() {
    }

    @PostMapping(
            value = "/user/v1/getToken",
            produces = "application/json;charset=UTF-8"
    )
    public Map<String, Object> getToken(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /user/v1/getToken");
        ArKnightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());

        // 解析 JSON 请求体，获取 `token`
        if (!JsonBody.containsKey("extension") || !JsonBody.getJSONObject("extension").containsKey("code")) {
            ArKnightsApplication.LOGGER.warn("请求体缺少 token (code)");
            return Map.of("result", 2, "error", "Missing Token");
        }

        String token = JsonBody.getJSONObject("extension").getString("code");

        // 检查服务器是否启用
        if (!ArKnightsApplication.enableServer) {
            ArKnightsApplication.LOGGER.warn("服务器已关闭");
            return Map.of(
                    "result", 2,
                    "error", ArKnightsApplication.serverConfig.getJSONObject("server").getString("closeMessage")
            );
        }

        // 通过 token 查询数据库获取用户信息
        List<Account> accounts = userDao.queryAccountBySecret(token);
        if (accounts.size() != 1) {
            ArKnightsApplication.LOGGER.warn("无效的 token: " + token);
            return Map.of("result", 2, "error", "无法查询到此账户");
        }

        // 获取用户 UID
        Account user = accounts.get(0);
        Long uid = user.getUid();

        // 构造返回数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", 0);
        data.put("uid", uid);
        data.put("error", "");
        data.put("channelUid", uid);
        data.put("token", token);
        data.put("isGuest", 0);
        data.put("extension", "{\"isGuest\":false}");

        return data;
    }
    @PostMapping(
            value = {"/user/verifyAccount"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject VerifyAccount(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String secret = JsonBody.getJSONObject("extension").getString("access_token");
        if (!ArKnightsApplication.enableServer) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", ArKnightsApplication.serverConfig.getJSONObject("server").getString("closeMessage"));
            return result;
        }
        else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            }
            else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject result = new JSONObject(true);
                result.put("result", 0);
                result.put("uid", uid);
                result.put("error", "");
                result.put("extension", "{\"isGuest\":false}");
                result.put("channelUid", uid);
                result.put("token", secret);
                result.put("isGuest", 0);
                return result;
            }
        }
    }
    @RequestMapping({"/pay/getAllProductList"})
    public JSONObject GetAllProductList(HttpServletResponse response, HttpServletRequest request) {
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        }
        else {
            return ArKnightsApplication.AllProductList;
        }
    }

    @PostMapping(
            value = {"/pay/confirmOrderState"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject confirmOrderState(HttpServletResponse response, HttpServletRequest request) {
        JSONObject result;
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        }
        else {
            result = new JSONObject(true);
            result.put("payState", 3);
            return result;
        }
    }
}
