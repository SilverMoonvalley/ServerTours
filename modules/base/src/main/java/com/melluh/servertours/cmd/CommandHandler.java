package com.melluh.servertours.cmd;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final Map<String, SubCommand> subCommands;

    public CommandHandler() {
        this.subCommands = new HashMap<>();
    }

    public static boolean verifyPlayer(CommandSender commandSender) {
        if (commandSender instanceof Player) {
            return true;
        }
        commandSender.sendMessage(ServerTours.translate("commands.errors.playerOnly"));
        return false;
    }

    public static void sendUsage(SubCommand subCommand, CommandSender commandSender) {
        commandSender.sendMessage(ServerTours.translate("commands.errors.usage", subCommand.getUsage()));
    }

    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, String @NotNull [] array) {
        this.execute(commandSender, array);
        return true;
    }

    private void execute(CommandSender commandSender, String[] array) {
        if (array.length == 0) {
            commandSender.sendMessage(String.valueOf(ChatColor.GOLD) + ChatColor.BOLD + "ServerTours " + ChatColor.GOLD + ServerTours.getInstance().getDescription().getVersion());
            for (SubCommand subCommand : this.getSortedSubCommands()) {
                if (subCommand.getDescription() != null) {
                    if (!this.verifyPermission(commandSender, subCommand)) {
                        continue;
                    }
                    commandSender.sendMessage(ChatColor.AQUA + subCommand.getUsage() + ChatColor.GRAY + " " + subCommand.getDescription());
                }
            }
            return;
        }
        SubCommand subCommand2 = this.subCommands.get(array[0].toLowerCase());
        if (subCommand2 == null) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.unknownSubCommand", array[0]));
            return;
        }
        if (subCommand2.isPlayerOnly() && !verifyPlayer(commandSender)) {
            return;
        }
        if (!this.verifyPermission(commandSender, subCommand2)) {
            commandSender.sendMessage(ServerTours.translate("commands.errors.noPermission", subCommand2.getPermission()));
            return;
        }
        subCommand2.execute(commandSender, array);
    }

    private List<SubCommand> getSortedSubCommands() {
        return this.subCommands.values().stream()
                .sorted(Comparator.comparing(SubCommand::getUsage))
                .toList();
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, String[] args) {
        if (args.length == 1) {
            return Utils.sortTabComplete(this.subCommands.entrySet().stream().filter(entry -> entry.getValue().getDescription() != null).filter(entry2 -> this.verifyPermission(sender, entry2.getValue())).map(Map.Entry::getKey).toList(), args[0]);
        }
        if (args.length <= 1) {
            return Collections.emptyList();
        }
        SubCommand subCommand = this.subCommands.get(args[0].toLowerCase());
        if (subCommand == null || !this.verifyPermission(sender, subCommand)) {
            return Collections.emptyList();
        }
        return subCommand.tabComplete(sender, args);
    }

    private boolean verifyPermission(CommandSender commandSender, SubCommand subCommand) {
        return subCommand.getPermission() == null || commandSender.hasPermission(subCommand.getPermission());
    }

    public void register(String s, SubCommand subCommand) {
        this.subCommands.put(s.toLowerCase(), subCommand);
    }

    public interface SubCommand {
        void execute(CommandSender p0, String[] p1);

        default List<String> tabComplete(CommandSender sender, String[] args) {
            return Collections.emptyList();
        }

        String getPermission();

        String getUsage();

        String getDescription();

        default boolean isPlayerOnly() {
            return true;
        }
    }
}
