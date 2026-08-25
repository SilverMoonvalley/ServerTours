package com.melluh.servertours.route.point;

import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.api.object.StationaryPoint;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.math.EasingFunction;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class CraftStationaryPoint extends CraftRoutePoint implements StationaryPoint {
    public CraftStationaryPoint(CraftRoute craftRoute) {
        super(craftRoute, RoutePointType.STATIONARY);
    }

    @Override
    public void showParticles(Player player) {
    }

    @Override
    public Location getPlaybackLocation(float n, EasingFunction easingFunction) {
        return this.getLocation();
    }
}
