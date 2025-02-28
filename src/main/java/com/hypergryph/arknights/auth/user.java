package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping({"/user"})
public class user {
    private static String Key = "IxMMveJRWsxStJgX";

    public user(){
    }

    @RequestMapping({"/info/v1/need_cloud_auth"})
    public com.alibaba.fastjson.JSONObject need_cloud_auth() {
        com.alibaba.fastjson.JSONObject result = new JSONObject(true);
        result.put("status", 0);
        result.put("msg", "faq");
        return result;
    }

    @RequestMapping({"/v1/guestLogin"})
    public JSONObject GuestLogin() {
        JSONObject result = new JSONObject(true);
        result.put("result", 6);
        result.put("message", "单机版禁止游客登录");
        return result;
    }

    @RequestMapping({"/authenticateUserIdentity"})
    public JSONObject AuthenticateUserIdentity() {
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("message", "OK");
        result.put("isMinor", false);
        return result;
    }

    @RequestMapping({"/updateAgreement"})
    public JSONObject updateAgreement() {
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("message", "OK");
        result.put("isMinor", false);
        return result;
    }

    @RequestMapping({"/checkIdCard"})
    public JSONObject checkIdCard() {
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("message", "OK");
        result.put("isMinor", false);
        return result;
    }

    @RequestMapping({"/sendSmsCode"})
    public JSONObject sendSmsCode(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
    }
}
