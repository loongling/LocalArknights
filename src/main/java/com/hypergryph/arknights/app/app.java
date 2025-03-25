package com.hypergryph.arknights.app;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/app")
public class app {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/getSettings")
    public JSONObject appGetSettings() {
        String url = "https://passport.arknights.global/app/getSettings";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return JSONObject.parseObject(response.getBody());
    }

    @GetMapping("/getCode")
    public JSONObject appGetCode() {
        String url = "https://passport.arknights.global/app/getCode";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return JSONObject.parseObject(response.getBody());
    }
}
