package com.melluh.servertours.api.event;

import com.melluh.servertours.api.TouringPlayer;
import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RoutePlaybackEndEvent extends RoutePlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS;

    static {
        HANDLERS = new HandlerList();
    }

    @Getter
    private final EndReason reason;
    private boolean isCancelled;

    public RoutePlaybackEndEvent(TouringPlayer touringPlayer, EndReason reason) {
        super(touringPlayer);
        this.isCancelled = false;
        this.reason = reason;
    }

    public static HandlerList getHandlerList() {
        return RoutePlaybackEndEvent.HANDLERS;
    }

    public @NotNull HandlerList getHandlers() {
        return RoutePlaybackEndEvent.HANDLERS;
    }

    public boolean isCancelled() {
        return this.isCancelled;
    }

    @Override
    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    public enum EndReason {
        FINISHED,
        EXITED,
        QUIT,
        API,
        PLUGIN_DISABLED,
        COMMAND,
        ERROR,
        REPLACED
    }
}
