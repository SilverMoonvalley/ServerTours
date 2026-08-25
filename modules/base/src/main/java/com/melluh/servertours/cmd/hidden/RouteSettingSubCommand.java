package com.melluh.servertours.cmd.hidden;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.PositionInterpolationMode;
import com.melluh.servertours.api.object.RotationInterpolationMode;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Applies route-wide settings selected from the edit-mode chat menu. */
public final class RouteSettingSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] arguments) {
        Player player = (Player) commandSender;
        if (arguments.length < 3) {
            CommandHandler.sendUsage(this, player);
            return;
        }

        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }

        String key = arguments[1];
        String value = Arrays.stream(arguments, 2, arguments.length).collect(Collectors.joining(" "));
        CraftRoute route = editingPlayer.getEditingRoute();
        switch (key) {
            case "positionInterpolation" -> {
                PositionInterpolationMode mode = Utils.safelyParseEnum(PositionInterpolationMode.class, value);
                if (mode == null) {
                    player.sendMessage(ChatColor.RED + "Invalid position interpolation mode");
                    return;
                }
                route.setPositionInterpolationMode(mode);
            }
            case "rotationInterpolation" -> {
                RotationInterpolationMode mode = Utils.safelyParseEnum(RotationInterpolationMode.class, value);
                if (mode == null) {
                    player.sendMessage(ChatColor.RED + "Invalid rotation interpolation mode");
                    return;
                }
                route.setRotationInterpolationMode(mode);
            }
            default -> {
                CommandHandler.sendUsage(this, player);
                return;
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
        return "/tour routesetting <key> <value>";
    }

    @Override
    public String getDescription() {
        return null;
    }
}
