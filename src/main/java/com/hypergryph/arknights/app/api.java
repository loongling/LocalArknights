package com.hypergryph.arknights.app;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
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

    @GetMapping("/game/get_latest")
    public Map<String, Object> getLatestGameInfo(@RequestParam Map<String, String> params) {
        logger.info("请求最新游戏信息: /api/game/get_latest");
        String platform = params.get("platform");
        String clientVersion = ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject(platform).getString("clientVersion");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", 0);
        response.put("version", "69.0.0");
        response.put("request_version", "69.0.0");

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("packs", "[]");
        pkg.put("total_size", "0");
        pkg.put("file_path", "https://ak.hycdn.cn/GzD1CpaWgmSq1wew/69.0/update/1/1/Windows/69.0.0_OCI5nGSI9gIFzxQn/files");
        pkg.put("url", "");
        pkg.put("md5", "");
        pkg.put("package_size", "0");
        pkg.put("file_id", "0");
        pkg.put("sub_channel", "1");
        pkg.put("game_files_md5", "109903ea32fc4d71b6b9541bc0b5f9f0");

        response.put("pkg", pkg);
        response.put("patch", null);
        response.put("state", 0);
        response.put("launcher_action", 0);
        return response;
    }
    @GetMapping("/remote_config/1/prod/default/Windows/remote_config")
    public Map<String, Object> akSdkConfig() {
        logger.info("请求 SDK 配置信息: /api/remote_config/1/prod/default/Windows/remote_config");

        return new LinkedHashMap<>();
    }
    @RequestMapping("/remote_config/1/prod/default/Windows/network_config")
    public JSONObject NetworkConfig(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /remote_config/1/prod/default/Win/network_config");
        JSONObject server_network = ArknightsApplication.serverConfig.getJSONObject("network");
        JSONObject hg_network = server_network.getJSONObject("configs").getJSONObject("V070").getJSONObject("network");
        JSONObject network = new JSONObject(true);
        network.put("an", hg_network.getString("an"));
        network.put("as", hg_network.getString("as"));
        network.put("gs", hg_network.getString("gs"));
        network.put("hu", hg_network.getString("hu"));
        network.put("hv", hg_network.getString("hv"));
        network.put("of", hg_network.getString("of"));
        network.put("sl", hg_network.getString("sl"));
        network.put("u8", hg_network.getString("u8"));
        network.put("pkgAd", hg_network.getString("pkgAd"));
        network.put("prean", hg_network.getString("prean"));
        network.put("devsdk", hg_network.getBoolean("devsdk"));
        network.put("pkgIOS", hg_network.getString("pkgIOS"));
        network.put("configVer", server_network.getString("configVer"));
        return network;
    }
}
