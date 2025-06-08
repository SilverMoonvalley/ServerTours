package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.util.LocalizableEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.function.Supplier;

public class MenuItemEnum implements ChatMenu.MenuItem {
    private final String label;
    private final Class<? extends Enum<?>> enumClass;
    private final Supplier<Enum<?>> supplier;
    private String command;

    public MenuItemEnum(String label, Class<? extends Enum<?>> enumClass, Supplier<Enum<?>> supplier) {
        this.label = label;
        this.enumClass = enumClass;
        this.supplier = supplier;
    }

    public MenuItemEnum setCommand(String command) {
        this.command = command;
        return this;
    }

    @Override
    public Component build() {
        Enum<?> enum1 = this.supplier.get();
        Component component = Component.text(this.label + ": ").color(NamedTextColor.GRAY);
        Enum<?>[] array = this.enumClass.getEnumConstants();
        for (int length = array.length, i = 0; i < length; ++i) {
            Enum<?> enum2 = array[i];
            LocalizableEnum localizableEnum = (LocalizableEnum) enum2;
            boolean b = localizableEnum == enum1;
            component = component.append(Component.text("[" + localizableEnum.getName()).color(b ? NamedTextColor.AQUA : NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(localizableEnum.getDescription() + "\n\n").append(Component.text(ServerTours.translate(b ? "chatMenu.tooltips.alreadySelected" : "chatMenu.tooltips.select")).color(b ? NamedTextColor.RED : NamedTextColor.GREEN)))).clickEvent(ClickEvent.runCommand(this.command.replace("{}", enum2.name())))).append(Component.text(" "));
        }
        return component;
    }
}
