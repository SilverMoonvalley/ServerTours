package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.RecordingSample;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A timestamp-parameterized cubic Hermite camera curve.
 *
 * <p>Unlike a uniformly parameterized Catmull-Rom curve, tangents account for the real time
 * on both sides of a keyframe. This preserves the timing of pauses and speed changes.</p>
 */
public final class NonUniformHermiteCurve {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final int COMPONENTS = 5;

    private final List<RecordingSample> keyframes;
    private final Set<Integer> linearSegments;
    private final long[] times;
    private final double[][] values;
    private final double[][] tangents;

    /**
     * @param keyframes strictly time-ordered keyframes beginning at 0ns
     * @param linearSegments indices of segments that must use linear interpolation
     */
    public NonUniformHermiteCurve(List<RecordingSample> keyframes, List<Integer> linearSegments) {
        this.keyframes = RecordingMath.validatedSamples(keyframes);
        this.linearSegments = validateLinearSegments(linearSegments, this.keyframes.size());
        this.times = new long[this.keyframes.size()];
        this.values = new double[this.keyframes.size()][COMPONENTS];
        this.tangents = new double[this.keyframes.size()][COMPONENTS];
        initializeValues();
        initializeTangents();
    }

    /** Samples the curve, clamping times beyond the end to the final keyframe. */
    public RecordingSample sampleAt(long timeNanos) {
        if (timeNanos < 0L) {
            throw new IllegalArgumentException("timeNanos cannot be negative");
        }
        if (this.keyframes.size() == 1 || timeNanos == 0L) {
            return this.keyframes.get(0);
        }
        int lastIndex = this.keyframes.size() - 1;
        if (timeNanos >= this.times[lastIndex]) {
            return this.keyframes.get(lastIndex);
        }

        int found = Arrays.binarySearch(this.times, timeNanos);
        if (found >= 0) {
            return this.keyframes.get(found);
        }
        int segment = -found - 2;
        double durationSeconds = seconds(this.times[segment + 1] - this.times[segment]);
        double progress = (timeNanos - this.times[segment])
                / (double) (this.times[segment + 1] - this.times[segment]);

        double[] result = new double[COMPONENTS];
        if (this.linearSegments.contains(segment)) {
            for (int component = 0; component < COMPONENTS; component++) {
                result[component] = lerp(this.values[segment][component],
                        this.values[segment + 1][component], progress);
            }
        } else {
            double progress2 = progress * progress;
            double progress3 = progress2 * progress;
            double h00 = 2.0 * progress3 - 3.0 * progress2 + 1.0;
            double h10 = progress3 - 2.0 * progress2 + progress;
            double h01 = -2.0 * progress3 + 3.0 * progress2;
            double h11 = progress3 - progress2;
            for (int component = 0; component < COMPONENTS; component++) {
                result[component] = h00 * this.values[segment][component]
                        + h10 * durationSeconds * this.tangents[segment][component]
                        + h01 * this.values[segment + 1][component]
                        + h11 * durationSeconds * this.tangents[segment + 1][component];
            }
        }

        return RecordingMath.sample(timeNanos, result[0], result[1], result[2], result[3], result[4]);
    }

    public List<RecordingSample> keyframes() {
        return this.keyframes;
    }

    public List<Integer> linearSegments() {
        return this.linearSegments.stream().sorted().toList();
    }

    private void initializeValues() {
        for (int index = 0; index < this.keyframes.size(); index++) {
            RecordingSample sample = this.keyframes.get(index);
            this.times[index] = sample.timeNanos();
            this.values[index][0] = sample.x();
            this.values[index][1] = sample.y();
            this.values[index][2] = sample.z();
            this.values[index][3] = sample.yawUnwrapped();
            this.values[index][4] = sample.pitch();
        }
    }

    private void initializeTangents() {
        if (this.keyframes.size() == 1) {
            return;
        }
        for (int component = 0; component < COMPONENTS; component++) {
            this.tangents[0][component] = slope(0, 1, component);
            int lastIndex = this.keyframes.size() - 1;
            this.tangents[lastIndex][component] = slope(lastIndex - 1, lastIndex, component);
            for (int index = 1; index < lastIndex; index++) {
                double previousDuration = seconds(this.times[index] - this.times[index - 1]);
                double nextDuration = seconds(this.times[index + 1] - this.times[index]);
                double previousSlope = slope(index - 1, index, component);
                double nextSlope = slope(index, index + 1, component);
                this.tangents[index][component] =
                        (nextDuration * previousSlope + previousDuration * nextSlope)
                                / (previousDuration + nextDuration);
            }
        }
    }

    private double slope(int start, int end, int component) {
        return (this.values[end][component] - this.values[start][component])
                / seconds(this.times[end] - this.times[start]);
    }

    private static Set<Integer> validateLinearSegments(List<Integer> segments, int keyframeCount) {
        if (segments == null) {
            throw new IllegalArgumentException("linearSegments cannot be null");
        }
        Set<Integer> validated = new HashSet<>();
        for (Integer segment : segments) {
            if (segment == null || segment < 0 || segment >= keyframeCount - 1) {
                throw new IllegalArgumentException("Invalid linear segment index: " + segment);
            }
            if (!validated.add(segment)) {
                throw new IllegalArgumentException("Duplicate linear segment index: " + segment);
            }
        }
        return Set.copyOf(validated);
    }

    private static double seconds(long nanos) {
        return nanos / NANOS_PER_SECOND;
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
