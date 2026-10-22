package com.hypergryph.arknights.game;


import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;

@RestController
@RequestMapping("/building")
public class building {

    @PostMapping(
            value = {"/sync"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject sync(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/sync");
        ArknightsApplication.LOGGER.info("BuildingSync:" + jsonBody);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        if(checkAccount(secret)){
            return new JSONObject();
        }
        else{
            createError(secret, response);
            return new JSONObject();
        }
    }
}
