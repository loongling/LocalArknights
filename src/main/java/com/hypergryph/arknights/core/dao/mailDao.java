package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.pojo.Mail;
import java.util.List;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

public class mailDao {
    public mailDao() {
    }

    public static List<Mail> queryMails() {
        String sql = "SELECT * from mail";
        BeanPropertyRowMapper<Mail> rowMapper = new BeanPropertyRowMapper(Mail.class);
        return ArknightsApplication.jdbcTemplate.query(sql, rowMapper);
    }

    public static List<Mail> queryMailById(int id) {
        String sql = "SELECT * FROM mail WHERE id = ?";
        BeanPropertyRowMapper<Mail> rowMapper = new BeanPropertyRowMapper(Mail.class);
        Object[] params = new Object[]{id};
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<Mail> queryMailByName(String name) {
        String sql = "SELECT * FROM mail WHERE name = ?";
        BeanPropertyRowMapper<Mail> rowMapper = new BeanPropertyRowMapper(Mail.class);
        Object[] params = new Object[]{name};
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static int setMailName(int id, String name) {
        String sql = "UPDATE mail SET name = ? where id = ?";
        Object[] params = new Object[]{name, id};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setMailFrom(int id, String from) {
        String sql = "UPDATE mail SET `from` = ? where id = ?";
        Object[] params = new Object[]{from, id};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setMailSubject(int id, String subject) {
        String sql = "UPDATE mail SET subject = ? where id = ?";
        Object[] params = new Object[]{subject, id};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setMailContent(int id, String content) {
        String sql = "UPDATE mail SET content = ? where id = ?";
        Object[] params = new Object[]{content, id};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setMailItems(int id, JSONArray items) {
        String sql = "UPDATE mail SET items = ? where id = ?";
        Object[] params = new Object[]{JSON.toJSONString(items, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), id};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int createMail(String name) {
        String sql = "insert into mail (`name`, `items`) value (?, '[]');";
        Object[] params = new Object[]{name};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int insertTable() {
        String sql = "create table mail\n(\n    id      int auto_increment\n        primary key,\n    name  text null,\n    `from`  text null,\n    subject text null,\n    content text null,\n    items   json null\n);";
        return ArknightsApplication.jdbcTemplate.update(sql);
    }

    /** 按 name 查模板 id，没有返回 null */
    public static Integer queryIdByName(String name) {
        String sql = "SELECT id FROM mail WHERE `name` = ? LIMIT 1";
        List<Integer> rows = ArknightsApplication.jdbcTemplate.queryForList(sql, Integer.class, name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 name 幂等写入模板（有则更新，无则插入） */
    public static int upsertTemplate(String name, String from, String subject, String content, JSONArray items) {
        String itemsJson = JSON.toJSONString(items, new SerializerFeature[]{SerializerFeature.WriteMapNullValue});
        Integer id = queryIdByName(name);
        if (id == null) {
            return ArknightsApplication.jdbcTemplate.update(
                    "INSERT INTO mail (`name`, `from`, subject, content, items) VALUES (?,?,?,?,?)",
                    name, from, subject, content, itemsJson);
        }
        return ArknightsApplication.jdbcTemplate.update(
                "UPDATE mail SET `from` = ?, subject = ?, content = ?, items = ? WHERE id = ?",
                from, subject, content, itemsJson, id);
    }
}
