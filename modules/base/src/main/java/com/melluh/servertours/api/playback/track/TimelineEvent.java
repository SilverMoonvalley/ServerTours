package com.melluh.servertours.api.playback.track;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A discrete action anchored to an absolute scene frame.
 *
 * @param id     identifier unique within its event track
 * @param frame  non-negative absolute scene frame
 * @param action action to execute when the playhead crosses the frame
 */
public record TimelineEvent(@NotNull String id, long frame, @NotNull TimelineEventAction action) {
    public TimelineEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (frame < 0L) {
            throw new IllegalArgumentException("frame must be non-negative");
        }
    }
}
