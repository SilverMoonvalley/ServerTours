package com.melluh.servertours.util.math;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardinalSplineTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void normalizedEndpointsAreExact() {
        CardinalSpline spline = initialized(
                location(-4.0, 3.0, 1.0),
                location(2.0, 5.0, -3.0),
                location(13.0, -2.0, 8.0),
                location(20.0, 4.0, 17.0)
        );

        assertCoordinates(spline.calculateNormalized(0.0f), 2.0, 5.0, -3.0, 0.0);
        assertCoordinates(spline.calculateNormalized(1.0f), 13.0, -2.0, 8.0, 0.0);
    }

    @Test
    void rawLegacyCurveBasisIsUnchanged() {
        CardinalSpline spline = initialized(
                location(0.0, 0.0, 0.0),
                location(1.0, 2.0, 3.0),
                location(5.0, 7.0, 11.0),
                location(13.0, 17.0, 19.0)
        );

        assertCoordinates(spline.calculate(0.5f), 2.125, 3.5, 6.375, EPSILON);
    }

    @Test
    void straightLineUsesApproximatelyEqualArcLengthFractions() {
        CardinalSpline spline = initialized(
                location(-10.0, 0.0, 0.0),
                location(0.0, 0.0, 0.0),
                location(10.0, 0.0, 0.0),
                location(20.0, 0.0, 0.0)
        );

        for (int quarter = 0; quarter <= 4; quarter++) {
            float progress = quarter / 4.0f;
            assertEquals(10.0 * progress, spline.calculateNormalized(progress).getX(), 0.002);
        }
    }

    @Test
    void degenerateCurveRemainsFinite() {
        Location point = location(3.0, -7.0, 11.0);
        CardinalSpline spline = initialized(point, point, point, point);

        assertEquals(0.0, spline.getTotalLength(), EPSILON);
        for (float progress : new float[]{0.0f, 0.25f, 0.5f, 1.0f}) {
            Location result = spline.calculateNormalized(progress);
            assertCoordinates(result, 3.0, -7.0, 11.0, EPSILON);
            assertFinite(result);
        }
    }

    @Test
    void progressIsClampedAndMustBeFinite() {
        CardinalSpline spline = initialized(
                location(-1.0, 0.0, 0.0),
                location(0.0, 0.0, 0.0),
                location(1.0, 0.0, 0.0),
                location(2.0, 0.0, 0.0)
        );

        assertEquals(0.0, spline.calculateNormalized(-1.0f).getX(), EPSILON);
        assertEquals(1.0, spline.calculateNormalized(2.0f).getX(), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> spline.calculateNormalized(Float.NaN));
    }

    private static CardinalSpline initialized(Location p0, Location p1, Location p2, Location p3) {
        CardinalSpline spline = new CardinalSpline();
        spline.initialize(p0, p1, p2, p3);
        return spline;
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    private static void assertCoordinates(Location location, double x, double y, double z, double tolerance) {
        assertEquals(x, location.getX(), tolerance);
        assertEquals(y, location.getY(), tolerance);
        assertEquals(z, location.getZ(), tolerance);
    }

    private static void assertFinite(Location location) {
        assertTrue(Double.isFinite(location.getX()));
        assertTrue(Double.isFinite(location.getY()));
        assertTrue(Double.isFinite(location.getZ()));
    }
}
