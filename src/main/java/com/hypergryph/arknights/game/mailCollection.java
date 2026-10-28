package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
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
@RequestMapping({"/mailCollection"})
public class mailCollection {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(value = {"/getList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getList(@RequestHeader(value = "secret", required = false) String secret,
                              @RequestBody(required = false) JSONObject JsonBody,
                              HttpServletResponse response,
                              HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mailCollection/getList");

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 2);
            bad.put("error", "无法查询到此账户");
            return bad;
        }

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("collections", new JSONArray());
        out.put("extra", new JSONArray());
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }
}
