package com.melluh.servertours.api;

import com.melluh.servertours.api.object.Route;

import java.util.Set;

public interface RouteManager {
    Route createRoute(String p0);

    void removeRoute(String p0);

    void removeRoute(Route p0);

    Route getRoute(String p0);

    Set<? extends Route> getRoutes();

    Set<String> getRouteNames();
}
