package com.hypergryph.arknights.command;

import java.util.Collections;
import java.util.List;

public abstract class CommandBase implements ICommand {
    public CommandBase() {
    }

    public List<String> getCommandAliases() {return Collections.emptyList();}

    @Override
    public int compareTo(ICommand p_compareTo_1_) {
        return this.getCommandName().compareTo(p_compareTo_1_.getCommandName());
    }
}