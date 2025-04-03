package com.hypergryph.arknights.general;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.function.httpClient;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/general/v1")
public class v1 {

    @GetMapping("/server_time")
    public JSONObject serverTime(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 请求服务器时间 /general/v1/server_time");
        long UnixTime = System.currentTimeMillis() / 1000L;
        boolean isHoliday = false;

        JSONObject data  = new JSONObject();
        data.put("isHoliday", isHoliday);
        data.put("serverTime", UnixTime);

        JSONObject result = new JSONObject();
        result.put("data", data);
        result.put("msg", "OK");
        result.put("status", 0);

        return result;
    }

    @RequestMapping({"/send_phone_code"})
    public JSONObject sendSmsCode(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 请求发送手机验证码 /general/v1/send_phone_code");
        String account = jsonBody.getString("account");
        JSONObject result;
        if (ArknightsApplication.serverConfig.getJSONObject("server").getBooleanValue("captcha")) {
            result = new JSONObject(true);
            result.put("result", 4);
            return result;
        } else if (httpClient.sentSmsCode(account).getIntValue("code") == 200) {
            result = new JSONObject(true);
            result.put("result", 0);
            return result;
        } else {
            result = new JSONObject(true);
            result.put("result", 4);
            return result;
        }
    }

}