package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.recording.model.RecordingSample;
import com.melluh.servertours.recording.storage.CameraRecording;
import com.melluh.servertours.playback.timeline.SceneClock;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/** Absolute-time camera track backed by a compiled free-flight recording. */
public final class RecordedCameraTrackRuntime implements CameraTrackRuntime {
    private final CraftTouringPlayer touringPlayer;
    private final MovementHandler movementHandler;
    private final CameraRecording recording;
    private final World world;
    private RecordingSample lastPresentationSample;
    private long presentationAnchorNanos;

    public RecordedCameraTrackRuntime(CraftTouringPlayer touringPlayer, MovementHandler movementHandler,
                                      CameraRecording recording, World world) {
        this.touringPlayer = Objects.requireNonNull(touringPlayer, "touringPlayer may not be null");
        this.movementHandler = Objects.requireNonNull(movementHandler, "movementHandler may not be null");
        this.recording = Objects.requireNonNull(recording, "recording may not be null");
        this.world = Objects.requireNonNull(world, "world may not be null");
    }

    @Override
    public long getEndFrame() {
        return this.recording.endFrame();
    }

    @Override
    public void setup(TrackContext context) {
        RecordingSample initial = this.recording.compiled().sampleAt(0L);
        this.movementHandler.initialize(this.touringPlayer, this.location(initial));
        this.lastPresentationSample = initial;
        this.presentationAnchorNanos = 0L;
    }

    @Override
    public void render(PlaybackFrame targetFrame) {
        RecordingSample target = this.recording.compiled().sampleAt(this.presentationNanos(targetFrame));
        if (this.lastPresentationSample != null
                && Math.abs(target.yawUnwrapped() - this.lastPresentationSample.yawUnwrapped()) > 180.0D) {
            this.movementHandler.rebase(this.touringPlayer, this.location(target),
                    StateRebaseReason.ROTATION_DISCONTINUITY);
        } else {
            this.movementHandler.move(this.touringPlayer, this.location(target));
        }
        this.lastPresentationSample = target;
    }

    @Override
    public void rebase(PlaybackFrame targetFrame, StateRebaseReason reason) {
        RecordingSample target = this.recording.compiled().sampleAt(targetFrame.sceneNanos());
        this.movementHandler.rebase(this.touringPlayer, this.location(target), reason);
        this.lastPresentationSample = target;
        this.presentationAnchorNanos = targetFrame.sceneNanos();
    }

    @Override
    public void onPause(TrackContext context, PauseReason reason) {
        long frameIndex = Math.min(context.getSession().getCurrentFrame(), this.getEndFrame());
        long sceneNanos = frameNanos(frameIndex);
        RecordingSample target = this.recording.compiled().sampleAt(sceneNanos);
        this.movementHandler.rebase(this.touringPlayer, this.location(target),
                StateRebaseReason.PAUSE_FREEZE);
        this.lastPresentationSample = target;
        this.presentationAnchorNanos = sceneNanos;
    }

    @Override
    public void teardown(TrackContext context) {
        this.movementHandler.cleanup();
        this.lastPresentationSample = null;
    }

    private Location location(RecordingSample sample) {
        return new Location(this.world, sample.x(), sample.y(), sample.z(),
                (float) sample.yawUnwrapped(), (float) sample.pitch());
    }

    private long presentationNanos(PlaybackFrame frame) {
        long configuredLead = Math.max(0, this.movementHandler.presentationLeadFrames());
        long elapsedSinceAnchor = Math.max(0L, frame.sceneNanos() - this.presentationAnchorNanos);
        long rampFrames = elapsedSinceAnchor / SceneClock.FRAME_NANOS;
        long leadFrames = Math.min(configuredLead, rampFrames);
        long leadNanos;
        try {
            leadNanos = Math.multiplyExact(leadFrames, SceneClock.FRAME_NANOS);
            return Math.addExact(frame.sceneNanos(), leadNanos);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long frameNanos(long frameIndex) {
        try {
            return Math.multiplyExact(frameIndex, SceneClock.FRAME_NANOS);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
