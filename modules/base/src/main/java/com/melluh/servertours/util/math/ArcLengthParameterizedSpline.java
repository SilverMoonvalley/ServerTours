package com.melluh.servertours.util.math;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Shared deterministic arc-length lookup for camera splines.
 *
 * <p>The table deliberately uses integer-indexed samples so both endpoints
 * are represented exactly. Curve implementations only provide their raw
 * parameterization.</p>
 */
abstract class ArcLengthParameterizedSpline implements Spline {
    static final int ARC_LENGTH_INTERVALS = 200;
    private static final double DEGENERATE_LENGTH = 1.0e-12;

    protected Location p0;
    protected Location p1;
    protected Location p2;
    protected Location p3;

    private final double[] cumulativeLengths = new double[ARC_LENGTH_INTERVALS + 1];
    private double totalLength;
    private boolean initialized;

    @Override
    public final void initialize(Location p0, Location p1, Location p2, Location p3) {
        this.p0 = Objects.requireNonNull(p0, "p0 may not be null").clone();
        this.p1 = Objects.requireNonNull(p1, "p1 may not be null").clone();
        this.p2 = Objects.requireNonNull(p2, "p2 may not be null").clone();
        this.p3 = Objects.requireNonNull(p3, "p3 may not be null").clone();
        this.onControlPointsInitialized();
        this.rebuildArcLengthTable();
        this.initialized = true;
    }

    protected void onControlPointsInitialized() {
    }

    protected abstract Location interpolate(float progress);

    @Override
    public final Location calculate(float progress) {
        this.requireInitialized();
        return this.interpolate(clampProgress(progress));
    }

    @Override
    public final Location calculateNormalized(float progress) {
        this.requireInitialized();
        float clamped = clampProgress(progress);
        if (clamped <= 0.0f) {
            return this.interpolate(0.0f);
        }
        if (clamped >= 1.0f) {
            return this.interpolate(1.0f);
        }
        if (this.totalLength <= DEGENERATE_LENGTH) {
            return this.interpolate(0.0f);
        }

        double targetLength = clamped * this.totalLength;
        int upper = this.findUpperSample(targetLength);
        int lower = upper - 1;
        double lowerLength = this.cumulativeLengths[lower];
        double intervalLength = this.cumulativeLengths[upper] - lowerLength;
        double intervalProgress = intervalLength <= DEGENERATE_LENGTH
                ? 0.0
                : (targetLength - lowerLength) / intervalLength;
        float curveProgress = (float) ((lower + intervalProgress) / ARC_LENGTH_INTERVALS);
        return this.interpolate(curveProgress);
    }

    @Override
    public final double getTotalLength() {
        this.requireInitialized();
        return this.totalLength;
    }

    private void rebuildArcLengthTable() {
        this.cumulativeLengths[0] = 0.0;
        Location previous = this.interpolate(0.0f);
        double cumulative = 0.0;
        for (int sample = 1; sample <= ARC_LENGTH_INTERVALS; sample++) {
            float progress = sample == ARC_LENGTH_INTERVALS
                    ? 1.0f
                    : (float) sample / (float) ARC_LENGTH_INTERVALS;
            Location current = this.interpolate(progress);
            cumulative += coordinateDistance(previous, current);
            this.cumulativeLengths[sample] = cumulative;
            previous = current;
        }
        this.totalLength = cumulative <= DEGENERATE_LENGTH ? 0.0 : cumulative;
    }

    private int findUpperSample(double targetLength) {
        int low = 1;
        int high = ARC_LENGTH_INTERVALS;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (this.cumulativeLengths[middle] >= targetLength) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("spline has not been initialized");
        }
    }

    private static float clampProgress(float progress) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("progress must be finite");
        }
        return Math.max(0.0f, Math.min(progress, 1.0f));
    }

    private static double coordinateDistance(Location first, Location second) {
        double x = second.getX() - first.getX();
        double y = second.getY() - first.getY();
        double z = second.getZ() - first.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }
}
