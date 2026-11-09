package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 矢量突破（vecBreakV2）。
 * 官服抓包（parsed_gf_rlv2_1256/001_08）：
 *   POST /vecBreakV2/getSeasonRecord {} → {"seasons":{},"playerDataDelta":{"modified":{},"deleted":{}}}
 */
@RestController
@RequestMapping({"/vecBreakV2"})
public class vecBreakV2 {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(
            value = {"/getSeasonRecord"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getSeasonRecord(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody(required = false) JSONObject JsonBody,
                                      HttpServletResponse response,
                                      HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /vecBreakV2/getSeasonRecord");
        JSONObject result = new JSONObject(true);
        result.put("seasons", new JSONObject(true));
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
