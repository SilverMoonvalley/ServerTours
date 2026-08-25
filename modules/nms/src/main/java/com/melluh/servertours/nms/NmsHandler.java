package com.melluh.servertours.nms;

import org.bukkit.Location;

public interface NmsHandler
{
    TemporaryDisplayCamera createTemporaryDisplayCamera(Location location, int interpolationTicks);
}
