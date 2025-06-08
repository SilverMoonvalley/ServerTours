package com.melluh.servertours.api.object;

import org.bukkit.Location;

public interface RoutePoint {
    void move(Location p0);

    Location getLocation();

    int getTicksVisible();

    void setTicksVisible(int p0);

    Route getRoute();

    RoutePointType getType();

    void clearTitle();

    String getTitle();

    void setTitle(String p0);

    float getTitleFadeInTime();

    void setTitleFadeInTime(float p0);

    float getTitleStayTime();

    void setTitleStayTime(float p0);

    float getTitleFadeOutTime();

    void setTitleFadeOutTime(float p0);

    void clearDescription();

    String getDescription();

    void setDescription(String p0);

    void clearLabel();

    String getLabel();

    void setLabel(String p0);

    default int getIndex() {
        return this.getRoute().indexOf(this);
    }
}
