package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class EditSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        if (array.length != 2) {
            CommandHandler.sendUsage(this, player);
            return;
        }
        if (ServerTours.getInstance().getEditModeManager().isEditing(player)) {
            player.sendMessage(ServerTours.translate("commands.errors.alreadyEditing"));
            return;
        }
        CraftRoute route = ServerTours.getInstance().getRouteManager().getRoute(array[1]);
        if (route == null) {
            player.sendMessage(ServerTours.translate("commands.errors.routeNotFound", array[1]));
            return;
        }
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(route);
        if (editingPlayer != null) {
            if (!ServerTours.getInstance().getConfig().getBoolean("editMode.forceEnter")) {
                player.sendMessage(ServerTours.translate("commands.errors.alreadyEditingOther", editingPlayer.getPlayer().getName()));
                return;
            }
            Player player2 = editingPlayer.getPlayer();
            player2.sendMessage(ServerTours.translate("commands.forcedOut", player.getName()));
            player.sendMessage(ServerTours.translate("commands.forcedOutOther", player2.getName()));
            ServerTours.getInstance().getEditModeManager().stopEditing(editingPlayer);
        }
        ServerTours.getInstance().getEditModeManager().startEditing(player, route);
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
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour edit <name>";
    }

    @Override
    public String getDescription() {
        return "Start editing a tour route";
    }
}
