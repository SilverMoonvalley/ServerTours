package com.melluh.servertours.api.event;

import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.object.RoutePoint;
import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class RoutePlaybackPointEvent extends RoutePlayerEvent {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    private final RoutePoint point;

    public RoutePlaybackPointEvent(TouringPlayer touringPlayer, RoutePoint point) {
        super(touringPlayer);
        this.point = point;
    }

    public static HandlerList getHandlerList() {
        return RoutePlaybackPointEvent.HANDLERS;
    }

    @NotNull
    public HandlerList getHandlers() {
        return RoutePlaybackPointEvent.HANDLERS;
    }
}
