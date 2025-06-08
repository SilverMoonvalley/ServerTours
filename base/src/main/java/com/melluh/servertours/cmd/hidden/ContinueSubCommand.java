package com.melluh.servertours.cmd.hidden;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.playback.CraftTouringPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ContinueSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        CraftTouringPlayer touringPlayer = ServerTours.getInstance().getPlaybackManager().getTouringPlayer(player);
        if (touringPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notWatching"));
            return;
        }
        if (!touringPlayer.isWaitingForConfirmation()) {
            player.sendMessage(ServerTours.translate("commands.errors.notWaiting"));
            return;
        }
        touringPlayer.onConfirm();
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public String getUsage() {
        return "/tour deselect";
    }

    @Override
    public String getDescription() {
        return null;
    }
}
