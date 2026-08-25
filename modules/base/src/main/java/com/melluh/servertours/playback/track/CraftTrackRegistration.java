package com.melluh.servertours.playback.track;

import com.melluh.servertours.api.playback.track.TrackRegistration;
import com.melluh.servertours.playback.CraftPlaybackManager;
import org.bukkit.NamespacedKey;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CraftTrackRegistration implements TrackRegistration {
    private final CraftPlaybackManager manager;
    private final TrackFactoryRegistration registration;
    private final AtomicBoolean registered = new AtomicBoolean(true);

    public CraftTrackRegistration(CraftPlaybackManager manager, TrackFactoryRegistration registration) {
        this.manager = Objects.requireNonNull(manager, "manager may not be null");
        this.registration = Objects.requireNonNull(registration, "registration may not be null");
    }

    @Override
    public NamespacedKey getKey() {
        return this.registration.key();
    }

    @Override
    public boolean isRegistered() {
        return this.registered.get() && this.manager.isTrackFactoryRegistered(this.registration);
    }

    @Override
    public void close() {
        if (this.registered.compareAndSet(true, false)) {
            this.manager.unregisterTrackFactory(this.registration);
        }
    }
}
