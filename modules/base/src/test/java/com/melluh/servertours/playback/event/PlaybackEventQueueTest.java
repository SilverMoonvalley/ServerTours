package com.melluh.servertours.playback.event;

import com.melluh.servertours.api.playback.PlaybackFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackEventQueueTest {

    @Test
    void ordersByFramePriorityTrackAndDeclarationOrder() throws Exception {
        List<String> calls = new ArrayList<>();
        PlaybackEventQueue queue = new PlaybackEventQueue(List.of(
                event(4L, 10, 1L, 0, "late", calls),
                event(3L, 10, 2L, 0, "track-two", calls),
                event(3L, 10, 1L, 1, "track-one-second", calls),
                event(3L, 5, 9L, 0, "higher-priority", calls),
                event(3L, 10, 1L, 0, "track-one-first", calls)
        ));

        drain(queue, 4L);

        assertEquals(List.of(
                "higher-priority",
                "track-one-first",
                "track-one-second",
                "track-two",
                "late"
        ), calls);
    }

    @Test
    void consumingBeforeCallbackMakesReentrantDispatchExactlyOnce() throws Exception {
        List<String> calls = new ArrayList<>();
        PlaybackEventQueue[] queueRef = new PlaybackEventQueue[1];
        ScheduledPlaybackEvent first = new ScheduledPlaybackEvent(
                1L, 0, 0L, 0, "first", ScheduledPlaybackEvent.Barrier.NONE,
                frame -> {
                    calls.add("first");
                    assertEquals("second", queueRef[0].peekDue(frame.index()).id());
                });
        ScheduledPlaybackEvent second = event(1L, 0, 0L, 1, "second", calls);
        queueRef[0] = new PlaybackEventQueue(List.of(first, second));

        ScheduledPlaybackEvent consumed = queueRef[0].consume();
        consumed.execute(frame(1L));
        drain(queueRef[0], 1L);
        drain(queueRef[0], 1L);

        assertEquals(List.of("first", "second"), calls);
        assertNull(queueRef[0].peekDue(1L));
    }

    @Test
    void clampsCatchUpAtFirstUnconsumedBarrier() throws Exception {
        List<String> calls = new ArrayList<>();
        PlaybackEventQueue queue = new PlaybackEventQueue(List.of(
                event(2L, 0, 0L, 0, "before", calls),
                barrier(3L, 0, "confirm", calls),
                event(3L, 0, 0L, 2, "same-frame-after", calls),
                event(6L, 0, 0L, 3, "future", calls)
        ));

        assertEquals(3L, queue.clampToBarrier(7L));
        drainUntilBarrier(queue, 3L);

        assertEquals(List.of("before", "confirm"), calls);
        assertEquals(3L, queue.clampToBarrier(3L));

        drain(queue, 3L);
        assertEquals(List.of("before", "confirm", "same-frame-after"), calls);
        assertEquals(7L, queue.clampToBarrier(7L));
    }

    @Test
    void seekAfterFrameSkipsEveryEventAtOrBeforeSeekPoint() throws Exception {
        List<String> calls = new ArrayList<>();
        PlaybackEventQueue queue = new PlaybackEventQueue(List.of(
                event(1L, 0, 0L, 0, "one", calls),
                event(3L, 0, 0L, 1, "three-a", calls),
                event(3L, 0, 0L, 2, "three-b", calls),
                event(4L, 0, 0L, 3, "four", calls)
        ));

        queue.seekAfterFrame(3L);
        drain(queue, 10L);

        assertEquals(List.of("four"), calls);
    }

    @Test
    void seekToRouteEntrySkipsTargetFramePluginsAndEntryButKeepsLaterRouteEvents() throws Exception {
        List<String> calls = new ArrayList<>();
        PlaybackEventQueue queue = new PlaybackEventQueue(List.of(
                scheduled(4L, 0, 0L, 0, "route/transition/0-1",
                        ScheduledPlaybackEvent.Barrier.NONE, calls),
                scheduled(10L, -100, 20L, 0, "example:track/at-target-before-route",
                        ScheduledPlaybackEvent.Barrier.NONE, calls),
                scheduled(10L, 0, 0L, 4, "route/transition/1-2",
                        ScheduledPlaybackEvent.Barrier.NONE, calls),
                scheduled(10L, 0, 0L, 5, "route/confirm/2/5",
                        ScheduledPlaybackEvent.Barrier.CONFIRMATION, calls),
                scheduled(10L, 0, 0L, 6, "route/transition/2-3",
                        ScheduledPlaybackEvent.Barrier.NONE, calls),
                scheduled(10L, 100, 30L, 0, "example:track/at-target-after-route",
                        ScheduledPlaybackEvent.Barrier.NONE, calls),
                scheduled(11L, 100, 30L, 1, "example:track/future",
                        ScheduledPlaybackEvent.Barrier.NONE, calls)
        ));

        queue.seekToRouteEntry(10L, "route/transition/1-2");

        assertEquals(10L, queue.clampToBarrier(20L));
        assertEquals("route/confirm/2/5", queue.peekDue(10L).id());
        queue.consume().execute(frame(10L));
        assertEquals("route/transition/2-3", queue.peekDue(10L).id());

        drain(queue, 20L);
        assertEquals(List.of(
                "route/confirm/2/5",
                "route/transition/2-3",
                "example:track/future"
        ), calls);
    }

    @Test
    void rejectsDuplicateEventIds() {
        List<String> calls = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> new PlaybackEventQueue(List.of(
                event(1L, 0, 0L, 0, "duplicate", calls),
                event(2L, 0, 0L, 1, "duplicate", calls)
        )));
    }

    private static ScheduledPlaybackEvent event(long frame, int priority, long trackOrder,
                                                int eventOrder, String id, List<String> calls) {
        return new ScheduledPlaybackEvent(frame, priority, trackOrder, eventOrder, id,
                ScheduledPlaybackEvent.Barrier.NONE, ignored -> calls.add(id));
    }

    private static ScheduledPlaybackEvent barrier(long frame, int eventOrder, String id, List<String> calls) {
        return new ScheduledPlaybackEvent(frame, 0, 0L, eventOrder, id,
                ScheduledPlaybackEvent.Barrier.CONFIRMATION, ignored -> calls.add(id));
    }

    private static ScheduledPlaybackEvent scheduled(long frame, int priority, long trackOrder, int eventOrder,
                                                    String id, ScheduledPlaybackEvent.Barrier barrier,
                                                    List<String> calls) {
        return new ScheduledPlaybackEvent(frame, priority, trackOrder, eventOrder, id, barrier,
                ignored -> calls.add(id));
    }

    private static void drain(PlaybackEventQueue queue, long targetFrame) throws Exception {
        ScheduledPlaybackEvent event;
        while ((event = queue.peekDue(targetFrame)) != null) {
            queue.consume().execute(frame(targetFrame));
        }
    }

    private static void drainUntilBarrier(PlaybackEventQueue queue, long targetFrame) throws Exception {
        ScheduledPlaybackEvent event;
        while ((event = queue.peekDue(targetFrame)) != null) {
            queue.consume().execute(frame(targetFrame));
            if (event.barrier() != ScheduledPlaybackEvent.Barrier.NONE) {
                return;
            }
        }
    }

    private static PlaybackFrame frame(long index) {
        return new PlaybackFrame(index, index * 50_000_000L, 100L);
    }
}
