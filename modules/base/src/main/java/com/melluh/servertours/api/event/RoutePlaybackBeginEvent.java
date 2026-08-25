package com.melluh.servertours.api.event;

import com.melluh.servertours.api.TouringPlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RoutePlaybackBeginEvent extends RoutePlayerEvent {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    public RoutePlaybackBeginEvent(TouringPlayer touringPlayer) {
        super(touringPlayer);
    }

    public static HandlerList getHandlerList() {
        return RoutePlaybackBeginEvent.HANDLERS;
    }

    public @NotNull HandlerList getHandlers() {
        return RoutePlaybackBeginEvent.HANDLERS;
    }
}
