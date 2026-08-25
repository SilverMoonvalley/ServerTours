package com.melluh.servertours.playback.timeline;

import com.melluh.servertours.route.point.CraftRoutePoint;

import java.util.Objects;

/**
 * An immutable camera sample within a compiled route timeline.
 */
public record TimelinePosition(
        long frame,
        int pointIndex,
        CraftRoutePoint point,
        long pointStartFrame,
        long pointEndFrame,
        float progress
) {
    public TimelinePosition {
        Objects.requireNonNull(point, "point may not be null");
        if (frame < 0L || pointStartFrame < 0L || pointEndFrame < pointStartFrame) {
            throw new IllegalArgumentException("timeline frames must be non-negative and ordered");
        }
        if (pointIndex < 0) {
            throw new IllegalArgumentException("pointIndex may not be negative");
        }
        if (!Float.isFinite(progress) || progress < 0.0f || progress > 1.0f) {
            throw new IllegalArgumentException("progress must be finite and between 0 and 1");
        }
    }

    public long pointDuration() {
        return this.pointEndFrame - this.pointStartFrame;
    }
}
