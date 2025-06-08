package com.melluh.servertours.playback;

import org.bukkit.Location;

public interface MovementHandler {
    void initialize(CraftTouringPlayer p0, Location p1);

    void move(CraftTouringPlayer p0, Location p1);

    void cleanup();
}
