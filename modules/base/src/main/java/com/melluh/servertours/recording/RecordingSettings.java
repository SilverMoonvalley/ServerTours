package com.melluh.servertours.recording;

import com.melluh.servertours.recording.math.RecordingTolerances;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

/** Validated capture and compilation settings loaded from config.yml. */
public record RecordingSettings(long maxDurationNanos, RecordingTolerances tolerances) {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public RecordingSettings {
        if (maxDurationNanos <= 0L) {
            throw new IllegalArgumentException("maxDurationNanos must be greater than zero");
        }
        tolerances = Objects.requireNonNull(tolerances, "tolerances may not be null");
    }

    public static RecordingSettings load(FileConfiguration config) {
        Objects.requireNonNull(config, "config may not be null");
        long maxSeconds = config.getLong("recording.maxDurationSeconds", 300L);
        if (maxSeconds <= 0L || maxSeconds > Long.MAX_VALUE / NANOS_PER_SECOND) {
            throw new IllegalArgumentException("recording.maxDurationSeconds must be greater than zero");
        }
        double position = config.getDouble("recording.positionTolerance", 0.05D);
        double rotation = config.getDouble("recording.rotationToleranceDegrees", 0.5D);
        return new RecordingSettings(maxSeconds * NANOS_PER_SECOND,
                new RecordingTolerances(position, rotation, rotation));
    }
}
