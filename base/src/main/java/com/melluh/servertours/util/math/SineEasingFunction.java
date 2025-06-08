package com.melluh.servertours.util.math;

public class SineEasingFunction implements EasingFunction {
    @Override
    public float getTime(float n, Mode mode) {
        return switch (mode) {
            case IN_OUT -> (float) (-(Math.cos(3.141592653589793 * n) - 1.0)) / 2.0f;
            case IN -> (float) (1.0 - Math.cos(n * 3.141592653589793 / 2.0));
            case OUT -> (float) Math.sin(n * 3.141592653589793 / 2.0);
            default -> n;
        };
    }
}
