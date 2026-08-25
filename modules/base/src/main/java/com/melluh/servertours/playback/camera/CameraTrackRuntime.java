package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.StateTrackRuntime;

/** Internal common contract for the single built-in camera track. */
public interface CameraTrackRuntime extends StateTrackRuntime {
    /**
     * Repositions the camera to the absolute scene frame associated with a
     * route cue. Point-backed cameras override this to preserve exact shared
     * boundary sampling; timestamped cameras use the absolute frame directly.
     */
    default void rebaseRoutePointStart(int pointIndex, PlaybackFrame frame, StateRebaseReason reason) {
        this.rebase(frame, reason);
    }
}
