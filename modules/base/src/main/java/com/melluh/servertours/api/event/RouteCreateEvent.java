package com.melluh.servertours.api.event;

import com.melluh.servertours.api.object.Route;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RouteCreateEvent extends RouteEvent {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    public RouteCreateEvent(Route route) {
        super(route);
    }

    public static HandlerList getHandlerList() {
        return RouteCreateEvent.HANDLERS;
    }

    public @NotNull HandlerList getHandlers() {
        return RouteCreateEvent.HANDLERS;
    }
}
