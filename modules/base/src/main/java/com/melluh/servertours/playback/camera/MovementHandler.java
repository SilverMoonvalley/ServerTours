package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.playback.CraftTouringPlayer;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public interface MovementHandler {
    void initialize(CraftTouringPlayer p0, Location p1);

    void move(CraftTouringPlayer p0, Location p1);

    /**
     * Presentation-only lead used to offset client interpolation latency.
     * Logical scene time and non-interpolating transports remain unchanged.
     */
    default int presentationLeadFrames() {
        return 0;
    }

    /**
     * Snaps the camera transport to a discontinuous target. Existing movement
     * handlers retain their previous behaviour through the default delegate.
     */
    default void rebase(CraftTouringPlayer touringPlayer, Location location,
                        @NotNull StateRebaseReason reason) {
        this.move(touringPlayer, location);
    }

    /**
     * Reasserts the current client camera target after a client-side reset.
     * Camera transports which do not use a separate target need no action.
     */
    default void reassertCamera(CraftTouringPlayer touringPlayer) {
    }

    void cleanup();
}
