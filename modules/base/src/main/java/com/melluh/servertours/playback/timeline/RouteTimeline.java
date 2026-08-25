package com.melluh.servertours.playback.timeline;

import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable frame layout for a route's camera points.
 *
 * <p>Durations are snapshotted when the timeline is constructed. Zero-length
 * points remain addressable for event scheduling but are skipped by normal
 * camera sampling.</p>
 */
public final class RouteTimeline {
    private final List<CraftRoutePoint> points;
    private final long[] pointStarts;
    private final long[] pointEnds;
    private final int[] cameraPointIndexes;
    private final long cameraDuration;

    public RouteTimeline(CraftRoute route) {
        this(Objects.requireNonNull(route, "route may not be null").getPoints());
    }

    public RouteTimeline(List<? extends CraftRoutePoint> points) {
        Objects.requireNonNull(points, "points may not be null");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a route timeline requires at least one point");
        }

        this.points = List.copyOf(points);
        this.pointStarts = new long[this.points.size()];
        this.pointEnds = new long[this.points.size()];
        List<Integer> cameraIndexes = new ArrayList<>(this.points.size());

        long cursor = 0L;
        for (int index = 0; index < this.points.size(); index++) {
            CraftRoutePoint point = Objects.requireNonNull(this.points.get(index), "route point may not be null");
            long duration = Math.max(0L, (long) point.getTicksVisible());
            this.pointStarts[index] = cursor;
            try {
                cursor = Math.addExact(cursor, duration);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("route duration exceeds supported frame range", exception);
            }
            this.pointEnds[index] = cursor;
            if (duration > 0L) {
                cameraIndexes.add(index);
            }
        }

        this.cameraDuration = cursor;
        this.cameraPointIndexes = cameraIndexes.stream().mapToInt(Integer::intValue).toArray();
    }

    public long cameraDuration() {
        return this.cameraDuration;
    }

    public int pointCount() {
        return this.points.size();
    }

    public List<CraftRoutePoint> points() {
        return this.points;
    }

    public CraftRoutePoint point(int pointIndex) {
        this.requirePointIndex(pointIndex);
        return this.points.get(pointIndex);
    }

    public long pointStart(int pointIndex) {
        this.requirePointIndex(pointIndex);
        return this.pointStarts[pointIndex];
    }

    public long pointEnd(int pointIndex) {
        this.requirePointIndex(pointIndex);
        return this.pointEnds[pointIndex];
    }

    public long pointDuration(int pointIndex) {
        return this.pointEnd(pointIndex) - this.pointStart(pointIndex);
    }

    public int indexOf(CraftRoutePoint point) {
        return this.points.indexOf(Objects.requireNonNull(point, "point may not be null"));
    }

    /**
     * Finds the point used to render a global frame. A positive-duration point
     * owns its end frame, so an ordinary boundary renders the outgoing point
     * at progress {@code 1}. Zero-duration points are skipped.
     */
    public int pointIndexAt(long targetFrame) {
        return this.sample(targetFrame).pointIndex();
    }

    public TimelinePosition sample(long targetFrame) {
        requireFrame(targetFrame);
        long frame = Math.min(targetFrame, this.cameraDuration);

        if (this.cameraPointIndexes.length == 0) {
            int finalIndex = this.points.size() - 1;
            return this.position(finalIndex, frame, 1.0f);
        }

        int cameraIndex;
        if (frame == 0L) {
            cameraIndex = this.cameraPointIndexes[0];
        } else if (frame == this.cameraDuration) {
            cameraIndex = this.cameraPointIndexes[this.cameraPointIndexes.length - 1];
        } else {
            cameraIndex = this.findOwningCameraPoint(frame);
        }

        long start = this.pointStarts[cameraIndex];
        long duration = this.pointEnds[cameraIndex] - start;
        float progress = (float) ((double) (frame - start) / (double) duration);
        progress = Math.max(0.0f, Math.min(progress, 1.0f));
        return this.position(cameraIndex, frame, progress);
    }

    /**
     * Returns the explicit start sample for a point. This is intentionally
     * separate from {@link #sample(long)} because a shared boundary belongs to
     * the outgoing point during natural playback.
     */
    public TimelinePosition pointStartPosition(int pointIndex) {
        this.requirePointIndex(pointIndex);
        return this.position(pointIndex, this.pointStarts[pointIndex], 0.0f);
    }

    /**
     * Samples a specific point, clamping the supplied global frame to that
     * point's own span. Useful for explicit seeks and camera-track adapters.
     */
    public TimelinePosition samplePoint(int pointIndex, long targetFrame) {
        this.requirePointIndex(pointIndex);
        requireFrame(targetFrame);
        long start = this.pointStarts[pointIndex];
        long end = this.pointEnds[pointIndex];
        long frame = Math.max(start, Math.min(targetFrame, end));
        long duration = end - start;
        float progress = duration == 0L
                ? 1.0f
                : (float) ((double) (frame - start) / (double) duration);
        return this.position(pointIndex, frame, progress);
    }

    private int findOwningCameraPoint(long frame) {
        int low = 0;
        int high = this.cameraPointIndexes.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            int pointIndex = this.cameraPointIndexes[middle];
            if (this.pointEnds[pointIndex] >= frame) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return this.cameraPointIndexes[low];
    }

    private TimelinePosition position(int pointIndex, long frame, float progress) {
        return new TimelinePosition(
                frame,
                pointIndex,
                this.points.get(pointIndex),
                this.pointStarts[pointIndex],
                this.pointEnds[pointIndex],
                progress
        );
    }

    private void requirePointIndex(int pointIndex) {
        if (pointIndex < 0 || pointIndex >= this.points.size()) {
            throw new IndexOutOfBoundsException("point index out of range: " + pointIndex);
        }
    }

    private static void requireFrame(long frame) {
        if (frame < 0L) {
            throw new IllegalArgumentException("targetFrame may not be negative");
        }
    }
}
