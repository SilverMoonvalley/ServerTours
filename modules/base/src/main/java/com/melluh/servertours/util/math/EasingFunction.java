package com.melluh.servertours.util.math;

public interface EasingFunction {
    float getTime(float p0, Mode p1);

    enum Mode {
        NONE,
        IN,
        OUT,
        IN_OUT
    }
}
