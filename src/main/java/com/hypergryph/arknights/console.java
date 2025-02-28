package com.hypergryph.arknights;

import net.minecrell.terminalconsole.SimpleTerminalConsole;

public class console extends SimpleTerminalConsole {
    public console() {
    }

    protected boolean isRunning() {
        return true;
    }

    protected void runCommand(String s) {
        ArKnightsApplication.ConsoleCommandManager.executeCommand(ArKnightsApplication.Sender, s);
    }

    protected void shutdown() {
    }
}