package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;

import com.hypergryph.arknights.command.CommandManager;
import com.hypergryph.arknights.command.ICommandSender;
import com.hypergryph.arknights.core.file.IOTools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
    }
}