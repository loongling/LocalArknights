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
import java.util.List;

@RestController
@RequestMapping({"/u8"})
public class u8 {
    public u8() {
    }

    @PostMapping(
            value = {"/user/v1/getToken"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getToken(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /u8/user/v1/getToken");
        String secret = JsonBody.getJSONObject("extension").getString("access_token");
        if (!ArKnightsApplication.enableServer){
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
