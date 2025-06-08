package com.melluh.servertours.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RoutesLoadEvent extends Event {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    public static HandlerList getHandlerList() {
        return RoutesLoadEvent.HANDLERS;
    }

    @NotNull
    public HandlerList getHandlers() {
        return RoutesLoadEvent.HANDLERS;
    }
}
