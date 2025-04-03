package com.hypergryph.arknights.command;

import com.hypergryph.arknights.ArknightsApplication;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandHelp extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger();

    public CommandHelp() {
    }

    public String getCommandName() {
        return "help";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "[string]<命令>";
    }

    public String getCommandDescription() {
        return "命令列表与使用方式";
    }

    public String getCommandExample() {
        return "/help help";
    }

    public String getCommandExampleUsage() {
        return "查看 help 的使用规则";
    }

    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        Map map;
        if (args.length != 1) {
            map = this.getCommands();
            ICommand icommand = (ICommand)map.get(args[1]);
            if (icommand == null) {
                LOGGER.error("未知或不完整的命令 '" + args[1] + "'");
            }
            else {
                LOGGER.info("§e------------------- §f命令帮助 §e-------------------");
                LOGGER.info("§6描述: §f" + icommand.getCommandDescription());
                LOGGER.info("§6使用方式: §f/" + icommand.getCommandName() + " " + icommand.getCommandUsage(sender));
                LOGGER.info("§6例子: §f" + icommand.getCommandExample());
                LOGGER.info("§6说明: §f" + icommand.getCommandExampleUsage());
            }
        }
        else {
            LOGGER.info("§e------------------- §f帮助菜单 §e-------------------");
            map = this.getCommands();
            Iterator var4 = map.entrySet().iterator();

            while(var4.hasNext()) {
                Map.Entry entry = (Map.Entry)var4.next();
                ICommand icommand = (ICommand)map.get(entry.getKey());
                LOGGER.info("§6/" + icommand.getCommandName() + " §f" + icommand.getCommandUsage(sender));
            }
        }
    }
    private Map<String, ICommand> getCommands() {return ArknightsApplication.ConsoleCommandManager.getCommands();}
}

