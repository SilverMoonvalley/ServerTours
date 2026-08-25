package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.RecordingSample;

/** Error bounds used while simplifying and validating a camera recording. */
public record RecordingTolerances(double position, double yaw, double pitch) {
    public static final RecordingTolerances DEFAULT = new RecordingTolerances(0.05, 0.5, 0.5);

    public RecordingTolerances {
        requirePositiveFinite("position", position);
        requirePositiveFinite("yaw", yaw);
        requirePositiveFinite("pitch", pitch);
    }

    /**
     * Returns a dimensionless error where values at or below {@code 1} are within tolerance.
     */
    public double normalizedError(RecordingSample expected, RecordingSample actual) {
        double dx = actual.x() - expected.x();
        double dy = actual.y() - expected.y();
        double dz = actual.z() - expected.z();
        double positionError = Math.sqrt(dx * dx + dy * dy + dz * dz) / this.position;
        double yawError = Math.abs(actual.yawUnwrapped() - expected.yawUnwrapped()) / this.yaw;
        double pitchError = Math.abs(actual.pitch() - expected.pitch()) / this.pitch;
        return Math.max(positionError, Math.max(yawError, pitchError));
    }

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " tolerance must be finite and greater than zero");
        }
    }
}
