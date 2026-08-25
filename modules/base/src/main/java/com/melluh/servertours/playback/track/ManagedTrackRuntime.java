package com.melluh.servertours.playback.track;

import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.track.EventTrackRuntime;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.StateTrackRuntime;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.api.playback.track.TrackRuntime;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Guards a third-party runtime so lifecycle callbacks are delivered once per
 * actual session transition, even when stop/pause requests are re-entrant.
 */
public final class ManagedTrackRuntime {
    private final Plugin owner;
    private final NamespacedKey key;
    private final int priority;
    private final long registrationOrder;
    private final long endFrame;
    private final TrackRuntime runtime;
    private final TrackContext context;
    private boolean setup;
    private boolean paused;
    private boolean closed;
    private boolean stateFrameRendered;
    private long lastStateFrame = -1L;

    public ManagedTrackRuntime(TrackFactoryRegistration registration, TrackRuntime runtime, TrackContext context,
                               long endFrame) {
        this.owner = registration.owner();
        this.key = registration.key();
        this.priority = registration.priority();
        this.registrationOrder = registration.registrationOrder();
        this.endFrame = endFrame;
        this.runtime = Objects.requireNonNull(runtime, "runtime may not be null");
        this.context = Objects.requireNonNull(context, "context may not be null");
    }

    public Plugin owner() {
        return this.owner;
    }

    public NamespacedKey key() {
        return this.key;
    }

    public int priority() {
        return this.priority;
    }

    public long registrationOrder() {
        return this.registrationOrder;
    }

    public TrackRuntime runtime() {
        return this.runtime;
    }

    public long endFrame() {
        return this.endFrame;
    }

    public StateTrackRuntime stateRuntime() {
        return this.runtime instanceof StateTrackRuntime stateTrackRuntime ? stateTrackRuntime : null;
    }

    public EventTrackRuntime eventRuntime() {
        return this.runtime instanceof EventTrackRuntime eventTrackRuntime ? eventTrackRuntime : null;
    }

    /**
     * Renders a normal absolute-time update. Repeated targets are ignored;
     * adjacent frames use the continuous callback and all other transitions
     * snap through {@link StateTrackRuntime#rebase(PlaybackFrame, StateRebaseReason)}.
     */
    public void renderState(PlaybackFrame targetFrame) {
        Objects.requireNonNull(targetFrame, "targetFrame may not be null");
        StateTrackRuntime stateTrackRuntime = this.stateRuntime();
        if (stateTrackRuntime == null || !this.setup || this.closed) {
            return;
        }
        if (this.stateFrameRendered && targetFrame.index() == this.lastStateFrame) {
            return;
        }

        if (!this.stateFrameRendered) {
            stateTrackRuntime.rebase(targetFrame, StateRebaseReason.SESSION_START);
        } else if (targetFrame.index() > this.lastStateFrame
                && targetFrame.index() - this.lastStateFrame == 1L) {
            stateTrackRuntime.render(targetFrame);
        } else {
            stateTrackRuntime.rebase(targetFrame, StateRebaseReason.CLOCK_CATCH_UP);
        }
        this.recordStateFrame(targetFrame);
    }

    /**
     * Forces a snap even when the target frame was already rendered. This is
     * required for same-frame seeks through zero-duration route points and for
     * recovery after a pause.
     */
    public void rebaseState(PlaybackFrame targetFrame, StateRebaseReason reason) {
        StateTrackRuntime stateTrackRuntime = this.stateRuntime();
        this.rebaseState(targetFrame, reason, () -> stateTrackRuntime.rebase(targetFrame, reason));
    }

    /**
     * Records a specialized state snap, such as the route camera's explicit
     * point-start sample, only after the transport callback succeeds.
     */
    public void rebaseState(PlaybackFrame targetFrame, StateRebaseReason reason, Runnable rebaseAction) {
        Objects.requireNonNull(targetFrame, "targetFrame may not be null");
        Objects.requireNonNull(reason, "reason may not be null");
        Objects.requireNonNull(rebaseAction, "rebaseAction may not be null");
        if (this.stateRuntime() == null || !this.setup || this.closed) {
            return;
        }
        rebaseAction.run();
        this.recordStateFrame(targetFrame);
    }

    public long lastSuccessfulStateFrame() {
        return this.lastStateFrame;
    }

    private void recordStateFrame(PlaybackFrame targetFrame) {
        this.lastStateFrame = targetFrame.index();
        this.stateFrameRendered = true;
    }

    public void setup() throws Exception {
        if (this.setup || this.closed) {
            return;
        }
        this.setup = true;
        this.runtime.setup(this.context);
    }

    public void pause(PauseReason reason) throws Exception {
        if (!this.setup || this.closed || this.paused) {
            return;
        }
        this.paused = true;
        this.runtime.onPause(this.context, reason);
    }

    public void resume() throws Exception {
        if (!this.setup || this.closed || !this.paused) {
            return;
        }
        this.paused = false;
        this.runtime.onResume(this.context);
    }

    public void teardown() throws Exception {
        if (!this.setup || this.closed) {
            return;
        }
        this.closed = true;
        this.runtime.teardown(this.context);
    }
}
