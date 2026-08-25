package com.melluh.servertours.playback.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered, session-local cursor over immutable one-shot events.
 */
public final class PlaybackEventQueue {
    private final List<ScheduledPlaybackEvent> events;
    private final boolean[] consumed;
    private int cursor;

    public PlaybackEventQueue(List<ScheduledPlaybackEvent> source) {
        List<ScheduledPlaybackEvent> sorted = new ArrayList<>(source);
        Collections.sort(sorted);
        Set<String> ids = new HashSet<>();
        for (ScheduledPlaybackEvent event : sorted) {
            if (!ids.add(event.id())) {
                throw new IllegalArgumentException("duplicate timeline event id: " + event.id());
            }
        }
        this.events = List.copyOf(sorted);
        this.consumed = new boolean[this.events.size()];
    }

    public long clampToBarrier(long targetFrame) {
        for (int index = this.cursor; index < this.events.size(); index++) {
            if (this.consumed[index]) {
                continue;
            }
            ScheduledPlaybackEvent event = this.events.get(index);
            if (event.frame() > targetFrame) {
                break;
            }
            if (event.barrier() != ScheduledPlaybackEvent.Barrier.NONE) {
                return event.frame();
            }
        }
        return targetFrame;
    }

    public ScheduledPlaybackEvent peekDue(long targetFrame) {
        this.advancePastConsumed();
        if (this.cursor >= this.events.size()) {
            return null;
        }
        ScheduledPlaybackEvent event = this.events.get(this.cursor);
        return event.frame() <= targetFrame ? event : null;
    }

    /** Marks the current event consumed before its callback is invoked. */
    public ScheduledPlaybackEvent consume() {
        this.advancePastConsumed();
        if (this.cursor >= this.events.size()) {
            return null;
        }
        ScheduledPlaybackEvent event = this.events.get(this.cursor);
        this.consumed[this.cursor++] = true;
        return event;
    }

    public void seekAfterFrame(long frame) {
        for (int index = 0; index < this.events.size(); index++) {
            this.consumed[index] = this.events.get(index).frame() <= frame;
        }
        this.cursor = 0;
        this.advancePastConsumed();
    }

    /**
     * Repositions an explicit route seek. Third-party events on the target
     * frame are skipped, while built-in route events after the target's entry
     * bundle remain pending (important for zero-duration points and confirms).
     */
    public void seekToRouteEntry(long frame, String entryEventId) {
        ScheduledPlaybackEvent entry = this.events.stream()
                .filter(event -> event.id().equals(entryEventId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route entry event: " + entryEventId));
        int entryOrder = entry.eventOrder();
        for (int index = 0; index < this.events.size(); index++) {
            ScheduledPlaybackEvent event = this.events.get(index);
            if (event.frame() < frame) {
                this.consumed[index] = true;
            } else if (event.frame() > frame) {
                this.consumed[index] = false;
            } else if (event.id().startsWith("route/")) {
                this.consumed[index] = event.eventOrder() <= entryOrder;
            } else {
                this.consumed[index] = true;
            }
        }
        this.cursor = 0;
        this.advancePastConsumed();
    }

    int cursor() {
        return this.cursor;
    }

    private void advancePastConsumed() {
        while (this.cursor < this.events.size() && this.consumed[this.cursor]) {
            ++this.cursor;
        }
    }
}
