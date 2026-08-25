package com.melluh.servertours.nms;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface TemporaryEntity
{
    void nmsAddPassenger(Entity p0);
    
    void nmsMove(Location p0);
    
    void nmsSetLocation(Location p0);
    
    void nmsRemove();
    
    Entity getBukkitEntity();
}
