package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.playback.timeline.RouteTimeline;
import com.melluh.servertours.playback.timeline.TimelinePosition;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.route.point.CraftInterpolatePoint;
import com.melluh.servertours.route.point.CraftStationaryPoint;
import com.melluh.servertours.util.math.EasingFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteCameraTrackRuntimeTest {

    @Test
    void continuousAndDiscontinuousSamplesUseDifferentMovementCallbacks() {
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RouteTimeline timeline = mock(RouteTimeline.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftRoutePoint point = mock(CraftRoutePoint.class);
        Location location = mock(Location.class);
        TimelinePosition position = new TimelinePosition(7L, 0, point, 0L, 10L, 0.7f);
        when(timeline.sample(7L)).thenReturn(position);
        when(point.getPlaybackLocation(0.7f, easing)).thenReturn(location);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);
        PlaybackFrame frame = new PlaybackFrame(7L, 350_000_000L, 10L);

        runtime.render(frame);
        runtime.rebase(frame, StateRebaseReason.CLOCK_CATCH_UP);

        verify(movementHandler).move(touringPlayer, location);
        verify(movementHandler).rebase(touringPlayer, location, StateRebaseReason.CLOCK_CATCH_UP);
    }

    @Test
    void explicitSeekRebasesTheRequestedPointStartInsteadOfSharedBoundarySample() {
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RouteTimeline timeline = mock(RouteTimeline.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftRoutePoint point = mock(CraftRoutePoint.class);
        Location location = mock(Location.class);
        TimelinePosition pointStart = new TimelinePosition(5L, 1, point, 5L, 9L, 0.0f);
        when(timeline.pointStartPosition(1)).thenReturn(pointStart);
        when(point.getPlaybackLocation(0.0f, easing)).thenReturn(location);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);

        runtime.rebaseRoutePointStart(1, new PlaybackFrame(5L, 250_000_000L, 9L),
                StateRebaseReason.EXPLICIT_SEEK);

        verify(movementHandler).rebase(touringPlayer, location, StateRebaseReason.EXPLICIT_SEEK);
    }

    @Test
    void movementHandlerDefaultRebaseDelegatesToLegacyMove() {
        AtomicInteger moves = new AtomicInteger();
        MovementHandler movementHandler = new MovementHandler() {
            @Override
            public void initialize(CraftTouringPlayer touringPlayer, Location location) {
            }

            @Override
            public void move(CraftTouringPlayer touringPlayer, Location location) {
                moves.incrementAndGet();
            }

            @Override
            public void cleanup() {
            }
        };

        movementHandler.rebase(mock(CraftTouringPlayer.class), mock(Location.class),
                StateRebaseReason.SESSION_START);

        assertEquals(1, moves.get());
    }

    @Test
    void stationaryPointsAtDifferentTransformsSnapAtTheirBoundary() {
        World world = mock(World.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftStationaryPoint outgoing = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 0.0, 0.0f));
        CraftStationaryPoint incoming = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 10.0, 0.0f));
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));
        MovementHandler movementHandler = mock(MovementHandler.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);

        runtime.rebase(frame(2L, 4L), StateRebaseReason.CLOCK_CATCH_UP);
        clearInvocations(movementHandler);
        runtime.render(frame(3L, 4L));

        verify(movementHandler).rebase(eq(touringPlayer), eq(location(world, 10.0, 0.0f)),
                eq(StateRebaseReason.ROUTE_DISCONTINUITY));
        verify(movementHandler, never()).move(any(), any());
    }

    @Test
    void interpolateEndpointMatchingTheNextPointRemainsContinuous() {
        World world = mock(World.class);
        EasingFunction easing = mock(EasingFunction.class);
        Location start = location(world, 0.0, 15.0f);
        Location joint = location(world, 10.0, 35.0f);
        CraftInterpolatePoint outgoing = point(CraftInterpolatePoint.class, 2,
                progress -> interpolate(start, joint, progress));
        CraftStationaryPoint incoming = point(CraftStationaryPoint.class, 2,
                ignored -> joint.clone());
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));
        MovementHandler movementHandler = mock(MovementHandler.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);

        runtime.rebase(frame(2L, 4L), StateRebaseReason.CLOCK_CATCH_UP);
        clearInvocations(movementHandler);
        runtime.render(frame(3L, 4L));

        verify(movementHandler).move(eq(touringPlayer), eq(joint));
        verify(movementHandler, never()).rebase(any(), any(), eq(StateRebaseReason.ROUTE_DISCONTINUITY));
    }

    @Test
    void yawValuesSeparatedByFullTurnRemainContinuous() {
        World world = mock(World.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftStationaryPoint outgoing = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 4.0, 190.0f));
        CraftStationaryPoint incoming = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 4.0, -170.0f));
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));
        MovementHandler movementHandler = mock(MovementHandler.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);

        runtime.rebase(frame(2L, 4L), StateRebaseReason.CLOCK_CATCH_UP);
        clearInvocations(movementHandler);
        runtime.render(frame(3L, 4L));

        verify(movementHandler).move(eq(touringPlayer), eq(location(world, 4.0, -170.0f)));
        verify(movementHandler, never()).rebase(any(), any(), eq(StateRebaseReason.ROUTE_DISCONTINUITY));
    }

    @Test
    void zeroDurationPointIsSkippedWhenCheckingNaturalBoundary() {
        World world = mock(World.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftStationaryPoint outgoing = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 3.0, 0.0f));
        CraftStationaryPoint skipped = point(CraftStationaryPoint.class, 0,
                ignored -> location(world, 100.0, 90.0f));
        CraftStationaryPoint incoming = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 3.0, 0.0f));
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, skipped, incoming));
        MovementHandler movementHandler = mock(MovementHandler.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);

        runtime.rebase(frame(2L, 4L), StateRebaseReason.CLOCK_CATCH_UP);
        clearInvocations(movementHandler);
        runtime.render(frame(3L, 4L));

        verify(movementHandler).move(eq(touringPlayer), eq(location(world, 3.0, 0.0f)));
        verify(movementHandler, never()).rebase(any(), any(), eq(StateRebaseReason.ROUTE_DISCONTINUITY));
        verify(skipped, never()).getPlaybackLocation(anyFloat(), any(EasingFunction.class));
    }

    @Test
    void failedBoundaryRebaseDoesNotAdvanceNaturalSample() {
        World world = mock(World.class);
        EasingFunction easing = mock(EasingFunction.class);
        CraftStationaryPoint outgoing = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 0.0, 0.0f));
        CraftStationaryPoint incoming = point(CraftStationaryPoint.class, 2,
                ignored -> location(world, 10.0, 0.0f));
        RouteTimeline timeline = new RouteTimeline(List.of(outgoing, incoming));
        MovementHandler movementHandler = mock(MovementHandler.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        RouteCameraTrackRuntime runtime = new RouteCameraTrackRuntime(
                touringPlayer, movementHandler, timeline, easing);
        doThrow(new IllegalStateException("snap failed")).doNothing().when(movementHandler)
                .rebase(eq(touringPlayer), any(Location.class), eq(StateRebaseReason.ROUTE_DISCONTINUITY));

        runtime.rebase(frame(2L, 4L), StateRebaseReason.CLOCK_CATCH_UP);
        assertThrows(IllegalStateException.class, () -> runtime.render(frame(3L, 4L)));
        runtime.render(frame(3L, 4L));

        verify(movementHandler, times(2)).rebase(eq(touringPlayer), any(Location.class),
                eq(StateRebaseReason.ROUTE_DISCONTINUITY));
    }

    private static <T extends CraftRoutePoint> T point(Class<T> type, int durationFrames,
                                                        LocationSampler sampler) {
        T point = mock(type);
        when(point.getTicksVisible()).thenReturn(durationFrames);
        when(point.getPlaybackLocation(anyFloat(), any(EasingFunction.class)))
                .thenAnswer(invocation -> sampler.sample(invocation.getArgument(0)));
        return point;
    }

    private static PlaybackFrame frame(long index, long duration) {
        return new PlaybackFrame(index, index * 50_000_000L, duration);
    }

    private static Location location(World world, double x, float yaw) {
        return new Location(world, x, 64.0, -2.0, yaw, 12.0f);
    }

    private static Location interpolate(Location start, Location end, float progress) {
        return new Location(start.getWorld(),
                start.getX() + (end.getX() - start.getX()) * progress,
                start.getY() + (end.getY() - start.getY()) * progress,
                start.getZ() + (end.getZ() - start.getZ()) * progress,
                start.getYaw() + (end.getYaw() - start.getYaw()) * progress,
                start.getPitch() + (end.getPitch() - start.getPitch()) * progress);
    }

    @FunctionalInterface
    private interface LocationSampler {
        Location sample(float progress);
    }
}
