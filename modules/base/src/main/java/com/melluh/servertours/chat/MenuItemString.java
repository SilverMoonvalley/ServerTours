package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

import java.util.function.Supplier;

public class MenuItemString implements ChatMenu.MenuItem {
    private final String label;
    private final Supplier<String> valueSupplier;
    private String command;
    private String instruction;

    public MenuItemString(String label, Supplier<String> valueSupplier) {
        this.label = label;
        this.valueSupplier = valueSupplier;
    }

    public MenuItemString setCommand(String command) {
        this.command = command;
        return this;
    }

    public MenuItemString setInstruction(String instruction) {
        this.instruction = instruction;
        return this;
    }

    @Override
    public Component build() {
        Component color = Component.text(this.label + ": ").color(NamedTextColor.GRAY);
        String replacement = this.valueSupplier.get();
        if (replacement == null) {
            return color.append(Component.text(ServerTours.translate("chatMenu.none")).color(NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(this.instruction).append(Component.text("\n\n" + ServerTours.translate("chatMenu.tooltips.setString")).color(NamedTextColor.GREEN)))).clickEvent(ClickEvent.suggestCommand(this.command.replace("{}", ""))));
        }
        boolean b = false;
        String s = replacement;
        if (s.length() > 15) {
            s = s.substring(0, 15) + ChatColor.WHITE + "...";
            b = true;
        }
        return color.append(Component.text(ChatColor.translateAlternateColorCodes('&', s)).color(NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(b ? (replacement + "\n\n") : "").append(Component.text(ServerTours.translate("chatMenu.tooltips.changeString")).color(NamedTextColor.GREEN)))).clickEvent(ClickEvent.suggestCommand(this.command.replace("{}", replacement)))).append(Component.text("[x]").color(NamedTextColor.RED).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.clearString")))).clickEvent(ClickEvent.runCommand(this.command.replace("{}", "clear"))));
    }
}
