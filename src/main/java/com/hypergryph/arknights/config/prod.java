package com.hypergryph.arknights.config;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/config/prod"})
public class prod {
    public prod(){}

    @RequestMapping({"/office/refresh_config"})
    public JSONObject RefreshConfig() {
        ArKnightsApplication.reloadServerConfig();
        JSONObject result = new JSONObject(true);
        result.put("statusCode", 200);
        return result;
    }

    @RequestMapping({"/official/remote_config"})
    public JSONObject RemoteConfig(HttpServletRequest request) {
        return ArKnightsApplication.serverConfig.getJSONObject("remote");
    }

    @RequestMapping({"/official/network_config"})
    public JSONObject NetworkConfig(HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/official/network_config");
        JSONObject server_network = ArKnightsApplication.serverConfig.getJSONObject("network");
        JSONObject network = new JSONObject(true);
        network.put("sign", server_network.getString("sign"));
        JSONObject content = new JSONObject(true);
        JSONObject configs = server_network.getJSONObject("configs");
        content.put("configVer", server_network.getString("configVer"));
        content.put("funcVer", server_network.getString("funcVer"));
        Iterator var7 = configs.entrySet().iterator();

        while(var7.hasNext()) {
            Map.Entry entry = (Map.Entry)var7.next();
            JSONObject funcNetwork = configs.getJSONObject(entry.getKey().toString()).getJSONObject("network");
            Iterator var10 = funcNetwork.entrySet().iterator();

            while(var10.hasNext()) {
                Map.Entry funcNetworkEntry = (Map.Entry)var10.next();
                String value = funcNetwork.getString(funcNetworkEntry.getKey().toString());
                funcNetwork.put(funcNetworkEntry.getKey().toString(), value.replace("{server}", ArKnightsApplication.serverConfig.getJSONObject("server").getString("url")));
            }
        }

        content.put("configs", configs);
        network.put("content", content.toJSONString());
        return network;
    }
    @RequestMapping({"/official/Android/version"})
    public JSONObject AndroidVersion(HttpServletRequest request) {
        return ArKnightsApplication.serverConfig.getJSONObject("version").getJSONObject("android");
    }

    @RequestMapping({"/official/IOS/version"})
    public JSONObject IosVersion(HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/official/IOS/version");
        return ArKnightsApplication.serverConfig.getJSONObject("version").getJSONObject("ios");
    }

    @RequestMapping({"/b/remote_config"})
    public JSONObject BRemoteConfig(HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/b/remote_config");
        return ArKnightsApplication.serverConfig.getJSONObject("remote");
    }

    @RequestMapping({"/b/network_config"})
    public JSONObject BNetworkConfig(HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/b/network_config");
        return ArKnightsApplication.serverConfig.getJSONObject("network");
    }

    @RequestMapping({"/b/Android/version"})
    public JSONObject BAndroidVersion(HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/b/Android/version");
        return ArKnightsApplication.serverConfig.getJSONObject("version").getJSONObject("android");
    }

    @RequestMapping({"/announce_meta/Android/preannouncement.meta.json"})
    public JSONObject PreAnnouncement(HttpServletRequest request) {
        return ArKnightsApplication.serverConfig.getJSONObject("announce").getJSONObject("preannouncement");
    }

    @RequestMapping({"/announce_meta/Android/announcement.meta.json"})
    public JSONObject announcement(HttpServletRequest request) {
        return ArKnightsApplication.serverConfig.getJSONObject("announce").getJSONObject("announcement");
    }

    @GetMapping("/announce_meta/Android/preannouncement.meta.json/api/gate/meta/Android")
    public Map<String, Object> prodGateMeta() {
        ArKnightsApplication.LOGGER.info("请求网关信息: /api/gate/meta/Android");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preAnnounceId", "478");
        response.put("actived", true);
        response.put("preAnnounceType", 2);

        return response;
    }


}
