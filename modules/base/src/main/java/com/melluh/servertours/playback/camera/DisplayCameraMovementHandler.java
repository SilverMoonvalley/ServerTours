package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.nms.NmsHandler;
import com.melluh.servertours.nms.TemporaryDisplayCamera;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import com.melluh.servertours.util.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Java camera transport backed by a viewer-only text display. Logical scene
 * time remains server-side; the display interpolation is presentation only.
 */
public final class DisplayCameraMovementHandler implements MovementHandler {
    private final NmsHandler nmsHandler;
    private final int interpolationTicks;
    private final int anchorIntervalFrames;
    private final double maxAnchorDistanceSquared;

    private Player player;
    private TemporaryDisplayCamera camera;
    private Location lastFeetLocation;
    private Location lastCameraLocation;
    private double eyeHeight;
    private int framesSinceAnchor;
    private boolean initialized;
    private boolean cleaned;

    public DisplayCameraMovementHandler(CameraPlaybackSettings settings) {
        this(NmsAdapter.getHandler(), settings);
    }

    DisplayCameraMovementHandler(NmsHandler nmsHandler, CameraPlaybackSettings settings) {
        this.nmsHandler = Objects.requireNonNull(nmsHandler, "nmsHandler may not be null");
        Objects.requireNonNull(settings, "settings may not be null");
        this.interpolationTicks = settings.interpolationTicks();
        this.anchorIntervalFrames = settings.anchorIntervalFrames();
        this.maxAnchorDistanceSquared = settings.maxAnchorDistance() * settings.maxAnchorDistance();
    }

    @Override
    public void initialize(CraftTouringPlayer touringPlayer, Location location) {
        Objects.requireNonNull(touringPlayer, "touringPlayer may not be null");
        Location feetLocation = checkedClone(location);
        if (this.initialized && !this.cleaned) {
            throw new IllegalStateException("display camera has already been initialized");
        }

        this.player = touringPlayer.getPlayer();
        this.eyeHeight = this.player.getEyeHeight(false);
        this.cleaned = false;

        PlayerRestoreWrapper restoreWrapper = touringPlayer.getRestoreWrapper();
        restoreWrapper.setAllowFlight(true);
        restoreWrapper.setFlying(true);
        this.player.setVelocity(new Vector());
        this.teleportAnchor(feetLocation);

        TemporaryDisplayCamera created = null;
        try {
            created = this.createCamera(this.toCameraLocation(feetLocation));
            created.nmsSpawn(this.player);
            created.nmsSetCamera(this.player);
            this.camera = created;
            this.lastFeetLocation = feetLocation;
            this.lastCameraLocation = this.toCameraLocation(feetLocation);
            this.framesSinceAnchor = 0;
            this.initialized = true;
        } catch (RuntimeException | Error failure) {
            if (created != null) {
                try {
                    created.nmsResetCamera(this.player);
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    created.nmsRemove(this.player);
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public void move(CraftTouringPlayer touringPlayer, Location location) {
        this.requireActive(touringPlayer);
        Location feetLocation = checkedClone(location);
        if (this.lastFeetLocation.getWorld() != feetLocation.getWorld()) {
            this.replaceCamera(feetLocation);
            return;
        }

        this.anchorIfNeeded(feetLocation);
        Location cameraLocation = this.toCameraLocation(feetLocation);
        if (!sameLocation(this.lastCameraLocation, cameraLocation)) {
            this.camera.nmsMove(this.player, cameraLocation);
            this.lastCameraLocation = cameraLocation;
            this.lastFeetLocation = feetLocation;
            ++this.framesSinceAnchor;
        }
    }

    @Override
    public void rebase(CraftTouringPlayer touringPlayer, Location location,
                       @NotNull StateRebaseReason reason) {
        Objects.requireNonNull(reason, "reason may not be null");
        this.requireActive(touringPlayer);
        Location feetLocation = checkedClone(location);
        if (reason == StateRebaseReason.SESSION_START && sameLocation(this.lastFeetLocation, feetLocation)) {
            this.camera.nmsSetCamera(this.player);
            return;
        }
        this.replaceCamera(feetLocation);
    }

    @Override
    public void reassertCamera(CraftTouringPlayer touringPlayer) {
        this.requireActive(touringPlayer);
        this.camera.nmsSetCamera(this.player);
    }

    @Override
    public void cleanup() {
        if (this.cleaned) {
            return;
        }
        this.cleaned = true;
        TemporaryDisplayCamera current = this.camera;
        this.camera = null;
        this.initialized = false;
        this.lastFeetLocation = null;
        this.lastCameraLocation = null;
        if (current == null || this.player == null || !this.player.isOnline()) {
            return;
        }

        Throwable failure = null;
        try {
            current.nmsResetCamera(this.player);
        } catch (RuntimeException | Error throwable) {
            failure = throwable;
        }
        try {
            current.nmsRemove(this.player);
        } catch (RuntimeException | Error throwable) {
            if (failure == null) {
                failure = throwable;
            } else {
                failure.addSuppressed(throwable);
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private void replaceCamera(Location feetLocation) {
        TemporaryDisplayCamera previous = this.camera;
        boolean crossWorld = this.lastFeetLocation.getWorld() != feetLocation.getWorld();
        if (crossWorld) {
            previous.nmsResetCamera(this.player);
            previous.nmsRemove(this.player);
            this.camera = null;
        }

        this.teleportAnchor(feetLocation);
        Location cameraLocation = this.toCameraLocation(feetLocation);
        TemporaryDisplayCamera replacement = this.createCamera(cameraLocation);
        try {
            replacement.nmsSpawn(this.player);
            replacement.nmsSetCamera(this.player);
        } catch (RuntimeException | Error failure) {
            try {
                replacement.nmsRemove(this.player);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        this.camera = replacement;
        this.lastFeetLocation = feetLocation;
        this.lastCameraLocation = cameraLocation;
        this.framesSinceAnchor = 0;
        if (!crossWorld) {
            previous.nmsRemove(this.player);
        }
    }

    private TemporaryDisplayCamera createCamera(Location location) {
        return Objects.requireNonNull(this.nmsHandler.createTemporaryDisplayCamera(location, this.interpolationTicks),
                "NMS handler returned a null display camera");
    }

    private void anchorIfNeeded(Location target) {
        Location playerLocation = this.player.getLocation();
        boolean wrongWorld = playerLocation.getWorld() != target.getWorld();
        boolean tooFar = !wrongWorld && playerLocation.distanceSquared(target) > this.maxAnchorDistanceSquared;
        if (wrongWorld || tooFar || this.framesSinceAnchor >= this.anchorIntervalFrames) {
            this.teleportAnchor(target);
            this.camera.nmsSetCamera(this.player);
        }
    }

    private void teleportAnchor(Location target) {
        if (!this.player.teleport(target)) {
            throw new IllegalStateException("display camera anchor teleport was cancelled or rejected");
        }
        this.framesSinceAnchor = 0;
    }

    private Location toCameraLocation(Location feetLocation) {
        return feetLocation.clone().add(0.0, this.eyeHeight, 0.0);
    }

    private void requireActive(CraftTouringPlayer touringPlayer) {
        Objects.requireNonNull(touringPlayer, "touringPlayer may not be null");
        if (!this.initialized || this.cleaned || this.camera == null) {
            throw new IllegalStateException("display camera is not active");
        }
        if (touringPlayer.getPlayer() != this.player) {
            throw new IllegalArgumentException("display camera belongs to a different player");
        }
    }

    private static Location checkedClone(Location location) {
        Objects.requireNonNull(location, "location may not be null");
        Objects.requireNonNull(location.getWorld(), "location world may not be null");
        return location.clone();
    }

    private static boolean sameLocation(Location first, Location second) {
        if (first == null || second == null || first.getWorld() != second.getWorld()) {
            return false;
        }
        return Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0
                && Float.compare(first.getYaw(), second.getYaw()) == 0
                && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }
}
