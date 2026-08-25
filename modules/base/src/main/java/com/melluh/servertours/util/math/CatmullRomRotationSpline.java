package com.melluh.servertours.util.math;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Smooth four-keyframe yaw/pitch interpolation using cubic Hermite tangents.
 */
public final class CatmullRomRotationSpline {
    private double yaw0;
    private double yaw1;
    private double yaw2;
    private double yaw3;
    private double pitch0;
    private double pitch1;
    private double pitch2;
    private double pitch3;
    private boolean initialized;

    public void initialize(Location p0, Location p1, Location p2, Location p3) {
        Objects.requireNonNull(p0, "p0 may not be null");
        Objects.requireNonNull(p1, "p1 may not be null");
        Objects.requireNonNull(p2, "p2 may not be null");
        Objects.requireNonNull(p3, "p3 may not be null");

        this.yaw0 = p0.getYaw();
        this.yaw1 = unwrapNear(this.yaw0, p1.getYaw());
        this.yaw2 = unwrapNear(this.yaw1, p2.getYaw());
        this.yaw3 = unwrapNear(this.yaw2, p3.getYaw());
        this.pitch0 = p0.getPitch();
        this.pitch1 = p1.getPitch();
        this.pitch2 = p2.getPitch();
        this.pitch3 = p3.getPitch();
        this.initialized = true;
    }

    public void apply(Location target, float progress) {
        Objects.requireNonNull(target, "target may not be null");
        this.requireInitialized();
        float clamped = clampProgress(progress);
        target.setYaw((float) cubic(this.yaw0, this.yaw1, this.yaw2, this.yaw3, clamped));
        double pitch = cubic(this.pitch0, this.pitch1, this.pitch2, this.pitch3, clamped);
        target.setPitch((float) Math.max(-90.0, Math.min(pitch, 90.0)));
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("rotation spline has not been initialized");
        }
    }

    private static double cubic(double p0, double p1, double p2, double p3, double progress) {
        double squared = progress * progress;
        double cubed = squared * progress;
        double startBasis = 2.0 * cubed - 3.0 * squared + 1.0;
        double startTangentBasis = cubed - 2.0 * squared + progress;
        double endBasis = -2.0 * cubed + 3.0 * squared;
        double endTangentBasis = cubed - squared;
        double startTangent = (p2 - p0) * 0.5;
        double endTangent = (p3 - p1) * 0.5;
        return startBasis * p1 + startTangentBasis * startTangent
                + endBasis * p2 + endTangentBasis * endTangent;
    }

    private static double unwrapNear(double reference, double angle) {
        double turns = Math.rint((reference - angle) / 360.0);
        return angle + turns * 360.0;
    }

    private static float clampProgress(float progress) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("progress must be finite");
        }
        return Math.max(0.0f, Math.min(progress, 1.0f));
    }
}
