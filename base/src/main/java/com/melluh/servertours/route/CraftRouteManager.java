package com.melluh.servertours.route;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.RouteManager;
import com.melluh.servertours.api.event.RouteCreateEvent;
import com.melluh.servertours.api.event.RouteRemoveEvent;
import com.melluh.servertours.api.object.Route;
import org.bukkit.Bukkit;

import java.util.*;

public class CraftRouteManager implements RouteManager {
    private final Map<String, CraftRoute> routes;

    public CraftRouteManager() {
        this.routes = new HashMap<>();
    }

    public void registerRoute(CraftRoute craftRoute) {
        this.routes.put(craftRoute.getName(), craftRoute);
    }

    @Override
    public CraftRoute createRoute(String lowerCase) {
        lowerCase = lowerCase.toLowerCase();
        if (this.routes.containsKey(lowerCase)) {
            throw new IllegalStateException("Route with that name already exists");
        }
        CraftRoute craftRoute = new CraftRoute(lowerCase);
        this.routes.put(lowerCase, craftRoute);
        Bukkit.getPluginManager().callEvent(new RouteCreateEvent(craftRoute));
        return craftRoute;
    }

    @Override
    public void removeRoute(String s) {
        CraftRoute craftRoute = this.routes.get(s.toLowerCase());
        if (craftRoute != null) {
            this.removeRoute(craftRoute);
        }
    }

    @Override
    public void removeRoute(Route obj) {
        Objects.requireNonNull(obj, "route may not be null");
        if (obj instanceof CraftRoute craftRoute) {
            this.routes.remove(obj.getName());
            ServerTours.getInstance().getEditModeManager().handleRouteRemoval(craftRoute);
            ServerTours.getInstance().getPersistenceManager().removeRoute(craftRoute);
            Bukkit.getPluginManager().callEvent(new RouteRemoveEvent(obj));
            return;
        }
        throw new IllegalArgumentException("route must be an instance of CraftRoute");
    }

    @Override
    public CraftRoute getRoute(String s) {
        return this.routes.get(s.toLowerCase());
    }

    @Override
    public Set<Route> getRoutes() {
        return new HashSet<>(this.routes.values());
    }

    @Override
    public Set<String> getRouteNames() {
        return new HashSet<>(this.routes.keySet());
    }
}
