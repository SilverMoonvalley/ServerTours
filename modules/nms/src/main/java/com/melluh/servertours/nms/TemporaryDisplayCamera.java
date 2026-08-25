package com.melluh.servertours.nms;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A client-side display entity used as a camera target for one viewer.
 * Implementations are not added to the server world and must therefore send
 * their own spawn, movement, camera and removal packets.
 */
public interface TemporaryDisplayCamera {
    int getEntityId();

    void nmsSpawn(Player viewer);

    void nmsSetCamera(Player viewer);

    void nmsMove(Player viewer, Location location);

    void nmsResetCamera(Player viewer);

    void nmsRemove(Player viewer);
}
