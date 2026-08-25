package com.melluh.servertours.api.object;

/** Selects the built-in camera track used by a route. */
public enum CameraSource {
    /** Sample the route's existing point and spline timeline. */
    POINTS,

    /** Sample a timestamped camera recording referenced by the route. */
    RECORDED
}
