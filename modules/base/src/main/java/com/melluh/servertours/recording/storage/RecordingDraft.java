package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;

import java.util.List;
import java.util.Objects;

/** Raw, resumable recording data. Raw samples are the authoritative source. */
public record RecordingDraft(RecordingMetadata metadata, List<RecordingSample> rawSamples) {
    public RecordingDraft {
        metadata = Objects.requireNonNull(metadata, "metadata may not be null");
        rawSamples = validateRawSamples(rawSamples);
    }

    public long durationNanos() {
        return this.rawSamples.get(this.rawSamples.size() - 1).timeNanos();
    }

    /** Builds a ready asset while ensuring the compiler used this exact raw snapshot. */
    public CameraRecording toReady(CompiledRecording compiled) {
        Objects.requireNonNull(compiled, "compiled may not be null");
        if (!this.rawSamples.equals(compiled.rawSamples())) {
            throw new IllegalArgumentException("compiled recording does not use this draft's raw samples");
        }
        return new CameraRecording(this.metadata, compiled);
    }

    private static List<RecordingSample> validateRawSamples(List<RecordingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("A recording draft requires at least one raw sample");
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
}
