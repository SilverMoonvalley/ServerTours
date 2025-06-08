package com.melluh.servertours.api;

import com.melluh.servertours.api.object.Route;
import org.bukkit.entity.Player;

import java.util.List;

public interface PlaybackManager {
    TouringPlayer showTour(Player p0, Route p1);

    TouringPlayer getTouringPlayer(Player p0);

    List<? extends TouringPlayer> getTouringPlayers(Route p0);

    List<? extends TouringPlayer> getTouringPlayers();
}
