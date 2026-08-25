package com.melluh.servertours.api.playback.track;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * A removable global track-factory registration.
 */
public interface TrackRegistration extends AutoCloseable {
    @NotNull NamespacedKey getKey();

    boolean isRegistered();

    @Override
    void close();
}
