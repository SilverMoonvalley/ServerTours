package com.melluh.servertours.api.playback.track;

/**
 * Explains why a state track must snap to an absolute frame instead of
 * continuing any interpolation from its previously rendered frame.
 */
public enum StateRebaseReason {
    /** The first state frame rendered for a newly started session. */
    SESSION_START,

    /** The absolute clock advanced by more than one logical frame. */
    CLOCK_CATCH_UP,

    /** Playback was explicitly moved to another position. */
    EXPLICIT_SEEK,

    /** Playback resumed after a pause or confirmation barrier. */
    RESUME_RECOVERY,

    /** Adjacent route segments do not meet at the same camera transform. */
    ROUTE_DISCONTINUITY
}
