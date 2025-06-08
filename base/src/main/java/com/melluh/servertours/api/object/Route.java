package com.melluh.servertours.api.object;

import org.bukkit.Location;

import java.util.List;

public interface Route {
    RoutePoint createPoint(Location p0, RoutePointType p1);

    RoutePoint insertPoint(int p0, Location p1, RoutePointType p2);

    RoutePoint getPoint(int p0);

    List<? extends RoutePoint> getPoints();

    int getNumPoints();

    int indexOf(RoutePoint p0);

    void removePoint(int p0);

    void removePoint(RoutePoint p0);

    String getName();

    void saveToDisk();
}
