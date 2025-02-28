package com.hypergryph.arknights.command;

public class CommandManager extends CommandHandler {
    public CommandManager() {
        this.registerCommand(new CommandHelp());

    }
}