package com.melluh.servertours.playback.timeline;

import java.util.Objects;

/**
 * Converts elapsed monotonic time to ServerTours' fixed 20 FPS scene frames.
 *
 * <p>The clock never accumulates scheduler ticks. Pausing stores an exact
 * scene frame and resuming establishes a new nanosecond anchor, thereby
 * discarding all real time spent paused.</p>
 */
public final class SceneClock {
    public static final long FRAME_NANOS = 50_000_000L;

    private final NanoClock clock;
    private long anchorNanos;
    private long anchorFrame;
    private long lastTargetFrame;
    private long pausedFrame;
    private boolean started;
    private boolean paused;

    public SceneClock() {
        this(NanoClock.system());
    }

    public SceneClock(NanoClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock may not be null");
    }

    /**
     * Starts, or explicitly repositions, this clock at {@code frame}.
     * Calling this method resets the monotonic target history.
     */
    public void startAt(long frame) {
        requireFrame(frame, "frame");
        this.anchorNanos = this.clock.now();
        this.anchorFrame = frame;
        this.lastTargetFrame = frame;
        this.pausedFrame = frame;
        this.started = true;
        this.paused = false;
    }

    /**
     * Returns the absolute scene frame for the current instant, capped at the
     * supplied scene end. Repeated calls cannot move backwards even if an
     * injected time source does.
     */
    public long currentTarget(long maxFrame) {
        this.requireStarted();
        requireFrame(maxFrame, "maxFrame");
        if (maxFrame < this.lastTargetFrame) {
            throw new IllegalArgumentException("maxFrame may not be before the current frame");
        }

        if (this.paused) {
            return this.pausedFrame;
        }

        long elapsedNanos = this.clock.now() - this.anchorNanos;
        if (elapsedNanos < 0L) {
            elapsedNanos = 0L;
        }
        long elapsedFrames = elapsedNanos / FRAME_NANOS;
        long target = saturatedAdd(this.anchorFrame, elapsedFrames);
        target = Math.min(target, maxFrame);
        target = Math.max(target, this.lastTargetFrame);
        this.lastTargetFrame = target;
        return target;
    }

    /**
     * Pauses at the effective frame selected by the playback engine.
     *
     * <p>The frame may be earlier than the most recently calculated candidate
     * target when a confirmation barrier clamps a scheduler catch-up window.
     * That candidate has not yet been rendered and is therefore deliberately
     * replaced by the barrier frame.</p>
     */
    public void pauseAt(long frame) {
        this.requireStarted();
        requireFrame(frame, "frame");
        if (this.paused) {
            return;
        }
        this.anchorFrame = frame;
        this.lastTargetFrame = frame;
        this.pausedFrame = frame;
        this.paused = true;
    }

    /**
     * Resumes from the paused frame using a fresh time anchor. Time elapsed
     * while paused is not added to the scene.
     */
    public void resume() {
        this.requireStarted();
        if (!this.paused) {
            return;
        }
        this.anchorNanos = this.clock.now();
        this.anchorFrame = this.pausedFrame;
        this.lastTargetFrame = this.pausedFrame;
        this.paused = false;
    }

    public boolean isStarted() {
        return this.started;
    }

    public boolean isPaused() {
        return this.started && this.paused;
    }

    public long getCurrentFrame() {
        this.requireStarted();
        return this.lastTargetFrame;
    }

    private void requireStarted() {
        if (!this.started) {
            throw new IllegalStateException("scene clock has not been started");
        }
    }

    private static void requireFrame(long frame, String name) {
        if (frame < 0L) {
            throw new IllegalArgumentException(name + " may not be negative");
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
