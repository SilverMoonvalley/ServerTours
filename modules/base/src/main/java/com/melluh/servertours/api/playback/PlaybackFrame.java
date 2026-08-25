package com.melluh.servertours.api.playback;

/**
 * An immutable view of the current absolute scene frame.
 *
 * @param index          zero-based scene frame index
 * @param sceneNanos     elapsed scene time in nanoseconds
 * @param durationFrames total duration of the scene in frames
 */
public record PlaybackFrame(long index, long sceneNanos, long durationFrames) {
    public PlaybackFrame {
        if (index < 0L) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (sceneNanos < 0L) {
            throw new IllegalArgumentException("sceneNanos must be non-negative");
        }
        if (durationFrames < 0L) {
            throw new IllegalArgumentException("durationFrames must be non-negative");
        }
    }

    /**
     * Returns the normalized scene progress in the range {@code [0, 1]}.
     */
    public double progress() {
        if (this.durationFrames == 0L) {
            return 1.0D;
        }
        return Math.min(1.0D, (double) this.index / (double) this.durationFrames);
    }
}
