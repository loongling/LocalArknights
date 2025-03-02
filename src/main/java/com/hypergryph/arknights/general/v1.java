package com.hypergryph.arknights.general;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
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