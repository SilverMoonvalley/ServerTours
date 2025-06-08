package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.playback.CraftTouringPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class StopSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        if (array.length != 2) {
            CommandHandler.sendUsage(this, commandSender);
            return;
        }
        if (array[1].equalsIgnoreCase("all")) {
            List<CraftTouringPlayer> touringPlayers = ServerTours.getInstance().getPlaybackManager().getTouringPlayers();
            touringPlayers.forEach(craftTouringPlayer -> craftTouringPlayer.exit(RoutePlaybackEndEvent.EndReason.COMMAND));
            commandSender.sendMessage(ServerTours.translate("commands.stoppedAll", touringPlayers.size()));
            return;
        }
        Player playerExact = Bukkit.getPlayerExact(array[1]);
        if (playerExact == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.playerNotFound", array[1]));
            return;
        }
        CraftTouringPlayer touringPlayer = ServerTours.getInstance().getPlaybackManager().getTouringPlayer(playerExact);
        if (touringPlayer == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.notWatchingOther", playerExact.getName()));
            return;
        }
        touringPlayer.exit(RoutePlaybackEndEvent.EndReason.COMMAND);
        commandSender.sendMessage(ServerTours.translate("commands.stoppedOther", touringPlayer.getRoute().getName(), playerExact.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, String[] array) {
        if (array.length == 2) {
            return Stream.concat(Bukkit.getOnlinePlayers().stream().map(Player::getName), Stream.of("all")).filter(s -> s.toLowerCase().startsWith(array[1].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public String getPermission() {
        return "servertours.commands.stop";
    }

    @Override
    public String getUsage() {
        return "/tour stop <player/all>";
    }

    @Override
    public String getDescription() {
        return "Stops tour playback for the specified player";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
