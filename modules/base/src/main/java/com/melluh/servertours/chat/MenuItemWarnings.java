package com.melluh.servertours.chat;

import com.melluh.servertours.route.point.CraftRoutePoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public class MenuItemWarnings implements ChatMenu.MenuItem {
    private final CraftRoutePoint point;

    public MenuItemWarnings(CraftRoutePoint point) {
        this.point = point;
    }

    @Override
    public Component build() {
        List<String> warnings = this.point.getWarnings();
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        Component component = Component.text("");
        for (String warning : warnings) {
            component = component.append(LegacyComponentSerializer.legacySection().deserialize(warning)).append(Component.text("\n"));
        }
        return component;
    }
}
