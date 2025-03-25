package com.hypergryph.arknights;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.dao.mailDao;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class}
)
public class ArKnightsApplication {
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

    public ArKnightsApplication() {
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
        LOGGER.info("服务端版本 0.2.0Alpha");
        LOGGER.info("客户端版本 2.4.61");
        LOGGER.info("构建时间 2025年3月22日");
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
    public static final ConcurrentHashMap<String, String> IP_SECRET_MAP = new ConcurrentHashMap<>();

    // 绑定 IP -> secret
    public static void addSecretForIP(String ip, String secret) {
        IP_SECRET_MAP.put(ip, secret);

        // 10 秒后自动删除
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                IP_SECRET_MAP.remove(ip);
            }
        }, 10000);
    }

    // 获取 secret
    public static String getSecretByIP(String ip) {
        return IP_SECRET_MAP.get(ip);
    }

    // 删除 secret
    public static void removeSecretByIP(String ip) {
        IP_SECRET_MAP.remove(ip);
    }
    //日志截取上线应删除-begin
    @Component
    public class RequestLoggingFilter implements Filter {

        private static final Logger LOGGER = LogManager.getLogger(RequestLoggingFilter.class);

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // 初始化逻辑（可选）
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            // 将 ServletRequest 转换为 HttpServletRequest
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // 记录请求信息
            logRequestDetails(httpRequest);

            // 继续处理请求
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            // 销毁逻辑（可选）
        }

        private void logRequestDetails(HttpServletRequest request) {
            // 获取请求的 URL
            String requestUrl = request.getRequestURL().toString();
            LOGGER.info("Request URL: " + requestUrl);

            // 获取请求方法（GET、POST 等）
            String requestMethod = request.getMethod();
            LOGGER.info("Request Method: " + requestMethod);

            // 获取请求头
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
            LOGGER.info("Request Headers: " + headers);

            // 获取请求体
            if ("POST".equalsIgnoreCase(requestMethod) || "PUT".equalsIgnoreCase(requestMethod)) {
                try {
                    StringBuilder requestBody = new StringBuilder();
                    BufferedReader reader = request.getReader();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        requestBody.append(line);
                    }
                    LOGGER.info("Request Body: " + requestBody.toString());
                } catch (IOException e) {
                    LOGGER.error("Failed to read request body", e);
                }
            } else {
                LOGGER.info("Request Body: (Empty or not applicable for GET/DELETE)");
            }
        }
    }
    //日志截取上线删除-end
    public static void reloadServerConfig() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("载入服务器配置...");
        serverConfig = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/config.json");
        enableServer = serverConfig.getJSONObject("server").getBooleanValue("enableServer");
        LOGGER.info("载入游戏数据...");
        DefaultSyncData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/defaultSyncData.json");
        roguelikeTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/roguelike_topic_table.json");
        stageTable = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/excel/stage_table.json").getJSONObject("stages");
        CrisisData = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/crisis/cc12.json");
        CashGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/CashGoodList.json");
        GPGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/GPGoodList.json");
        LowGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/LowGoodList.json");
        HighGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/HighGoodList.json");
        ExtraGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/ExtraGoodList.json");
        LMTGSGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/LMTGSGoodList.json");
        EPGSGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/EPGSGoodList.json");
        RepGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/RepGoodList.json");
        FurniGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/FurniGoodList.json");
        SocialGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/SocialGoodList.json");
        AllProductList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/AllProductList.json");
        skinGoodList = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/shop/SkinGoodList.json");
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