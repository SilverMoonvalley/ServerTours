package com.melluh.servertours.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.function.Supplier;

public class MenuItemNumber implements ChatMenu.MenuItem {
    private final String label;
    private final Supplier<Float> supplier;
    private String unit;
    private String command;
    private boolean showPlus;

    public MenuItemNumber(String label, Supplier<Float> supplier) {
        this.label = label;
        this.supplier = supplier;
    }

    public MenuItemNumber setUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public MenuItemNumber setShowPlus(boolean showPlus) {
        this.showPlus = showPlus;
        return this;
    }

    public MenuItemNumber setCommand(String command) {
        this.command = command;
        return this;
    }

    private String formatValue(float f) {
        return ((f >= 0.0f && this.showPlus) ? "+" : "") + String.format("%.1f", f) + ((this.unit != null) ? this.unit : "");
    }

    private HoverEvent<Component> createHoverEvent(float n) {
        if (n >= 0.0f) {
            return HoverEvent.showText(Component.text("+" + n + this.unit).color(NamedTextColor.GREEN));
        }
        return HoverEvent.showText(Component.text(n + this.unit).color(NamedTextColor.RED));
    }

    private ClickEvent createClickEvent(float f) {
        return ClickEvent.runCommand(this.command.replace("{}", String.valueOf(f)));
    }

    @Override
    public Component build() {
        float floatValue = this.supplier.get();
        return Component.text(this.label + ": ").color(NamedTextColor.GRAY).append(Component.text("--").color(NamedTextColor.RED).hoverEvent(this.createHoverEvent(-5.0f)).clickEvent(this.createClickEvent(floatValue - 5.0f))).append(Component.text(" ")).append(Component.text("-").color(NamedTextColor.RED).hoverEvent(this.createHoverEvent(-1.0f)).clickEvent(this.createClickEvent(floatValue - 1.0f))).append(Component.text(" ")).append(Component.text(this.formatValue(floatValue)).color(NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text("Click to set value"))).clickEvent(ClickEvent.suggestCommand(this.command.replace("{}", String.valueOf(floatValue))))).append(Component.text(" ")).append(Component.text("+").color(NamedTextColor.GREEN).hoverEvent(this.createHoverEvent(1.0f)).clickEvent(this.createClickEvent(floatValue + 1.0f))).append(Component.text(" ")).append(Component.text("++").color(NamedTextColor.GREEN).hoverEvent(this.createHoverEvent(5.0f)).clickEvent(this.createClickEvent(floatValue + 5.0f)));
    }
}
