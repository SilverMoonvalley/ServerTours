package com.melluh.servertours.api.playback.track;

import com.melluh.servertours.api.playback.PlaybackFrame;
import org.jetbrains.annotations.NotNull;

/**
 * A continuous track which directly renders the requested absolute frame.
 */
public interface StateTrackRuntime extends TrackRuntime {
    void render(@NotNull PlaybackFrame targetFrame);

    /**
     * Snaps this track to an absolute frame and resets any interpolation
     * history owned by the runtime.
     *
     * <p>The default keeps existing state tracks source-compatible. Runtimes
     * which smooth or interpolate transport should override this callback so
     * a discontinuity cannot interpolate from stale state.</p>
     */
    default void rebase(@NotNull PlaybackFrame targetFrame, @NotNull StateRebaseReason reason) {
        this.render(targetFrame);
    }
}
