package com.hypergryph.arknights.command;

import com.hypergryph.arknights.core.dao.userDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.DigestUtils;

public class CommandCreateAC extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger();

    public CommandCreateAC() {
    }

    public String getCommandName() {
        return "createAC";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "[phone number] [password]";
    }

    public String getCommandDescription() {
        return "createAC与使用方式";
    }

    public String getCommandExample() {
        return "/createAC [手机号] [密码]";
    }

    public String getCommandExampleUsage() {
        return "查看 createAC 的使用规则";
    }

    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        try {
            int startIndex = 0;
            if (args.length > 0 && args[0].equalsIgnoreCase("createAC")) {
                startIndex = 1;
            }

            // 检查是否有足够的参数
            if (args.length <= startIndex) {
                LOGGER.error("参数不足");
                LOGGER.info("用法: /createAC [手机号] [密码]");
                return;
            }

            long phone = Long.parseLong(args[startIndex]);
            if (phone <= 10000000000L) {
                LOGGER.error("手机号无效");
                return;
            }
            if (phone >= 19999999999L) {
                LOGGER.error("手机号无效");
                return;
            }

            String passwd = null;
            if (args.length > startIndex + 1) {
                passwd = args[startIndex + 1];
                if (passwd.length() < 6) {
                    LOGGER.error("密码长度不能少于6位");
                    return;
                }
                // 执行添加
                boolean success = createAC(phone, passwd);
                if (success) {
                    LOGGER.info("成功创建账户");
                } else {
                    LOGGER.error("账户创建失败");
                }

            }
        }
        catch (Exception e){
            LOGGER.error("执行 CreateAC 命令失败,账户创建失败", e);
        }
    }
    private boolean createAC(long account,String passwd){
        String Key = "IxMMveJRWsxStJgX";
        try{
            String secret = DigestUtils.md5DigestAsHex((account + Key).getBytes());
            if (!userDao.queryAccountByPhone(String.valueOf(account)).isEmpty()) {
                LOGGER.error("手机号已注册: {}", account);
                throw new RuntimeException("手机号已存在");
            }
            else if (userDao.RegisterAccount(String.valueOf(account), DigestUtils.md5DigestAsHex((passwd + Key).getBytes()), secret) != 1) {
                LOGGER.error("注册失败未知错误");
                throw new RuntimeException("未知错误，账户无法创建");
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.error("账户创建失败", e);
            return false;
        }
    }
}