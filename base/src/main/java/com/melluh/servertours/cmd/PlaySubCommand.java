package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class PlaySubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        if (array.length != 2 && array.length != 3) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }
        CraftRoute route = ServerTours.getInstance().getRouteManager().getRoute(array[1]);
        if (route == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.routeNotFound", array[1]));
            return;
        }
        if (route.getNumPoints() < 1) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.noPoints"));
            return;
        }
        Player playerExact;
        if (array.length == 2) {
            if (!CommandHandler.verifyPlayer(commandSender)) {
                return;
            }
            playerExact = (Player) commandSender;
            if (ServerTours.getInstance().getPlaybackManager().isTouringPlayer(playerExact)) {
                commandSender.sendMessage(ServerTours.translate("commands.errors.alreadyWatching"));
                return;
            }
        } else {
            if (!commandSender.hasPermission("servertours.commands.play.other")) {
                commandSender.sendMessage(ServerTours.translate("commands.errors.noPermission", "servertours.commands.play.other"));
                return;
            }
            if (array[2].equalsIgnoreCase("all")) {
                Bukkit.getOnlinePlayers().forEach(player -> ServerTours.getInstance().getPlaybackManager().showTour(player, route));
                return;
            }
            playerExact = Bukkit.getPlayerExact(array[2]);
            if (playerExact == null) {
                commandSender.sendMessage(ServerTours.translate("commands.errors.playerNotFound", array[2]));
                return;
            }
            if (ServerTours.getInstance().getPlaybackManager().isTouringPlayer(playerExact)) {
                commandSender.sendMessage(ServerTours.translate("commands.errors.alreadyWatchingOther", playerExact.getName()));
                return;
            }
        }
        ServerTours.getInstance().getPlaybackManager().showTour(playerExact, route);
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, String[] array) {
        if (array.length == 2) {
            return Utils.sortTabComplete(ServerTours.getInstance().getRouteManager().getRouteNames(), array[1]);
        }
        if (array.length == 3 && commandSender.hasPermission("servertours.commands.play.other")) {
            return Stream.concat(Bukkit.getOnlinePlayers().stream().map(Player::getName), Stream.of("all")).filter(s -> s.toLowerCase().startsWith(array[2].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public String getPermission() {
        return "servertours.commands.play";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getUsage() {
        return "/tour play <name> [player/all]";
    }

    @Override
    public String getDescription() {
        return "Starts playing a tour route";
    }
}
