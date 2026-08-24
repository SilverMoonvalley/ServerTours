package com.melluh.servertours.nms;

import org.bukkit.entity.Player;
import org.bukkit.Location;

public interface NmsHandler
{
    TemporaryEntity spawnTemporaryEntity(Location p0);
    
    void rotatePlayerHead(Player p0, float p1, float p2);
    
    void sendMoveVehiclePacket(Player p0, TemporaryEntity p1);
}
