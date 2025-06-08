package com.melluh.servertours.nms;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ModernMovementNmsHandler
{
    void sendEntityTeleportPacket(Player p0, int p1, Location p2);
}
