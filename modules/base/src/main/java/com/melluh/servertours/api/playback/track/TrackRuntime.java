package com.melluh.servertours.api.playback.track;

import com.melluh.servertours.api.playback.PauseReason;
import org.jetbrains.annotations.NotNull;

/**
 * Per-session state owned by a registered track factory. All callbacks run on
 * the Bukkit main thread. Lifecycle methods are delivered at most once for
 * each corresponding session transition.
 */
public interface TrackRuntime {
    long getEndFrame();

    default void setup(@NotNull TrackContext context) {
    }

    default void onPause(@NotNull TrackContext context, @NotNull PauseReason reason) {
    }

    default void onResume(@NotNull TrackContext context) {
    }

    default void teardown(@NotNull TrackContext context) {
    }
}
