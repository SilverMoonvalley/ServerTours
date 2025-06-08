package com.melluh.servertours.api.event;

import com.melluh.servertours.api.object.Route;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RouteRemoveEvent extends RouteEvent {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    public RouteRemoveEvent(Route route) {
        super(route);
    }

    public static HandlerList getHandlerList() {
        return RouteRemoveEvent.HANDLERS;
    }

    public @NotNull HandlerList getHandlers() {
        return RouteRemoveEvent.HANDLERS;
    }
}
