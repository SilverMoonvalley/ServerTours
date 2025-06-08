package com.melluh.servertours.cmd.editmode;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreatePointSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        editingPlayer.placePoint();
    }

    @Override
    public String getPermission() {
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour createpoint";
    }

    @Override
    public String getDescription() {
        return "Creates a new route point";
    }
}
