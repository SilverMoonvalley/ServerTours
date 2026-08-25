package com.melluh.servertours.util.math;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatmullRomRotationSplineTest {
    private static final double EPSILON = 1.0e-5;

    @Test
    void yawIsUnwrappedAcrossPositiveBoundary() {
        CatmullRomRotationSpline spline = initialized(
                rotation(160.0f, 0.0f),
                rotation(170.0f, 10.0f),
                rotation(-170.0f, 20.0f),
                rotation(-160.0f, 30.0f)
        );

        Location midpoint = rotation(0.0f, 0.0f);
        spline.apply(midpoint, 0.5f);
        assertEquals(180.0, midpoint.getYaw(), EPSILON);
        assertEquals(15.0, midpoint.getPitch(), EPSILON);

        Location endpoint = rotation(0.0f, 0.0f);
        spline.apply(endpoint, 1.0f);
        assertEquals(190.0, endpoint.getYaw(), EPSILON);
        assertEquals(20.0, endpoint.getPitch(), EPSILON);
    }

    @Test
    void yawIsUnwrappedAcrossNegativeBoundary() {
        CatmullRomRotationSpline spline = initialized(
                rotation(-160.0f, 0.0f),
                rotation(-170.0f, 0.0f),
                rotation(170.0f, 0.0f),
                rotation(160.0f, 0.0f)
        );

        Location midpoint = rotation(0.0f, 0.0f);
        spline.apply(midpoint, 0.5f);
        assertEquals(-180.0, midpoint.getYaw(), EPSILON);
    }

    @Test
    void adjacentSegmentsKeepTheSameUnwrappedYawBranch() {
        CatmullRomRotationSpline first = initialized(
                rotation(150.0f, 0.0f),
                rotation(170.0f, 0.0f),
                rotation(-170.0f, 0.0f),
                rotation(-150.0f, 0.0f)
        );
        CatmullRomRotationSpline second = initialized(
                rotation(170.0f, 0.0f),
                rotation(-170.0f, 0.0f),
                rotation(-150.0f, 0.0f),
                rotation(-130.0f, 0.0f)
        );

        Location firstEnd = rotation(0.0f, 0.0f);
        Location secondStart = rotation(0.0f, 0.0f);
        first.apply(firstEnd, 1.0f);
        second.apply(secondStart, 0.0f);
        assertEquals(firstEnd.getYaw(), secondStart.getYaw(), EPSILON);
        assertEquals(190.0, secondStart.getYaw(), EPSILON);
    }

    @Test
    void pitchOvershootIsClampedToMinecraftRange() {
        CatmullRomRotationSpline spline = initialized(
                rotation(0.0f, -90.0f),
                rotation(0.0f, 80.0f),
                rotation(0.0f, 80.0f),
                rotation(0.0f, -90.0f)
        );

        Location midpoint = rotation(0.0f, 0.0f);
        spline.apply(midpoint, 0.5f);
        assertEquals(90.0, midpoint.getPitch(), EPSILON);
    }

    @Test
    void endpointsAndProgressValidationAreDeterministic() {
        CatmullRomRotationSpline spline = initialized(
                rotation(0.0f, -10.0f),
                rotation(10.0f, 5.0f),
                rotation(40.0f, 25.0f),
                rotation(70.0f, 30.0f)
        );

        Location before = rotation(0.0f, 0.0f);
        spline.apply(before, -1.0f);
        assertEquals(10.0, before.getYaw(), EPSILON);
        assertEquals(5.0, before.getPitch(), EPSILON);

        Location after = rotation(0.0f, 0.0f);
        spline.apply(after, 2.0f);
        assertEquals(40.0, after.getYaw(), EPSILON);
        assertEquals(25.0, after.getPitch(), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> spline.apply(after, Float.NaN));
    }

    private static CatmullRomRotationSpline initialized(Location p0, Location p1,
                                                         Location p2, Location p3) {
        CatmullRomRotationSpline spline = new CatmullRomRotationSpline();
        spline.initialize(p0, p1, p2, p3);
        return spline;
    }

    private static Location rotation(float yaw, float pitch) {
        return new Location(null, 0.0, 0.0, 0.0, yaw, pitch);
    }
}
