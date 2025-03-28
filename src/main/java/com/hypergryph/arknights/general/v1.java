package com.hypergryph.arknights.general;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.function.httpClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;

@RestController
@RequestMapping("/general/v1")
public class v1 {

    private static final String HOLIDAY_API_URL = "https://timor.tech/api/holiday/info/";

    @GetMapping("/server_time")
    public JSONObject serverTime() {
        long UnixTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        boolean isHoliday = checkIfHoliday(today.toString());

        JSONObject data  = new JSONObject();
        data.put("isHoliday", isHoliday);
        data.put("UnixTime", UnixTime);

        JSONObject result = new JSONObject();
        result.put("data", data);
        result.put("msg", "OK");
        result.put("status", 0);

        return result;
    }

    @RequestMapping({"/send_phone_code"})
    public JSONObject sendSmsCode(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] 请求发送手机验证码 /general/v1/send_phone_code");
        String account = jsonBody.getString("account");
        JSONObject result;
        if (ArKnightsApplication.serverConfig.getJSONObject("server").getBooleanValue("captcha")) {
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

    private boolean checkIfHoliday(String date) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = HOLIDAY_API_URL + date;
            JSONObject response = restTemplate.getForObject(url, JSONObject.class);
            if (response != null && response.getBoolean("holiday")) {
                return true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}