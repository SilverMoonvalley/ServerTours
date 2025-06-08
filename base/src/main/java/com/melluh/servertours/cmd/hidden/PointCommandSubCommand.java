package com.melluh.servertours.cmd.hidden;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PointCommandSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        if (array.length < 3) {
            CommandHandler.sendUsage(this, player);
            return;
        }
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        CraftRoutePoint selectedPoint = editingPlayer.getSelectedPoint();
        if (selectedPoint == null) {
            player.sendMessage(ServerTours.translate("commands.errors.noPointSelected"));
            return;
        }
        String s = array[1];
        switch (s) {
            case "add" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < array.length; ++i) {
                    sb.append(array[i]);
                }
                String s2 = sb.toString().trim();
                if (s2.startsWith("/")) {
                    s2 = s2.substring(1);
                }
                selectedPoint.addCommand(new RoutePointCommand(RoutePointCommand.CommandExecutorType.CONSOLE, RoutePointCommand.CommandTrigger.ENTER, s2));
            }
            case "remove" -> {
                Integer safelyParseInt = Utils.safelyParseInt(array[2]);
                if (safelyParseInt == null || safelyParseInt < 0 || safelyParseInt >= selectedPoint.getCommands().size()) {
                    player.sendMessage(ChatColor.RED + "Command index out of range");
                    return;
                }
                selectedPoint.removeCommand(safelyParseInt);
            }
            case "setexecutor" -> {
                Integer safelyParseInt2 = Utils.safelyParseInt(array[2]);
                if (safelyParseInt2 == null || safelyParseInt2 < 0 || safelyParseInt2 >= selectedPoint.getCommands().size()) {
                    player.sendMessage(ChatColor.RED + "Command index out of range");
                    return;
                }
                RoutePointCommand.CommandExecutorType executorType = Utils.safelyParseEnum(RoutePointCommand.CommandExecutorType.class, array[3]);
                if (executorType == null) {
                    return;
                }
                selectedPoint.getCommands().get(safelyParseInt2).setExecutorType(executorType);
            }
            case "settrigger" -> {
                Integer safelyParseInt3 = Utils.safelyParseInt(array[2]);
                if (safelyParseInt3 == null || safelyParseInt3 < 0 || safelyParseInt3 >= selectedPoint.getCommands().size()) {
                    player.sendMessage(ChatColor.RED + "Command index out of range");
                    return;
                }
                RoutePointCommand.CommandTrigger triggerType = Utils.safelyParseEnum(RoutePointCommand.CommandTrigger.class, array[3]);
                if (triggerType == null) {
                    return;
                }
                selectedPoint.getCommands().get(safelyParseInt3).setTriggerType(triggerType);
            }
            case "set" -> {
                Integer safelyParseInt4 = Utils.safelyParseInt(array[2]);
                if (safelyParseInt4 == null || safelyParseInt4 < 0 || safelyParseInt4 >= selectedPoint.getCommands().size()) {
                    player.sendMessage(ChatColor.RED + "Command index out of range");
                    return;
                }
                StringBuilder sb2 = new StringBuilder();
                for (int j = 3; j < array.length; ++j) {
                    sb2.append(array[j]);
                }
                String command = sb2.toString().trim();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                selectedPoint.getCommands().get(safelyParseInt4).setCommand(command);
            }
        }
        ServerTours.getInstance().getEditModeManager().updateChatMenu(player);
    }

    @Override
    public String getPermission() {
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour pointcommand <add|remove|setexecutor|set> <value>";
    }

    @Override
    public String getDescription() {
        return null;
    }
}
