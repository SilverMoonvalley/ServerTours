package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.RecordingSample;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Time-aware Ramer-Douglas-Peucker simplification for recorded camera poses. */
public final class TimeAwareRdp {
    private TimeAwareRdp() {
    }

    /** Returns ascending indices into {@code samples}; the first and last are always retained. */
    public static List<Integer> simplify(List<RecordingSample> samples, RecordingTolerances tolerances) {
        List<RecordingSample> validated = RecordingMath.validatedSamples(samples);
        if (tolerances == null) {
            throw new IllegalArgumentException("tolerances cannot be null");
        }
        if (validated.size() == 1) {
            return List.of(0);
        }

        boolean[] retained = new boolean[validated.size()];
        retained[0] = true;
        retained[validated.size() - 1] = true;

        Deque<Range> pending = new ArrayDeque<>();
        pending.push(new Range(0, validated.size() - 1));
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("camera recording simplification was cancelled");
            }
            Range range = pending.pop();
            RecordingSample start = validated.get(range.start());
            RecordingSample end = validated.get(range.end());
            double worstScore = 1.0;
            int worstIndex = -1;
            for (int index = range.start() + 1; index < range.end(); index++) {
                RecordingSample expected = RecordingMath.interpolateLinear(
                        start, end, validated.get(index).timeNanos());
                double score = tolerances.normalizedError(expected, validated.get(index));
                if (score > worstScore) {
                    worstScore = score;
                    worstIndex = index;
                }
            }
            if (worstIndex < 0) {
                continue;
            }

            retained[worstIndex] = true;
            // Push right first so the earlier range is evaluated first. Ties therefore remain deterministic.
            pending.push(new Range(worstIndex, range.end()));
            pending.push(new Range(range.start(), worstIndex));
        }

        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < retained.length; index++) {
            if (retained[index]) {
                indices.add(index);
            }
        }
        return List.copyOf(indices);
    }

    private record Range(int start, int end) {
    }
}
