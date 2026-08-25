package com.melluh.servertours.recording.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YawUnwrapperTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void preservesContinuousTurnsAcrossWrappedBoundary() {
        YawUnwrapper unwrapper = new YawUnwrapper();

        assertEquals(170.0, unwrapper.accept(170.0), EPSILON);
        assertEquals(190.0, unwrapper.accept(-170.0), EPSILON);
        assertEquals(350.0, unwrapper.accept(-10.0), EPSILON);
        assertEquals(370.0, unwrapper.accept(10.0), EPSILON);
        assertTrue(unwrapper.isInitialized());
        assertEquals(370.0, unwrapper.getLastYaw(), EPSILON);
    }

    @Test
    void exactHalfTurnUsesSignOfRawDelta() {
        assertEquals(180.0, YawUnwrapper.unwrap(0.0, 180.0), EPSILON);
        assertEquals(-180.0, YawUnwrapper.unwrap(0.0, -180.0), EPSILON);
        assertEquals(-10.0, YawUnwrapper.unwrap(170.0, -10.0), EPSILON);
        assertEquals(10.0, YawUnwrapper.unwrap(-170.0, 10.0), EPSILON);
    }

    @Test
    void resetForgetsPreviousSequence() {
        YawUnwrapper unwrapper = new YawUnwrapper();
        unwrapper.accept(170.0);
        unwrapper.accept(-170.0);
        unwrapper.reset();

        assertFalse(unwrapper.isInitialized());
        assertThrows(IllegalStateException.class, unwrapper::getLastYaw);
        assertEquals(-170.0, unwrapper.accept(-170.0), EPSILON);
    }
}
