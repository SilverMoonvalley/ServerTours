package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.route.point.CraftRoutePoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;

public class MenuItemCommands implements ChatMenu.MenuItem {
    private final CraftRoutePoint point;

    public MenuItemCommands(CraftRoutePoint point) {
        this.point = point;
    }

    @Override
    public Component build() {
        Component component = this.buildHeader();
        for (RoutePointCommand routePointCommand : this.point.getCommands()) {
            component = component.append(this.buildListItem(routePointCommand));
        }
        return component;
    }

    private Component buildHeader() {
        int size = this.point.getCommands().size();
        return Component.text(ServerTours.translate("chatMenu.labels.commands") + ": ").color(NamedTextColor.GRAY).append(Component.text((size == 0) ? ServerTours.translate("chatMenu.none") : ServerTours.translate("chatMenu.numSet", Integer.toString(size))).color(NamedTextColor.WHITE)).append(Component.text("[+]").color(NamedTextColor.GREEN).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.instructions.addCommand")))).clickEvent(ClickEvent.suggestCommand("/tour pointcommand add /")));
    }

    private Component buildListItem(RoutePointCommand obj) {
        int index = this.point.getCommands().indexOf(obj);
        String content = "/" + obj.getCommand();
        boolean b = false;
        if (content.length() > 45) {
            content = content.substring(0, 45) + "...";
            b = true;
        }
        Component component = Component.text("\n #" + (index + 1) + ": ").color(NamedTextColor.GRAY).append(Component.text(content).color(NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(b ? ("/" + obj.getCommand() + "\n\n") : "").append(Component.text(ServerTours.translate("chatMenu.tooltips.changeString")).color(NamedTextColor.GREEN)))).clickEvent(ClickEvent.suggestCommand("/tour pointcommand set " + index + " /" + obj.getCommand()))).append(Component.text(" [x]").color(NamedTextColor.RED).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.clearString")))).clickEvent(ClickEvent.runCommand("/tour pointcommand remove " + index))).append(Component.text("\n       "));
        String translate = ServerTours.translate("chatMenu.labels.triggeredBy");
        Class<RoutePointCommand.CommandTrigger> clazz = RoutePointCommand.CommandTrigger.class;
        Objects.requireNonNull(obj);
        return component.append(new MenuItemEnum(translate, clazz, obj::getTriggerType).setCommand("/tour pointcommand settrigger " + index + " {}").build());
    }
}
