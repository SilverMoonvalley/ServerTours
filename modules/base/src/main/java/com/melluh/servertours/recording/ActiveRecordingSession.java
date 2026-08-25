package com.melluh.servertours.recording;

import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.recording.math.FixedRateSampleGate;
import com.melluh.servertours.recording.math.YawUnwrapper;
import com.melluh.servertours.recording.model.RecordingSample;
import com.melluh.servertours.recording.storage.RecordingDraft;
import com.melluh.servertours.recording.storage.RecordingMetadata;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Main-thread-owned mutable capture state for one player. */
final class ActiveRecordingSession {
    private final Player player;
    private final RecordingMetadata metadata;
    private final NanoClock clock;
    private final PlayerRestoreWrapper restoreWrapper;
    private final Location returnLocation;
    private final List<RecordingSample> baselineSamples;
    private final List<RecordingSample> samples;
    private final FixedRateSampleGate sampleGate;
    private final YawUnwrapper yawUnwrapper;
    private final long anchorNanos;
    private final long timeOffsetNanos;
    private final long maxDurationNanos;

    private boolean stopped;
    private boolean restored;

    ActiveRecordingSession(Player player, RecordingMetadata metadata, List<RecordingSample> baselineSamples,
                           PlayerRestoreWrapper restoreWrapper, Location returnLocation, NanoClock clock,
                           long maxDurationNanos) {
        this.player = Objects.requireNonNull(player, "player may not be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata may not be null");
        this.clock = Objects.requireNonNull(clock, "clock may not be null");
        this.restoreWrapper = Objects.requireNonNull(restoreWrapper, "restoreWrapper may not be null");
        this.returnLocation = checkedClone(returnLocation);
        this.baselineSamples = List.copyOf(Objects.requireNonNull(baselineSamples,
                "baselineSamples may not be null"));
        this.samples = new ArrayList<>(this.baselineSamples);
        this.sampleGate = new FixedRateSampleGate(metadata.sampleIntervalNanos());
        this.yawUnwrapper = new YawUnwrapper();
        this.anchorNanos = this.clock.now();
        if (maxDurationNanos <= 0L) {
            throw new IllegalArgumentException("maxDurationNanos must be greater than zero");
        }
        this.maxDurationNanos = maxDurationNanos;

        if (this.samples.isEmpty()) {
            this.timeOffsetNanos = 0L;
            if (!this.sampleGate.shouldCapture(0L)) {
                throw new IllegalStateException("initial sample gate rejected frame zero");
            }
            this.captureAt(0L, this.player.getLocation());
        } else {
            RecordingSample last = this.samples.get(this.samples.size() - 1);
            this.timeOffsetNanos = last.timeNanos();
            if (this.timeOffsetNanos >= this.maxDurationNanos) {
                throw new IllegalArgumentException("baseline has already reached the recording duration limit");
            }
            this.validateSamples(this.samples);
            this.sampleGate.shouldCapture(this.timeOffsetNanos);
            this.yawUnwrapper.accept(last.yawUnwrapped());
        }
    }

    Player player() {
        return this.player;
    }

    RecordingMetadata metadata() {
        return this.metadata;
    }

    int sampleCount() {
        return this.samples.size();
    }

    long elapsedNanos() {
        long delta = this.clock.now() - this.anchorNanos;
        if (delta < 0L) {
            delta = 0L;
        }
        if (Long.MAX_VALUE - this.timeOffsetNanos < delta) {
            return this.maxDurationNanos;
        }
        return Math.min(this.maxDurationNanos, this.timeOffsetNanos + delta);
    }

    long maxDurationNanos() {
        return this.maxDurationNanos;
    }

    void tick() {
        this.requireActive();
        long elapsed = this.elapsedNanos();
        if (this.sampleGate.shouldCapture(elapsed)) {
            this.captureAt(elapsed, this.player.getLocation());
        }
    }

    RecordingDraft stopAndSnapshot(boolean captureFinalPose) {
        this.requireActive();
        if (captureFinalPose) {
            this.captureFinal();
        }
        this.stopped = true;
        return new RecordingDraft(this.metadata, List.copyOf(this.samples));
    }

    List<RecordingSample> baselineSamples() {
        return this.baselineSamples;
    }

    void restore(Consumer<Location> internalTeleporter) {
        Objects.requireNonNull(internalTeleporter, "internalTeleporter may not be null");
        if (this.restored) {
            return;
        }
        this.restored = true;
        Throwable teleportFailure = null;
        try {
            internalTeleporter.accept(this.returnLocation.clone());
        } catch (Throwable throwable) {
            teleportFailure = throwable;
        }
        this.restoreWrapper.restore();
        if (teleportFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (teleportFailure instanceof Error error) {
            throw error;
        }
    }

    PlayerRestoreWrapper restoreWrapper() {
        return this.restoreWrapper;
    }

    private void captureFinal() {
        long elapsed = this.elapsedNanos();
        Location location = this.player.getLocation();
        RecordingSample last = this.samples.get(this.samples.size() - 1);
        if (elapsed > last.timeNanos()) {
            this.captureAt(elapsed, location);
            return;
        }

        double yaw = this.yawUnwrapper.isInitialized()
                ? YawUnwrapper.unwrap(last.yawUnwrapped(), location.getYaw())
                : location.getYaw();
        this.samples.set(this.samples.size() - 1, sample(last.timeNanos(), location, yaw));
    }

    private void captureAt(long elapsedNanos, Location location) {
        this.requireRecordingWorld(location);
        double yaw = this.yawUnwrapper.accept(location.getYaw());
        this.samples.add(sample(elapsedNanos, location, yaw));
    }

    private RecordingSample sample(long elapsedNanos, Location location, double yaw) {
        double pitch = Math.max(-90.0, Math.min(90.0, location.getPitch()));
        return new RecordingSample(elapsedNanos, location.getX(), location.getY(), location.getZ(), yaw, pitch);
    }

    private void requireRecordingWorld(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "player location world may not be null");
        if (!world.getUID().equals(this.metadata.worldId())) {
            throw new IllegalStateException("camera recording cannot cross worlds");
        }
    }

    private void validateSamples(List<RecordingSample> raw) {
        if (raw.get(0).timeNanos() != 0L) {
            throw new IllegalArgumentException("baseline must start at 0ns");
        }
        long previous = -1L;
        for (RecordingSample sample : raw) {
            if (sample.timeNanos() <= previous) {
                throw new IllegalArgumentException("baseline sample times must be strictly increasing");
            }
            previous = sample.timeNanos();
        }
    }

    private void requireActive() {
        if (this.stopped) {
            throw new IllegalStateException("recording session has already stopped");
        }
    }

    private static Location checkedClone(Location location) {
        Objects.requireNonNull(location, "location may not be null");
        Objects.requireNonNull(location.getWorld(), "location world may not be null");
        return location.clone();
    }
}
