package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/survey"})
public class survey {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(value = {"/startSurvey"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject startSurvey(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject JsonBody,
                                  HttpServletResponse response,
                                  HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + "/" + clientIp + "] /survey/startSurvey body=" + JsonBody
                + " secret=" + (secret == null ? "(none)" : secret.substring(0, Math.min(8, secret.length())) + "…")
                + " ua=" + request.getHeader("user-agent"));
        if (secret != null) {
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            if (accounts.size() == 1) {
                LOGGER.info("[survey] startSurvey 来自 uid={}", accounts.get(0).getUid());
            }
        }
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }
}
