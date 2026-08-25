package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.playback.timeline.RouteTimeline;
import com.melluh.servertours.playback.timeline.TimelinePosition;
import com.melluh.servertours.util.math.EasingFunction;
import org.bukkit.Location;

import java.util.Objects;

/** Adapts the existing route point samplers to an absolute state track. */
public final class RouteCameraTrackRuntime implements CameraTrackRuntime {
    private static final double POSITION_EPSILON = 1.0e-6;
    private static final float ROTATION_EPSILON = 1.0e-3f;

    private final CraftTouringPlayer touringPlayer;
    private final MovementHandler movementHandler;
    private final RouteTimeline timeline;
    private final EasingFunction easingFunction;
    private TimelinePosition lastNaturalSample;

    public RouteCameraTrackRuntime(CraftTouringPlayer touringPlayer, MovementHandler movementHandler,
                                   RouteTimeline timeline, EasingFunction easingFunction) {
        this.touringPlayer = Objects.requireNonNull(touringPlayer, "touringPlayer may not be null");
        this.movementHandler = Objects.requireNonNull(movementHandler, "movementHandler may not be null");
        this.timeline = Objects.requireNonNull(timeline, "timeline may not be null");
        this.easingFunction = Objects.requireNonNull(easingFunction, "easingFunction may not be null");
    }

    @Override
    public long getEndFrame() {
        return this.timeline.cameraDuration();
    }

    @Override
    public void setup(TrackContext context) {
        TimelinePosition initial = this.timeline.pointStartPosition(0);
        this.movementHandler.initialize(this.touringPlayer, this.location(initial));
        this.lastNaturalSample = initial;
    }

    @Override
    public void render(PlaybackFrame targetFrame) {
        TimelinePosition position = this.timeline.sample(targetFrame.index());
        Location target = this.location(position);
        if (this.crossesDiscontinuousBoundary(this.lastNaturalSample, position)) {
            this.movementHandler.rebase(this.touringPlayer, target, StateRebaseReason.ROUTE_DISCONTINUITY);
        } else {
            this.movementHandler.move(this.touringPlayer, target);
        }
        this.lastNaturalSample = position;
    }

    @Override
    public void rebase(PlaybackFrame targetFrame, StateRebaseReason reason) {
        TimelinePosition position = this.timeline.sample(targetFrame.index());
        this.movementHandler.rebase(this.touringPlayer, this.location(position), reason);
        this.lastNaturalSample = position;
    }

    @Override
    public void rebaseRoutePointStart(int pointIndex, PlaybackFrame frame, StateRebaseReason reason) {
        Objects.requireNonNull(frame, "frame may not be null");
        TimelinePosition pointStart = this.timeline.pointStartPosition(pointIndex);
        this.movementHandler.rebase(this.touringPlayer, this.location(pointStart), reason);
        this.lastNaturalSample = pointStart;
    }

    @Override
    public void onPause(TrackContext context, PauseReason reason) {
        long frameIndex = Math.min(context.getSession().getCurrentFrame(), this.getEndFrame());
        TimelinePosition position = this.timeline.sample(frameIndex);
        this.movementHandler.rebase(this.touringPlayer, this.location(position),
                StateRebaseReason.PAUSE_FREEZE);
        this.lastNaturalSample = position;
    }

    @Override
    public void teardown(TrackContext context) {
        this.movementHandler.cleanup();
        this.lastNaturalSample = null;
    }

    private boolean crossesDiscontinuousBoundary(TimelinePosition previous, TimelinePosition current) {
        if (previous == null || previous.pointIndex() == current.pointIndex()) {
            return false;
        }
        TimelinePosition previousEnd = this.timeline.samplePoint(
                previous.pointIndex(), previous.pointEndFrame());
        TimelinePosition currentStart = this.timeline.pointStartPosition(current.pointIndex());
        return !sameTransform(this.location(previousEnd), this.location(currentStart));
    }

    private static boolean sameTransform(Location first, Location second) {
        if (first.getWorld() != second.getWorld()) {
            return false;
        }
        return nearlyEqual(first.getX(), second.getX(), POSITION_EPSILON)
                && nearlyEqual(first.getY(), second.getY(), POSITION_EPSILON)
                && nearlyEqual(first.getZ(), second.getZ(), POSITION_EPSILON)
                && angularDistance(first.getYaw(), second.getYaw()) <= ROTATION_EPSILON
                && nearlyEqual(first.getPitch(), second.getPitch(), ROTATION_EPSILON);
    }

    private static boolean nearlyEqual(double first, double second, double epsilon) {
        return Double.isFinite(first) && Double.isFinite(second) && Math.abs(first - second) <= epsilon;
    }

    private static float angularDistance(float first, float second) {
        if (!Float.isFinite(first) || !Float.isFinite(second)) {
            return Float.POSITIVE_INFINITY;
        }
        float delta = (first - second) % 360.0f;
        if (delta > 180.0f) {
            delta -= 360.0f;
        } else if (delta < -180.0f) {
            delta += 360.0f;
        }
        return Math.abs(delta);
    }

    private Location location(TimelinePosition position) {
        return position.point().getPlaybackLocation(position.progress(), this.easingFunction);
    }
}
