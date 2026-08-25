package com.melluh.servertours.api.playback;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A clock-driven scene playback session.
 */
public interface PlaybackSession {
    @NotNull PlaybackState getPlaybackState();

    long getCurrentFrame();

    long getDurationFrames();

    double getSceneProgress();

    @Nullable PauseReason getPauseReason();

    void pause();

    void resume();

    boolean isPaused();
}
