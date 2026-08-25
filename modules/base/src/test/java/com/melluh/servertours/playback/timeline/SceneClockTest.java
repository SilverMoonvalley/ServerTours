package com.melluh.servertours.playback.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneClockTest {

    @Test
    void usesFixedFiftyMillisecondFrames() {
        MutableNanoClock time = new MutableNanoClock();
        SceneClock clock = new SceneClock(time);
        clock.startAt(0L);

        time.advanceNanos(SceneClock.FRAME_NANOS - 1L);
        assertEquals(0L, clock.currentTarget(100L));

        time.advanceNanos(1L);
        assertEquals(1L, clock.currentTarget(100L));
    }

    @Test
    void jumpsDirectlyFromFiftyToThreeHundredFiftyMilliseconds() {
        MutableNanoClock time = new MutableNanoClock();
        SceneClock clock = new SceneClock(time);
        clock.startAt(0L);

        time.setNanos(50_000_000L);
        assertEquals(1L, clock.currentTarget(100L));

        time.setNanos(350_000_000L);
        assertEquals(7L, clock.currentTarget(100L));
    }

    @Test
    void repeatedReadsAtSameTimeNeverAccumulateDrift() {
        MutableNanoClock time = new MutableNanoClock();
        SceneClock clock = new SceneClock(time);
        clock.startAt(0L);
        time.setNanos(125_000_000L);

        for (int index = 0; index < 100; index++) {
            assertEquals(2L, clock.currentTarget(100L));
        }

        time.setNanos(150_000_000L);
        assertEquals(3L, clock.currentTarget(100L));
    }

    @Test
    void pauseAtBarrierClampsCandidateAndDiscardsPausedWallTime() {
        MutableNanoClock time = new MutableNanoClock();
        SceneClock clock = new SceneClock(time);
        clock.startAt(0L);
        time.setNanos(350_000_000L);

        assertEquals(7L, clock.currentTarget(100L));
        clock.pauseAt(3L);

        assertTrue(clock.isPaused());
        assertEquals(3L, clock.getCurrentFrame());
        time.setNanos(10_350_000_000L);
        assertEquals(3L, clock.currentTarget(100L));

        clock.resume();
        assertFalse(clock.isPaused());
        assertEquals(3L, clock.currentTarget(100L));
        time.advanceNanos(SceneClock.FRAME_NANOS);
        assertEquals(4L, clock.currentTarget(100L));
    }

    @Test
    void startAtReanchorsAnExplicitSeek() {
        MutableNanoClock time = new MutableNanoClock();
        SceneClock clock = new SceneClock(time);
        clock.startAt(12L);
        time.advanceNanos(100_000_000L);
        assertEquals(14L, clock.currentTarget(100L));

        clock.startAt(3L);
        assertEquals(3L, clock.currentTarget(100L));
        time.advanceNanos(SceneClock.FRAME_NANOS);
        assertEquals(4L, clock.currentTarget(100L));
    }

    private static final class MutableNanoClock implements NanoClock {
        private long nanos;

        @Override
        public long now() {
            return this.nanos;
        }

        void setNanos(long nanos) {
            this.nanos = nanos;
        }

        void advanceNanos(long nanos) {
            this.nanos += nanos;
        }
    }
}
