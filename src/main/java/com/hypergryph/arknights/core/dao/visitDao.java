package com.hypergryph.arknights.core.dao;

import com.hypergryph.arknights.ArknightsApplication;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class visitDao {
    private static volatile boolean tableReady = false;

    public static void ensureTable() {
        if (tableReady) {
            return;
        }
        synchronized (visitDao.class) {
            if (tableReady) {
                return;
            }
            ArknightsApplication.jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS visitor_info ("
                            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                            + "host_uid BIGINT NOT NULL,"
                            + "visitor_uid BIGINT NOT NULL,"
                            + "nick VARCHAR(64) DEFAULT '',"
                            + "nick_num VARCHAR(16) DEFAULT '',"
                            + "level INT DEFAULT 1,"
                            + "avatar VARCHAR(64) DEFAULT '',"
                            + "ts BIGINT NOT NULL,"
                            + "claimed TINYINT DEFAULT 0,"
                            + "KEY idx_host (host_uid, ts)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            tableReady = true;
            ArknightsApplication.LOGGER.info("[visit] visitor_info 表已就绪");
        }
    }

    public static long weekStartTs(long now) {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        java.time.ZonedDateTime t = java.time.Instant.ofEpochSecond(now).atZone(zone);
        java.time.ZonedDateTime reset = t.withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (t.isBefore(reset)) {
            reset = reset.minusDays(1);
        }
        // 回退到本周一
        int dow = reset.getDayOfWeek().getValue();   // 1=周一 … 7=周日
        reset = reset.minusDays(dow - 1L);
        return reset.toEpochSecond();
    }

    public static int purgeOldWeeks() {
        ensureTable();
        dedupe();
        long start = weekStartTs(ArknightsApplication.getTimestamp());
        return ArknightsApplication.jdbcTemplate.update(
                "DELETE FROM visitor_info WHERE ts < ?", start);
    }

    public static void addVisit(long hostUid, long visitorUid, String nick, String nickNum,
                                int level, String avatar, long ts) {
        ensureTable();
        ArknightsApplication.jdbcTemplate.update(
                "INSERT INTO visitor_info (host_uid, visitor_uid, nick, nick_num, level, avatar, ts, claimed) "
                        + "VALUES (?,?,?,?,?,?,?,0)",
                hostUid, visitorUid, nick == null ? "" : nick, nickNum == null ? "" : nickNum,
                level, avatar == null ? "" : avatar, ts);
    }

    public static boolean visitedToday(long hostUid, long visitorUid, long dayStartTs) {
        ensureTable();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visitor_info WHERE host_uid = ? AND visitor_uid = ? AND ts >= ?",
                Integer.class, hostUid, visitorUid, dayStartTs);
        return n != null && n > 0;
    }

    public static void touchToday(long hostUid, long visitorUid, long dayStartTs, long ts) {
        ensureTable();
        ArknightsApplication.jdbcTemplate.update(
                "UPDATE visitor_info SET ts = ? WHERE host_uid = ? AND visitor_uid = ? AND ts >= ?",
                ts, hostUid, visitorUid, dayStartTs);
    }

    public static List<Map<String, Object>> listVisits(long hostUid, long sinceTs) {
        ensureTable();
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> rows = ArknightsApplication.jdbcTemplate.queryForList(
                "SELECT visitor_uid, nick, nick_num, level, avatar, ts, claimed FROM visitor_info "
                        + "WHERE host_uid = ? AND ts >= ? ORDER BY ts DESC LIMIT 200",
                hostUid, sinceTs);
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", String.valueOf(r.get("visitor_uid")));
            m.put("nickName", r.get("nick"));
            m.put("nickNumber", r.get("nick_num"));
            m.put("level", r.get("level"));
            m.put("avatar", r.get("avatar"));
            m.put("ts", r.get("ts"));
            m.put("claimed", ((Number) r.get("claimed")).intValue());
            out.add(m);
        }
        return out;
    }

    public static int countUnclaimed(long hostUid) {
        ensureTable();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visitor_info WHERE host_uid = ? AND claimed = 0", Integer.class, hostUid);
        return n == null ? 0 : n;
    }

    public static int countClaimedSince(long hostUid, long sinceTs) {
        ensureTable();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visitor_info WHERE host_uid = ? AND claimed = 1 AND ts >= ?",
                Integer.class, hostUid, sinceTs);
        return n == null ? 0 : n;
    }

    public static int dedupe() {
        ensureTable();
        return ArknightsApplication.jdbcTemplate.update(
                "DELETE v1 FROM visitor_info v1 JOIN visitor_info v2 "
                        + "ON v1.host_uid = v2.host_uid AND v1.visitor_uid = v2.visitor_uid "
                        + "AND DATE(FROM_UNIXTIME(v1.ts)) = DATE(FROM_UNIXTIME(v2.ts)) AND v1.ts < v2.ts");
    }

    public static int markClaimed(long hostUid) {
        ensureTable();
        return ArknightsApplication.jdbcTemplate.update(
                "UPDATE visitor_info SET claimed = 1 WHERE host_uid = ? AND claimed = 0", hostUid);
    }

    public static int countSince(long hostUid, long sinceTs) {
        ensureTable();
        Integer n = ArknightsApplication.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visitor_info WHERE host_uid = ? AND ts >= ?", Integer.class, hostUid, sinceTs);
        return n == null ? 0 : n;
    }
}
