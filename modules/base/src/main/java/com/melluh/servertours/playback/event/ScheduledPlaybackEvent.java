package com.melluh.servertours.playback.event;

import com.melluh.servertours.api.playback.PlaybackFrame;

import java.util.Objects;

public final class ScheduledPlaybackEvent implements Comparable<ScheduledPlaybackEvent> {
    public enum Barrier {
        NONE,
        CONFIRMATION,
        TERMINAL
    }

    @FunctionalInterface
    public interface Action {
        void execute(PlaybackFrame frame) throws Exception;
    }

    private final long frame;
    private final int priority;
    private final long trackOrder;
    private final int eventOrder;
    private final String id;
    private final Barrier barrier;
    private final Action action;

    public ScheduledPlaybackEvent(long frame, int priority, long trackOrder, int eventOrder,
                                  String id, Barrier barrier, Action action) {
        if (frame < 0L) {
            throw new IllegalArgumentException("event frame may not be negative");
        }
        this.frame = frame;
        this.priority = priority;
        this.trackOrder = trackOrder;
        this.eventOrder = eventOrder;
        this.id = Objects.requireNonNull(id, "id may not be null");
        this.barrier = Objects.requireNonNull(barrier, "barrier may not be null");
        this.action = Objects.requireNonNull(action, "action may not be null");
    }

    public long frame() {
        return this.frame;
    }

    public String id() {
        return this.id;
    }

    public int eventOrder() {
        return this.eventOrder;
    }

    public Barrier barrier() {
        return this.barrier;
    }

    public void execute(PlaybackFrame playbackFrame) throws Exception {
        this.action.execute(playbackFrame);
    }

    @Override
    public int compareTo(ScheduledPlaybackEvent other) {
        int result = Long.compare(this.frame, other.frame);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.priority, other.priority);
        if (result != 0) {
            return result;
        }
        result = Long.compare(this.trackOrder, other.trackOrder);
        if (result != 0) {
            return result;
        }
        return Integer.compare(this.eventOrder, other.eventOrder);
    }
}
