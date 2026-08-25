package com.melluh.servertours.recording.model;

/**
 * One camera pose captured at a monotonic time relative to the start of a recording.
 *
 * <p>Yaw is intentionally stored unwrapped so recordings can preserve complete turns
 * instead of taking the shortest path between every compiled keyframe.</p>
 */
public record RecordingSample(long timeNanos, double x, double y, double z,
                              double yawUnwrapped, double pitch) {
    public RecordingSample {
        if (timeNanos < 0L) {
            throw new IllegalArgumentException("timeNanos cannot be negative");
        }
        requireFinite("x", x);
        requireFinite("y", y);
        requireFinite("z", z);
        requireFinite("yawUnwrapped", yawUnwrapped);
        requireFinite("pitch", pitch);
        if (pitch < -90.0 || pitch > 90.0) {
            throw new IllegalArgumentException("pitch must be between -90 and 90 degrees");
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
