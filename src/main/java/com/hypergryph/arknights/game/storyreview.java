package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/storyreview"})
public class storyreview {
    public storyreview() {
    }

    @RequestMapping({"/readStory"})
    public JSONObject readStory() {
        return JSONObject.parseObject("{\"result\":0,\"rewards\":[],\"unlockStages\":[],\"alert\":[],\"playerDataDelta\":{\"modified\":{},\"deleted\":{}}}");
    }

    @PostMapping(
            value = {"/markStoryAcceKnown"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject markStoryAcceKnown(HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /story/finishStory");
        List<Account> Accounts = userDao.queryAccountBySecret(secret);

        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        } else {
            Long uid = Accounts.get(0).getUid();

            if (Accounts.get(0).getBan() == 1L) {
                response.setStatus(500);
                JSONObject UserSyncData = new JSONObject(true);
                UserSyncData.put("statusCode", 403);
                UserSyncData.put("error", "Bad Request");
                UserSyncData.put("message", "error");
                return UserSyncData;
            }

            // 解析用户数据
            JSONObject UserSyncData = JSONObject.parseObject(Accounts.get(0).getUser());

            // 确保 storyreview 存在
            JSONObject storyReview = UserSyncData.getJSONObject("storyreview");
            if (storyReview == null) {
                storyReview = new JSONObject(true);
                UserSyncData.put("storyreview", storyReview);
            }

            // 确保 tags 存在
            JSONObject tags = storyReview.getJSONObject("tags");
            if (tags == null) {
                tags = new JSONObject(true);
                storyReview.put("tags", tags);
            }

            // 更新 knownStoryAcceleration
            tags.put("knownStoryAcceleration", 1);

            // 更新用户数据
            userDao.setUserData(uid, UserSyncData);
            ArKnightsApplication.LOGGER.info("Received JSON: " + UserSyncData.toJSONString());

            // 构造返回数据
            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            JSONObject modified = new JSONObject(true);
            JSONObject dungeon = new JSONObject(true);
            JSONObject stages = new JSONObject(true);

            dungeon.put("stages", stages);
            modified.put("storyreview", UserSyncData.getJSONObject("storyreview"));
            playerDataDelta.put("deleted", new JSONObject(true));
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);

            return result;
        }
    }

}
