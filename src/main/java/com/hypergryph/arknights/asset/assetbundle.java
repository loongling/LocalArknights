package com.hypergryph.arknights.asset;

import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.file.IOTools;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/assetbundle")
public class assetbundle {

    @GetMapping("/official/{os}/version")
    public JSONObject getversion(@PathVariable String os, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /assetbundle/official/{os}/version (YoStar)");
        return new JSONObject();
    }
}
