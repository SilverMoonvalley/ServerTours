package com.melluh.servertours.cmd.editmode;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.util.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleParticlesSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        boolean boolean1 = !editingPlayer.areParticlesEnabled();
        if (array.length > 1) {
            boolean1 = Utils.parseBoolean(array[1]);
        }
        editingPlayer.setParticlesEnabled(boolean1);
        player.sendMessage(ServerTours.translate(boolean1 ? "commands.particlesEnabled" : "commands.particlesDisabled"));
    }

    @Override
    public String getPermission() {
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour toggleparticles [on|off]";
    }

    @Override
    public String getDescription() {
        return "Toggle edit mode particles";
    }
}
