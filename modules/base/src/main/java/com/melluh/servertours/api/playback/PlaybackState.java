package com.melluh.servertours.api.playback;

/**
 * The lifecycle state of a playback session.
 */
public enum PlaybackState {
    CREATED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED
}
