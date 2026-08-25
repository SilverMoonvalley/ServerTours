package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.model.CompiledRecording;

import java.util.Objects;

/** Immutable, playback-ready camera recording with its raw source retained. */
public record CameraRecording(RecordingMetadata metadata, CompiledRecording compiled) {
    public CameraRecording {
        metadata = Objects.requireNonNull(metadata, "metadata may not be null");
        compiled = Objects.requireNonNull(compiled, "compiled may not be null");
    }

    public long durationNanos() {
        return this.compiled.durationNanos();
    }

    public long endFrame() {
        return this.compiled.endFrame();
    }
}
