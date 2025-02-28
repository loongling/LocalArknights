package com.hypergryph.arknights.command;

import java.util.List;

public interface ICommand extends Comparable<ICommand> {
    String getCommandName();
    String getCommandUsage(ICommandSender var1);
    String getCommandDescription();
    String getCommandExample();
    String getCommandExampleUsage();
    List<String> getCommandAliases();
    void processCommand(ICommandSender var1, String[] var2) throws CommandException;
}
