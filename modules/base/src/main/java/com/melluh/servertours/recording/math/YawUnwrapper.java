package com.melluh.servertours.recording.math;

/** Converts wrapped camera yaw values into a continuous angle sequence. */
public final class YawUnwrapper {
    private boolean initialized;
    private double lastYaw;

    /**
     * Adds the next raw yaw value. The first value is preserved as-is; later values use the
     * shortest angular delta. An exact 180-degree tie preserves the sign of the raw delta.
     */
    public double accept(double rawYaw) {
        requireFinite(rawYaw);
        if (!this.initialized) {
            this.initialized = true;
            this.lastYaw = rawYaw;
            return rawYaw;
        }

        this.lastYaw = unwrap(this.lastYaw, rawYaw);
        return this.lastYaw;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public double getLastYaw() {
        if (!this.initialized) {
            throw new IllegalStateException("No yaw has been accepted");
        }
        return this.lastYaw;
    }

    public void reset() {
        this.initialized = false;
        this.lastYaw = 0.0;
    }

    public static double unwrap(double previousUnwrappedYaw, double rawYaw) {
        requireFinite(previousUnwrappedYaw);
        requireFinite(rawYaw);

        double previousWrapped = wrap(previousUnwrappedYaw);
        double rawDelta = rawYaw - previousWrapped;
        double shortestDelta = rawDelta % 360.0;
        if (shortestDelta > 180.0) {
            shortestDelta -= 360.0;
        } else if (shortestDelta < -180.0) {
            shortestDelta += 360.0;
        }
        if (Math.abs(shortestDelta) == 180.0) {
            shortestDelta = Math.copySign(180.0, rawDelta);
        }
        return previousUnwrappedYaw + shortestDelta;
    }

    private static double wrap(double yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    private static void requireFinite(double yaw) {
        if (!Double.isFinite(yaw)) {
            throw new IllegalArgumentException("yaw must be finite");
        }
    }
}
