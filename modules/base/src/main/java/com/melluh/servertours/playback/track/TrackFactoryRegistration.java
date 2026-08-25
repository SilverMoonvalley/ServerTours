package com.melluh.servertours.playback.track;

import com.melluh.servertours.api.playback.track.TrackFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Immutable registry entry captured by a playback session when it starts.
 */
public record TrackFactoryRegistration(
        Plugin owner,
        NamespacedKey key,
        int priority,
        long registrationOrder,
        TrackFactory factory
) implements Comparable<TrackFactoryRegistration> {

    public TrackFactoryRegistration {
        Objects.requireNonNull(owner, "owner may not be null");
        Objects.requireNonNull(key, "key may not be null");
        Objects.requireNonNull(factory, "factory may not be null");
    }

    @Override
    public int compareTo(TrackFactoryRegistration other) {
        int priorityComparison = Integer.compare(this.priority, other.priority);
        return priorityComparison != 0
                ? priorityComparison
                : Long.compare(this.registrationOrder, other.registrationOrder);
    }
}
