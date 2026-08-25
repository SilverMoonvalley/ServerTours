package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.editmode.EditingPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExitSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        editingPlayer.exit();
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public String getUsage() {
        return "/tour exit";
    }

    @Override
    public String getDescription() {
        return "Exit edit mode";
    }
}
