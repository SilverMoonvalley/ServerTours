package com.melluh.servertours.api.event;

import com.melluh.servertours.api.object.Route;
import lombok.Getter;
import org.bukkit.event.Event;

@Getter
public abstract class RouteEvent extends Event {
    private final Route route;

    protected RouteEvent(Route route) {
        this.route = route;
    }

    public String getRouteName() {
        return this.route.getName();
    }
}
