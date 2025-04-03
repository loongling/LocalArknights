package com.hypergryph.arknights;

import net.minecrell.terminalconsole.SimpleTerminalConsole;

public class console extends SimpleTerminalConsole {
    public console() {
    }

    protected boolean isRunning() {
        return true;
    }

    protected void runCommand(String s) {
        ArknightsApplication.ConsoleCommandManager.executeCommand(ArknightsApplication.Sender, s);
    }

    protected void shutdown() {
    }
}