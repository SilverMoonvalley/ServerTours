package com.melluh.servertours.playback.camera;

import java.util.Locale;

public enum JavaCameraBackend {
    VEHICLE,
    DISPLAY;

    static JavaCameraBackend parse(String value) {
        if (value == null) {
            return VEHICLE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return VEHICLE;
        }
    }
}
