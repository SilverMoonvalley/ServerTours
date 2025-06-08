package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class RemoveSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        if (array.length != 2) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }
        CraftRoute route = ServerTours.getInstance().getRouteManager().getRoute(array[1]);
        if (route == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.routeNotFound", array[1]));
            return;
        }
        ServerTours.getInstance().getRouteManager().removeRoute(route);
        commandSender.sendMessage(ServerTours.translate("commands.routeRemoved"));
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, String[] array) {
        if (array.length == 2) {
            return Utils.sortTabComplete(ServerTours.getInstance().getRouteManager().getRouteNames(), array[1]);
        }
        return Collections.emptyList();
    }

    @Override
    public String getPermission() {
        return "servertours.commands.remove";
    }

    @Override
    public String getUsage() {
        return "/tour remove <name>";
    }

    @Override
    public String getDescription() {
        return "Remove a tour route";
    }
}
