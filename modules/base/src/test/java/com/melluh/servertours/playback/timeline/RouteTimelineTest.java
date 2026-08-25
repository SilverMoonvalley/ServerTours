package com.melluh.servertours.playback.timeline;

import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.route.point.CraftStationaryPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RouteTimelineTest {

    @Test
    void naturalSamplingAssignsSharedBoundaryToOutgoingPoint() {
        CraftRoutePoint outgoing = point(4);
        CraftRoutePoint incoming = point(6);
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));

        TimelinePosition boundary = timeline.sample(4L);

        assertEquals(10L, timeline.cameraDuration());
        assertEquals(0, boundary.pointIndex());
        assertSame(outgoing, boundary.point());
        assertEquals(4L, boundary.frame());
        assertEquals(1.0f, boundary.progress());

        TimelinePosition nextFrame = timeline.sample(5L);
        assertEquals(1, nextFrame.pointIndex());
        assertSame(incoming, nextFrame.point());
        assertEquals(1.0f / 6.0f, nextFrame.progress(), 0.000_001f);
    }

    @Test
    void pointStartPositionExplicitlySelectsIncomingPointAtSharedBoundary() {
        CraftRoutePoint outgoing = point(4);
        CraftRoutePoint incoming = point(6);
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));

        TimelinePosition explicitStart = timeline.pointStartPosition(1);

        assertEquals(1, explicitStart.pointIndex());
        assertSame(incoming, explicitStart.point());
        assertEquals(4L, explicitStart.frame());
        assertEquals(4L, explicitStart.pointStartFrame());
        assertEquals(10L, explicitStart.pointEndFrame());
        assertEquals(0.0f, explicitStart.progress());
    }

    @Test
    void zeroDurationPointIsAddressableButSkippedByNaturalCameraSampling() {
        CraftRoutePoint first = point(2);
        CraftRoutePoint zero = point(0);
        CraftRoutePoint last = point(3);
        RouteTimeline timeline = new RouteTimeline(List.of(first, zero, last));

        assertEquals(2L, timeline.pointStart(1));
        assertEquals(2L, timeline.pointEnd(1));
        assertEquals(0L, timeline.pointDuration(1));

        TimelinePosition boundary = timeline.sample(2L);
        assertEquals(0, boundary.pointIndex());
        assertEquals(1.0f, boundary.progress());

        TimelinePosition afterBoundary = timeline.sample(3L);
        assertEquals(2, afterBoundary.pointIndex());
        assertEquals(1.0f / 3.0f, afterBoundary.progress(), 0.000_001f);

        TimelinePosition explicitZeroStart = timeline.pointStartPosition(1);
        assertEquals(1, explicitZeroStart.pointIndex());
        assertSame(zero, explicitZeroStart.point());
        assertEquals(2L, explicitZeroStart.frame());
        assertEquals(0.0f, explicitZeroStart.progress());

        TimelinePosition zeroSample = timeline.samplePoint(1, 2L);
        assertEquals(1, zeroSample.pointIndex());
        assertEquals(1.0f, zeroSample.progress());
    }

    @Test
    void allZeroDurationTimelineHoldsFinalPointAsNaturalCameraState() {
        CraftRoutePoint first = point(0);
        CraftRoutePoint middle = point(0);
        CraftRoutePoint last = point(0);
        RouteTimeline timeline = new RouteTimeline(List.of(first, middle, last));

        assertEquals(0L, timeline.cameraDuration());

        TimelinePosition atStart = timeline.sample(0L);
        assertEquals(2, atStart.pointIndex());
        assertSame(last, atStart.point());
        assertEquals(0L, atStart.frame());
        assertEquals(1.0f, atStart.progress());

        TimelinePosition afterEnd = timeline.sample(100L);
        assertEquals(2, afterEnd.pointIndex());
        assertSame(last, afterEnd.point());
        assertEquals(0L, afterEnd.frame());
        assertEquals(1.0f, afterEnd.progress());

        TimelinePosition explicitMiddle = timeline.pointStartPosition(1);
        assertEquals(1, explicitMiddle.pointIndex());
        assertSame(middle, explicitMiddle.point());
        assertEquals(0L, explicitMiddle.frame());
        assertEquals(0.0f, explicitMiddle.progress());
    }

    private static CraftRoutePoint point(int ticksVisible) {
        CraftStationaryPoint point = new CraftStationaryPoint(null);
        point.setTicksVisible(ticksVisible);
        return point;
    }
}
