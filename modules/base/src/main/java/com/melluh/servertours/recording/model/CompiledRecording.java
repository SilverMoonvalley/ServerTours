package com.melluh.servertours.recording.model;

import com.melluh.servertours.recording.math.NonUniformHermiteCurve;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable raw and compiled representation of a recorded camera path. */
public final class CompiledRecording {
    public static final long LOGICAL_FRAME_NANOS = 50_000_000L;

    private final List<RecordingSample> rawSamples;
    private final List<Integer> keyframeIndices;
    private final List<RecordingSample> keyframes;
    private final List<Integer> linearSegments;
    private final NonUniformHermiteCurve curve;

    public CompiledRecording(List<RecordingSample> rawSamples, List<Integer> keyframeIndices,
                             List<Integer> linearSegments) {
        this.rawSamples = validateRawSamples(rawSamples);
        this.keyframeIndices = validateKeyframeIndices(keyframeIndices, this.rawSamples.size());
        this.linearSegments = validateLinearSegments(linearSegments, this.keyframeIndices.size());

        List<RecordingSample> selected = new ArrayList<>(this.keyframeIndices.size());
        for (int index : this.keyframeIndices) {
            selected.add(this.rawSamples.get(index));
        }
        this.keyframes = List.copyOf(selected);
        this.curve = new NonUniformHermiteCurve(this.keyframes, this.linearSegments);
    }

    public List<RecordingSample> rawSamples() {
        return this.rawSamples;
    }

    public List<Integer> keyframeIndices() {
        return this.keyframeIndices;
    }

    public List<RecordingSample> keyframes() {
        return this.keyframes;
    }

    /** Segment indices into {@link #keyframes()} that use linear interpolation. */
    public List<Integer> linearSegments() {
        return this.linearSegments;
    }

    public long durationNanos() {
        return this.rawSamples.get(this.rawSamples.size() - 1).timeNanos();
    }

    /** Returns the logical final frame using a ceiling conversion at 20 FPS. */
    public long endFrame() {
        long duration = durationNanos();
        return duration / LOGICAL_FRAME_NANOS
                + (duration % LOGICAL_FRAME_NANOS == 0L ? 0L : 1L);
    }

    /** Samples by absolute recording time; values beyond the duration hold the final pose. */
    public RecordingSample sampleAt(long timeNanos) {
        return this.curve.sampleAt(timeNanos);
    }

    private static List<RecordingSample> validateRawSamples(List<RecordingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("A compiled recording requires at least one raw sample");
        }
        List<RecordingSample> immutable = List.copyOf(samples);
        if (immutable.get(0).timeNanos() != 0L) {
            throw new IllegalArgumentException("The first raw sample must start at 0ns");
        }
        for (int index = 1; index < immutable.size(); index++) {
            if (immutable.get(index).timeNanos() <= immutable.get(index - 1).timeNanos()) {
                throw new IllegalArgumentException("Raw sample times must be strictly increasing");
            }
        }
        return immutable;
    }

    private static List<Integer> validateKeyframeIndices(List<Integer> indices, int rawSampleCount) {
        if (indices == null || indices.isEmpty()) {
            throw new IllegalArgumentException("At least one keyframe index is required");
        }
        List<Integer> immutable = List.copyOf(indices);
        if (immutable.get(0) != 0 || immutable.get(immutable.size() - 1) != rawSampleCount - 1) {
            throw new IllegalArgumentException("Keyframes must include the first and last raw samples");
        }
        int previous = -1;
        for (int index : immutable) {
            if (index <= previous || index >= rawSampleCount) {
                throw new IllegalArgumentException("Keyframe indices must be unique, ascending and in range");
            }
            previous = index;
        }
        return immutable;
    }

    private static List<Integer> validateLinearSegments(List<Integer> segments, int keyframeCount) {
        if (segments == null) {
            throw new IllegalArgumentException("linearSegments cannot be null");
        }
        List<Integer> immutable = List.copyOf(segments);
        Set<Integer> unique = new HashSet<>();
        int previous = -1;
        for (int segment : immutable) {
            if (segment <= previous || segment >= keyframeCount - 1 || !unique.add(segment)) {
                throw new IllegalArgumentException("Linear segments must be unique, ascending and in range");
            }
            previous = segment;
        }
        return immutable;
    }
}
