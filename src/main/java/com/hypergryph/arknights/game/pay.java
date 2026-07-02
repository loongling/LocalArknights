package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping({"/pay"})
public class pay {
    @PostMapping(
            value = "/getUnconfirmedOrderIdList",
            produces = "application/json;charset=UTF-8"
    )
    public Map<String, Object> getUnconfirmOrderId(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/getUnconfirmedOrderIdList");
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());

        JSONObject result = new JSONObject(true); // true 保持字段顺序

        try {
            JSONArray orderIdArray = new JSONArray();
            result.put("orderIdList", orderIdArray);

            // 4. 构建 pushMessage
            JSONArray pushMessageArray = buildPushMessage();
            result.put("pushMessage", pushMessageArray);

            // 5. 构建 playerDataDelta
            JSONObject playerDataDelta = buildPlayerDataDelta();
            result.put("playerDataDelta", playerDataDelta);

            ArknightsApplication.LOGGER.info("Response: " + result.toJSONString());

        } catch (Exception e) {
            ArknightsApplication.LOGGER.error("Error processing request", e);
            // 返回空数据
            result.put("orderIdList", new JSONArray());
            result.put("pushMessage", new JSONArray());
            result.put("playerDataDelta", buildEmptyPlayerDataDelta());
        }
        return result;
    }
    private JSONArray buildPushMessage() {
        JSONArray pushMessageArray = new JSONArray();

        JSONObject dataPayload = new JSONObject();
        dataPayload.put("content", "Welcome to Local Arknights!这是一个开源项目,如果你为它付费了,那你一定被骗了");
        dataPayload.put("loop", 3);
        dataPayload.put("majorVersion", "369");

        JSONObject payload = new JSONObject();
        payload.put("data", dataPayload.toJSONString());

        JSONObject pushItem = new JSONObject();
        pushItem.put("path", "flushAlerts");
        pushItem.put("payload", payload);

        pushMessageArray.add(pushItem);

        return pushMessageArray;
    }

    private JSONObject buildPlayerDataDelta() {
        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        return playerDataDelta;
    }

    private JSONObject buildEmptyPlayerDataDelta() {
        JSONObject delta = new JSONObject();
        delta.put("modified", new JSONObject());
        delta.put("deleted", new JSONObject());
        return delta;
    }
}
