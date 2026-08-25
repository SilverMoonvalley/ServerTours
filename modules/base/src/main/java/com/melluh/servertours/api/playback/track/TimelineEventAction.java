package com.melluh.servertours.api.playback.track;

import com.melluh.servertours.api.playback.PlaybackFrame;
import org.jetbrains.annotations.NotNull;

/**
 * An action executed when a playback session crosses a timeline event.
 */
@FunctionalInterface
public interface TimelineEventAction {
    void execute(@NotNull TrackContext context, @NotNull PlaybackFrame frame);
}
