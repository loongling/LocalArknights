package com.hypergryph.arknights.app;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class api {
    private static final Logger logger = LoggerFactory.getLogger(api.class);

    @GetMapping("/gate/meta/Android")
    public Map<String, Object> prodGateMeta(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 请求网关信息 /api/gate/meta/Android");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preAnnounceId", "478");
        response.put("actived", true);
        response.put("preAnnounceType", 2);

        return response;
    }
    @GetMapping("/gate/meta/Windows")
    public Map<String, Object> prodGateMateW(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);

        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 请求网关信息 /api/gate/meta/Windows");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preAnnounceId", "528");
        response.put("actived", true);
        response.put("preAnnounceType", 2);

        return response;
    }

    @GetMapping("/game/get_latest")
    public Map<String, Object> getLatestGameInfo(@RequestParam Map<String, String> params) {
        logger.info("请求最新游戏信息: /api/game/get_latest");
        String platform = params.get("platform");
        String clientVersion = ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject(platform).getString("clientVersion");
        String funcVer = ArknightsApplication.serverConfig.getString("clientFuncVer");
        String file_path = ArknightsApplication.serverConfig.getString("file_path");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", 0);
        response.put("version", funcVer);
        response.put("request_version", funcVer);

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("packs", "[]");
        pkg.put("total_size", "0");
        pkg.put("file_path", file_path);
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
    @GetMapping("/remote_config/1/prod/default/{os}/remote_config")
    public Map<String, Object> akSdkConfig(HttpServletRequest request, @PathVariable String os) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 请求 SDK 配置信息: /api/remote_config/1/prod/default/{os}/remote_config");
        return ArknightsApplication.serverConfig.getJSONObject("remote");
    }
    @RequestMapping("/remote_config/1/prod/default/{os}/network_config")
    public JSONObject NetworkConfig(HttpServletRequest request, @PathVariable String os) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /remote_config/1/prod/default/{os}/network_config");
        JSONObject server_network = ArknightsApplication.serverConfig.getJSONObject("network");
        JSONObject hg_network = server_network.getJSONObject("configs").getJSONObject(server_network.getString("funcVer")).getJSONObject("network");
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
        network.put("funcVer", server_network.getString("funcVer"));
        network.put("secure", hg_network.getBoolean("secure"));
        return network;
    }

    @GetMapping("/remote_config/104/prod-download/default/Android/download-remote-config")
    public JSONObject downloadRemoteConfig(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /api/remote_config/104/prod-download/default/Android/download-remote-config");
        JSONObject result = new JSONObject();
        result.put("use-assets", false);
        return result;
    }

    @GetMapping("/game/get_latest_game_info")
    public JSONObject getLatestGameInfo(HttpServletRequest request,@RequestParam Map<String, String> params) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /api/game/get_latest_game_info");
        String platform = params.get("platform");
        String clientVersion = ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject(platform).getString("clientVersion");
        String funcVer = ArknightsApplication.serverConfig.getString("clientFuncVer");

       JSONObject response = new JSONObject();
        response.put("action", 0);
        response.put("version", funcVer);
        response.put("update_type", 0);

        JSONObject update_info = new JSONObject();
        update_info.put("patch", null);
        update_info.put("custom_info", "");
        update_info.put("source_package", null);

        JSONObject packageA = new JSONObject();
        packageA.put("packs", new JSONArray());
        packageA.put("total_size", "0");
        packageA.put("file_path", "");
        packageA.put("url", "");
        packageA.put("md5", "");
        packageA.put("package_size", "0");
        packageA.put("file_id", "1");
        packageA.put("sub_channel", "1");
        packageA.put("game_files_md5", "");
        update_info.put("package", packageA);

        response.put("update_info", update_info);
        response.put("client_version", clientVersion);
        response.put("state", 0);
        response.put("preload_action", 0);
        JSONObject preload_info = new JSONObject();
        preload_info.put("res_version", "");
        preload_info.put("res_game_version", "");
        preload_info.put("package_size", "0");
        preload_info.put("total_size", "0");
        preload_info.put("patches", new JSONArray());
        preload_info.put("cd_key", "");
        preload_info.put("source_res_version", "");
        response.put("preload_info", preload_info);
        return response;
    }
}
