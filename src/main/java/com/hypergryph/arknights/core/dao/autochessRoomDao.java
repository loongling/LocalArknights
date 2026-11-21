package com.hypergryph.arknights.core.dao;

import com.hypergryph.arknights.ArknightsApplication;

import java.util.List;
import java.util.Map;

/**
 * 卫戍协议房间服数据表。
 *
 * token 是「按玩家」签发的（createTeam 给房主、joinTeam 给加入者，每人一个），
 * 因此拆成两张表：
 *   autochessRoom        —— 房间元数据（teamId 唯一）
 *   autochessRoomPlayer  —— 房间成员（teamId + uid 唯一），token 存在这里
 */
public class autochessRoomDao {

    public autochessRoomDao() {
    }

    public static int insertTable() {
        String room = "CREATE TABLE IF NOT EXISTS `autochessRoom` (\n"
                + "  `teamId`   VARCHAR(32) NOT NULL COMMENT '房间号',\n"
                + "  `ownerUid` BIGINT      NOT NULL COMMENT '房主 uid',\n"
                + "  `modeId`   VARCHAR(64) NULL     COMMENT 'mode_multi_*',\n"
                + "  `stageId`  VARCHAR(64) NULL     COMMENT 'act3autochess_mNN',\n"
                + "  `bossId`   VARCHAR(64) NULL     COMMENT 'Boss',\n"
                + "  `createTs` BIGINT      NOT NULL,\n"
                + "  `expireTs` BIGINT      NOT NULL,\n"
                + "  `state`    TINYINT     NOT NULL DEFAULT 0 COMMENT '0=等待 1=开局 2=结束',\n"
                + "  PRIMARY KEY (`teamId`),\n"
                + "  KEY `idx_expire` (`expireTs`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;";
        ArknightsApplication.jdbcTemplate.update(room);

        String player = "CREATE TABLE IF NOT EXISTS `autochessRoomPlayer` (\n"
                + "  `teamId`    VARCHAR(32)  NOT NULL COMMENT '房间号',\n"
                + "  `uid`       BIGINT       NOT NULL COMMENT '玩家 uid',\n"
                + "  `token`     VARCHAR(128) NOT NULL COMMENT '该玩家的入场 token（64 hex）',\n"
                + "  `seatIndex` TINYINT      NOT NULL COMMENT '座位号 1~4（op=300 的 index）',\n"
                + "  `isOwner`   TINYINT      NOT NULL DEFAULT 0,\n"
                + "  `ready`     TINYINT      NOT NULL DEFAULT 0,\n"
                + "  `bandId`    VARCHAR(64)  NULL     COMMENT '所选盟约',\n"
                + "  `joinTs`    BIGINT       NOT NULL,\n"
                + "  PRIMARY KEY (`teamId`,`uid`),\n"
                + "  KEY `idx_token` (`token`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;";
        return ArknightsApplication.jdbcTemplate.update(player);
    }

    /** 建房间（房主同时作为第一个成员写入 token） */
    public static int createRoom(String teamId, long ownerUid, String ownerToken, String modeId, int ttl) {
        long now = System.currentTimeMillis() / 1000L;
        String room = "INSERT INTO `autochessRoom` (`teamId`,`ownerUid`,`modeId`,`createTs`,`expireTs`,`state`) "
                + "VALUES (?,?,?,?,?,0) "
                + "ON DUPLICATE KEY UPDATE `ownerUid`=VALUES(`ownerUid`),`modeId`=VALUES(`modeId`),"
                + "`createTs`=VALUES(`createTs`),`expireTs`=VALUES(`expireTs`),`state`=0";
        ArknightsApplication.jdbcTemplate.update(room, teamId, ownerUid, modeId, now, now + ttl);
        return joinRoom(teamId, ownerUid, ownerToken, true);
    }

    /** 加入房间：分配座位号并写入该玩家自己的 token */
    public static int joinRoom(String teamId, long uid, String token, boolean isOwner) {
        long now = System.currentTimeMillis() / 1000L;
        Integer seat = queryUsedSeat(teamId);
        int seatIndex = isOwner ? 1 : (seat == null ? 1 : seat + 1);
        String sql = "INSERT INTO `autochessRoomPlayer` (`teamId`,`uid`,`token`,`seatIndex`,`isOwner`,`joinTs`) "
                + "VALUES (?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE `token`=VALUES(`token`),`joinTs`=VALUES(`joinTs`)";
        return ArknightsApplication.jdbcTemplate.update(sql, teamId, uid, token, seatIndex, isOwner ? 1 : 0, now);
    }

    private static Integer queryUsedSeat(String teamId) {
        String sql = "SELECT MAX(`seatIndex`) FROM `autochessRoomPlayer` WHERE `teamId` = ?";
        List<Integer> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, Integer.class, teamId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查房间元数据，过期返回 null */
    public static Map<String, Object> queryRoom(String teamId) {
        String sql = "SELECT * FROM `autochessRoom` WHERE `teamId` = ? LIMIT 1";
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, teamId);
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

    /** 查某玩家在该房间的成员记录 */
    public static Map<String, Object> queryPlayer(String teamId, long uid) {
        String sql = "SELECT * FROM `autochessRoomPlayer` WHERE `teamId` = ? AND `uid` = ? LIMIT 1";
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, teamId, uid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<Map<String, Object>> queryPlayers(String teamId) {
        String sql = "SELECT * FROM `autochessRoomPlayer` WHERE `teamId` = ? ORDER BY `seatIndex`";
        return ArknightsApplication.jdbcTemplate.queryForList(sql, teamId);
    }

    /** 鉴权：该 uid 在房间内且 token 与库里一致 */
    public static boolean verifyToken(String teamId, long uid, String token) {
        if (token == null || queryRoom(teamId) == null) {
            return false;
        }
        Map<String, Object> p = queryPlayer(teamId, uid);
        if (p == null) {
            return false;
        }
        Object dbToken = p.get("token");
        return dbToken != null && token.equals(dbToken.toString());
    }

    public static int setReady(String teamId, long uid, boolean ready) {
        String sql = "UPDATE `autochessRoomPlayer` SET `ready` = ? WHERE `teamId` = ? AND `uid` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, ready ? 1 : 0, teamId, uid);
    }

    public static int setBand(String teamId, long uid, String bandId) {
        String sql = "UPDATE `autochessRoomPlayer` SET `bandId` = ? WHERE `teamId` = ? AND `uid` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, bandId, teamId, uid);
    }

    public static int updateMode(String teamId, String modeId) {
        String sql = "UPDATE `autochessRoom` SET `modeId` = ? WHERE `teamId` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, modeId, teamId);
    }

    public static int updateState(String teamId, int state) {
        String sql = "UPDATE `autochessRoom` SET `state` = ? WHERE `teamId` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, state, teamId);
    }

    public static int leaveRoom(String teamId, long uid) {
        String sql = "DELETE FROM `autochessRoomPlayer` WHERE `teamId` = ? AND `uid` = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, teamId, uid);
    }

    public static int deleteRoom(String teamId) {
        ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessRoomPlayer` WHERE `teamId` = ?", teamId);
        return ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessRoom` WHERE `teamId` = ?", teamId);
    }

    /** 清理过期房间（含成员） */
    public static int deleteExpired() {
        long now = System.currentTimeMillis() / 1000L;
        ArknightsApplication.jdbcTemplate.update(
                "DELETE p FROM `autochessRoomPlayer` p JOIN `autochessRoom` r ON p.`teamId` = r.`teamId` WHERE r.`expireTs` < ?", now);
        return ArknightsApplication.jdbcTemplate.update("DELETE FROM `autochessRoom` WHERE `expireTs` < ?", now);
    }
}
