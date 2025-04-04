package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping({"/char"})
public class character {
    public character() {
    }

    @PostMapping(
            value = {"/changeMarkStar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject battleStart(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 标星角色 /char/ChangeMarkStar");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject ncresult = new JSONObject(true);
                ncresult.put("result", 2);
                ncresult.put("error", "无法查询到此账户");
                return ncresult;
            } else {

                Account account = Accounts.get(0);
                Long uid = account.getUid();

                if (account.getBan() == 1L) {
                    response.setStatus(500);
                    JSONObject banResult = new JSONObject(true);
                    banResult.put("statusCode", 403);
                    banResult.put("error", "Bad Request");
                    banResult.put("message", "error");
                    return banResult;
                } else {
                    JSONObject UserSyncData = JSON.parseObject(account.getUser());
                    JSONObject chars = UserSyncData
                            .getJSONObject("troop")
                            .getJSONObject("chars");

                    // 获取传入的角色标星数据
                    JSONObject setData = JsonBody.getJSONObject("set");

                    JSONObject modifiedChars = new JSONObject(true);

                    // 遍历并更新数据
                    for (String charId : setData.keySet()) {
                        int markStatus = setData.getIntValue(charId);

                        for (String key : chars.keySet()) {
                            JSONObject charData = chars.getJSONObject(key);
                            if (charData.getString("charId").equals(charId)) {
                                charData.put("starMark", markStatus);

                                // 构建返回的 delta 数据
                                JSONObject deltaChar = new JSONObject(true);
                                deltaChar.put("starMark", markStatus);
                                modifiedChars.put(key, deltaChar);
                            }
                        }
                    }

                    // 保存到数据库
                    userDao.setUserData(uid, UserSyncData); // 你之前的调用方式

                    // 构造返回
                    JSONObject delta = new JSONObject(true);
                    delta.put("deleted", new JSONObject(true));
                    JSONObject troop = new JSONObject(true);
                    troop.put("chars", modifiedChars);
                    JSONObject modified = new JSONObject(true);
                    modified.put("troop", troop);
                    JSONObject playerDataDelta = new JSONObject(true);
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));

                    JSONObject responseData = new JSONObject(true);
                    responseData.put("playerDataDelta", playerDataDelta);
                    ArknightsApplication.LOGGER.info("RSD:" + responseData);

                    return responseData;
                }
            }
        }
    }
}
