package com.melluh.servertours.api;

import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.playback.track.TrackFactory;
import com.melluh.servertours.api.playback.track.TrackRegistration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PlaybackManager {
    /**
     * Registers a per-session track factory. Lower priority values are
     * dispatched first; equal priorities retain registration order. Closing
     * the returned handle affects future sessions only.
     */
    @NotNull TrackRegistration registerTrackFactory(@NotNull Plugin plugin, @NotNull NamespacedKey key, int priority,
                                                    @NotNull TrackFactory factory);

    TouringPlayer showTour(Player p0, Route p1);

    TouringPlayer getTouringPlayer(Player p0);

    List<? extends TouringPlayer> getTouringPlayers(Route p0);

    List<? extends TouringPlayer> getTouringPlayers();
}
