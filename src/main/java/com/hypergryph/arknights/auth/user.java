package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.function.httpClient;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /user/register");
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
        } else if (ArknightsApplication.serverConfig.getJSONObject("server").getBooleanValue("captcha") && httpClient.verifySmsCode(account, smsCode).getIntValue("code") == 503) {
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
            value = {"/auth/v1/token_by_phone_password"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject Login(@RequestBody JSONObject JsonBody, HttpServletRequest request) {
        String account = JsonBody.getString("phone");
        String password = JsonBody.getString("password");
        String clientIp = ArknightsApplication.getIpAddr(request);

        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 用户登录 /auth/v1/token_by_phone_password");

        List<Account> accounts = userDao.LoginAccount(account, DigestUtils.md5DigestAsHex((password + Key).getBytes()));

        if (accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("status", 1);  // 1 = 登录失败
            result.put("msg", "Invalid account or password");
            return result;
        }

        Account user = accounts.get(0);
        String token = user.getSecret();

        JSONObject data = new JSONObject(true);
        data.put("token", token);

        JSONObject result = new JSONObject(true);
        result.put("status", 0);
        result.put("msg", "OK");
        result.put("data", data);

        return result;
    }

    @GetMapping("/info/v1/basic")
    public Map<String, Object> getUserInfo(@RequestParam("token") String token) {
        ArknightsApplication.LOGGER.info("请求用户信息: /user/info/v1/basic?token=" + token);

        // 通过 token (Secret) 查询数据库中的账户
        List<Account> accounts = userDao.queryAccountBySecret(token);
        if (accounts.size() != 1) {
            ArknightsApplication.LOGGER.warn("无效的 token: " + token);
            JSONObject result = new JSONObject(true);
            result.put("status", 1);
            result.put("msg", "Invalid Token");
            return result;
        }

        // 获取数据库中的用户信息
        Account user = accounts.get(0);
        String email = user.getPhone();
        String phone = user.getPhone();
        String identityNum = String.valueOf(user.getUid());
        String identityName = user.getPhone();  // 用邮箱代替身份名称
        String hgId = "1"; // 服务器 ID，通常固定

        // 构造返回数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hgId", hgId);
        data.put("phone", phone);
        data.put("email", email);
        data.put("identityNum", identityNum);
        data.put("identityName", identityName);
        data.put("isMinor", false);
        data.put("isLatestUserAgreement", true);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 0);
        response.put("msg", "OK");
        response.put("data", data);

        return response;
    }

    @PostMapping(
            value = {"/oauth2/v2/grant"},
            produces = {"application/json;charset=UTF-8"}
    )
    public Map<String, Object> grantOAuth(@RequestBody JSONObject JsonBody) {
        ArknightsApplication.LOGGER.info("请求 OAuth 授权: /oauth2/v2/grant");

        // 从 JSON 请求体中获取 token
        String token = JsonBody.getString("token");
        if (token == null || token.isEmpty()) {
            ArknightsApplication.LOGGER.warn("请求体中缺少 token");
            return Map.of("status", 1, "msg", "Missing Token");
        }

        // 通过 token (Secret) 查询数据库中的账户
        List<Account> accounts = userDao.queryAccountBySecret(token);
        if (accounts.size() != 1) {
            ArknightsApplication.LOGGER.warn("无效的 token: " + token);
            return Map.of("status", 1, "msg", "Invalid Token");
        }

        // 获取用户 UID
        Account user = accounts.get(0);
        String uid = String.valueOf(user.getUid());

        // 构造返回数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", token);  // code 就是 token
        data.put("uid", uid);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 0);
        response.put("msg", "OK");
        response.put("data", data);

        return response;
    }

    @PostMapping(
            value = {"/online/v1/loginout"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject LoginOut(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject(true);
        jsonObject.put("result", 0);
        return jsonObject;
    }
}
