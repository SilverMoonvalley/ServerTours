package com.melluh.servertours.api.object;

public interface InterpolatePoint extends RoutePoint {
    Validity getValidity();

    boolean isValid();

    enum Validity {
        VALID,
        NO_NEXT_POINT,
        DIFFERENT_WORLD
    }
}
