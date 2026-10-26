package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/story"})
public class story {
    public story() {
    }

    @PostMapping(
            value = {"/finishStory"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject FinishStory(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /story/finishStory");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String storyId = JsonBody.getString("storyId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    UserSyncData.getJSONObject("status").getJSONObject("flags").put(storyId, 1);
                    JSONObject status = new JSONObject(true);
                    if (ArknightsApplication.storyTable.getJSONObject(storyId).getInteger("setProgress") != -1) {
                        int setProgress = ArknightsApplication.storyTable.getJSONObject(storyId).getInteger("setProgress");
                        UserSyncData.getJSONObject("status").put("progress", setProgress);
                        status.put("progress", setProgress);
                    }
                    String kitGranted = null;
                    try {
                        kitGranted = com.hypergryph.arknights.game.building.grantTutorialKit(UserSyncData);
                    } catch (Exception ignored) {
                    }
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    result.put("result", 0);
                    result.put("items", new JSONArray());
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject flag = UserSyncData.getJSONObject("status").getJSONObject("flags");
                    status.put("flags", flag);
                    modified.put("status", status);
                    modified.put("mission", UserSyncData.getJSONObject("mission"));
                    // 本次刚发了教程材料，把 inventory 一起下发，客户端才能立刻看到
                    if (kitGranted != null && !"skip".equals(kitGranted)) {
                        modified.put("inventory", UserSyncData.getJSONObject("inventory"));
                    }
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }
        }
    }
}
