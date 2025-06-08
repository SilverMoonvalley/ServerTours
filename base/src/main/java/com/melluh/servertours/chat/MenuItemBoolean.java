package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.function.Supplier;

public class MenuItemBoolean implements ChatMenu.MenuItem {
    private final String label;
    private final Supplier<Boolean> supplier;
    private String description;
    private String command;

    public MenuItemBoolean(String label, Supplier<Boolean> supplier) {
        this.label = label;
        this.supplier = supplier;
    }

    public MenuItemBoolean setDescription(String description) {
        this.description = description;
        return this;
    }

    public MenuItemBoolean setCommand(String command) {
        this.command = command;
        return this;
    }

    @Override
    public Component build() {
        boolean booleanValue = this.supplier.get();
        return Component.text(this.label + ": ").color(NamedTextColor.GRAY).append(Component.text(booleanValue ? "✔" : "✘").color(booleanValue ? NamedTextColor.GREEN : NamedTextColor.RED).hoverEvent(HoverEvent.showText(Component.text(this.description + "\n\n").append(Component.text(ServerTours.translate(booleanValue ? "chatMenu.tooltips.disable" : "chatMenu.tooltips.enable")).color(NamedTextColor.GREEN)))).clickEvent(ClickEvent.runCommand(this.command.replace("{}", String.valueOf(!booleanValue)))));
    }
}
