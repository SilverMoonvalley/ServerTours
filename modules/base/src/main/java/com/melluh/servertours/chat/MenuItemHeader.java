package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class MenuItemHeader implements ChatMenu.MenuItem {
    private final CraftRoute route;
    private final CraftRoutePoint point;

    public MenuItemHeader(CraftRoute route, CraftRoutePoint point) {
        this.route = route;
        this.point = point;
    }

    @Override
    public Component build() {
        int index = this.route.indexOf(this.point);
        boolean b = index != 0;
        boolean b2 = index != this.route.getNumPoints() - 1;
        return Component.text("                  " + ServerTours.translate("chatMenu.header")).append(Component.text("< ").decorate(TextDecoration.BOLD).color(b ? NamedTextColor.GREEN : NamedTextColor.GRAY).clickEvent(b ? ClickEvent.runCommand("/tour pointaction indexdown") : null)).append(Component.text("#" + (index + 1)).color(NamedTextColor.WHITE)).append(Component.text(" >").decorate(TextDecoration.BOLD).color(b2 ? NamedTextColor.GREEN : NamedTextColor.GRAY).clickEvent(b2 ? ClickEvent.runCommand("/tour pointaction indexup") : null)).append(Component.text("\n" + ServerTours.translate("chatMenu.buttons.preview")).color(NamedTextColor.AQUA).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.preview")))).clickEvent(ClickEvent.runCommand("/tour pointaction preview"))).append(Component.text(" ")).append(Component.text(ServerTours.translate("chatMenu.buttons.teleport")).color(NamedTextColor.AQUA).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.teleport")))).clickEvent(ClickEvent.runCommand("/tour pointaction teleport"))).append(Component.text(" ")).append(Component.text(ServerTours.translate("chatMenu.buttons.move")).color(NamedTextColor.AQUA).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.move")))).clickEvent(ClickEvent.runCommand("/tour pointaction movehere"))).append(Component.text(" ")).append(Component.text(ServerTours.translate("chatMenu.buttons.remove")).color(NamedTextColor.RED).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.remove")))).clickEvent(ClickEvent.runCommand("/tour pointaction remove"))).append(Component.text(" ")).append(Component.text(ServerTours.translate("chatMenu.buttons.close")).color(NamedTextColor.YELLOW).hoverEvent(HoverEvent.showText(Component.text(ServerTours.translate("chatMenu.tooltips.close")))).clickEvent(ClickEvent.runCommand("/tour pointaction deselect")));
    }
}
