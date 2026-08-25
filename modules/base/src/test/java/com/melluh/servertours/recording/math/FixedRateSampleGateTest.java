package com.melluh.servertours.recording.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedRateSampleGateTest {
    @Test
    void acceptsAtMostOneRealSamplePerFiftyMillisecondBucket() {
        FixedRateSampleGate gate = new FixedRateSampleGate();

        assertTrue(gate.shouldCapture(0L));
        assertFalse(gate.shouldCapture(20_000_000L));
        assertFalse(gate.shouldCapture(49_999_999L));
        assertTrue(gate.shouldCapture(50_000_000L));
        assertTrue(gate.shouldCapture(350_000_000L));
        assertFalse(gate.shouldCapture(351_000_000L));
    }

    @Test
    void rejectsTimeGoingBackwardsAndCanBeReset() {
        FixedRateSampleGate gate = new FixedRateSampleGate();
        gate.shouldCapture(100_000_000L);

        assertThrows(IllegalArgumentException.class, () -> gate.shouldCapture(99_999_999L));
        gate.reset();
        assertTrue(gate.shouldCapture(0L));
    }
}
