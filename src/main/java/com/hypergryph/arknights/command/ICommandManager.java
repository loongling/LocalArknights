package com.hypergryph.arknights.command;

import java.util.List;
import java.util.Map;

public interface ICommandManager {
    static int executeCommand(ICommandSender sender, String rawCommand) {
        return 0;
    }
    List<ICommand> getPossibleCommands(ICommandSender var1);
    Map<String, ICommand> getCommands();
}
