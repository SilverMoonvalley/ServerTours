package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.RecordingSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeAwareRdpTest {
    @Test
    void preservesTimingChangesEvenWhenPositionsAreGeometricallyCollinear() {
        List<RecordingSample> raw = List.of(
                sample(0L, 0.0, 0.0, 0.0),
                sample(50_000_000L, 9.0, 0.0, 0.0),
                sample(100_000_000L, 10.0, 0.0, 0.0)
        );

        assertEquals(List.of(0, 1, 2), TimeAwareRdp.simplify(raw, RecordingTolerances.DEFAULT));
    }

    @Test
    void rotationAloneCanRetainAKeyframe() {
        List<RecordingSample> raw = List.of(
                sample(0L, 0.0, 0.0, 0.0),
                sample(50_000_000L, 0.0, 5.0, 0.0),
                sample(100_000_000L, 0.0, 0.0, 0.0)
        );

        assertEquals(List.of(0, 1, 2), TimeAwareRdp.simplify(raw, RecordingTolerances.DEFAULT));
    }

    @Test
    void constantVelocitySamplesCollapseToEndpoints() {
        List<RecordingSample> raw = List.of(
                sample(0L, 0.0, 0.0, 0.0),
                sample(50_000_000L, 1.0, 5.0, 0.0),
                sample(100_000_000L, 2.0, 10.0, 0.0),
                sample(150_000_000L, 3.0, 15.0, 0.0)
        );

        assertEquals(List.of(0, 3), TimeAwareRdp.simplify(raw, RecordingTolerances.DEFAULT));
    }

    private static RecordingSample sample(long time, double x, double yaw, double pitch) {
        return new RecordingSample(time, x, 0.0, 0.0, yaw, pitch);
    }
}
