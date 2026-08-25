package com.melluh.servertours.util.math;

import org.bukkit.Location;

/**
 * Catmull-Rom segment using the centripetal ({@code alpha = 0.5}) knot spacing.
 */
public final class CentripetalCatmullRomSpline extends ArcLengthParameterizedSpline {
    private static final double MINIMUM_KNOT_DELTA = 1.0e-6;

    private double t0;
    private double t1;
    private double t2;
    private double t3;

    @Override
    protected void onControlPointsInitialized() {
        this.t0 = 0.0;
        this.t1 = this.t0 + knotDelta(this.p0, this.p1);
        this.t2 = this.t1 + knotDelta(this.p1, this.p2);
        this.t3 = this.t2 + knotDelta(this.p2, this.p3);
    }

    @Override
    protected Location interpolate(float progress) {
        if (progress <= 0.0f) {
            return point(this.p1.getX(), this.p1.getY(), this.p1.getZ());
        }
        if (progress >= 1.0f) {
            return point(this.p2.getX(), this.p2.getY(), this.p2.getZ());
        }

        double parameter = this.t1 + (this.t2 - this.t1) * progress;
        return point(
                interpolateCoordinate(this.p0.getX(), this.p1.getX(), this.p2.getX(), this.p3.getX(), parameter),
                interpolateCoordinate(this.p0.getY(), this.p1.getY(), this.p2.getY(), this.p3.getY(), parameter),
                interpolateCoordinate(this.p0.getZ(), this.p1.getZ(), this.p2.getZ(), this.p3.getZ(), parameter)
        );
    }

    private double interpolateCoordinate(double p0, double p1, double p2, double p3, double parameter) {
        double a1 = blend(p0, p1, this.t0, this.t1, parameter);
        double a2 = blend(p1, p2, this.t1, this.t2, parameter);
        double a3 = blend(p2, p3, this.t2, this.t3, parameter);
        double b1 = blend(a1, a2, this.t0, this.t2, parameter);
        double b2 = blend(a2, a3, this.t1, this.t3, parameter);
        return blend(b1, b2, this.t1, this.t2, parameter);
    }

    private Location point(double x, double y, double z) {
        return new Location(this.p1.getWorld(), x, y, z);
    }

    private static double blend(double first, double second, double firstParameter,
                                double secondParameter, double parameter) {
        double span = secondParameter - firstParameter;
        double firstWeight = (secondParameter - parameter) / span;
        return firstWeight * first + (1.0 - firstWeight) * second;
    }

    private static double knotDelta(Location first, Location second) {
        double x = second.getX() - first.getX();
        double y = second.getY() - first.getY();
        double z = second.getZ() - first.getZ();
        double squaredDistance = x * x + y * y + z * z;
        return Math.max(MINIMUM_KNOT_DELTA, Math.pow(squaredDistance, 0.25));
    }
}
