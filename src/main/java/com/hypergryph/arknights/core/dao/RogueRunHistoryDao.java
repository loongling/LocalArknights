package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 集成战略结算历史（写入独立的 DSH 库，供 bp 得分公式回归分析）。
 *
 * 连接信息来自 config.json → "dsh": {host, port, dbname, user, password}。
 * 写入失败只记日志，绝不影响正常结算。
 *
 * 表结构：
 * CREATE TABLE roguelike_run_history (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   uid BIGINT, theme VARCHAR(32), mode VARCHAR(32), mode_grade INT, seed VARCHAR(80),
 *   success TINYINT, removed TINYINT, level INT, end_zone VARCHAR(32),
 *   cnt_zone INT, cnt_arrived INT, cnt_battle_normal INT, cnt_battle_elite INT, cnt_battle_boss INT,
 *   cnt_kill INT, cnt_perfect INT, cnt_shop_buy INT, cnt_recruit INT, cnt_upgrade INT,
 *   cnt_recruit_free INT, cnt_recruit_assist INT, cnt_recruit_npc INT,
 *   cnt_gain_relic INT, cnt_use_gold INT, cnt_save_gold INT,
 *   bp_from INT, bp_to INT, score_from INT, score_to INT, bonus DOUBLE,
 *   start_ts BIGINT, end_ts BIGINT, record_json LONGTEXT, created_at BIGINT
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */
public class RogueRunHistoryDao {

    private static volatile boolean ready = false;
    private static volatile boolean disabled = false;

    private RogueRunHistoryDao() {
    }

    private static JSONObject cfg() {
        JSONObject server = ArknightsApplication.serverConfig;
        return server == null ? null : server.getJSONObject("dsh");
    }

    private static Connection connect() throws Exception {
        JSONObject c = cfg();
        if (c == null) {
            throw new IllegalStateException("config.json 缺少 dsh 段");
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

    /**
     * 记录一局结算。
     *
     * @param uid    账号
     * @param brief  结算 brief
     * @param record 结算 record
     * @param bpFrom/bpTo/scoreFrom/scoreTo 局外 bp / score 前后值
     */
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
