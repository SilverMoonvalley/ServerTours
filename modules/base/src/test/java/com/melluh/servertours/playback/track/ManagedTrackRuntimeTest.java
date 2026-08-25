package com.melluh.servertours.playback.track;

import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.StateTrackRuntime;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.api.playback.track.TrackRuntime;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ManagedTrackRuntimeTest {

    @Test
    void lifecycleTransitionsStayIdempotentUnderReentry() throws Exception {
        AtomicInteger setups = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        AtomicInteger resumes = new AtomicInteger();
        AtomicInteger teardowns = new AtomicInteger();
        ManagedTrackRuntime[] managed = new ManagedTrackRuntime[1];
        TrackRuntime runtime = new TrackRuntime() {
            @Override
            public long getEndFrame() {
                return 10L;
            }

            @Override
            public void setup(TrackContext context) {
                setups.incrementAndGet();
                assertDoesNotThrowManaged(managed[0]::setup);
            }

            @Override
            public void onPause(TrackContext context, PauseReason reason) {
                pauses.incrementAndGet();
                assertDoesNotThrowManaged(() -> managed[0].pause(reason));
            }

            @Override
            public void onResume(TrackContext context) {
                resumes.incrementAndGet();
                assertDoesNotThrowManaged(managed[0]::resume);
            }

            @Override
            public void teardown(TrackContext context) {
                teardowns.incrementAndGet();
                assertDoesNotThrowManaged(managed[0]::teardown);
            }
        };
        managed[0] = managed(runtime);

        managed[0].setup();
        managed[0].setup();
        managed[0].pause(PauseReason.MANUAL);
        managed[0].pause(PauseReason.MANUAL);
        managed[0].resume();
        managed[0].resume();
        managed[0].teardown();
        managed[0].teardown();

        assertEquals(1, setups.get());
        assertEquals(1, pauses.get());
        assertEquals(1, resumes.get());
        assertEquals(1, teardowns.get());
    }

    @Test
    void failedSetupStillGetsExactlyOneTeardown() throws Exception {
        AtomicInteger setups = new AtomicInteger();
        AtomicInteger teardowns = new AtomicInteger();
        TrackRuntime runtime = new TrackRuntime() {
            @Override
            public long getEndFrame() {
                return 0L;
            }

            @Override
            public void setup(TrackContext context) {
                setups.incrementAndGet();
                throw new IllegalStateException("boom");
            }

            @Override
            public void teardown(TrackContext context) {
                teardowns.incrementAndGet();
            }
        };
        ManagedTrackRuntime managed = managed(runtime);

        assertThrows(IllegalStateException.class, managed::setup);
        managed.teardown();
        managed.teardown();

        assertEquals(1, setups.get());
        assertEquals(1, teardowns.get());
    }

    @Test
    void stateFramesUseContinuousRenderOnlyForAdjacentTargets() throws Exception {
        List<String> calls = new ArrayList<>();
        StateTrackRuntime runtime = new StateTrackRuntime() {
            @Override
            public long getEndFrame() {
                return 10L;
            }

            @Override
            public void render(PlaybackFrame targetFrame) {
                calls.add("render:" + targetFrame.index());
            }

            @Override
            public void rebase(PlaybackFrame targetFrame, StateRebaseReason reason) {
                calls.add("rebase:" + targetFrame.index() + ":" + reason);
            }
        };
        ManagedTrackRuntime managed = managed(runtime);
        managed.setup();

        managed.renderState(frame(0L));
        managed.renderState(frame(0L));
        managed.renderState(frame(1L));
        managed.renderState(frame(4L));
        managed.renderState(frame(4L));
        managed.renderState(frame(2L));

        assertEquals(List.of(
                "rebase:0:SESSION_START",
                "render:1",
                "rebase:4:CLOCK_CATCH_UP",
                "rebase:2:CLOCK_CATCH_UP"
        ), calls);
        assertEquals(2L, managed.lastSuccessfulStateFrame());
    }

    @Test
    void forcedRebaseRunsAtSameFrameAndRecordsOnlyAfterSuccess() throws Exception {
        List<String> calls = new ArrayList<>();
        AtomicInteger failedAttempts = new AtomicInteger();
        StateTrackRuntime runtime = new StateTrackRuntime() {
            @Override
            public long getEndFrame() {
                return 10L;
            }

            @Override
            public void render(PlaybackFrame targetFrame) {
                if (targetFrame.index() == 1L && failedAttempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("render failed");
                }
                calls.add("render:" + targetFrame.index());
            }

            @Override
            public void rebase(PlaybackFrame targetFrame, StateRebaseReason reason) {
                calls.add("rebase:" + targetFrame.index() + ":" + reason);
            }
        };
        ManagedTrackRuntime managed = managed(runtime);
        managed.setup();
        managed.renderState(frame(0L));

        assertThrows(IllegalStateException.class, () -> managed.renderState(frame(1L)));
        assertEquals(0L, managed.lastSuccessfulStateFrame());
        managed.renderState(frame(1L));
        managed.rebaseState(frame(1L), StateRebaseReason.RESUME_RECOVERY);
        managed.rebaseState(frame(1L), StateRebaseReason.EXPLICIT_SEEK);

        assertEquals(List.of(
                "rebase:0:SESSION_START",
                "render:1",
                "rebase:1:RESUME_RECOVERY",
                "rebase:1:EXPLICIT_SEEK"
        ), calls);
        assertEquals(1L, managed.lastSuccessfulStateFrame());
    }

    @Test
    void defaultStateRebaseKeepsLegacyRuntimeCompatible() throws Exception {
        AtomicInteger renders = new AtomicInteger();
        StateTrackRuntime runtime = new StateTrackRuntime() {
            @Override
            public long getEndFrame() {
                return 0L;
            }

            @Override
            public void render(PlaybackFrame targetFrame) {
                renders.incrementAndGet();
            }
        };
        ManagedTrackRuntime managed = managed(runtime);
        managed.setup();

        managed.renderState(frame(0L));
        managed.rebaseState(frame(0L), StateRebaseReason.RESUME_RECOVERY);

        assertEquals(2, renders.get());
    }

    private static ManagedTrackRuntime managed(TrackRuntime runtime) {
        TrackFactoryRegistration registration = new TrackFactoryRegistration(
                mock(Plugin.class), new NamespacedKey("test", "track"), 0, 0L,
                context -> java.util.Optional.of(runtime));
        return new ManagedTrackRuntime(registration, runtime, mock(TrackContext.class), runtime.getEndFrame());
    }

    private static PlaybackFrame frame(long index) {
        return new PlaybackFrame(index, index * 50_000_000L, 10L);
    }

    private static void assertDoesNotThrowManaged(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
