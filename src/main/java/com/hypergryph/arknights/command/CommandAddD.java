package com.hypergryph.arknights.command;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class CommandAddD extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger();

    public CommandAddD() {
    }

    public String getCommandName() {
        return "addD";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "[number] [UID]";
    }

    public String getCommandDescription() {
        return "addD与使用方式";
    }

    public String getCommandExample() {
        return "/addD [增加源石数] [UID]";
    }

    public String getCommandExampleUsage() {
        return "查看 addD 的使用规则";
    }

    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        try {
            // 如果 args[0] 是命令名 "addD"，则从 args[1] 开始解析
            int startIndex = 0;
            if (args.length > 0 && args[0].equalsIgnoreCase("addD")) {
                startIndex = 1;
            }

            // 检查是否有足够的参数
            if (args.length <= startIndex) {
                LOGGER.error("参数不足");
                LOGGER.info("用法: /addD <数量> <UID>");
                return;
            }

            // 解析数量
            int amount = Integer.parseInt(args[startIndex]);
            if (amount <= 0) {
                LOGGER.error("数量必须大于0");
                return;
            }

            // 解析 UID（如果有）
            long uid = 0;
            if (args.length > startIndex + 1) {
                uid = Long.parseLong(args[startIndex + 1]);
            }

            // 执行添加
            boolean success = addDiamond(uid, amount);
            if (success) {
                LOGGER.info("源石添加成功: UID={}, 数量={},请重新登录", uid, amount);
            } else {
                LOGGER.error("源石添加失败: UID={}, 数量={}", uid, amount);
            }

        } catch (NumberFormatException e) {
            LOGGER.error("参数格式错误: 数量必须是数字");
            LOGGER.info("用法: /addD <数量> <UID>");
        } catch (Exception e) {
            LOGGER.error("执行 addD 命令失败", e);
        }
    }
    private boolean addDiamond(long uid, int amount){
        try {
                // 通过 UID 获取玩家数据
            List<Account> Accounts = userDao.queryAccountByUid(uid);
                if (Accounts.size() != 1) {
                    LOGGER.error("未找到玩家: {}", uid);
                    return false;
                }
            // 获取源石数量
            JSONObject userData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
            JSONObject status = userData.getJSONObject("status");
            LOGGER.info("status" + status);
            // 增加源石 保存数据
            status.put("iosDiamond", status.getIntValue("iosDiamond") + amount);
            status.put("androidDiamond", status.getIntValue("androidDiamond") + amount);
            userDao.setUserData(uid, userData);

            return true;

        } catch (Exception e) {
            LOGGER.error("添加源石失败", e);
            return false;
        }
    }
}
