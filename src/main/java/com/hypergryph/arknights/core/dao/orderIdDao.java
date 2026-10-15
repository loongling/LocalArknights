package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;

import java.util.List;
import java.util.Map;

public class orderIdDao {
    public  orderIdDao() {
    }

    public static String queryOrderIdBtSecret(String secret) {
        String sql = "SELECT order_id FROM orders WHERE secret = ?";
        List<String> list = ArknightsApplication.jdbcTemplate.queryForList(sql, String.class, secret);
        return list.isEmpty() ? null : list.get(0);
    }

    public static JSONObject queryAllBySecret(String secret) {
        String sql = "SELECT * FROM orders WHERE secret = ?";
        List<Map<String, Object>> list = ArknightsApplication.jdbcTemplate.queryForList(sql, secret);
        if (list.isEmpty()) {
            return null;
        }
        return new JSONObject(list.get(0));
    }

    public static String queryGoodIdByOrderId(String orderId) {
        String sql = "SELECT good_id FROM orders WHERE order_id = ?";
        return ArknightsApplication.jdbcTemplate.queryForObject(sql, String.class, orderId);
    }

    public static int queryStoreIdBtSecret(String secret) {
        String sql = "SELECT store_id FROM orders WHERE secret = ?";
        return ArknightsApplication.jdbcTemplate.queryForObject(sql, int.class, secret);
    }

    public static int UpdateTable(String secret, String orderId, String goodId, int storeId, int price, String pName, int times, long create_time) {
        String sql = "INSERT INTO orders (secret, order_id, good_id, store_id, price, product_name, times, create_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return ArknightsApplication.jdbcTemplate.update(sql, secret, orderId, goodId, storeId, price, pName, times, create_time);
    }

    public static int updateTimes(String secret) {
        String sql = "UPDATE orders SET times = times + 1 WHERE secret = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, secret);
    }

    public static int checkTimes(String secret) {
        String sql = "SELECT times FROM orders WHERE secret = ?";
        List<Integer> list = ArknightsApplication.jdbcTemplate.queryForList(sql, Integer.class, secret);
        return list.isEmpty() ? 0 : list.get(0);
    }

    public static int DelTable(String orderId) {
        String sql = "DELETE FROM orders WHERE order_id = ?";
        return ArknightsApplication.jdbcTemplate.update(sql, orderId);
    }

    public static int insertTable() {
        String sql = "SET NAMES utf8mb4;\n" +
                "SET FOREIGN_KEY_CHECKS = 0;\n" +
                "DROP TABLE IF EXISTS `orders`;\n" +
                "CREATE TABLE `orders` (\n" +
                "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
                "\t`secret` VARCHAR ( 255 ) CHARACTER \n" +
                "\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n" +
                "\t`order_id` VARCHAR ( 255 ) CHARACTER \n" +
                "\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n" +
                "\t`store_id` INT NULL DEFAULT NULL,\n" +
                "\t`times` INT NULL DEFAULT NULL,\n" +
                "\t`price` INT NULL DEFAULT NULL,\n" +
                "\t`create_at` LONG NULL DEFAULT NULL,\n" +
                "\t`good_id` VARCHAR ( 255 ) CHARACTER \n" +
                "\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n" +
                "\t`product_name` VARCHAR ( 255 ) CHARACTER \n" +
                "\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n" +
                "\tPRIMARY KEY USING BTREE ( `id` ) \n" +
                ") ENGINE = INNODB CHARACTER \n" +
                "SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = Dynamic;\n" +
                "SET FOREIGN_KEY_CHECKS = 1;";
        return ArknightsApplication.jdbcTemplate.update(sql);
    }

    public static List tableExists(String table_name) {
        String sql = "SHOW TABLES LIKE ?";
        Object[] params = new Object[]{table_name};
        return ArknightsApplication.jdbcTemplate.queryForList(sql, params);
    }
}
