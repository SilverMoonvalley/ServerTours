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

    /**
     * Returns the positional interpolation used by this route.
     *
     * <p>The default keeps third-party {@code Route} implementations binary
     * compatible while selecting the current ServerTours default.</p>
     */
    default PositionInterpolationMode getPositionInterpolationMode() {
        return PositionInterpolationMode.CENTRIPETAL_CATMULL_ROM;
    }

    default void setPositionInterpolationMode(PositionInterpolationMode mode) {
        throw new UnsupportedOperationException("this route does not support configurable position interpolation");
    }

    /** Returns the rotation interpolation used by this route. */
    default RotationInterpolationMode getRotationInterpolationMode() {
        return RotationInterpolationMode.CATMULL_ROM;
    }

    default void setRotationInterpolationMode(RotationInterpolationMode mode) {
        throw new UnsupportedOperationException("this route does not support configurable rotation interpolation");
    }

    void saveToDisk();
}
