package com.melluh.servertours.api.object;

public interface OrbitPoint extends RoutePoint {
    float getDistance();

    void setDistance(float p0);

    float getSpeed();

    void setSpeed(float p0);

    float getHeight();

    void setHeight(float p0);

    float getStartingPoint();

    void setStartingPoint(float p0);
}
