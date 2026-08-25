package com.melluh.servertours.util.math;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentripetalCatmullRomSplineTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void rawAndNormalizedEndpointsAreExact() {
        CentripetalCatmullRomSpline spline = initialized(
                location(-20.0, 3.0, 8.0),
                location(1.0, 2.0, 3.0),
                location(5.0, 7.0, 11.0),
                location(80.0, -4.0, 13.0)
        );

        assertCoordinates(spline.calculate(0.0f), 1.0, 2.0, 3.0);
        assertCoordinates(spline.calculate(1.0f), 5.0, 7.0, 11.0);
        assertCoordinates(spline.calculateNormalized(0.0f), 1.0, 2.0, 3.0);
        assertCoordinates(spline.calculateNormalized(1.0f), 5.0, 7.0, 11.0);
    }

    @Test
    void nonUniformStraightLineStillUsesArcLengthProgress() {
        CentripetalCatmullRomSpline spline = initialized(
                location(-40.0, 0.0, 0.0),
                location(0.0, 0.0, 0.0),
                location(10.0, 0.0, 0.0),
                location(11.0, 0.0, 0.0)
        );

        for (int quarter = 0; quarter <= 4; quarter++) {
            float progress = quarter / 4.0f;
            assertEquals(10.0 * progress, spline.calculateNormalized(progress).getX(), 0.002);
        }
    }

    @Test
    void coincidentControlPointsDoNotProduceInvalidCoordinates() {
        Location start = location(0.0, 0.0, 0.0);
        Location end = location(10.0, 5.0, -2.0);
        CentripetalCatmullRomSpline spline = initialized(start, start, end, end);

        for (int sample = 0; sample <= 100; sample++) {
            Location result = spline.calculateNormalized(sample / 100.0f);
            assertTrue(Double.isFinite(result.getX()));
            assertTrue(Double.isFinite(result.getY()));
            assertTrue(Double.isFinite(result.getZ()));
        }
    }

    @Test
    void allCoincidentPointsHaveZeroLength() {
        Location point = location(4.0, 5.0, 6.0);
        CentripetalCatmullRomSpline spline = initialized(point, point, point, point);

        assertEquals(0.0, spline.getTotalLength(), EPSILON);
        assertCoordinates(spline.calculateNormalized(0.5f), 4.0, 5.0, 6.0);
    }

    private static CentripetalCatmullRomSpline initialized(Location p0, Location p1,
                                                            Location p2, Location p3) {
        CentripetalCatmullRomSpline spline = new CentripetalCatmullRomSpline();
        spline.initialize(p0, p1, p2, p3);
        return spline;
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    private static void assertCoordinates(Location location, double x, double y, double z) {
        assertEquals(x, location.getX(), EPSILON);
        assertEquals(y, location.getY(), EPSILON);
        assertEquals(z, location.getZ(), EPSILON);
    }
}
