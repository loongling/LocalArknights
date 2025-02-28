package com.hypergryph.arknights.command;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandHandler implements ICommandManager{
        private static final Logger logger = LogManager.getLogger();
        private final Map<String,ICommand> commandMap = new HashMap<String,ICommand>();
        private final Set<ICommand> commandSet = Sets.newHashSet();
        public CommandHandler(){
        }

    public List<ICommand> getPossibleCommands(ICommandSender sender) {
            List<ICommand> list = Lists.newArrayList();
            Iterator var3 = this.commandSet.iterator();

            while(var3.hasNext()) {
                ICommand icommand = (ICommand)var3.next();
                list.add(icommand);
            }
            return list;
    }

    public ICommand registerCommand(ICommand command) {
            this.commandMap.put(command.getCommandName(), command);
            this.commandSet.add(command);
            Iterator var2 = command.getCommandAliases().iterator();

            while(true) {
                String s;
                ICommand icommand;
                do {
                    if(!var2.hasNext()) {
                        return command;
                    }
                    s = (String)var2.next();
                    icommand = (ICommand)this.commandMap.get(s);
                }
                while(icommand != null && icommand.getCommandName().equals(s));
                this.commandMap.put(s, command);
            }
    }

    public int executeCommand(ICommandSender sender,String rawCommand) {
            rawCommand = rawCommand.trim();
            String[] astring = rawCommand.split(" ");
            String s = astring[0];
            ICommand icommand = this.commandMap.get(s);
            if(icommand == null) {
                logger.error("命令错误或不规范");
                return 0;
            }
            else {
                try {
                    icommand.processCommand(sender, astring);
                }
                catch(CommandException var7) {
                    CommandException e = var7;
                    e.printStackTrace();
                }
                return 0;
            }
    }

    public Map<String,ICommand> getCommands() {return this.commandMap;}
}