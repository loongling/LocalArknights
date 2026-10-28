package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.List;
import java.util.Map;

public class mailInstanceDao {

    private static volatile boolean schemaReady = false;

    private static void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (mailInstanceDao.class) {
            if (schemaReady) {
                return;
            }
            ArknightsApplication.jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS mail_instance ("
                            + " id BIGINT NOT NULL AUTO_INCREMENT,"
                            + " uid BIGINT NOT NULL,"
                            + " mail_id INT NULL,"
                            + " survey_mail_id VARCHAR(64) NULL,"
                            + " def_id VARCHAR(128) NULL,"
                            + " `from` VARCHAR(64) NULL,"
                            + " subject VARCHAR(255) NULL,"
                            + " content TEXT NULL,"
                            + " create_at BIGINT NULL,"
                            + " expire_at BIGINT NULL,"
                            + " state INT NOT NULL DEFAULT 0,"
                            + " has_item INT NOT NULL DEFAULT 0,"
                            + " type INT NOT NULL DEFAULT 1,"
                            + " platform INT NOT NULL DEFAULT -1,"
                            + " style JSON NULL,"
                            + " items JSON NULL,"
                            + " removed TINYINT NOT NULL DEFAULT 0,"
                            + " PRIMARY KEY (id),"
                            + " KEY idx_mail_uid (uid),"
                            + " UNIQUE KEY uk_mail_uid_mail (uid, mail_id),"
                            + " UNIQUE KEY uk_mail_uid_survey (uid, survey_mail_id)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ArknightsApplication.jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS mail_survey ("
                            + " id INT NOT NULL AUTO_INCREMENT,"
                            + " survey_mail_id VARCHAR(64) NOT NULL,"
                            + " `from` VARCHAR(64) NULL,"
                            + " subject VARCHAR(255) NULL,"
                            + " content TEXT NULL,"
                            + " expire_days INT NOT NULL DEFAULT 2,"
                            + " style JSON NULL,"
                            + " enabled TINYINT NOT NULL DEFAULT 1,"
                            + " PRIMARY KEY (id),"
                            + " UNIQUE KEY uk_survey_id (survey_mail_id)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            schemaReady = true;
            seedSurveys();
        }
    }

    private static void seedSurveys() {
        try {
            Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mail_survey", Integer.class);
            if (n != null && n > 0) {
                return;
            }
            String path = System.getProperty("user.dir") + "/data/mail/survey_table.json";
            JSONObject table = com.hypergryph.arknights.core.file.IOTools.ReadJsonFile(path);
            JSONArray surveys = table == null ? null : table.getJSONArray("surveys");
            if (surveys == null || surveys.isEmpty()) {
                return;
            }
            for (int i = 0; i < surveys.size(); i++) {
                JSONObject def = surveys.getJSONObject(i);
                if (def == null || def.getString("surveyMailId") == null) {
                    continue;
                }
                ArknightsApplication.jdbcTemplate.update(
                        "INSERT INTO mail_survey (`survey_mail_id`,`from`,subject,content,expire_days,style,enabled)"
                                + " VALUES (?,?,?,?,?,?,1)",
                        def.getString("surveyMailId"),
                        def.getString("from"),
                        def.getString("subject"),
                        def.getString("content"),
                        def.getIntValue("expireDays") == 0 ? 2 : def.getIntValue("expireDays"),
                        toJsonString(def.getJSONObject("style")));
            }
            ArknightsApplication.LOGGER.info("[mail] 已从 survey_table.json 导入 {} 条调研邮件模板", surveys.size());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[mail] 导入调研邮件模板失败（不影响启动）: {}", e.toString());
        }
    }

    public static JSONArray listByUid(long uid) {
        ensureSchema();
        migrateLegacyJson(uid);
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(
                "SELECT * FROM mail_instance WHERE uid = ? ORDER BY id ASC", uid);
        JSONArray out = new JSONArray();
        for (Map<String, Object> row : rows) {
            out.add(toJson(uid, row));
        }
        return out;
    }

    public static int countUnreceived(long uid, long now) {
        ensureSchema();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_instance WHERE uid = ? AND removed = 0 AND state = 0"
                        + " AND has_item = 1 AND (expire_at <= 0 OR expire_at > ?)", Integer.class, uid, now);
        return n == null ? 0 : n;
    }

    public static int nextMailId(long uid) {
        ensureSchema();
        Integer max = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(mail_id), 750) FROM mail_instance WHERE uid = ?", Integer.class, uid);
        return (max == null ? 750 : max) + 1;
    }

    /** 启用的调研邮件模板 */
    public static JSONArray surveyTemplates() {
        ensureSchema();
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(
                "SELECT * FROM mail_survey WHERE enabled = 1 ORDER BY id ASC");
        JSONArray out = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject def = new JSONObject(true);
            def.put("surveyMailId", str(row.get("survey_mail_id")));
            def.put("from", str(row.get("from")) != null ? str(row.get("from")) : str(row.get("`from`")));
            def.put("subject", str(row.get("subject")));
            def.put("content", str(row.get("content")));
            def.put("expireDays", num(row.get("expire_days")));
            def.put("style", parseObject(str(row.get("style"))));
            out.add(def);
        }
        return out;
    }

    // ---------------------------------------------------------------- 写入

    public static int insert(long uid, JSONObject mail) {
        ensureSchema();
        Integer mailId = mail.get("mailId") == null || mail.getIntValue("mailId") <= 0 ? null : mail.getIntValue("mailId");
        String surveyId = mail.getString("surveyMailId");
        String sql = "INSERT INTO mail_instance"
                + " (uid, mail_id, survey_mail_id, def_id, `from`, subject, content, create_at, expire_at,"
                + "  state, has_item, type, platform, style, items, removed)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        return ArknightsApplication.jdbcTemplate.update(sql,
                uid,
                mailId,
                surveyId,
                mail.getString("defId"),
                mail.getString("from"),
                mail.getString("subject"),
                mail.getString("content"),
                mail.getLongValue("createAt"),
                mail.getLongValue("expireAt"),
                mail.getIntValue("state"),
                mail.getIntValue("hasItem"),
                mail.containsKey("type") ? mail.getIntValue("type") : 1,
                mail.containsKey("platform") ? mail.getIntValue("platform") : -1,
                toJsonString(mail.getJSONObject("style")),
                toJsonString(mail.getJSONArray("items")),
                mail.getBooleanValue("removed") ? 1 : 0);
    }

    public static int setState(long uid, int mailId, int state) {
        ensureSchema();
        return ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail_instance SET state = ? WHERE uid = ? AND mail_id = ?", state, uid, mailId);
    }

    public static int deleteNormal(long uid, int mailId) {
        ensureSchema();
        return ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail_instance SET removed = 1, state = 1 WHERE uid = ? AND mail_id = ? AND removed = 0",
                uid, mailId);
    }

    public static int deleteExpired(long uid, long now) {
        ensureSchema();
        return ArknightsApplication.jdbcTemplate.update(
                "DELETE FROM mail_instance WHERE uid = ? AND expire_at > 0 AND expire_at < ?", uid, now);
    }

    public static int tombstoneSurvey(long uid, String surveyMailId) {
        ensureSchema();
        return ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail_instance SET removed = 1, state = 1 WHERE uid = ? AND survey_mail_id = ?",
                uid, surveyMailId);
    }

    public static int resetSurvey(long uid, String surveyMailId) {
        ensureSchema();
        return ArknightsApplication.jdbcTemplate.update(
                "DELETE FROM mail_instance WHERE uid = ? AND survey_mail_id = ?", uid, surveyMailId);
    }

    public static int clearNormal(long uid) {
        ensureSchema();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_instance WHERE uid = ? AND mail_id IS NOT NULL AND removed = 0",
                Integer.class, uid);
        ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail_instance SET removed = 1, state = 1 WHERE uid = ? AND mail_id IS NOT NULL", uid);
        ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail_instance SET removed = 1, state = 1 WHERE uid = ? AND survey_mail_id IS NOT NULL", uid);
        return n == null ? 0 : n;
    }

    private static void migrateLegacyJson(long uid) {
        Integer exist = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_instance WHERE uid = ?", Integer.class, uid);
        if (exist != null && exist > 0) {
            return;
        }
        String raw = null;
        try {
            List<String> rows = ArknightsApplication.jdbcTemplate.queryForList(
                    "SELECT `mails` FROM account WHERE uid = ?", String.class, uid);
            raw = rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[mail] 读 account.mails 失败（跳过迁移）: {}", e.toString());
            return;
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        JSONArray legacy;
        try {
            legacy = JSON.parseArray(raw);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[mail] account.mails 不是数组，跳过迁移: {}", e.toString());
            return;
        }
        int moved = 0;
        for (int i = 0; i < legacy.size(); i++) {
            JSONObject m = legacy.getJSONObject(i);
            if (m == null) {
                continue;
            }
            try {
                insert(uid, m);
                moved++;
            } catch (Exception e) {
                ArknightsApplication.LOGGER.warn("[mail] 迁移邮件失败 uid={} mail={}: {}", uid, m, e.toString());
            }
        }
        if (moved > 0) {
            ArknightsApplication.LOGGER.info("[mail] 已把 uid={} 的 {} 封旧邮件从 account.mails 迁到 mail_instance", uid, moved);
        }
    }

    private static JSONObject toJson(long uid, Map<String, Object> row) {
        boolean survey = str(row.get("survey_mail_id")) != null;
        JSONObject m = new JSONObject(true);
        if (survey) {
            m.put("surveyMailId", str(row.get("survey_mail_id")));
        } else {
            m.put("mailId", num(row.get("mail_id")));
        }
        if (str(row.get("def_id")) != null) {
            m.put("defId", str(row.get("def_id")));
        }
        m.put("from", str(row.get("from")));
        m.put("subject", str(row.get("subject")));
        m.put("content", str(row.get("content")));
        m.put("createAt", lng(row.get("create_at")));
        m.put("expireAt", lng(row.get("expire_at")));
        m.put("state", num(row.get("state")));
        m.put("type", num(row.get("type")));
        m.put("platform", num(row.get("platform")));
        m.put("uid", String.valueOf(uid));

        JSONObject style = parseObject(str(row.get("style")));
        if (style == null) {
            style = new JSONObject(true);
            style.put("route", survey ? 2 : 0);
            style.put("banner", survey ? "survey" : "");
        }
        m.put("style", style);

        if (survey) {
            m.put("hasItem", 0);
        } else {
            m.put("hasItem", num(row.get("has_item")));
            JSONArray items = parseArray(str(row.get("items")));
            m.put("items", items == null ? new JSONArray() : items);
            m.put("collectionId", null);
        }
        if (num(row.get("removed")) == 1) {
            m.put("removed", true);
        }
        return m;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int num(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private static long lng(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static String toJsonString(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }

    private static JSONObject parseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray parseArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.parseArray(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
