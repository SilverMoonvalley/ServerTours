package com.melluh.servertours.route;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.RouteManager;
import com.melluh.servertours.api.event.RouteCreateEvent;
import com.melluh.servertours.api.event.RouteRemoveEvent;
import com.melluh.servertours.api.object.Route;
import org.bukkit.Bukkit;

import java.util.*;
import java.io.IOException;
import java.util.logging.Level;

public class CraftRouteManager implements RouteManager {
    private final Map<String, CraftRoute> routes;

    public CraftRouteManager() {
        this.routes = new HashMap<>();
    }

    public void registerRoute(CraftRoute craftRoute) {
        this.routes.put(craftRoute.getName(), craftRoute);
    }

    /** Registers an already-persisted newly-created route and then publishes its create event. */
    public void registerNewRoute(CraftRoute craftRoute) {
        Objects.requireNonNull(craftRoute, "route may not be null");
        if (this.routes.putIfAbsent(craftRoute.getName(), craftRoute) != null) {
            throw new IllegalStateException("Route with that name already exists");
        }
        Bukkit.getPluginManager().callEvent(new RouteCreateEvent(craftRoute));
    }

    @Override
    public CraftRoute createRoute(String lowerCase) {
        lowerCase = lowerCase.toLowerCase();
        if (this.routes.containsKey(lowerCase)) {
            throw new IllegalStateException("Route with that name already exists");
        }
        if (ServerTours.getInstance().getRecordingManager() != null
                && ServerTours.getInstance().getRecordingManager().isRouteNameReserved(lowerCase)) {
            throw new IllegalStateException("Route name is reserved by a camera recording draft");
        }
        CraftRoute craftRoute = new CraftRoute(lowerCase);
        this.registerNewRoute(craftRoute);
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
            if (this.routes.get(craftRoute.getName()) != craftRoute) {
                return;
            }
            try {
                ServerTours.getInstance().getPersistenceManager().removeRouteChecked(craftRoute);
            } catch (IOException exception) {
                ServerTours.getInstance().getLogger().log(Level.SEVERE,
                        "Could not durably remove route '" + craftRoute.getName()
                                + "'; keeping it and its camera asset registered", exception);
                return;
            }
            this.routes.remove(craftRoute.getName(), craftRoute);
            try {
                ServerTours.getInstance().getEditModeManager().handleRouteRemoval(craftRoute);
            } catch (Throwable throwable) {
                ServerTours.getInstance().getLogger().log(Level.SEVERE,
                        "Route removal cleanup failed for '" + craftRoute.getName() + "'", throwable);
            }
            try {
                Bukkit.getPluginManager().callEvent(new RouteRemoveEvent(obj));
            } catch (Throwable throwable) {
                ServerTours.getInstance().getLogger().log(Level.SEVERE,
                        "RouteRemoveEvent listener failed for '" + craftRoute.getName() + "'", throwable);
            }
            craftRoute.getCameraRecordingId().ifPresent(recordingId -> {
                boolean stillReferenced = this.routes.values().stream()
                        .anyMatch(route -> route.getCameraRecordingId().filter(recordingId::equals).isPresent());
                if (!stillReferenced && ServerTours.getInstance().getRecordingManager() != null) {
                    try {
                        ServerTours.getInstance().getRecordingManager().getRepository().deleteReady(recordingId);
                    } catch (IOException exception) {
                        ServerTours.getInstance().getLogger().log(Level.SEVERE,
                                "Could not remove camera recording " + recordingId, exception);
                    }
                }
            });
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
