package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.canCheckIn;
import static com.hypergryph.arknights.core.work.checkNeedChangeGroup.needChange;
import static com.hypergryph.arknights.core.work.checkNeedChangeGroup.nextGroupId;

@RestController
@RequestMapping("/test")
public class testAPI {

    public testAPI() {}

    @GetMapping("/checkCanCheckIn")
    public JSONObject checkCanCheckIn(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String sk = request.getHeader("SK");
        JSONObject result = new JSONObject();
        if (sk == null) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else if (!sk.equals(ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey"))) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else {
            long checkInTime = JsonBody.getLong("checkInTime");
            long nowTime = JsonBody.getLong("nowTime");
            boolean can = canCheckIn(checkInTime, nowTime);
            result.put("status", "200");
            result.put("msg", "ok");
            result.put("can", can);
        }
        return result;
    }

    @GetMapping("/checkNeedChange")
    public JSONObject checkNeedChange(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String sk = request.getHeader("SK");
        JSONObject result = new JSONObject();
        if (sk == null) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        } else if (!sk.equals(ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey"))) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else {
            long LoginTime = JsonBody.getLong("LoginTime");
            long nowTime = JsonBody.getLong("nowTime");
            boolean need = needChange(LoginTime, nowTime);
            result.put("status", "200");
            result.put("msg", "ok");
            result.put("need", need);
        }
        return result;
    }

    @GetMapping("/changeGroup")
    public JSONObject changeGroup(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String sk = request.getHeader("SK");
        JSONObject result = new JSONObject();
        if (sk == null) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        } else if(!sk.equals(ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey"))) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else  {
            String id = JsonBody.getString("id");
            String next = nextGroupId(id);
            result.put("status", "200");
            result.put("msg", "ok");
            result.put("next", next);
        }
        return result;
    }
}
