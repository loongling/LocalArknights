package com.hypergryph.arknights.config;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.file.IOTools;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/common") //这是YoStar特有的
public class common {

    @PostMapping("/config")
    public JSONObject getConfig(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /common/config (YoStar)");
        return IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/yostar/YS_common.json");
    }

    @PostMapping("/client-code")
    public JSONObject clientCode(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /common/client-code (YoStar)");
        return IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/yostar/YS_code.json");
    }

    @PostMapping("/client-log")
    public JSONObject clientLog(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /common/client-log (YoStar)");
        return IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/yostar/YS_code.json");
    }

    @PostMapping("/version")
    public JSONObject version(HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /common/version (YoStar)");
        return IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/yostar/YS_version.json");
    }
}
