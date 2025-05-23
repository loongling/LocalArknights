package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.dao.mailDao;
import com.hypergryph.arknights.command.CommandManager;
import com.hypergryph.arknights.command.ICommandSender;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.function.randomPwd;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class}
)
public class ArknightsApplication {
    public static final Logger LOGGER = LogManager.getLogger();
    public static JdbcTemplate jdbcTemplate = null;
    public static JSONObject serverConfig = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/config.json");
    public static boolean enableServer;
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
    public static JSONObject CrisisV2Data;
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

    public ArknightsApplication() {
    }

    public static void main(String[] args) throws Exception {
        String host = serverConfig.getJSONObject("database").getString("host");
        String port = serverConfig.getJSONObject("database").getString("port");
        String dbname = serverConfig.getJSONObject("database").getString("dbname");
        String username = serverConfig.getJSONObject("database").getString("user");
        String password = serverConfig.getJSONObject("database").getString("password");
        String extra = serverConfig.getJSONObject("database").getString("extra");
        DriverManagerDataSource DataSource = new DriverManagerDataSource();
        DataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        DataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + dbname + "?" + extra);
        DataSource.setUsername(username);
        DataSource.setPassword(password);
        jdbcTemplate = new JdbcTemplate(DataSource);
        SpringApplication springApplication = new SpringApplication(new Class[]{ArknightsApplication.class});
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
        LOGGER.info("服务端版本 0.4.4Alpha");
        LOGGER.info("客户端版本 2.4.61");
        LOGGER.info("构建时间 2025年4月6日21:49:05");
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
        // 读取 JSON 配置中的时间字符串
        String ts = serverConfig.getJSONObject("timer").getString("set_server_time");

        // 处理 null 或空字符串情况
        if (ts == null || ts.isEmpty()) {
            return System.currentTimeMillis() / 1000L; // 返回当前时间戳（秒）
        }

        // 定义支持的时间格式
        List<String> timeFormats = List.of(
                "yyyy/MM/dd HH:mm:ss",
                "ddMMyyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyyMMdd HH:mm:ss"
        );

        // 遍历尝试解析时间字符串
        for (String format : timeFormats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                LocalDateTime dt = LocalDateTime.parse(ts, formatter);
                long unixTime = dt.atZone(ZoneId.systemDefault()).toEpochSecond(); // 转换为 Unix 时间戳（秒）
                return unixTime == -1 ? System.currentTimeMillis() / 1000L : unixTime; // -1 时返回真实时间
            } catch (Exception ignored) {
                // 解析失败则尝试下一个格式
            }
        }
        return System.currentTimeMillis() / 1000L;
    };
        public static final ConcurrentHashMap<String, String> IP_SECRET_MAP = new ConcurrentHashMap<>();

        // 绑定 IP -> secret
        public static void addSecretForIP (String ip, String secret){
            IP_SECRET_MAP.put(ip, secret);
        }

    @Configuration
    public class WebConfig implements WebMvcConfigurer {

        @Autowired
        private HttpRequestLoggerInterceptor requestLoggerInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(requestLoggerInterceptor);
        }
    }


    // 获取 secret
    public static String getSecretByIP(String ip) {
        return IP_SECRET_MAP.get(ip);
    }

    public static void reloadServerConfig() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("载入服务器配置...");
        serverConfig = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/config.json");
        enableServer = serverConfig.getJSONObject("server").getBooleanValue("enableServer");
        LOGGER.info("载入游戏数据...");
        DefaultSyncData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/defaultSyncData.json");
        characterJson = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/character_table.json");
        roguelikeTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/roguelike_topic_table.json");
        stageTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/stage_table.json").getJSONObject("stages");
        itemTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/item_table.json").getJSONObject("items");
        mainStage = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/battle/stage.json").getJSONObject("MainStage");
        uniequipTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/uniequip_table.json").getJSONObject("equipDict");
        skinGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/SkinGoodList.json");
        skinTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/skin_table.json").getJSONObject("charSkins");
        charwordTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/charword_table.json");
        CashGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/CashGoodList.json");
        GPGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/GPGoodList.json");
        normalGachaData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/normalGacha.json");
        LowGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/LowGoodList.json");
        HighGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/HighGoodList.json");
        ExtraGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/ExtraGoodList.json");
        LMTGSGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/LMTGSGoodList.json");
        EPGSGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/EPGSGoodList.json");
        RepGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/RepGoodList.json");
        FurniGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/FurniGoodList.json");
        SocialGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/SocialGoodList.json");
        AllProductList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/AllProductList.json");
        CrisisData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/crisis/cc12.json");
        CrisisV2Data = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/crisisv2/cc3.json");
        buildingData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/building_data.json").getJSONObject("workshopFormulas");
        long endTime = System.currentTimeMillis();
        LOGGER.info("载入完成，耗时：" + (endTime - startTime) + "ms");
    }

    static {
        enableServer = serverConfig.getJSONObject("server").getBooleanValue("enableServer");
        DefaultSyncData = new JSONObject();
        characterJson = new JSONObject();
        roguelikeTable = new JSONObject();
        stageTable = new JSONObject();
        itemTable = new JSONObject();
        mainStage = new JSONObject();
        normalGachaData = new JSONObject();
        uniequipTable = new JSONObject();
        skinGoodList = new JSONObject();
        skinTable = new JSONObject();
        charwordTable = new JSONObject();
        CrisisData = new JSONObject();
        CrisisV2Data = new JSONObject();
        CashGoodList = new JSONObject();
        GPGoodList = new JSONObject();
        LowGoodList = new JSONObject();
        HighGoodList = new JSONObject();
        ExtraGoodList = new JSONObject();
        LMTGSGoodList = new JSONObject();
        EPGSGoodList = new JSONObject();
        RepGoodList = new JSONObject();
        FurniGoodList = new JSONObject();
        SocialGoodList = new JSONObject();
        AllProductList = new JSONObject();
        unlockActivity = new JSONObject();
        buildingData = new JSONObject();
        ConsoleCommandManager = new CommandManager();
        Sender = () -> {
            return "Console";
        };
    }
}