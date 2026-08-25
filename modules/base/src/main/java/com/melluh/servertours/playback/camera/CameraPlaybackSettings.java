package com.melluh.servertours.playback.camera;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public record CameraPlaybackSettings(int interpolationTicks, int anchorIntervalFrames,
                                     double maxAnchorDistance) {
    static final int DEFAULT_INTERPOLATION_TICKS = 3;
    static final int DEFAULT_ANCHOR_INTERVAL_FRAMES = 10;
    static final double DEFAULT_MAX_ANCHOR_DISTANCE = 25.0;

    public CameraPlaybackSettings {
        if (interpolationTicks < 0 || interpolationTicks > 59) {
            throw new IllegalArgumentException("interpolationTicks must be between 0 and 59");
        }
        if (anchorIntervalFrames < 1) {
            throw new IllegalArgumentException("anchorIntervalFrames must be positive");
        }
        if (!Double.isFinite(maxAnchorDistance) || maxAnchorDistance <= 0.0) {
            throw new IllegalArgumentException("maxAnchorDistance must be finite and positive");
        }
    }

    public static CameraPlaybackSettings load(ConfigurationSection configuration) {
        Objects.requireNonNull(configuration, "configuration may not be null");
        String root = "playMode.camera.";
        return new CameraPlaybackSettings(
                bounded(configuration.getInt(root + "interpolationTicks", DEFAULT_INTERPOLATION_TICKS),
                        0, 59, DEFAULT_INTERPOLATION_TICKS),
                positive(configuration.getInt(root + "anchorIntervalFrames", DEFAULT_ANCHOR_INTERVAL_FRAMES),
                        DEFAULT_ANCHOR_INTERVAL_FRAMES),
                positiveFinite(configuration.getDouble(root + "maxAnchorDistance", DEFAULT_MAX_ANCHOR_DISTANCE),
                        DEFAULT_MAX_ANCHOR_DISTANCE)
        );
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value >= min && value <= max ? value : fallback;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double positiveFinite(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
