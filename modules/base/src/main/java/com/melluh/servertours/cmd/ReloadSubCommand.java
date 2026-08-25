package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        ServerTours.getInstance().reload();
        commandSender.sendMessage(ServerTours.translate("commands.reloaded"));
    }

    @Override
    public String getPermission() {
        return "servertours.commands.reload";
    }

    @Override
    public String getUsage() {
        return "/tour reload";
    }

    @Override
    public String getDescription() {
        return "Reload the configuration files";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
