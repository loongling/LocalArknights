package com.hypergryph.arknights.app;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.hypergryph.arknights.ArknightsApplication.LOGGER;

@RestController
@RequestMapping("/app")
public class app {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/getSettings")
    public JSONObject appGetSettings(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /app/getSettings");
        String url = "https://passport.arknights.global/app/getSettings";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return JSONObject.parseObject(response.getBody());
    }

    @GetMapping("/getCode")
    public JSONObject appGetCode( HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /app/getCode");
        String url = "https://passport.arknights.global/app/getCode";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return JSONObject.parseObject(response.getBody());
    }
    @GetMapping("/v1/config")
    public JSONObject appV1Config(@RequestParam("appCode") String appCode, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[/" + clientIp + "] /app/v1/config");

        Map<String, Integer> antiAddiction = new HashMap<>();
        antiAddiction.put("minorPeriodEnd", 21);
        antiAddiction.put("minorPeriodStart", 20);

        // 支付方式
        List<Map<String, Object>> payment = List.of(
                Map.of("key", "alipay", "recommend", true),
                Map.of("key", "wechat", "recommend", false),
                Map.of("key", "pcredit", "recommend", false)
        );

        // 相关 URL
        Map<String, String> agreementUrl = Map.of(
                "account", "https://user.hypergryph.com/protocol/plain/index",
                "childrenPrivacy","https://user.hypergryph.com/protocol/plain/children_privacy",
                "game","https://user.hypergryph.com/protocol/plain/ak/index",
                "gameService","https://user.hypergryph.com/protocol/plain/ak/service",
                "privacy","https://user.hypergryph.com/protocol/plain/privacy",
                "register","https://user.hypergryph.com/protocol/plain/registration",
                "unbind","https://user.hypergryph.com/protocol/plain/ak/cancellation",
                "updateOverview", "https://user.hypergryph.com/protocol/plain/overview_of_changes"
        );

        // App 设定
        Map<String, Object> app = new HashMap<>();
        app.put("alipayAppId", "");
        app.put("appAmount", 600);
        app.put("appName", "明日方舟");
        app.put("customerServiceUrl", "https://customer-service.hypergryph.com/ak?hg_token={hg_token}&source_from=sdk");
        app.put("deviceCheckMode", 2);
        app.put("enableAntiAddiction", true);
        app.put("enableAuthenticate", true);
        app.put("enableAutoLogin", false);
        app.put("enableGiftCode", true);
        app.put("enablePaidApp", false);
        app.put("enablePayment", true);
        app.put("enableScanLogin", false);
        app.put("enableUnbindGrant", true);
        app.put("needAntiAddictionAlert", true);
        app.put("needShowName", false);
        app.put("oneLoginAppId", "");
        app.put("wechatAppId", "");

        // 组合 Data 数据
        Map<String, Object> data = new HashMap<>();
        data.put("antiAddiction", antiAddiction);
        data.put("payment", payment);
        data.put("customerServiceUrl", "https://chat.hypergryph.com/chat/h5/v2/index.html?sysnum=889ee281e3564ddf883942fe85764d44&channelid=2");
        data.put("cancelDeactivateUrl", "https://user.hypergryph.com/cancellation");
        data.put("agreementUrl", agreementUrl);
        data.put("app", app);
        JSONObject response = new JSONObject();
        response.put("data", data);
        response.put("status", 0);
        response.put("msg", "OK");

        return response;
    }


    private static Map<String, Object> createPayment(String key, boolean recommend) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("key", key);
        payment.put("recommend", recommend);
        return payment;
    }

}
