package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;

/** Compiles raw timestamped camera samples into a bounded-error Hermite path. */
public final class RecordingCompiler {
    private static final double ACCEPTED_SCORE = 1.0;

    private final RecordingTolerances tolerances;

    public RecordingCompiler() {
        this(RecordingTolerances.DEFAULT);
    }

    public RecordingCompiler(RecordingTolerances tolerances) {
        if (tolerances == null) {
            throw new IllegalArgumentException("tolerances cannot be null");
        }
        this.tolerances = tolerances;
    }

    public CompiledRecording compile(List<RecordingSample> samples) {
        List<RecordingSample> raw = RecordingMath.validatedSamples(samples);
        TreeSet<Integer> retained = new TreeSet<>(TimeAwareRdp.simplify(raw, this.tolerances));
        Set<RawSegment> forcedLinear = new HashSet<>();

        while (true) {
            requireNotInterrupted();
            CurveSnapshot snapshot = snapshot(raw, retained, forcedLinear);
            Violation violation = findWorstViolation(raw, retained, snapshot.curve());
            if (violation == null) {
                return new CompiledRecording(raw, snapshot.keyframeIndices(), snapshot.linearSegments());
            }

            if (violation.rawIndexToInsert() >= 0) {
                if (!retained.add(violation.rawIndexToInsert())) {
                    throw new IllegalStateException("Recording compiler could not make validation progress");
                }
                continue;
            }

            if (!forcedLinear.add(violation.linearFallback())) {
                throw new IllegalStateException("Recording compiler could not resolve curve overshoot");
            }
        }
    }

    public RecordingTolerances tolerances() {
        return this.tolerances;
    }

    private Violation findWorstViolation(List<RecordingSample> raw, Set<Integer> retained,
                                         NonUniformHermiteCurve curve) {
        Violation worst = null;
        double worstScore = ACCEPTED_SCORE;
        for (int index = 0; index < raw.size(); index++) {
            requireNotInterrupted();
            RecordingSample expected = raw.get(index);
            double score = this.tolerances.normalizedError(expected,
                    curve.sampleAt(expected.timeNanos()));
            if (score > worstScore) {
                worstScore = score;
                worst = Violation.insert(index);
            }

            if (index == raw.size() - 1) {
                continue;
            }
            RecordingSample next = raw.get(index + 1);
            long duration = next.timeNanos() - expected.timeNanos();
            if (duration <= 1L) {
                continue;
            }
            long midpoint = expected.timeNanos() + duration / 2L;
            RecordingSample midpointExpected = RecordingMath.interpolateLinear(expected, next, midpoint);
            double midpointScore = this.tolerances.normalizedError(midpointExpected,
                    curve.sampleAt(midpoint));
            if (midpointScore <= worstScore) {
                continue;
            }

            worstScore = midpointScore;
            if (!retained.contains(index)) {
                worst = Violation.insert(index);
            } else if (!retained.contains(index + 1)) {
                worst = Violation.insert(index + 1);
            } else {
                worst = Violation.linear(new RawSegment(index, index + 1));
            }
        }
        return worst;
    }

    private static void requireNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("camera recording compilation was cancelled");
        }
    }

    private static CurveSnapshot snapshot(List<RecordingSample> raw, TreeSet<Integer> retained,
                                          Set<RawSegment> forcedLinear) {
        List<Integer> indices = List.copyOf(retained);
        List<RecordingSample> keyframes = new ArrayList<>(indices.size());
        List<Integer> linearSegments = new ArrayList<>();
        for (int keyIndex = 0; keyIndex < indices.size(); keyIndex++) {
            keyframes.add(raw.get(indices.get(keyIndex)));
            if (keyIndex < indices.size() - 1
                    && forcedLinear.contains(new RawSegment(indices.get(keyIndex), indices.get(keyIndex + 1)))) {
                linearSegments.add(keyIndex);
            }
        }
        return new CurveSnapshot(indices, linearSegments,
                new NonUniformHermiteCurve(keyframes, linearSegments));
    }

    private record RawSegment(int startRawIndex, int endRawIndex) {
    }

    private record CurveSnapshot(List<Integer> keyframeIndices, List<Integer> linearSegments,
                                 NonUniformHermiteCurve curve) {
    }

    private record Violation(int rawIndexToInsert, RawSegment linearFallback) {
        static Violation insert(int rawIndex) {
            return new Violation(rawIndex, null);
        }

        static Violation linear(RawSegment segment) {
            return new Violation(-1, segment);
        }
    }
}
