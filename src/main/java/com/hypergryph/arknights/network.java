package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class network {
    public network() {
    }

    @RequestMapping({"/"})
    public JSONObject network_config(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /config/prod/official/network_config");
        JSONObject server_network = ArknightsApplication.serverConfig.getJSONObject("network");
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
                funcNetwork.put(funcNetworkEntry.getKey().toString(), value.replace("{server}", ArknightsApplication.serverConfig.getJSONObject("server").getString("url")));
            }
        }

        content.put("configs", configs);
        network.put("content", content.toJSONString());
        return network;
    }
}
