package com.hypergryph.arknights;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;

import com.hypergryph.arknights.command.CommandManager;
import com.hypergryph.arknights.command.ICommandSender;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.function.randomPwd;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class}
)
public class ArKnightsApplication {
    public static final Logger LOGGER = LogManager.getLogger();
    public static JdbcTemplate jdbcTemplate = null;
    public static JSONObject serverConfig = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/config.json");
    public static JSONObject DefaultSyncData;
    public static JSONObject characterJson;
    public static JSONObject roguelikeTable;
    public static JSONObject stageTable;
    public static JSONObject itemTable;
    public static JSONObject mainStage;
    public static JSONObject normalGachaData;
    public static JSONObject uniequipTable;
    public static JSONObject skinGoodList;
    public static JSONObject skinTable;
    public static JSONObject charwordTable;
    public static JSONObject CrisisData;
    public static JSONObject CashGoodList;
    public static JSONObject GPGoodList;
    public static JSONObject LowGoodList;
    public static JSONObject HighGoodList;
    public static JSONObject ExtraGoodList;
    public static JSONObject LMTGSGoodList;
    public static JSONObject EPGSGoodList;
    public static JSONObject RepGoodList;
    public static JSONObject FurniGoodList;
    public static JSONObject SocialGoodList;
    public static JSONObject AllProductList;
    public static JSONObject unlockActivity;
    public static JSONObject buildingData;
    public static CommandManager ConsoleCommandManager;
    public static ICommandSender Sender;
    
    public ArKnightsApplication(){
    }

    public static void main(String[] args) throws Exception {
        String host = serverConfig.getJSONObject("database").getString("host");
        String port = serverConfig.getJSONObject("database").getString("port");
        String dbname = serverConfig.getJSONObject("database").getString("dbname");
        String username = serverConfig.getJSONObject("database").getString("user");
        String password = serverConfig.getJSONObject("database").getString("password");
        String extra = serverConfig.getJSONObject("database").getString("extra");
        DriverManagerDataSource DataSource = new DriverManagerDataSource();
        DataSource.setDriverClassName("com.mysql.jdbc.Driver");
        DataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + dbname + "?" + extra);
        DataSource.setUsername(username);
        DataSource.setPassword(password);
        jdbcTemplate = new JdbcTemplate(DataSource);
        SpringApplication springApplication = new SpringApplication(new Class[]{ArKnightsApplication.class});
        springApplication.setBannerMode(Banner.Mode.OFF);
        String[] disabledCommands = new String[]{"--server.port=" + serverConfig.getJSONObject("server").getString("https"), "--spring.profiles.active=default"};
        String[] fullArgs = StringUtils.concatenateStringArrays(args, disabledCommands);
        springApplication.run(fullArgs);
        reloadServerConfig();
        String MysqlVersion = null;
        LOGGER.info("检测数据库版本中...");

        try {
            MysqlVersion = userDao.queryMysqlVersion();
        } catch (Exception var13) {
            LOGGER.error("无法连接至Mysql数据库");
            System.exit(0);
        }

        if (Integer.valueOf(MysqlVersion.substring(0, 1)) < 8) {
            LOGGER.error("Mysql版本需要 >= 8.0");
            LOGGER.error("请升级后重试");
            System.exit(0);
        }

        LOGGER.info("数据库版本 " + MysqlVersion);
        LOGGER.info("服务端版本 1.9.3");
        LOGGER.info("客户端版本 1.7.21");
        LOGGER.info("构建时间 ");
        if (serverConfig.getJSONObject("server").getString("GMKey") == null) {
            serverConfig.getJSONObject("server").put("GMKey", randomPwd.getRandomPwd(64));
            IOTools.SaveJsonFile(System.getProperty("user.dir") + "/config.json", serverConfig);
            LOGGER.info("已随机生成新的管理员密钥");
        }

        LOGGER.info("管理员密钥 " + serverConfig.getJSONObject("server").getString("GMKey"));
        if (userDao.tableExists("account").size() == 0) {
            userDao.insertTable();
            LOGGER.info("玩家数据库不存在，已生成");
        }

        if (userDao.tableExists("mail").size() == 0) {
            mailDao.insertTable();
            LOGGER.info("邮件数据库不存在，已生成");
        }

        getTimestamp();
        LOGGER.info("服务端更新日志:");
        LOGGER.info("启动完成! 如果需要获取帮助,请输入 \"help\"");
        (new console()).start();
    }

    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress = null;

        try {
            ipAddress = request.getHeader("x-forwarded-for");
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }

            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }

            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
                if (ipAddress.equals("127.0.0.1")) {
                    try {
                        ipAddress = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException var3) {
                        UnknownHostException e = var3;
                        e.printStackTrace();
                    }
                }
            }

            if (ipAddress != null) {
                return ipAddress.contains(",") ? ipAddress.split(",")[0] : ipAddress;
            } else {
                return "";
            }
        } catch (Exception var4) {
            Exception e = var4;
            e.printStackTrace();
            return "";
        }
    }

    public static long getTimestamp() {
        long ts = serverConfig.getJSONObject("timestamp").getLongValue(DateUtil.dayOfWeekEnum(DateUtil.date()).toString().toLowerCase());
        if (ts == -1L) {
            ts = (new Date()).getTime() / 1000L;
        }

        return ts;
    }

    public static void reloadServerConfig() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("载入服务器配置...");
    }
}