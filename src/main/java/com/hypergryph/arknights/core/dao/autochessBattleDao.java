package com.hypergryph.arknights.core.dao;

import com.hypergryph.arknights.ArknightsApplication;

import java.util.List;
import java.util.Map;

/**
 * 卫戍协议对局服数据表。
 *
 * 对局 token 同样是「按玩家」签发（getSceneInfo 每人各调一次，
 * 格式为 32 字节 hex + "-" + 签发时间戳），因此拆成：
 *   autochessBattle        —— 对局元数据（sceneId 唯一）
 *   autochessBattlePlayer  —— 参战玩家（sceneId + uid 唯一），token 存在这里
 */
public class autochessBattleDao {

    public autochessBattleDao() {
    }

    public static int insertTable() {
        String battle = "CREATE TABLE IF NOT EXISTS `autochessBattle` (\n"
                + "  `sceneId`   VARCHAR(32) NOT NULL COMMENT '对局号',\n"
                + "  `teamId`    VARCHAR(32) NULL     COMMENT '来源房间号',\n"
                + "  `modeId`    VARCHAR(64) NULL     COMMENT 'mode_multi_* / mode_training_1',\n"
                + "  `stageId`   VARCHAR(64) NULL     COMMENT 'act3autochess_mNN',\n"
                + "  `bossId`    VARCHAR(64) NULL     COMMENT 'Boss',\n"
                + "  `stageSeed` INT         NULL     COMMENT '地图种子（决定抽哪张图）',\n"
                + "  `mapId`     VARCHAR(64) NULL     COMMENT '具体战场 level_act3autochess_01~07',\n"
                + "  `round`     INT         NOT NULL DEFAULT 0 COMMENT '当前波次',\n"
                + "  `createTs`  BIGINT      NOT NULL,\n"
                + "  `expireTs`  BIGINT      NOT NULL,\n"
                + "  `endTs`     BIGINT      NULL,\n"
                + "  `state`     TINYINT     NOT NULL DEFAULT 0 COMMENT '0=等待 1=进行 2=结算',\n"
                + "  `result`    TEXT        NULL     COMMENT '结算摘要 JSON',\n"
                + "  PRIMARY KEY (`sceneId`),\n"
                + "  KEY `idx_team` (`teamId`),\n"
                + "  KEY `idx_expire` (`expireTs`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;";
        ArknightsApplication.jdbcTemplate.update(battle);

        String player = "CREATE TABLE IF NOT EXISTS `autochessBattlePlayer` (\n"
                + "  `sceneId`   VARCHAR(32)  NOT NULL,\n"
                + "  `uid`       BIGINT       NOT NULL,\n"
                + "  `token`     VARCHAR(128) NOT NULL COMMENT '该玩家的对局 token（64hex-时间戳）',\n"
                + "  `uidIndex`  TINYINT      NOT NULL COMMENT '对局内序号 1~4',\n"
                + "  `hp`        INT          NOT NULL DEFAULT 0 COMMENT '目标生命值',\n"
                + "  `connect`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=离线 1=在线',\n"
                + "  `joinTs`    BIGINT       NOT NULL,\n"
                + "  PRIMARY KEY (`sceneId`,`uid`),\n"
                + "  KEY `idx_token` (`token`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;";
        return ArknightsApplication.jdbcTemplate.update(player);
    }

    /** 建对局 */
    public static int createBattle(String sceneId, String teamId, String modeId, String stageId,
                                   String mapId, String bossId, int stageSeed, int ttl) {
        long now = System.currentTimeMillis() / 1000L;
        String sql = "INSERT INTO `autochessBattle` "
                + "(`sceneId`,`teamId`,`modeId`,`stageId`,`mapId`,`bossId`,`stageSeed`,`round`,`createTs`,`expireTs`,`state`) "
                + "VALUES (?,?,?,?,?,?,?,0,?,?,0) "
                + "ON DUPLICATE KEY UPDATE `teamId`=VALUES(`teamId`),`modeId`=VALUES(`modeId`),"
                + "`stageId`=VALUES(`stageId`),`mapId`=VALUES(`mapId`),`bossId`=VALUES(`bossId`),"
                + "`stageSeed`=VALUES(`stageSeed`),`createTs`=VALUES(`createTs`),"
                + "`expireTs`=VALUES(`expireTs`),`state`=0";
        return ArknightsApplication.jdbcTemplate.update(sql, sceneId, teamId, modeId, stageId, mapId,
                bossId, stageSeed, now, now + ttl);
    }

    /** 单独设置地图（重开/换图时用） */
    public static int setMap(String sceneId, String mapId, int stageSeed) {
        String sql = "UPDATE `autochessBattle` SET `mapId` = ?, `stageSeed` = ? WHERE `sceneId` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, mapId, stageSeed, sceneId);
    }

    /**
     * 按种子从地图池挑一张。
     * 普通/高难：01~07；训练：tr01~tr04；逃脱：escaped_multi/single。
     */
    public static String pickMapId(String modeId, String stageId, int stageSeed) {
        String[] pool;
        String mode = modeId == null ? "" : modeId;
        String stage = stageId == null ? "" : stageId;
        if (mode.contains("training") || stage.contains("_tr")) {
            // 教学模式（新手）：level_act3autochess_tr01~tr04
            pool = new String[]{"act3autochess_tr01", "act3autochess_tr02",
                    "act3autochess_tr03", "act3autochess_tr04"};
        } else if (mode.contains("escaped") || stage.contains("escaped")) {
            pool = new String[]{"act3autochess_escaped_multi", "act3autochess_escaped_single"};
        } else {
            // 普通/险境/绝境/终极：01~07 随机一张
            pool = new String[]{"act3autochess_01", "act3autochess_02", "act3autochess_03",
                    "act3autochess_04", "act3autochess_05", "act3autochess_06", "act3autochess_07"};
        }
        return pool[Math.floorMod(stageSeed, pool.length)];
    }

    /** 教学模式：act3 的 tr01~tr04 没有 stage 注册，stageId 固定用 mode_training_1 */
    public static String trainingStageId() {
        return "mode_training_1";
    }

    /** 登记某玩家的对局 token（每人独立，格式 64hex-时间戳） */
    public static int joinBattle(String sceneId, long uid, String token) {
        long now = System.currentTimeMillis() / 1000L;
        Integer max = usedIndex(sceneId);
        int uidIndex = max == null ? 1 : max + 1;
        String sql = "INSERT INTO `autochessBattlePlayer` (`sceneId`,`uid`,`token`,`uidIndex`,`joinTs`) "
                + "VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE `token`=VALUES(`token`),`joinTs`=VALUES(`joinTs`)";
        return ArknightsApplication.jdbcTemplate.update(sql, sceneId, uid, token, uidIndex, now);
    }

    private static Integer usedIndex(String sceneId) {
        String sql = "SELECT MAX(`uidIndex`) FROM `autochessBattlePlayer` WHERE `sceneId` = ?";
        List<Integer> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, Integer.class, sceneId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static Map<String, Object> queryBattle(String sceneId) {
        String sql = "SELECT * FROM `autochessBattle` WHERE `sceneId` = ? LIMIT 1";
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, sceneId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Object expire = row.get("expireTs");
        if (expire != null && ((Number) expire).longValue() < System.currentTimeMillis() / 1000L) {
            return null;
        }
        return row;
    }

    public static Map<String, Object> queryByTeam(String teamId) {
        String sql = "SELECT * FROM `autochessBattle` WHERE `teamId` = ? ORDER BY `createTs` DESC LIMIT 1";
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, teamId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static Map<String, Object> queryPlayer(String sceneId, long uid) {
        String sql = "SELECT * FROM `autochessBattlePlayer` WHERE `sceneId` = ? AND `uid` = ? LIMIT 1";
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, sceneId, uid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<Map<String, Object>> queryPlayers(String sceneId) {
        String sql = "SELECT * FROM `autochessBattlePlayer` WHERE `sceneId` = ? ORDER BY `uidIndex`";
        return ArknightsApplication.jdbcTemplate.queryForList(sql, sceneId);
    }

    /** 鉴权：该玩家 token 一致且未过期 */
    public static boolean verifyToken(String sceneId, long uid, String token) {
        if (token == null || queryBattle(sceneId) == null) {
            return false;
        }
        Map<String, Object> p = queryPlayer(sceneId, uid);
        if (p == null) {
            return false;
        }
        Object dbToken = p.get("token");
        if (dbToken == null || !token.equals(dbToken.toString())) {
            return false;
        }
        int dash = token.lastIndexOf('-');
        if (dash > 0 && dash + 1 < token.length()) {
            try {
                long issued = Long.parseLong(token.substring(dash + 1));
                Object expire = queryBattle(sceneId).get("expireTs");
                if (expire != null && issued > ((Number) expire).longValue()) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }

    public static int setConnect(String sceneId, long uid, boolean online) {
        String sql = "UPDATE `autochessBattlePlayer` SET `connect` = ? WHERE `sceneId` = ? AND `uid` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, online ? 1 : 0, sceneId, uid);
    }

    public static int setHp(String sceneId, long uid, int hp) {
        String sql = "UPDATE `autochessBattlePlayer` SET `hp` = ? WHERE `sceneId` = ? AND `uid` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, hp, sceneId, uid);
    }

    public static int updateRound(String sceneId, int round) {
        String sql = "UPDATE `autochessBattle` SET `round` = ?, `state` = 1 WHERE `sceneId` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, round, sceneId);
    }

    public static int finishBattle(String sceneId, String result) {
        long now = System.currentTimeMillis() / 1000L;
        String sql = "UPDATE `autochessBattle` SET `state` = 2, `endTs` = ?, `result` = ? WHERE `sceneId` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, now, result, sceneId);
    }

    public static int deleteBattle(String sceneId) {
        ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessBattlePlayer` WHERE `sceneId` = ?", sceneId);
        return ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessBattle` WHERE `sceneId` = ?", sceneId);
    }

    public static int deleteExpired() {
        long now = System.currentTimeMillis() / 1000L;
        ArknightsApplication.jdbcTemplate.update(
                "DELETE p FROM `autochessBattlePlayer` p JOIN `autochessBattle` b ON p.`sceneId` = b.`sceneId` WHERE b.`expireTs` < ?", now);
        return ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessBattle` WHERE `expireTs` < ?", now);
    }
}
