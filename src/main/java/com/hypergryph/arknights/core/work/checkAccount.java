package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class checkAccount {

    public static boolean checkAccount(String secret) {
        if (!ArknightsApplication.enableServer) {
            return false;
        } else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                return false;
            } else {
                return ((Account) Accounts.get(0)).getBan() != 1L;
            }
        }
    }

    public static JSONObject createError(String secret, HttpServletResponse response) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("result", 400);
            result.put("message", "服务器维护中");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                response.setStatus(401);
                result.put("result", 2);
                result.put("error", "账户验证失败");
                return result;
            } else {
                Long uid = ((Account) Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account) Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("result", 403);
                    UserSyncData.put("message", "账号已被封禁");
                    return UserSyncData;
                }
            }
        }
        return null;
    }
}
