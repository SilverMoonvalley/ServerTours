package com.melluh.servertours.api.playback.track;

import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.playback.PlaybackSession;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable session context supplied to track factories and event actions.
 */
public interface TrackContext {
    @NotNull PlaybackSession getSession();

    @NotNull TouringPlayer getTouringPlayer();

    @NotNull Player getPlayer();

    @NotNull Route getRoute();

    /** Returns the duration of the route's selected point or recorded built-in camera track. */
    long getCameraDurationFrames();
}
