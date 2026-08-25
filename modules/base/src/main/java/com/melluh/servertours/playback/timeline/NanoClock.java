package com.melluh.servertours.playback.timeline;

/**
 * A monotonic nanosecond time source used by the playback timeline.
 *
 * <p>The production clock is backed by {@link System#nanoTime()}. Tests may
 * inject a deterministic implementation without making the playback engine
 * depend on wall-clock time.</p>
 */
@FunctionalInterface
public interface NanoClock {
    NanoClock SYSTEM = System::nanoTime;

    long now();

    static NanoClock system() {
        return SYSTEM;
    }
}
