package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static com.hypergryph.arknights.ArknightsApplication.LOGGER;
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

    @GetMapping("/chain_login")
    public JSONObject nextDay(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String sk = request.getHeader("SK");
        JSONObject result = new JSONObject();
        if (sk == null) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        } else  if(!sk.equals(ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey"))) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else  {
            long uid = JsonBody.getLong("uid");
            List<Account> accounts = userDao.queryAccountByUid(uid);
            Account user = accounts.get(0);
            JSONObject UserSyncData;
            try {
                UserSyncData = JSONObject.parseObject(user.getUser());
                if (UserSyncData == null) {
                    throw new Exception("解析用户数据失败");
                }
            } catch (Exception e) {
                LOGGER.error("解析用户数据失败, UID: " + uid, e);
                return new JSONObject();
            }
            UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getJSONArray("history").add(1);
            int a = UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").getInteger("nowIndex");
            UserSyncData.getJSONObject("openServer").getJSONObject("chainLogin").put("nowIndex", a+1);
            result.put("status", "200");
            result.put("msg", "ok");
            result.put("uid", uid);
            userDao.setUserData(uid, UserSyncData);
            return  result;
        }
        return result;
    }

    @GetMapping("/checkInAct")
    public JSONObject nextDayActCheckIn(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String sk = request.getHeader("SK");
        JSONObject result = new JSONObject();
        if (sk == null) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        } else  if(!sk.equals(ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey"))) {
            response.setStatus(400);
            result.put("status", "400");
            result.put("msg", "Auth Failed");
        }
        else  {
            long uid = JsonBody.getLong("uid");
            List<Account> accounts = userDao.queryAccountByUid(uid);
            Account user = accounts.get(0);
            JSONObject UserSyncData;
            try {
                UserSyncData = JSONObject.parseObject(user.getUser());
                if (UserSyncData == null) {
                    throw new Exception("解析用户数据失败");
                }
            } catch (Exception e) {
                LOGGER.error("解析用户数据失败, UID: " + uid, e);
                return new JSONObject();
            }
            UserSyncData.getJSONObject("openServer").getJSONObject("checkIn").getJSONArray("history").add(1);
            result.put("status", "200");
            result.put("msg", "ok");
            result.put("uid", uid);
            userDao.setUserData(uid, UserSyncData);
            return  result;
        }
        return result;
    }
}
