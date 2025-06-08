package com.melluh.servertours.util.math;

import org.bukkit.Location;

public interface Spline {
    void initialize(Location p0, Location p1, Location p2, Location p3);

    Location calculate(float p0);

    Location calculateNormalized(float p0);

    double getTotalLength();
}
