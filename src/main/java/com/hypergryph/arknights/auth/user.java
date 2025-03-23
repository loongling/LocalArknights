package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.function.httpClient;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping({"/user"})
public class user {
    private static String Key = "IxMMveJRWsxStJgX";

    public user() {
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

    @PostMapping(
            value = {"/register"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject Register(@RequestBody JSONObject JsonBody, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /user/register");
        String account = JsonBody.getString("account");
        String password = JsonBody.getString("password");
        String smsCode = JsonBody.getString("smsCode");
        String secret = DigestUtils.md5DigestAsHex((account + Key).getBytes());
        JSONObject result;
        if (userDao.queryAccountByPhone(account).size() != 0) {
            result = new JSONObject(true);
            result.put("result", 5);
            result.put("errMsg", "该用户已存在，请确认注册信息");
            return result;
        } else if (ArKnightsApplication.serverConfig.getJSONObject("server").getBooleanValue("captcha") && httpClient.verifySmsCode(account, smsCode).getIntValue("code") == 503) {
            result = new JSONObject(true);
            result.put("result", 5);
            result.put("errMsg", "验证码错误");
            return result;
        } else if (userDao.RegisterAccount(account, DigestUtils.md5DigestAsHex((password + Key).getBytes()), secret) != 1) {
            result = new JSONObject(true);
            result.put("result", 5);
            result.put("errMsg", "注册失败，未知错误");
            return result;
        } else {
            result = new JSONObject(true);
            result.put("result", 0);
            result.put("uid", 0);
            result.put("token", secret);
            result.put("isAuthenticate", true);
            result.put("isMinor", false);
            result.put("needAuthenticate", false);
            result.put("isLatestUserAgreement", true);
            return result;
        }
    }

    @PostMapping(
            value = {"/login"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject Login(@RequestBody JSONObject JsonBody, HttpServletRequest request) {
        String account = JsonBody.getString("account");
        String password = JsonBody.getString("password");
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /user/login");
        List<Account> accounts = userDao.LoginAccount(account, DigestUtils.md5DigestAsHex((password + Key).getBytes()));
        JSONObject result;
        if (accounts.size() != 1) {
            result = new JSONObject(true);
            result.put("result", 1);
            return result;
        } else {
            result = new JSONObject(true);
            result.put("result", 0);
            result.put("uid", ((Account) accounts.get(0)).getUid());
            result.put("token", ((Account) accounts.get(0)).getSecret());
            result.put("isAuthenticate", true);
            result.put("isMinor", false);
            result.put("needAuthenticate", false);
            result.put("isLatestUserAgreement", true);
            return result;
        }
    }

    @PostMapping(
            value = {"/oauth2/v2/grant"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject Auth(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /user/oauth2/v2/grant");
        String secret = JsonBody.getString("token");
        if (secret == null && secret.length() < 0) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "invalid token");
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
                JSONObject result = new JSONObject(true);
                result.put("uid", uid);
                result.put("isMinor", false);
                result.put("isAuthenticate", true);
                result.put("isGuest", false);
                result.put("needAuthenticate", false);
                result.put("isLatestUserAgreement", true);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/online/v1/loginout"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject LoginOut(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject(true);
        jsonObject.put("result", 0);
        return jsonObject;
    }
}
