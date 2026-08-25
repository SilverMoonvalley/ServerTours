package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.CameraSource;
import com.melluh.servertours.recording.RecordingManager;
import com.melluh.servertours.util.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Command surface for camera capture, drafts and source switching. */
public final class RecordSubCommand implements CommandHandler.SubCommand {
    private static final List<String> ACTIONS = List.of(
            "start", "stop", "cancel", "drafts", "resume", "discard", "source");

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandHandler.sendUsage(this, sender);
            return;
        }
        RecordingManager manager = ServerTours.getInstance().getRecordingManager();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                if (args.length != 3 || !CommandHandler.verifyPlayer(sender)) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.start((Player) sender, args[2]);
            }
            case "stop" -> {
                if (args.length != 2 || !CommandHandler.verifyPlayer(sender)) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.stop((Player) sender);
            }
            case "cancel" -> {
                if (args.length != 2 || !CommandHandler.verifyPlayer(sender)) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.cancel((Player) sender);
            }
            case "drafts" -> {
                if (args.length != 2) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.listDrafts(sender);
            }
            case "resume" -> {
                if (args.length != 3 || !CommandHandler.verifyPlayer(sender)) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.resume((Player) sender, args[2]);
            }
            case "discard" -> {
                if (args.length != 3) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.discardDraft(sender, args[2]);
            }
            case "source" -> {
                if (args.length != 4) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                CameraSource source;
                try {
                    source = CameraSource.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    CommandHandler.sendUsage(this, sender);
                    return;
                }
                manager.setSource(sender, args[2], source);
            }
            default -> CommandHandler.sendUsage(this, sender);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Utils.sortTabComplete(ACTIONS, args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("resume")
                || args[1].equalsIgnoreCase("discard"))) {
            return Utils.sortTabComplete(
                    ServerTours.getInstance().getRecordingManager().draftNamesFor(sender), args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("source")) {
            return Utils.sortTabComplete(
                    ServerTours.getInstance().getRouteManager().getRouteNames(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("source")) {
            return Utils.sortTabComplete(List.of("points", "recorded"), args[3]);
        }
        return List.of();
    }

    @Override
    public String getPermission() {
        return "servertours.commands.record";
    }

    @Override
    public String getUsage() {
        return "/tour record <start <name>|stop|cancel|drafts|resume <name>|discard <name>|source <route> <points|recorded>>";
    }

    @Override
    public String getDescription() {
        return "Record and manage timestamped camera paths";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
