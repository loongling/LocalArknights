package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 集成战略（rlv2）单局数据表。
 *
 * 目的：把一局的关键状态（seed / 生命 / 源石锭 / 楼层 / 坐标 …）落到独立表，
 * 便于查询、统计，以及服务端重启/存档损坏时恢复。
 *
 * 表结构（启动时自动建表，MySQL / MariaDB）：
 * <pre>
 * CREATE TABLE roguelike_run (
 *   uid BIGINT NOT NULL,
 *   theme VARCHAR(32) NOT NULL,
 *   run_id VARCHAR(80) NOT NULL,
 *   seed VARCHAR(80),
 *   mode VARCHAR(32),
 *   mode_grade INT DEFAULT 0,
 *   state VARCHAR(32),
 *   hp INT DEFAULT 0, hp_max INT DEFAULT 0,
 *   gold INT DEFAULT 0, shield INT DEFAULT 0,
 *   hope INT DEFAULT 0, capacity INT DEFAULT 0,
 *   level INT DEFAULT 1, exp INT DEFAULT 0,
 *   zone INT DEFAULT 0, pos_x INT DEFAULT -1, pos_y INT DEFAULT -1,
 *   start_ts BIGINT DEFAULT 0, update_ts BIGINT DEFAULT 0,
 *   cleared_boss TINYINT DEFAULT 0,
 *   extra LONGTEXT,
 *   PRIMARY KEY (uid, run_id),
 *   KEY idx_seed (seed),
 *   KEY idx_update (update_ts)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 * </pre>
 */
public class RogueRunDao {

    private static volatile boolean tableReady = false;

    private RogueRunDao() {
    }

    /** 建表（幂等） */
    public static synchronized void ensureTable() {
        if (tableReady) {
            return;
        }
        try {
            ArknightsApplication.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `roguelike_run` ("
                    + "`uid` BIGINT NOT NULL,"
                    + "`theme` VARCHAR(32) NOT NULL,"
                    + "`run_id` VARCHAR(80) NOT NULL,"
                    + "`seed` VARCHAR(80) DEFAULT NULL,"
                    + "`mode` VARCHAR(32) DEFAULT NULL,"
                    + "`mode_grade` INT DEFAULT 0,"
                    + "`state` VARCHAR(32) DEFAULT NULL,"
                    + "`hp` INT DEFAULT 0,"
                    + "`hp_max` INT DEFAULT 0,"
                    + "`gold` INT DEFAULT 0,"
                    + "`shield` INT DEFAULT 0,"
                    + "`hope` INT DEFAULT 0,"
                    + "`capacity` INT DEFAULT 0,"
                    + "`level` INT DEFAULT 1,"
                    + "`exp` INT DEFAULT 0,"
                    + "`zone` INT DEFAULT 0,"
                    + "`pos_x` INT DEFAULT -1,"
                    + "`pos_y` INT DEFAULT -1,"
                    + "`start_ts` BIGINT DEFAULT 0,"
                    + "`update_ts` BIGINT DEFAULT 0,"
                    + "`cleared_boss` TINYINT DEFAULT 0,"
                    + "`extra` LONGTEXT,"
                    + "PRIMARY KEY (`uid`,`run_id`),"
                    + "KEY `idx_seed` (`seed`),"
                    + "KEY `idx_update` (`update_ts`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            tableReady = true;
            ArknightsApplication.LOGGER.info("[rlv2] roguelike_run 表就绪");
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 建表 roguelike_run 失败（将跳过落库）: {}", e.toString());
        }
    }

    /** 把一局快照写入/更新（存在则更新） */
    public static void save(long uid, JSONObject cur) {
        if (cur == null) {
            return;
        }
        try {
            ensureTable();
            if (!tableReady) {
                return;
            }
            JSONObject game = cur.getJSONObject("game");
            JSONObject player = cur.getJSONObject("player");
            if (game == null || player == null) {
                return;
            }
            String theme = game.getString("theme");
            if (theme == null || "NONE".equals(game.getString("mode"))) {
                // 没有进行中的探索：删掉旧的当前局记录
                clear(uid);
                return;
            }
            JSONObject prop = player.getJSONObject("property");
            JSONObject cursor = player.getJSONObject("cursor");
            JSONObject pos = cursor == null ? null : cursor.getJSONObject("position");
            JSONObject rec = cur.getJSONObject("record");
            long now = System.currentTimeMillis() / 1000L;
            long startTs = game.getLongValue("start");
            String runId = theme + "_" + startTs;
            String seed = game.getString("seed");

            String sql = "INSERT INTO `roguelike_run` "
                    + "(`uid`,`theme`,`run_id`,`seed`,`mode`,`mode_grade`,`state`,`hp`,`hp_max`,"
                    + "`gold`,`shield`,`hope`,`capacity`,`level`,`exp`,`zone`,`pos_x`,`pos_y`,"
                    + "`start_ts`,`update_ts`,`cleared_boss`,`extra`) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "`seed`=VALUES(`seed`),`mode`=VALUES(`mode`),`mode_grade`=VALUES(`mode_grade`),"
                    + "`state`=VALUES(`state`),`hp`=VALUES(`hp`),`hp_max`=VALUES(`hp_max`),"
                    + "`gold`=VALUES(`gold`),`shield`=VALUES(`shield`),`hope`=VALUES(`hope`),"
                    + "`capacity`=VALUES(`capacity`),`level`=VALUES(`level`),`exp`=VALUES(`exp`),"
                    + "`zone`=VALUES(`zone`),`pos_x`=VALUES(`pos_x`),`pos_y`=VALUES(`pos_y`),"
                    + "`update_ts`=VALUES(`update_ts`),`cleared_boss`=VALUES(`cleared_boss`),"
                    + "`extra`=VALUES(`extra`)";

            JSONObject extra = new JSONObject(true);
            extra.put("troop", cur.getJSONObject("troop") == null ? 0
                    : cur.getJSONObject("troop").getJSONObject("chars") == null ? 0
                    : cur.getJSONObject("troop").getJSONObject("chars").size());
            extra.put("relic", cur.getJSONObject("inventory") == null ? 0
                    : cur.getJSONObject("inventory").getJSONObject("relic") == null ? 0
                    : cur.getJSONObject("inventory").getJSONObject("relic").size());
            extra.put("pending", player.getJSONArray("pending") == null ? 0 : player.getJSONArray("pending").size());
            if (rec != null) {
                extra.put("acc", rec.getJSONObject("acc"));
                extra.put("lastSpendGold", rec.getIntValue("lastSpendGold"));
            }

            ArknightsApplication.jdbcTemplate.update(sql,
                    uid,
                    theme,
                    runId,
                    seed,
                    game.getString("mode"),
                    game.getIntValue("modeGrade"),
                    player.getString("state"),
                    prop.getJSONObject("hp").getIntValue("current"),
                    prop.getJSONObject("hp").getIntValue("max"),
                    prop.getIntValue("gold"),
                    prop.getIntValue("shield"),
                    prop.getJSONObject("population").getIntValue("max"),
                    prop.getIntValue("capacity"),
                    prop.getIntValue("level"),
                    prop.getIntValue("exp"),
                    cursor == null ? 0 : cursor.getIntValue("zone"),
                    pos == null ? -1 : pos.getIntValue("x"),
                    pos == null ? -1 : pos.getIntValue("y"),
                    startTs,
                    now,
                    (rec != null && rec.getBooleanValue("clearedBoss")) ? 1 : 0,
                    extra.toJSONString());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 写入 roguelike_run 失败: {}", e.toString());
        }
    }

    /** 结束/放弃时删除当前局记录 */
    public static void clear(long uid) {
        try {
            ensureTable();
            if (!tableReady) {
                return;
            }
            ArknightsApplication.jdbcTemplate.update("DELETE FROM `roguelike_run` WHERE `uid` = ?", uid);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 清理 roguelike_run 失败: {}", e.toString());
        }
    }

    /** 读回某账号正在进行的一局（服务重启/存档丢失时用于恢复） */
    public static JSONObject load(long uid) {
        try {
            ensureTable();
            if (!tableReady) {
                return null;
            }
            List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(
                    "SELECT * FROM `roguelike_run` WHERE `uid` = ? ORDER BY `update_ts` DESC LIMIT 1", uid);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            JSONObject out = new JSONObject(true);
            for (Map.Entry<String, Object> e : row.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
            return out;
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 读取 roguelike_run 失败: {}", e.toString());
            return null;
        }
    }

    /** 按 seed 查历史（用于统计/复盘） */
    public static List<JSONObject> findBySeed(String seed) {
        List<JSONObject> out = new ArrayList<>();
        try {
            ensureTable();
            if (!tableReady) {
                return out;
            }
            List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(
                    "SELECT * FROM `roguelike_run` WHERE `seed` = ? ORDER BY `update_ts` DESC", seed);
            for (Map<String, Object> row : rows) {
                JSONObject one = new JSONObject(true);
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    one.put(e.getKey(), e.getValue());
                }
                out.add(one);
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[rlv2] 按 seed 查询失败: {}", e.toString());
        }
        return out;
    }

}
