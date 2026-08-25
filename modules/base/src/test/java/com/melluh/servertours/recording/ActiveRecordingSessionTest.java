package com.melluh.servertours.recording;

import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.recording.math.FixedRateSampleGate;
import com.melluh.servertours.recording.math.RecordingTolerances;
import com.melluh.servertours.recording.model.RecordingSample;
import com.melluh.servertours.recording.storage.RecordingDraft;
import com.melluh.servertours.recording.storage.RecordingMetadata;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveRecordingSessionTest {
    private static final long FRAME_NANOS = FixedRateSampleGate.DEFAULT_INTERVAL_NANOS;

    @Test
    void capturesFrameZeroFromPlayersFeetLocation() {
        Harness harness = new Harness(9_000_000_000L);
        harness.moveTo(12.25, 64.0, -8.5, 35.0F, -12.0F);

        ActiveRecordingSession session = harness.start(List.of());
        RecordingSample first = session.stopAndSnapshot(false).rawSamples().get(0);

        assertEquals(0L, first.timeNanos());
        assertEquals(12.25, first.x());
        assertEquals(64.0, first.y());
        assertEquals(-8.5, first.z());
        assertEquals(35.0, first.yawUnwrapped());
        assertEquals(-12.0, first.pitch());
    }

    @Test
    void capturesAtMostOneRealPosePerFiftyMillisecondBucket() {
        Harness harness = new Harness(1_000L);
        ActiveRecordingSession session = harness.start(List.of());

        harness.clock.advance(FRAME_NANOS - 1L);
        harness.moveTo(1.0, 65.0, 0.0, 0.0F, 0.0F);
        session.tick();

        harness.clock.advance(1L);
        harness.moveTo(2.0, 66.0, 0.0, 0.0F, 0.0F);
        session.tick();

        harness.clock.advance(FRAME_NANOS - 1L);
        harness.moveTo(3.0, 67.0, 0.0, 0.0F, 0.0F);
        session.tick();

        harness.clock.advance(1L);
        harness.moveTo(4.0, 68.0, 0.0, 0.0F, 0.0F);
        session.tick();

        List<RecordingSample> samples = session.stopAndSnapshot(false).rawSamples();
        assertEquals(List.of(0L, FRAME_NANOS, FRAME_NANOS * 2L), times(samples));
        assertEquals(List.of(0.0, 2.0, 4.0), xs(samples));
    }

    @Test
    void doesNotSynthesizeSkippedSamplesWhenClockJumpsFromFiftyToThreeHundredFiftyMilliseconds() {
        Harness harness = new Harness(50_000L);
        ActiveRecordingSession session = harness.start(List.of());

        harness.clock.advance(FRAME_NANOS);
        harness.moveTo(1.0, 64.0, 0.0, 0.0F, 0.0F);
        session.tick();

        harness.clock.advance(FRAME_NANOS * 6L);
        harness.moveTo(7.0, 64.0, 0.0, 0.0F, 0.0F);
        session.tick();

        List<RecordingSample> samples = session.stopAndSnapshot(false).rawSamples();
        assertEquals(List.of(0L, FRAME_NANOS, FRAME_NANOS * 7L), times(samples));
        assertEquals(List.of(0.0, 1.0, 7.0), xs(samples));
    }

    @Test
    void stopAppendsTheExactFinalPoseEvenInsideTheCurrentBucket() {
        Harness harness = new Harness(2_000L);
        ActiveRecordingSession session = harness.start(List.of());

        harness.clock.advance(FRAME_NANOS / 2L);
        harness.moveTo(3.5, 70.0, -4.0, 80.0F, 20.0F);
        RecordingDraft draft = session.stopAndSnapshot(true);

        assertEquals(List.of(0L, FRAME_NANOS / 2L), times(draft.rawSamples()));
        RecordingSample last = draft.rawSamples().get(1);
        assertEquals(3.5, last.x());
        assertEquals(70.0, last.y());
        assertEquals(-4.0, last.z());
        assertEquals(80.0, last.yawUnwrapped());
        assertEquals(20.0, last.pitch());
    }

    @Test
    void unwrapsYawAcrossTheSignedBoundary() {
        Harness harness = new Harness(3_000L);
        harness.moveTo(0.0, 64.0, 0.0, 179.0F, 0.0F);
        ActiveRecordingSession session = harness.start(List.of());

        harness.clock.advance(FRAME_NANOS);
        harness.moveTo(0.0, 64.0, 0.0, -179.0F, 0.0F);
        session.tick();

        harness.clock.advance(FRAME_NANOS);
        harness.moveTo(0.0, 64.0, 0.0, -175.0F, 0.0F);
        session.tick();

        List<RecordingSample> samples = session.stopAndSnapshot(false).rawSamples();
        assertEquals(List.of(179.0, 181.0, 185.0), yaws(samples));
    }

    @Test
    void resumeContinuesAtBaselineOffsetWithoutCountingOfflineTime() {
        List<RecordingSample> baseline = List.of(
                sample(0L, 0.0, 170.0),
                sample(FRAME_NANOS * 3L, 3.0, 201.0)
        );
        Harness harness = new Harness(3_600_000_000_000L);
        harness.moveTo(3.0, 64.0, 0.0, -159.0F, 0.0F);

        ActiveRecordingSession session = harness.start(baseline);
        assertEquals(FRAME_NANOS * 3L, session.elapsedNanos());

        harness.clock.advance(FRAME_NANOS - 1L);
        session.tick();
        harness.clock.advance(1L);
        harness.moveTo(4.0, 64.0, 0.0, -158.0F, 0.0F);
        session.tick();

        List<RecordingSample> samples = session.stopAndSnapshot(false).rawSamples();
        assertEquals(List.of(0L, FRAME_NANOS * 3L, FRAME_NANOS * 4L), times(samples));
        assertEquals(202.0, samples.get(2).yawUnwrapped());
    }

    @Test
    void exposesAnImmutableResumeBaselineForCancelRollback() {
        List<RecordingSample> mutableBaseline = new ArrayList<>(List.of(
                sample(0L, 0.0, 0.0),
                sample(FRAME_NANOS, 1.0, 10.0)
        ));
        Harness harness = new Harness(4_000L);
        ActiveRecordingSession session = harness.start(mutableBaseline);
        mutableBaseline.clear();

        harness.clock.advance(FRAME_NANOS);
        harness.moveTo(2.0, 64.0, 0.0, 20.0F, 0.0F);
        session.tick();

        assertEquals(2, session.baselineSamples().size());
        assertEquals(3, session.sampleCount());
        assertThrows(UnsupportedOperationException.class,
                () -> session.baselineSamples().add(sample(FRAME_NANOS * 2L, 2.0, 20.0)));
    }

    @Test
    void rejectsRepeatedStop() {
        Harness harness = new Harness(5_000L);
        ActiveRecordingSession session = harness.start(List.of());

        session.stopAndSnapshot(false);

        assertThrows(IllegalStateException.class, () -> session.stopAndSnapshot(false));
        assertThrows(IllegalStateException.class, session::tick);
    }

    @Test
    void rejectsAWorldChangeWithoutAddingTheForeignPose() {
        Harness harness = new Harness(6_000L);
        ActiveRecordingSession session = harness.start(List.of());
        World otherWorld = mock(World.class);
        when(otherWorld.getUID()).thenReturn(UUID.randomUUID());

        harness.clock.advance(FRAME_NANOS);
        harness.currentLocation = new Location(otherWorld, 100.0, 80.0, 100.0, 0.0F, 0.0F);

        assertThrows(IllegalStateException.class, session::tick);
        assertEquals(1, session.sampleCount());
    }

    @Test
    void durationLimitClampsAStalledCaptureToTheConfiguredMaximum() {
        Harness harness = new Harness(7_000L);
        ActiveRecordingSession session = harness.start(List.of(), FRAME_NANOS * 2L);

        harness.clock.advance(FRAME_NANOS * 7L);
        harness.moveTo(7.0, 64.0, 0.0, 70.0F, 0.0F);
        session.tick();

        RecordingDraft draft = session.stopAndSnapshot(true);
        assertEquals(FRAME_NANOS * 2L, session.elapsedNanos());
        assertEquals(List.of(0L, FRAME_NANOS * 2L), times(draft.rawSamples()));
        assertEquals(7.0, draft.rawSamples().get(1).x());
    }

    private static RecordingSample sample(long timeNanos, double x, double yaw) {
        return new RecordingSample(timeNanos, x, 64.0, 0.0, yaw, 0.0);
    }

    private static List<Long> times(List<RecordingSample> samples) {
        return samples.stream().map(RecordingSample::timeNanos).toList();
    }

    private static List<Double> xs(List<RecordingSample> samples) {
        return samples.stream().map(RecordingSample::x).toList();
    }

    private static List<Double> yaws(List<RecordingSample> samples) {
        return samples.stream().map(RecordingSample::yawUnwrapped).toList();
    }

    private static final class Harness {
        private final UUID worldId = UUID.randomUUID();
        private final World world = mock(World.class);
        private final Player player = mock(Player.class);
        private final PlayerRestoreWrapper restoreWrapper = mock(PlayerRestoreWrapper.class);
        private final MutableNanoClock clock;
        private Location currentLocation;

        private Harness(long initialNanos) {
            this.clock = new MutableNanoClock(initialNanos);
            this.currentLocation = new Location(this.world, 0.0, 64.0, 0.0, 0.0F, 0.0F);
            when(this.world.getUID()).thenReturn(this.worldId);
            when(this.player.getLocation()).thenAnswer(ignored -> this.currentLocation.clone());
        }

        private void moveTo(double x, double y, double z, float yaw, float pitch) {
            this.currentLocation = new Location(this.world, x, y, z, yaw, pitch);
        }

        private ActiveRecordingSession start(List<RecordingSample> baseline) {
            return this.start(baseline, 300_000_000_000L);
        }

        private ActiveRecordingSession start(List<RecordingSample> baseline, long maxDurationNanos) {
            RecordingMetadata metadata = new RecordingMetadata(
                    UUID.randomUUID(), "recording-test", UUID.randomUUID(), "Recorder",
                    this.worldId, "world", 1L, FRAME_NANOS, RecordingTolerances.DEFAULT, 1);
            return new ActiveRecordingSession(this.player, metadata, baseline, this.restoreWrapper,
                    this.currentLocation.clone(), this.clock, maxDurationNanos);
        }
    }

    private static final class MutableNanoClock implements NanoClock {
        private long now;

        private MutableNanoClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return this.now;
        }

        private void advance(long nanos) {
            this.now += nanos;
        }
    }
}
