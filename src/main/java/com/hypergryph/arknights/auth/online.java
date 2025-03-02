package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/online")
public class online {
    private static final Logger LOGGER = LogManager.getLogger();

    public online() {
    }

    @PostMapping(
        value = {"/v1/ping"},
        produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject Ping(HttpServletRequest request) {
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("message", "OK");
        result.put("interval", 2242);
        result.put("timeLeft", -1);
        result.put("alertTime", 600);
        return result;
    }

    @PostMapping(
            value = {"/v1/loginout"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject LoginOut(@RequestHeader("secret") String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject(true);
        jsonObject.put("result", 0);
        return jsonObject;
    }
}