package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.PlaybackSession;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.recording.math.RecordingTolerances;
import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;
import com.melluh.servertours.recording.storage.CameraRecording;
import com.melluh.servertours.recording.storage.RecordingMetadata;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordedCameraTrackRuntimeTest {
    private static final long FRAME_NANOS = 50_000_000L;

    @Test
    void setupInitializesTheTransportAtTheExactFirstPose() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer,
                movementHandler,
                recording(
                        sample(0L, 2.0, 64.0, -3.0, 370.0, -12.0),
                        sample(500_000_000L, 12.0, 74.0, 7.0, 470.0, 8.0)
                ),
                world
        );

        runtime.setup(mock(TrackContext.class));

        ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).initialize(eq(touringPlayer), location.capture());
        assertLocation(location.getValue(), world, 2.0, 64.0, -3.0, 370.0f, -12.0f);
        verify(movementHandler, never()).move(eq(touringPlayer), location.capture());
    }

    @Test
    void renderSamplesAbsoluteSceneNanosRatherThanDerivingTimeFromFrameIndex() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer,
                movementHandler,
                linearRecording(1_000_000_000L),
                world
        );

        runtime.render(new PlaybackFrame(1L, 350_000_000L, 20L));

        ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).move(eq(touringPlayer), location.capture());
        assertLocation(location.getValue(), world, 3.5, 67.5, -0.5, 35.0f, 7.0f);
    }

    @Test
    void aClockJumpDoesNotSynthesizeIntermediateCameraPoses() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer,
                movementHandler,
                linearRecording(1_000_000_000L),
                world
        );

        runtime.render(new PlaybackFrame(1L, 50_000_000L, 20L));
        runtime.render(new PlaybackFrame(7L, 350_000_000L, 20L));

        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler, times(2)).move(eq(touringPlayer), locations.capture());
        assertEquals(2, locations.getAllValues().size());
        assertEquals(0.5, locations.getAllValues().get(0).getX(), 1.0e-9);
        assertEquals(3.5, locations.getAllValues().get(1).getX(), 1.0e-9);
    }

    @Test
    void logicalEndFrameBeyondTheRawDurationHoldsTheFinalPose() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        CameraRecording recording = linearRecording(125_000_000L);
        RecordedCameraTrackRuntime runtime = runtime(touringPlayer, movementHandler, recording, world);

        runtime.render(new PlaybackFrame(recording.endFrame(), 150_000_000L, recording.endFrame()));

        ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).move(eq(touringPlayer), location.capture());
        assertLocation(location.getValue(), world, 10.0, 74.0, 6.0, 100.0f, 20.0f);
    }

    @Test
    void rebaseSamplesTheRequestedAbsoluteTimeAndTeardownCleansTheTransport() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer,
                movementHandler,
                linearRecording(1_000_000_000L),
                world
        );
        PlaybackFrame target = new PlaybackFrame(2L, 700_000_000L, 20L);
        TrackContext context = mock(TrackContext.class);

        runtime.rebase(target, StateRebaseReason.EXPLICIT_SEEK);
        runtime.teardown(context);

        ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).rebase(
                eq(touringPlayer), location.capture(), eq(StateRebaseReason.EXPLICIT_SEEK));
        assertLocation(location.getValue(), world, 7.0, 71.0, 3.0, 70.0f, 14.0f);
        verify(movementHandler, never()).move(eq(touringPlayer), location.capture());
        verify(movementHandler).cleanup();
    }

    @Test
    void endFrameUsesCeilingConversionAtTwentyFramesPerSecond() {
        RecordedCameraTrackRuntime exact = runtime(
                mock(CraftTouringPlayer.class), mock(MovementHandler.class),
                linearRecording(350_000_000L), mock(World.class));
        RecordedCameraTrackRuntime partial = runtime(
                mock(CraftTouringPlayer.class), mock(MovementHandler.class),
                linearRecording(350_000_001L), mock(World.class));

        assertAll(
                () -> assertEquals(7L, exact.getEndFrame()),
                () -> assertEquals(8L, partial.getEndFrame())
        );
    }

    @Test
    void displayLeadRampsFromTheExactStartInsteadOfJumpingAheadImmediately() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        when(movementHandler.presentationLeadFrames()).thenReturn(3);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer, movementHandler, linearRecording(1_000_000_000L), world);

        runtime.rebase(new PlaybackFrame(0L, 0L, 20L), StateRebaseReason.SESSION_START);
        runtime.render(new PlaybackFrame(1L, 50_000_000L, 20L));
        runtime.render(new PlaybackFrame(2L, 100_000_000L, 20L));
        runtime.render(new PlaybackFrame(3L, 150_000_000L, 20L));

        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler, times(3)).move(eq(touringPlayer), locations.capture());
        assertAll(
                () -> assertEquals(1.0, locations.getAllValues().get(0).getX(), 1.0e-9),
                () -> assertEquals(2.0, locations.getAllValues().get(1).getX(), 1.0e-9),
                () -> assertEquals(3.0, locations.getAllValues().get(2).getX(), 1.0e-9)
        );
    }

    @Test
    void pauseFreezesExactLogicalPoseAndResumeRebuildsLeadGradually() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        when(movementHandler.presentationLeadFrames()).thenReturn(3);
        RecordedCameraTrackRuntime runtime = runtime(
                touringPlayer, movementHandler, linearRecording(1_000_000_000L), world);
        TrackContext context = mock(TrackContext.class);
        PlaybackSession session = mock(PlaybackSession.class);
        when(context.getSession()).thenReturn(session);
        when(session.getCurrentFrame()).thenReturn(7L);

        runtime.onPause(context, PauseReason.CONFIRMATION);
        runtime.rebase(new PlaybackFrame(7L, 350_000_000L, 20L),
                StateRebaseReason.RESUME_RECOVERY);
        runtime.render(new PlaybackFrame(8L, 400_000_000L, 20L));

        ArgumentCaptor<Location> rebases = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).rebase(eq(touringPlayer), rebases.capture(),
                eq(StateRebaseReason.PAUSE_FREEZE));
        assertEquals(3.5, rebases.getValue().getX(), 1.0e-9);
        ArgumentCaptor<Location> moves = ArgumentCaptor.forClass(Location.class);
        verify(movementHandler).move(eq(touringPlayer), moves.capture());
        assertEquals(4.5, moves.getValue().getX(), 1.0e-9);
    }

    @Test
    void skippedRotationBeyondClientRangeSnapsInsteadOfInterpolatingBackwards() {
        World world = mock(World.class);
        CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
        MovementHandler movementHandler = mock(MovementHandler.class);
        RecordedCameraTrackRuntime runtime = runtime(touringPlayer, movementHandler,
                recording(
                        sample(0L, 0.0, 64.0, 0.0, 0.0, 0.0),
                        sample(1_000_000_000L, 10.0, 64.0, 0.0, 400.0, 0.0)), world);
        runtime.setup(mock(TrackContext.class));

        runtime.render(new PlaybackFrame(10L, 500_000_000L, 20L));

        verify(movementHandler).rebase(eq(touringPlayer), org.mockito.ArgumentMatchers.any(Location.class),
                eq(StateRebaseReason.ROTATION_DISCONTINUITY));
        verify(movementHandler, never()).move(eq(touringPlayer),
                org.mockito.ArgumentMatchers.any(Location.class));
    }

    private static RecordedCameraTrackRuntime runtime(CraftTouringPlayer touringPlayer,
                                                       MovementHandler movementHandler,
                                                       CameraRecording recording,
                                                       World world) {
        return new RecordedCameraTrackRuntime(touringPlayer, movementHandler, recording, world);
    }

    private static CameraRecording linearRecording(long durationNanos) {
        return recording(
                sample(0L, 0.0, 64.0, -4.0, 0.0, 0.0),
                sample(durationNanos, 10.0, 74.0, 6.0, 100.0, 20.0)
        );
    }

    private static CameraRecording recording(RecordingSample first, RecordingSample last) {
        RecordingMetadata metadata = new RecordingMetadata(
                UUID.fromString("f3ea3837-0eb4-4450-a524-f61922ee3b0f"),
                "runtime-test",
                UUID.fromString("14191e95-6711-45ad-af97-f66ba5379dce"),
                "CameraOperator",
                UUID.fromString("2014c8d8-1c4d-416d-b488-d7daea2ca428"),
                "world",
                1_774_321_234_567L,
                FRAME_NANOS,
                RecordingTolerances.DEFAULT,
                1
        );
        CompiledRecording compiled = new CompiledRecording(
                List.of(first, last), List.of(0, 1), List.of(0));
        return new CameraRecording(metadata, compiled);
    }

    private static RecordingSample sample(long timeNanos, double x, double y, double z,
                                          double yaw, double pitch) {
        return new RecordingSample(timeNanos, x, y, z, yaw, pitch);
    }

    private static void assertLocation(Location actual, World expectedWorld,
                                       double x, double y, double z, float yaw, float pitch) {
        assertAll(
                () -> assertSame(expectedWorld, actual.getWorld()),
                () -> assertEquals(x, actual.getX(), 1.0e-9),
                () -> assertEquals(y, actual.getY(), 1.0e-9),
                () -> assertEquals(z, actual.getZ(), 1.0e-9),
                () -> assertEquals(yaw, actual.getYaw(), 1.0e-5),
                () -> assertEquals(pitch, actual.getPitch(), 1.0e-5)
        );
    }
}
