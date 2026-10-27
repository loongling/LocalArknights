package com.hypergryph.arknights.asset;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/assetbundle")
public class assetbundle {

    @GetMapping("/official/{os}/version")
    public JSONObject getVersion(@PathVariable String os, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /assetbundle/official/{os}/version (YoStar)");
        String clientVer = ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("global_" + os).getString("clientVersion");
        String resVersion = ArknightsApplication.serverConfig.getJSONObject("version").getJSONObject("global_" + os).getString("resVersion");
        JSONObject result = new JSONObject();
        result.put("resVersion", resVersion);
        result.put("clientVersion", clientVer);
        return result;
    }
}
