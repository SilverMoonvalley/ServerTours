package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import org.bukkit.command.CommandSender;

public class CreateSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        if (array.length != 2) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }
        if (ServerTours.getInstance().getRouteManager().getRoute(array[1]) != null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.routeAlreadyExists", array[1]));
            return;
        }
        if (ServerTours.getInstance().getRecordingManager().isRouteNameReserved(array[1])) {
            commandSender.sendMessage(ServerTours.translate("commands.record.errors.nameReserved", array[1]));
            return;
        }
        ServerTours.getInstance().getRouteManager().createRoute(array[1]).saveToDisk();
        commandSender.sendMessage(ServerTours.translate("commands.routeCreated", "/tour edit " + array[1].toLowerCase()));
    }

    @Override
    public String getPermission() {
        return "servertours.commands.create";
    }

    @Override
    public String getUsage() {
        return "/tour create <name>";
    }

    @Override
    public String getDescription() {
        return "Create a new tour route";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
