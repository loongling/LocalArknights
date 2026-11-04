package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class RogueRunHistoryDao {

    private static volatile boolean ready = false;
    private static volatile boolean disabled = false;

    private RogueRunHistoryDao() {
    }

    private static JSONObject cfg() {
        JSONObject env = new JSONObject(true);
        String host = System.getenv("DSH_DB_HOST");
        if (host != null && !host.isEmpty()) {
            env.put("host", host);
            env.put("port", System.getenv("DSH_DB_PORT") == null ? 3306 : Integer.parseInt(System.getenv("DSH_DB_PORT")));
            env.put("dbname", System.getenv("DSH_DB_NAME") == null ? "DSH" : System.getenv("DSH_DB_NAME"));
            env.put("user", System.getenv("DSH_DB_USER"));
            env.put("password", System.getenv("DSH_DB_PASSWORD"));
            return env;
        }
        java.io.File f = new java.io.File(System.getProperty("user.home"), ".dsh/rogue_db.json");
        if (!f.isFile()) {
            return null;
        }
        try {
            return JSONObject.parseObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static Connection connect() throws Exception {
        JSONObject c = cfg();
        if (c == null) {
            throw new IllegalStateException("未配置 DSH 存档库（环境变量或 ~/.dsh/rogue_db.json）");
        }
        String url = "jdbc:mysql://" + c.getString("host") + ":" + c.getIntValue("port") + "/" + c.getString("dbname")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
                + "&connectTimeout=4000&socketTimeout=8000";
        return DriverManager.getConnection(url, c.getString("user"), c.getString("password"));
    }

    private static synchronized void ensureTable(Connection conn) throws Exception {
        if (ready) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS `roguelike_run_history` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                    + "`uid` BIGINT NOT NULL,"
                    + "`theme` VARCHAR(32) NOT NULL,"
                    + "`mode` VARCHAR(32) DEFAULT NULL,"
                    + "`mode_grade` INT DEFAULT 0,"
                    + "`seed` VARCHAR(80) DEFAULT NULL,"
                    + "`success` TINYINT DEFAULT 0,"
                    + "`removed` TINYINT DEFAULT 0,"
                    + "`level` INT DEFAULT 0,"
                    + "`end_zone` VARCHAR(32) DEFAULT NULL,"
                    + "`cnt_zone` INT DEFAULT 0,"
                    + "`cnt_arrived` INT DEFAULT 0,"
                    + "`cnt_battle_normal` INT DEFAULT 0,"
                    + "`cnt_battle_elite` INT DEFAULT 0,"
                    + "`cnt_battle_boss` INT DEFAULT 0,"
                    + "`cnt_kill` INT DEFAULT 0,"
                    + "`cnt_perfect` INT DEFAULT 0,"
                    + "`cnt_shop_buy` INT DEFAULT 0,"
                    + "`cnt_recruit` INT DEFAULT 0,"
                    + "`cnt_upgrade` INT DEFAULT 0,"
                    + "`cnt_recruit_free` INT DEFAULT 0,"
                    + "`cnt_recruit_assist` INT DEFAULT 0,"
                    + "`cnt_recruit_npc` INT DEFAULT 0,"
                    + "`cnt_gain_relic` INT DEFAULT 0,"
                    + "`cnt_use_gold` INT DEFAULT 0,"
                    + "`cnt_save_gold` INT DEFAULT 0,"
                    + "`bp_from` INT DEFAULT 0,"
                    + "`bp_to` INT DEFAULT 0,"
                    + "`score_from` INT DEFAULT 0,"
                    + "`score_to` INT DEFAULT 0,"
                    + "`bonus` DOUBLE DEFAULT 0,"
                    + "`start_ts` BIGINT DEFAULT 0,"
                    + "`end_ts` BIGINT DEFAULT 0,"
                    + "`record_json` LONGTEXT,"
                    + "`created_at` BIGINT DEFAULT 0,"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_theme` (`theme`),"
                    + "KEY `idx_uid` (`uid`),"
                    + "KEY `idx_grade` (`mode_grade`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ready = true;
            ArknightsApplication.LOGGER.info("[rlv2] DSH.roguelike_run_history 表就绪");
        }
    }

    public static void save(long uid, JSONObject brief, JSONObject record,
                            int bpFrom, int bpTo, int scoreFrom, int scoreTo, double bonus) {
        if (disabled) {
            return;
        }
        try (Connection conn = connect()) {
            ensureTable(conn);
            String sql = "INSERT INTO `roguelike_run_history` ("
                    + "`uid`,`theme`,`mode`,`mode_grade`,`seed`,`success`,`removed`,`level`,`end_zone`,"
                    + "`cnt_zone`,`cnt_arrived`,`cnt_battle_normal`,`cnt_battle_elite`,`cnt_battle_boss`,"
                    + "`cnt_kill`,`cnt_perfect`,`cnt_shop_buy`,`cnt_recruit`,`cnt_upgrade`,"
                    + "`cnt_recruit_free`,`cnt_recruit_assist`,`cnt_recruit_npc`,"
                    + "`cnt_gain_relic`,`cnt_use_gold`,`cnt_save_gold`,"
                    + "`bp_from`,`bp_to`,`score_from`,`score_to`,`bonus`,`start_ts`,`end_ts`,`record_json`,`created_at`)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setLong(i++, uid);
                ps.setString(i++, brief == null ? "" : String.valueOf(brief.getString("theme")));
                ps.setString(i++, brief == null ? "" : String.valueOf(brief.getString("mode")));
                ps.setInt(i++, brief == null ? 0 : brief.getIntValue("modeGrade"));
                ps.setString(i++, brief == null ? "" : String.valueOf(brief.getString("seed")));
                ps.setInt(i++, brief == null ? 0 : brief.getIntValue("success"));
                ps.setInt(i++, "giveUpGame".equals(brief == null ? "" : brief.getString("removed")) ? 1 : 0);
                ps.setInt(i++, brief == null ? 0 : brief.getIntValue("level"));
                ps.setString(i++, brief == null ? "" : String.valueOf(brief.getString("endZoneId")));
                ps.setInt(i++, rec(record, "cntZone"));
                ps.setInt(i++, rec(record, "cntArrivedNode"));
                ps.setInt(i++, rec(record, "cntBattleNormal"));
                ps.setInt(i++, rec(record, "cntBattleElite"));
                ps.setInt(i++, rec(record, "cntBattleBoss"));
                ps.setInt(i++, rec(record, "cntKillEnemy"));
                ps.setInt(i++, rec(record, "cntPerfectBattle"));
                ps.setInt(i++, rec(record, "cntShopBuy"));
                ps.setInt(i++, rec(record, "cntRecruitChar"));
                ps.setInt(i++, rec(record, "cntUpgradeChar"));
                ps.setInt(i++, rec(record, "cntRecruitFree"));
                ps.setInt(i++, rec(record, "cntRecruitAssist"));
                ps.setInt(i++, rec(record, "cntRecruitNpc"));
                ps.setInt(i++, rec(record, "cntGainRelic"));
                ps.setInt(i++, rec(record, "cntUseGold"));
                ps.setInt(i++, rec(record, "cntSaveGold"));
                ps.setInt(i++, bpFrom);
                ps.setInt(i++, bpTo);
                ps.setInt(i++, scoreFrom);
                ps.setInt(i++, scoreTo);
                ps.setDouble(i++, bonus);
                ps.setLong(i++, brief == null ? 0 : brief.getLongValue("startTs"));
                ps.setLong(i++, brief == null ? 0 : brief.getLongValue("endTs"));
                ps.setString(i++, record == null ? "{}" : record.toJSONString());
                ps.setLong(i++, System.currentTimeMillis() / 1000L);
                ps.executeUpdate();
                ArknightsApplication.LOGGER.info("[rlv2] 已写入 DSH 结算历史：theme={} score=+{} bp=+{}",
                        brief == null ? "" : brief.getString("theme"), scoreTo - scoreFrom, bpTo - bpFrom);
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 写 DSH 结算历史失败（不影响结算）: {}", e.toString());
            if (e instanceof ClassNotFoundException || String.valueOf(e.getMessage()).contains("Access denied")) {
                disabled = true;
            }
        }
    }

    private static int rec(JSONObject record, String key) {
        if (record == null) {
            return 0;
        }
        if ("cntRecruitChar".equals(key)) {
            JSONArray troop = record.getJSONArray("troopChars");
            if (troop != null && troop.size() > 0) {
                return troop.size();
            }
        }
        return record.getIntValue(key);
    }
}
