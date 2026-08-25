package com.melluh.servertours.api.playback.track;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Creates an independent track runtime for a playback session.
 */
@FunctionalInterface
public interface TrackFactory {
    @NotNull Optional<TrackRuntime> create(@NotNull TrackContext context);
}
