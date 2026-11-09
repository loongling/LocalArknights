package com.hypergryph.arknights.command;

public class CommandManager extends CommandHandler {
    public CommandManager() {
        this.registerCommand(new CommandHelp());
        this.registerCommand(new CommandAddD());
        this.registerCommand(new CommandCreateAC());
        this.registerCommand(new CommandMail());
        this.registerCommand(new CommandReload());
        org.apache.logging.log4j.LogManager.getLogger("CommandManager").info(
                "[command] 已注册控制台指令: {}",
                String.join(", ", new java.util.TreeSet<>(this.getCommands().keySet())));
    }
}