package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/building"})
public class buildingFallback {

    @PostMapping(value = {"/**"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject fallback(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody(required = false) JSONObject jsonBody,
                               HttpServletRequest request) {
        String uri = request.getRequestURI();
        ArknightsApplication.LOGGER.warn("[building][未实现] {} body={}", uri,
                jsonBody == null ? "{}" : jsonBody.toJSONString());
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }
}
