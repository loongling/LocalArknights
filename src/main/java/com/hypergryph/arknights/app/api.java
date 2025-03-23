package com.hypergryph.arknights.app;

import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class api {
    private static final Logger logger = LoggerFactory.getLogger(api.class);

    @GetMapping("/gate/meta/Android")
    public Map<String, Object> prodGateMeta() {
        logger.info("请求网关信息: /api/gate/meta/Android");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preAnnounceId", "478");
        response.put("actived", true);
        response.put("preAnnounceType", 2);

        return response;
    }

    @GetMapping("/game/get_latest_game_info")
    public Map<String, Object> getLatestGameInfo() {
        logger.info("请求最新游戏信息: /api/game/get_latest_game_info");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", 3);
        response.put("version","");

        Map<String, Object> updateInfo = new LinkedHashMap<>();
        updateInfo.put("custom_info", "");
        updateInfo.put("package", null);
        updateInfo.put("patch", null);
        updateInfo.put("source_package", null);

        response.put("update_info", updateInfo);
        response.put("update_type", 0);
        response.put("client_version", "");

        return response;
    }
    @GetMapping("/remote_config/101/prod/default/Android/ak_sdk_config")
    public Map<String, Object> akSdkConfig() {
        logger.info("请求 SDK 配置信息: /api/remote_config/101/prod/default/Android/ak_sdk_config");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("report_device_info", 10000);

        return response;
    }
}
