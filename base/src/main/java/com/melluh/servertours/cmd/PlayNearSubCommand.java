package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PlayNearSubCommand implements CommandHandler.SubCommand {
    private static Location getLocation(CommandSender commandSender) {
        if (commandSender instanceof Player player) {
            return player.getLocation();
        }
        if (commandSender instanceof BlockCommandSender blockCommandSender) {
            return blockCommandSender.getBlock().getLocation();
        }
        return null;
    }

    @Override
    public void execute(CommandSender commandSender, String[] array2) {
        if (array2.length != 3 && array2.length != 4) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }
        CraftRoute route = ServerTours.getInstance().getRouteManager().getRoute(array2[1]);
        if (route == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.routeNotFound", array2[1]));
            return;
        }
        if (route.getNumPoints() < 1) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.noPoints"));
            return;
        }
        Location location = getLocation(commandSender);
        if (location == null) {
            return;
        }
        float safelyParseFloat = Utils.safelyParseFloat(array2[2], -1.0f);
        if (safelyParseFloat < 0.0f) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }

        boolean z = array2.length == 4 && array2[3].equalsIgnoreCase("true");
        float f = safelyParseFloat * safelyParseFloat;
        List<Player> list = Bukkit.getOnlinePlayers().stream()
                .map(Player::getPlayer)
                .filter(Objects::nonNull)
                .filter(player -> player.getWorld() == location.getWorld())
                .filter(player -> player.getLocation().distanceSquared(location) <= f)
                .filter(player -> z || player != commandSender)
                .toList();
        list.forEach(player4 -> ServerTours.getInstance().getPlaybackManager().showTour(player4, route));

        commandSender.sendMessage(ServerTours.translate("commands.playedNear", route.getName(), String.valueOf(list.size())));
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, String[] array) {
        if (array.length == 2) {
            return Utils.sortTabComplete(ServerTours.getInstance().getRouteManager().getRouteNames(), array[1]);
        }
        if (array.length == 4) {
            return Stream.of(new String[]{"true", "false"}).filter(s -> s.startsWith(array[3].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "servertours.commands.playnear";
    }

    @Override
    public String getUsage() {
        return "/tour playnear <name> <range> [include self?]";
    }

    @Override
    public String getDescription() {
        return "Starts playing a tour route for players within a certain range";
    }
}
