package com.hypergryph.arknights.core.dao;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.pojo.SearchAssistCharList;
import com.hypergryph.arknights.core.pojo.SearchUidList;
import com.hypergryph.arknights.core.pojo.UserInfo;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.util.List;

public class userDao {
    public userDao() {
    }

    public static List<Account> queryAccountByUid(long uid) {
        String sql = "SELECT * FROM account WHERE uid = ?";
        BeanPropertyRowMapper<Account> rowMapper = new BeanPropertyRowMapper(Account.class);
        Object[] params = new Object[] { uid };
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<Account> queryAccountBySecret(String secret) {
        String sql = "SELECT * FROM account WHERE secret = ?";
        BeanPropertyRowMapper<Account> rowMapper = new BeanPropertyRowMapper(Account.class);
        Object[] params = new Object[] { secret };
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<Account> queryAccountByPhone(String phone) {
        String sql = "SELECT * FROM account WHERE phone = ?";
        BeanPropertyRowMapper<Account> rowMapper = new BeanPropertyRowMapper(Account.class);
        Object[] params = new Object[] { phone };
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<SearchUidList> searchPlayer(String nickName, String nickNumber) {
        String sql = "SELECT uid as uid,user -> '$.status.level' as level FROM account  WHERE user -> '$.status.nickName' LIKE ? AND user -> '$.status.nickNumber' LIKE ?";
        BeanPropertyRowMapper<SearchUidList> rowMapper = new BeanPropertyRowMapper(SearchUidList.class);
        Object[] params = new Object[] { nickName, nickNumber };
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<UserInfo>queryUserInfo(long uid) {
        String sql = "SELECT uid as uid,user -> '$.status' as status, user -> '$.troop.chars' as chars, user -> '$.social.assistCharList' as socialAssistCharList,assistCharList as assistCharList,friend as friend FROM account WHERE uid = ?";
        BeanPropertyRowMapper<UserInfo> rowMapper = new BeanPropertyRowMapper(UserInfo.class);
        Object[] params = new Object[]{uid};
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<SearchAssistCharList> SearchAssistCharList(String profession) {
        String sql = "SELECT uid as uid,user -> '$.status' as status, user -> '$.troop.chars' as chars, user -> '$.social.assistCharList' as socialAssistCharList, assistCharList -> ? as assistCharList FROM account WHERE assistCharList -> ?";
        BeanPropertyRowMapper<SearchAssistCharList> rowMapper = new BeanPropertyRowMapper(SearchAssistCharList.class);
        Object[] params = new Object[]{profession, profession};
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static List<Account> LoginAccount(String phone, String password) {
        String sql = "SELECT * FROM account  WHERE `phone` = ? and `password` = ?";
        BeanPropertyRowMapper<Account> rowMapper = new BeanPropertyRowMapper(Account.class);
        Object[] params = new Object[]{phone, password};
        return ArknightsApplication.jdbcTemplate.query(sql, params, rowMapper);
    }

    public static int RegisterAccount(String phone, String password, String secret) {
        String sql = "INSERT INTO account (`phone`, `password`, `secret`, `user`, `mails`, `assistCharList`, `friend`, `ban`) VALUES (?, ?, ?, '{}', '[]', '{}', '{\"list\":[],\"request\":[]}', 0)";
        Object[] params = new Object[]{phone, password, secret};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setAssistCharListData(Long uid, JSONObject assistCharList) {
        String sql = "UPDATE account SET assistCharList = ? WHERE uid = ?";
        Object[] params = new Object[]{JSON.toJSONString(assistCharList, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), uid};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setMailsData(Long uid, JSONArray mailsData) {
        String sql = "UPDATE account SET mails = ? WHERE uid = ?";
        Object[] params = new Object[]{JSON.toJSONString(mailsData, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), uid};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setUserData(Long uid, JSONObject userData) {
        String sql = "UPDATE account SET user = ? WHERE uid = ?";
        Object[] params = new Object[]{JSON.toJSONString(userData, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), uid};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setFriendData(Long uid, JSONObject friendData) {
        String sql = "UPDATE account SET friend = ? WHERE uid = ?";
        Object[] params = new Object[]{JSON.toJSONString(friendData, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), uid};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static int setBanStatus(int uid, int status) {
        String sql = "UPDATE account SET ban = ? WHERE uid = ?";
        Object[] params = new Object[]{JSON.toJSONString(status, new SerializerFeature[]{SerializerFeature.WriteMapNullValue}), uid};
        return ArknightsApplication.jdbcTemplate.update(sql, params);
    }

    public static String queryMysqlVersion() {
        String sql = "SELECT VERSION()";
        return (String) ArknightsApplication.jdbcTemplate.queryForObject(sql, String.class);
    }

    public static List queryNickName(String nickName) {
        String sql = "SELECT uid FROM account WHERE user -> '$.status.nickName' = ?";
        Object[] params = new Object[]{nickName};
        return ArknightsApplication.jdbcTemplate.queryForList(sql, params);
    }

    public static List tableExists(String table_name) {
        String sql = "SHOW TABLES LIKE ?";
        Object[] params = new Object[]{table_name};
        return ArknightsApplication.jdbcTemplate.queryForList(sql, params);
    }

    public static int insertTable() {
        String sql = "\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nDROP TABLE IF EXISTS `account`;\nCREATE TABLE `account` (\n\t`uid` INT NOT NULL AUTO_INCREMENT,\n\t`phone` VARCHAR ( 255 ) CHARACTER \n\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n\t`password` VARCHAR ( 255 ) CHARACTER \n\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n\t`secret` VARCHAR ( 255 ) CHARACTER \n\tSET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,\n\t`user` json NULL,\n\t`mails` json NULL,\n\t`assistCharList` json NULL,\n\t`friend` json NULL,\n\t`ban` INT NULL DEFAULT NULL,\n\tPRIMARY KEY USING BTREE ( `uid` ) \n) ENGINE = INNODB AUTO_INCREMENT = 3 CHARACTER \nSET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = Dynamic;\nSET FOREIGN_KEY_CHECKS = 1;\nALTER TABLE account AUTO_INCREMENT = 10000000;";
        return ArknightsApplication.jdbcTemplate.update(sql);
    }
}
