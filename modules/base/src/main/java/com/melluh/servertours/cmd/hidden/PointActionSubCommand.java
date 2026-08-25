package com.melluh.servertours.cmd.hidden;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PointActionSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        if (array.length != 2) {
            CommandHandler.sendUsage(this, player);
            return;
        }
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        CraftRoute editingRoute = editingPlayer.getEditingRoute();
        CraftRoutePoint selectedPoint = editingPlayer.getSelectedPoint();
        if (selectedPoint == null) {
            player.sendMessage(ServerTours.translate("commands.errors.noPointSelected"));
            return;
        }
        int index = editingRoute.indexOf(selectedPoint);
        String s = array[1];
        switch (s) {
            case "movehere": {
                selectedPoint.move(player.getLocation());
                break;
            }
            case "teleport": {
                player.teleport(selectedPoint.getLocation());
                break;
            }
            case "remove": {
                editingRoute.removePoint(index);
                break;
            }
            case "indexup": {
                editingRoute.swapPoints(index, index + 1);
                break;
            }
            case "indexdown": {
                editingRoute.swapPoints(index - 1, index);
                break;
            }
            case "preview": {
                editingPlayer.preview(index);
                break;
            }
        }
    }

    @Override
    public String getPermission() {
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour pointaction <movehere|teleport|remove>";
    }

    @Override
    public String getDescription() {
        return null;
    }
}
