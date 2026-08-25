package com.melluh.servertours.api.playback.track;

/**
 * Explains why a state track must snap to an absolute frame instead of
 * continuing any interpolation from its previously rendered frame.
 */
public enum StateRebaseReason {
    /** The first state frame rendered for a newly started session. */
    SESSION_START,

    /** A state track was asked to move backwards relative to its last rendered frame. */
    CLOCK_CATCH_UP,

    /** Playback was explicitly moved to another position. */
    EXPLICIT_SEEK,

    /** Playback resumed after a pause or confirmation barrier. */
    RESUME_RECOVERY,

    /** Playback paused and any client-side interpolation must freeze at the logical head. */
    PAUSE_FREEZE,

    /** A skipped interval contained more rotation than the client can interpolate faithfully. */
    ROTATION_DISCONTINUITY,

    /** Adjacent route segments do not meet at the same camera transform. */
    ROUTE_DISCONTINUITY
}
