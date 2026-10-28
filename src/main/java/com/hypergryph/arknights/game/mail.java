package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.mailInstanceDao;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.work.processArray;
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
@RequestMapping({"/mail"})
public class mail {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Object LOCK = new Object();
    public static final int FIRST_MAIL_ID = 751;

    @PostMapping(value = {"/getMetaInfoList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getMetaInfoList(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody(required = false) JSONObject JsonBody,
                                      HttpServletResponse response,
                                      HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mail/getMetaInfoList");
        Account account = loadAccount(secret, response);
        if (account == null) {
            return errorResult();
        }
        long uid = account.getUid();
        long now = ArknightsApplication.getTimestamp();
        JSONArray mails = mailInstanceDao.listByUid(uid);
        ensureSurveyMails(mails, uid, now);

        JSONArray result = new JSONArray();
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            if (m == null || isRemoved(m) || isExpired(m, now)) {
                continue;
            }
            JSONObject meta = new JSONObject(true);
            if (isSurvey(m)) {
                meta.put("mailId", -1);
                meta.put("surveyMailId", m.getString("surveyMailId"));
            } else {
                meta.put("mailId", m.getIntValue("mailId"));
            }
            meta.put("createAt", m.getLongValue("createAt"));
            meta.put("state", m.getIntValue("state"));
            meta.put("hasItem", isSurvey(m) ? 0 : m.getIntValue("hasItem"));
            if (!isSurvey(m)) {
                meta.put("collectionId", m.get("collectionId"));
            }
            meta.put("type", m.getIntValue("type"));
            result.add(meta);
        }

        JSONObject out = new JSONObject(true);
        out.put("result", result);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    @PostMapping(value = {"/listMailBox"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject listMailBox(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody(required = false) JSONObject JsonBody,
                                  HttpServletResponse response,
                                  HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mail/listMailBox");
        Account account = loadAccount(secret, response);
        if (account == null) {
            return errorResult();
        }
        long uid = account.getUid();
        long now = ArknightsApplication.getTimestamp();
        JSONArray mails = mailInstanceDao.listByUid(uid);
        ensureSurveyMails(mails, uid, now);

        // 官服请求体：{"sysMailIdList":[],"surveyMailIdList":["sv_2"],"mailIdList":[]}
        java.util.Set<Integer> wantNormal = new java.util.HashSet<>();
        java.util.Set<String> wantSurvey = new java.util.HashSet<>();
        if (JsonBody != null) {
            collectMailIds(JsonBody.getJSONArray("sysMailIdList"), wantNormal);
            collectMailIds(JsonBody.getJSONArray("mailIdList"), wantNormal);
            collectSurveyIds(JsonBody.getJSONArray("surveyMailIdList"), wantSurvey);
        }

        JSONArray mailList = new JSONArray();
        JSONArray surveyMailList = new JSONArray();
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            if (m == null || isRemoved(m) || isExpired(m, now)) {
                continue;
            }
            if (isSurvey(m)) {
                if (wantSurvey.contains(m.getString("surveyMailId"))) {
                    surveyMailList.add(m);
                }
            } else if (wantNormal.contains(m.getIntValue("mailId"))) {
                mailList.add(m);
            }
        }

        JSONObject out = new JSONObject(true);
        out.put("mailList", mailList);
        out.put("surveyMailList", surveyMailList);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    @PostMapping(value = {"/receiveMail"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject receiveMail(@RequestHeader(value = "secret", required = false) String secret,
                                  @RequestBody JSONObject JsonBody,
                                  HttpServletResponse response,
                                  HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mail/receiveMail");
        int mailId = JsonBody.getIntValue("mailId");

        Account account = loadAccount(secret, response);
        if (account == null) {
            return errorResult();
        }
        long uid = account.getUid();
        JSONArray mails = mailInstanceDao.listByUid(uid);

        JSONObject UserSyncData = JSON.parseObject(account.getUser());
        JSONObject applied = applyReceiveMail(mails, UserSyncData, mailId);
        if (applied.getString("error") != null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", applied.getString("error"));
            return bad;
        }
        userDao.setUserData(account.getUid(), UserSyncData);
        mailInstanceDao.setState(uid, mailId, 1);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", applied.getJSONObject("modified"));
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("result", 0);
        out.put("items", applied.getJSONArray("items"));
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    @PostMapping(value = {"/removeAllReceivedMail"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject removeAllReceivedMail(@RequestHeader(value = "secret", required = false) String secret,
                                            @RequestBody(required = false) JSONObject JsonBody,
                                            HttpServletResponse response,
                                            HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mail/removeAllReceivedMail");
        Account account = loadAccount(secret, response);
        if (account == null) {
            return errorResult();
        }
        long uid = account.getUid();

        java.util.Set<Integer> ids = new java.util.HashSet<>();
        java.util.Set<String> surveyIds = new java.util.HashSet<>();
        if (JsonBody != null) {
            collectMailIds(JsonBody.getJSONArray("sysMailIdList"), ids);
            collectMailIds(JsonBody.getJSONArray("mailIdList"), ids);
            collectSurveyIds(JsonBody.getJSONArray("surveyMailIdList"), surveyIds);
        }

        int removed = 0;
        for (Integer id : ids) {
            removed += mailInstanceDao.deleteNormal(uid, id);
        }
        for (String surveyId : surveyIds) {
            removed += mailInstanceDao.tombstoneSurvey(uid, surveyId);
        }
        LOGGER.info("[mail] removeAllReceivedMail 删掉 {} 封（uid={}）", removed, uid);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    @PostMapping(value = {"/receiveAllMail"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject receiveAllMail(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody(required = false) JSONObject JsonBody,
                                     HttpServletResponse response,
                                     HttpServletRequest request) {
        LOGGER.info("[" + "/" + ArknightsApplication.getIpAddr(request) + "] /mail/receiveAllMail");
        Account account = loadAccount(secret, response);
        if (account == null) {
            return errorResult();
        }
        long uid = account.getUid();
        JSONArray mails = mailInstanceDao.listByUid(uid);

        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        if (JsonBody != null) {
            collectMailIds(JsonBody.getJSONArray("sysMailIdList"), ids);
            collectMailIds(JsonBody.getJSONArray("mailIdList"), ids);
        }

        JSONObject UserSyncData = JSON.parseObject(account.getUser());
        JSONArray granted = new JSONArray();
        JSONObject merged = new JSONObject(true);
        int received = 0;
        for (Integer mailId : ids) {
            JSONObject applied = applyReceiveMail(mails, UserSyncData, mailId);
            if (applied.getString("error") != null) {
                LOGGER.info("[mail] receiveAllMail 跳过 mailId={}：{}", mailId, applied.getString("error"));
                continue;
            }
            JSONArray items = applied.getJSONArray("items");
            if (items != null) {
                granted.addAll(items);
            }
            mergeInto(merged, applied.getJSONObject("modified"));
            mailInstanceDao.setState(uid, mailId, 1);
            received++;
        }
        userDao.setUserData(account.getUid(), UserSyncData);

        if (!merged.containsKey("pushFlags")) {
            JSONObject pushFlags = new JSONObject(true);
            pushFlags.put("hasGifts", hasUnreceived(mails) ? 1 : 0);
            merged.put("pushFlags", pushFlags);
        } else {
            merged.getJSONObject("pushFlags").put("hasGifts", hasUnreceived(mails) ? 1 : 0);
        }
        LOGGER.info("[mail] receiveAllMail 领取 {} 封，共 {} 件附件", received, granted.size());

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", merged);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("result", 0);
        out.put("items", granted);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private static void mergeInto(JSONObject target, JSONObject source) {
        if (source == null) {
            return;
        }
        for (String key : source.keySet()) {
            Object value = source.get(key);
            Object exist = target.get(key);
            if (value instanceof JSONObject && exist instanceof JSONObject) {
                JSONObject dst = (JSONObject) exist;
                dst.putAll((JSONObject) value);
            } else {
                target.put(key, value);
            }
        }
    }

    public static JSONObject applyReceiveMail(JSONArray mails, JSONObject UserSyncData, int mailId) {
        JSONObject out = new JSONObject(true);
        JSONObject m = findMail(mails, mailId);
        if (m == null) {
            out.put("error", "没有这封邮件: " + mailId);
            return out;
        }
        if (m.getIntValue("state") != 0) {
            out.put("error", "这封邮件已经领过了: " + mailId);
            return out;
        }

        JSONObject statusBefore = new JSONObject(true);
        copyInto(UserSyncData.getJSONObject("status"), statusBefore);
        JSONObject invBefore = new JSONObject(true);
        copyInto(UserSyncData.getJSONObject("inventory"), invBefore);

        JSONArray items = m.getJSONArray("items");
        JSONArray granted = new JSONArray();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                JSONObject grant = new JSONObject(true);
                grant.put("id", it.getString("id"));
                grant.put("type", it.getString("type"));
                grant.put("count", it.getIntValue("count") == 0 ? 1 : it.getIntValue("count"));
                processArray.handleUserDataChange(grant, UserSyncData, 0L, -1, null);
                JSONObject echo = new JSONObject(true);
                echo.put("type", it.getString("type"));
                echo.put("id", it.getString("id"));
                echo.put("count", it.getIntValue("count") == 0 ? 1 : it.getIntValue("count"));
                granted.add(echo);
            }
        }
        m.put("state", 1);

        JSONObject modified = new JSONObject(true);
        JSONObject statusDelta = diff(UserSyncData.getJSONObject("status"), statusBefore);
        if (!statusDelta.isEmpty()) {
            modified.put("status", statusDelta);
        }
        JSONObject invDelta = diff(UserSyncData.getJSONObject("inventory"), invBefore);
        if (!invDelta.isEmpty()) {
            modified.put("inventory", invDelta);
        }
        JSONObject pushFlags = new JSONObject(true);
        pushFlags.put("hasGifts", hasUnreceived(mails) ? 1 : 0);
        modified.put("pushFlags", pushFlags);

        out.put("items", granted);
        out.put("modified", modified);
        return out;
    }

    public static JSONObject buildMail(JSONObject def, int mailId, String uid, JSONArray existingMails) {
        long now = ArknightsApplication.getTimestamp();
        if (mailId <= 0) {
            mailId = existingMails == null ? FIRST_MAIL_ID : nextMailId(existingMails);
        }
        JSONObject m = new JSONObject(true);
        m.put("mailId", mailId);
        if (def.containsKey("defId")) {
            m.put("defId", def.getString("defId"));
        }
        m.put("from", def.getString("from") == null ? "penguin_logistics" : def.getString("from"));
        m.put("subject", def.getString("subject") == null ? "来自罗德岛的邮件" : def.getString("subject"));
        m.put("content", def.getString("content") == null ? "" : def.getString("content"));
        m.put("createAt", now);
        long days = def.getLongValue("expireDays");
        m.put("expireAt", days <= 0 ? -1 : now + days * 86400L);
        JSONArray defItems = def.getJSONArray("items");
        JSONArray items = new JSONArray();
        if (defItems != null) {
            for (int k = 0; k < defItems.size(); k++) {
                JSONObject it = defItems.getJSONObject(k);
                JSONObject item = new JSONObject(true);
                item.put("id", it.getString("id"));
                item.put("type", it.getString("type"));
                item.put("count", it.getIntValue("count") == 0 ? 1 : it.getIntValue("count"));
                item.put("ts", 0);
                items.add(item);
            }
        }
        m.put("items", items);
        m.put("hasItem", items.isEmpty() ? 0 : 1);
        m.put("platform", -1);
        JSONObject style = new JSONObject(true);
        style.put("route", 0);
        style.put("banner", "");
        m.put("style", style);
        m.put("state", 0);
        m.put("uid", uid);
        m.put("collectionId", null);
        m.put("type", 1);
        return m;
    }

    private static void ensureSurveyMails(JSONArray mails, long uid, long now) {
        JSONArray defs = mailInstanceDao.surveyTemplates();
        if (defs == null || defs.isEmpty()) {
            return;
        }
        for (int i = 0; i < defs.size(); i++) {
            JSONObject def = defs.getJSONObject(i);
            String id = def == null ? null : def.getString("surveyMailId");
            if (id == null || id.isEmpty()) {
                continue;
            }
            boolean seen = false;
            for (int k = 0; k < mails.size(); k++) {
                JSONObject m = mails.getJSONObject(k);
                if (m != null && id.equals(m.getString("surveyMailId"))) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            JSONObject m = new JSONObject(true);
            m.put("surveyMailId", id);
            m.put("uid", String.valueOf(uid));
            m.put("from", def.getString("from") == null ? "penguin_logistics" : def.getString("from"));
            m.put("subject", def.getString("subject") == null ? "" : def.getString("subject"));
            m.put("content", def.getString("content") == null ? "" : def.getString("content"));
            m.put("createAt", now);
            long days = def.getLongValue("expireDays");
            m.put("expireAt", days <= 0 ? -1 : now + days * 86400L);
            m.put("state", 0);
            m.put("type", 2);
            m.put("platform", -1);
            JSONObject style = def.getJSONObject("style");
            if (style == null) {
                style = new JSONObject(true);
                style.put("route", 2);
                style.put("banner", "survey");
            }
            m.put("style", style);
            mailInstanceDao.insert(uid, m);
            mails.add(m);
            LOGGER.info("[mail] 补发调研邮件 {} 给 uid={}", id, uid);
        }
    }

    private static boolean isSurvey(JSONObject m) {
        return m != null && (m.getIntValue("type") == 2 || m.getString("surveyMailId") != null);
    }

    private static boolean isRemoved(JSONObject m) {
        return m != null && m.getBooleanValue("removed");
    }

    private static void collectMailIds(JSONArray source, java.util.Set<Integer> out) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            Object v = source.get(i);
            if (v == null) {
                continue;
            }
            try {
                out.add(Integer.parseInt(String.valueOf(v).trim()));
            } catch (NumberFormatException ignored) {
                // surveyMailId 是字符串（sv_2），走 collectSurveyIds
            }
        }
    }

    private static void collectSurveyIds(JSONArray source, java.util.Set<String> out) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            Object v = source.get(i);
            if (v != null) {
                out.add(String.valueOf(v).trim());
            }
        }
    }

    private static JSONObject findMail(JSONArray mails, int mailId) {
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            if (m != null && !isSurvey(m) && m.getIntValue("mailId") == mailId) {
                return m;
            }
        }
        return null;
    }

    private static int nextMailId(JSONArray mails) {
        int max = FIRST_MAIL_ID - 1;
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            if (m != null && !isSurvey(m)) {
                max = Math.max(max, m.getIntValue("mailId"));
            }
        }
        return max + 1;
    }

    private static boolean isExpired(JSONObject m, long now) {
        long expireAt = m.getLongValue("expireAt");
        return expireAt > 0 && expireAt < now;
    }

    private static boolean hasUnreceived(JSONArray mails) {
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            if (m != null && !isSurvey(m) && m.getIntValue("state") == 0 && m.getIntValue("hasItem") == 1) {
                return true;
            }
        }
        return false;
    }

    private static void copyInto(JSONObject src, JSONObject dst) {
        if (src == null) {
            return;
        }
        for (String key : src.keySet()) {
            dst.put(key, src.get(key));
        }
    }

    private static JSONObject diff(JSONObject after, JSONObject before) {
        JSONObject out = new JSONObject(true);
        if (after == null) {
            return out;
        }
        for (String key : after.keySet()) {
            Object now = after.get(key);
            Object old = before == null ? null : before.get(key);
            if (old == null ? now != null : !old.equals(now)) {
                out.put(key, now);
            }
        }
        return out;
    }

    private static JSONObject emptyDelta() {
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        return delta;
    }

    private Account loadAccount(String secret, HttpServletResponse response) {
        synchronized (LOCK) {
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                return null;
            }
            Account account = (Account) Accounts.get(0);
            if (account.getBan() == 1L) {
                response.setStatus(500);
                return null;
            }
            return account;
        }
    }

    private JSONObject errorResult() {
        JSONObject result = new JSONObject(true);
        result.put("result", 2);
        result.put("error", "无法查询到此账户");
        return result;
    }
}
