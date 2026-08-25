package com.melluh.servertours.api.playback.track;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A discrete track whose events are dispatched exactly once while the playhead
 * crosses their frames. The engine snapshots the list before setup and marks
 * each event consumed before invoking its action.
 */
public interface EventTrackRuntime extends TrackRuntime {
    /**
     * Returns this runtime's immutable event list. Event identifiers must be
     * unique within the runtime.
     */
    @NotNull List<TimelineEvent> events();
}
