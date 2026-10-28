package com.hypergryph.arknights.core.mail;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.mailDao;
import com.hypergryph.arknights.core.dao.mailInstanceDao;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.pojo.Mail;
import com.hypergryph.arknights.game.mail;
import java.util.List;

/**
 * 邮件投递服务：Admin 接口（/admin/mail/*）和控制台命令（/mail send 等）共用。
 *
 * 数据模型（2026-10-28 起全部落 MySQL）：
 * <ul>
 *   <li><b>mail 表</b> —— 模板库（id / name / from / subject / content / items）</li>
 *   <li><b>mail_instance 表</b> —— 某个玩家收到过的每封邮件（一行一封）</li>
 *   <li><b>mail_survey 表</b> —— 调研邮件模板（type:2 / surveyMailId）</li>
 * </ul>
 * 不会自动给谁发信：只有显式调用 send 才投递。
 */
public final class MailService {

    private MailService() {
    }

    /** 读玩家邮件数组（含 removed 墓碑；没有这个玩家返回 null） */
    public static JSONArray mailsOf(long uid) {
        if (!accountExists(uid)) {
            return null;
        }
        return mailInstanceDao.listByUid(uid);
    }

    public static boolean accountExists(long uid) {
        return userDao.queryAccountByUid(uid).size() == 1;
    }

    /**
     * 给玩家发一封邮件（每次都是新 mailId，可重复发）。
     *
     * @param templateId   模板 id（mail 表主键），与 templateName 二选一
     * @param templateName 模板 name，与 templateId 二选一
     * @param expireDays   有效天数，<=0 表示不过期
     * @return 成功返回 {result:0, mailId, subject, items}；失败返回 {result:1, error}
     */
    public static JSONObject send(long uid, Integer templateId, String templateName, long expireDays) {
        return send(uid, templateId, templateName, expireDays, null);
    }

    /**
     * 同上，但可以覆盖发件人（fromOverride 非空时用它代替模板里的 from）。
     * 用来试任意发件人 id 显示成什么样。
     */
    public static JSONObject send(long uid, Integer templateId, String templateName, long expireDays,
                                  String fromOverride) {
        JSONObject out = new JSONObject(true);

        List<Mail> rows = templateName != null && !templateName.isBlank()
                ? mailDao.queryMailByName(templateName)
                : (templateId != null ? mailDao.queryMailById(templateId) : null);
        if (rows == null || rows.isEmpty()) {
            out.put("result", 1);
            out.put("error", "mail 表里没有这个模板: "
                    + (templateName != null ? "name=" + templateName : "id=" + templateId));
            return out;
        }
        Mail row = rows.get(0);

        if (!accountExists(uid)) {
            out.put("result", 1);
            out.put("error", "找不到玩家存档: uid=" + uid);
            return out;
        }

        JSONObject def = new JSONObject(true);
        def.put("defId", "db:" + row.getId());
        def.put("from", fromOverride != null && !fromOverride.isBlank() ? fromOverride : row.getFrom());
        def.put("subject", row.getSubject());
        def.put("content", row.getContent());
        def.put("items", parseItems(row.getItems()));
        def.put("expireDays", expireDays);

        int mailId = mailInstanceDao.nextMailId(uid);
        JSONObject instance = mail.buildMail(def, mailId, String.valueOf(uid), null);
        mailInstanceDao.insert(uid, instance);

        ArknightsApplication.LOGGER.info("[mail] 给 uid={} 发邮件 mailId={} 主题={}（模板 db:{}）",
                uid, mailId, instance.getString("subject"), row.getId());

        out.put("result", 0);
        out.put("uid", uid);
        out.put("mailId", mailId);
        out.put("subject", instance.getString("subject"));
        out.put("items", instance.getJSONArray("items"));
        return out;
    }

    /** 删掉玩家的一封邮件；返回删掉了几封（0/1） */
    public static int delete(long uid, int mailId) {
        if (!accountExists(uid)) {
            return -1;
        }
        return mailInstanceDao.deleteNormal(uid, mailId);
    }

    /** 清空某个玩家所有邮件；返回清掉的普通邮件数；-1 表示没有这个玩家 */
    public static int clear(long uid) {
        if (!accountExists(uid)) {
            return -1;
        }
        return mailInstanceDao.clearNormal(uid);
    }

    /** 列出 mail 表里的模板 */
    public static List<Mail> templates() {
        return mailDao.queryMails();
    }

    private static JSONArray parseItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JSONArray();
        }
        try {
            Object parsed = JSON.parse(raw);
            if (parsed instanceof JSONArray) {
                return (JSONArray) parsed;
            }
            if (parsed instanceof JSONObject) {
                JSONArray inner = ((JSONObject) parsed).getJSONArray("items");
                return inner == null ? new JSONArray() : inner;
            }
        } catch (Exception ignored) {
            // 忽略，当作没有附件
        }
        return new JSONArray();
    }
}
