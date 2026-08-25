package com.melluh.servertours.playback.track;

import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.playback.PlaybackSession;
import com.melluh.servertours.api.playback.track.TrackContext;
import com.melluh.servertours.playback.CraftTouringPlayer;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class CraftTrackContext implements TrackContext {
    private final CraftTouringPlayer touringPlayer;
    private final long cameraDurationFrames;

    public CraftTrackContext(CraftTouringPlayer touringPlayer, long cameraDurationFrames) {
        this.touringPlayer = Objects.requireNonNull(touringPlayer, "touringPlayer may not be null");
        this.cameraDurationFrames = cameraDurationFrames;
    }

    @Override
    public PlaybackSession getSession() {
        return this.touringPlayer;
    }

    @Override
    public TouringPlayer getTouringPlayer() {
        return this.touringPlayer;
    }

    @Override
    public Player getPlayer() {
        return this.touringPlayer.getPlayer();
    }

    @Override
    public Route getRoute() {
        return this.touringPlayer.getRoute();
    }

    @Override
    public long getCameraDurationFrames() {
        return this.cameraDurationFrames;
    }
}
