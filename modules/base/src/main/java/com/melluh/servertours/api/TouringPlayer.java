package com.melluh.servertours.api;

import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.api.playback.PlaybackSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface TouringPlayer extends PlaybackSession {
    void exit();

    float getRouteProgress();

    float getPointProgress();

    RoutePoint getCurrentPoint();

    void setCurrentPoint(int p0);

    void setCurrentPoint(RoutePoint p0);

    Route getRoute();

    Player getPlayer();

    boolean isWaitingForConfirmation();

    void setWaitingForConfirmation(boolean p0);

    boolean isProgressBarEnabled();

    void setProgressBarEnabled(boolean p0);

    boolean isActionBarEnabled();

    void setActionBarEnabled(boolean p0);

    boolean canExit();

    void setCanExit(boolean p0);

    boolean isExitByMoving();

    void setExitByMoving(boolean p0);

    Location getExitLocation();

    void setExitLocation(Location p0);
}
