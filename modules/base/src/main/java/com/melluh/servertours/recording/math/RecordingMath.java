package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.RecordingSample;

import java.util.List;

final class RecordingMath {
    private RecordingMath() {
    }

    static List<RecordingSample> validatedSamples(List<RecordingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("A recording requires at least one sample");
        }

        List<RecordingSample> immutable = List.copyOf(samples);
        if (immutable.get(0).timeNanos() != 0L) {
            throw new IllegalArgumentException("The first recording sample must start at 0ns");
        }
        for (int index = 1; index < immutable.size(); index++) {
            if (immutable.get(index).timeNanos() <= immutable.get(index - 1).timeNanos()) {
                throw new IllegalArgumentException("Recording sample times must be strictly increasing");
            }
        }
        return immutable;
    }

    static RecordingSample interpolateLinear(RecordingSample start, RecordingSample end, long timeNanos) {
        if (timeNanos <= start.timeNanos()) {
            return start;
        }
        if (timeNanos >= end.timeNanos()) {
            return end;
        }
        double progress = (timeNanos - start.timeNanos())
                / (double) (end.timeNanos() - start.timeNanos());
        return sample(
                timeNanos,
                lerp(start.x(), end.x(), progress),
                lerp(start.y(), end.y(), progress),
                lerp(start.z(), end.z(), progress),
                lerp(start.yawUnwrapped(), end.yawUnwrapped(), progress),
                lerp(start.pitch(), end.pitch(), progress)
        );
    }

    static RecordingSample sample(long timeNanos, double x, double y, double z,
                                  double yawUnwrapped, double pitch) {
        return new RecordingSample(timeNanos, x, y, z, yawUnwrapped,
                Math.max(-90.0, Math.min(90.0, pitch)));
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
