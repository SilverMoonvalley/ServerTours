package com.melluh.servertours.recording.math;

/**
 * Selects at most one real camera sample from each fixed-duration time bucket.
 *
 * <p>Skipped buckets are never synthesized. For example, after accepting 50ms, a call at
 * 350ms accepts that real pose once and does not manufacture samples for 100-300ms.</p>
 */
public final class FixedRateSampleGate {
    public static final long DEFAULT_INTERVAL_NANOS = 50_000_000L;

    private final long intervalNanos;
    private boolean initialized;
    private long lastElapsedNanos;
    private long lastBucket;

    public FixedRateSampleGate() {
        this(DEFAULT_INTERVAL_NANOS);
    }

    public FixedRateSampleGate(long intervalNanos) {
        if (intervalNanos <= 0L) {
            throw new IllegalArgumentException("intervalNanos must be greater than zero");
        }
        this.intervalNanos = intervalNanos;
    }

    public boolean shouldCapture(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("elapsedNanos cannot be negative");
        }
        if (this.initialized && elapsedNanos < this.lastElapsedNanos) {
            throw new IllegalArgumentException("elapsedNanos cannot move backwards");
        }

        long bucket = elapsedNanos / this.intervalNanos;
        this.lastElapsedNanos = elapsedNanos;
        if (!this.initialized) {
            this.initialized = true;
            this.lastBucket = bucket;
            return true;
        }
        if (bucket == this.lastBucket) {
            return false;
        }

        this.lastBucket = bucket;
        return true;
    }

    public long getIntervalNanos() {
        return this.intervalNanos;
    }

    public void reset() {
        this.initialized = false;
        this.lastElapsedNanos = 0L;
        this.lastBucket = 0L;
    }
}
