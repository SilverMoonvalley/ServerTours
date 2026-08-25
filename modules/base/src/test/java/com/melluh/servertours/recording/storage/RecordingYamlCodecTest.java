package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.math.FixedRateSampleGate;
import com.melluh.servertours.recording.math.RecordingTolerances;
import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingYamlCodecTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void draftRoundTripPreservesIdentityTimingAndRawCameraValues() throws IOException {
        RecordingYamlCodec codec = new RecordingYamlCodec();
        RecordingDraft expected = draft(UUID.randomUUID(), "Opening_Shot");
        Path file = this.temporaryDirectory.resolve("draft.yml");
        Files.write(file, codec.encodeDraft(expected));

        RecordingDraft actual = codec.readDraft(file);

        assertEquals("opening_shot", actual.metadata().routeName());
        assertEquals(expected, actual);
        assertEquals(125_000_000L, actual.durationNanos());
        assertEquals(450.0, actual.rawSamples().get(1).yawUnwrapped());
    }

    @Test
    void readyRoundTripRebuildsCompiledCurveFromRawIndices() throws IOException {
        RecordingYamlCodec codec = new RecordingYamlCodec();
        RecordingDraft draft = draft(UUID.randomUUID(), "compiled");
        CompiledRecording compiled = new CompiledRecording(
                draft.rawSamples(), List.of(0, 1, 2), List.of(0));
        CameraRecording expected = draft.toReady(compiled);
        Path file = this.temporaryDirectory.resolve("ready.yml");
        Files.write(file, codec.encodeReady(expected));

        CameraRecording actual = codec.readReady(file);

        assertEquals(expected.metadata(), actual.metadata());
        assertEquals(expected.compiled().rawSamples(), actual.compiled().rawSamples());
        assertEquals(List.of(0, 1, 2), actual.compiled().keyframeIndices());
        assertEquals(List.of(0), actual.compiled().linearSegments());
        assertEquals(compiled.sampleAt(25_000_000L), actual.compiled().sampleAt(25_000_000L));

        String yaml = Files.readString(file);
        assertTrue(yaml.contains("rawSamples:"));
        assertTrue(yaml.contains("keyframeIndices:"));
        assertTrue(yaml.contains("createdAtEpochMillis:"));
    }

    @Test
    void rejectsStoredDurationThatDisagreesWithAuthoritativeRawSamples() throws IOException {
        RecordingYamlCodec codec = new RecordingYamlCodec();
        RecordingDraft draft = draft(UUID.randomUUID(), "duration");
        String yaml = new String(codec.encodeDraft(draft), StandardCharsets.UTF_8);
        Path file = this.temporaryDirectory.resolve("bad-duration.yml");
        Files.writeString(file, yaml.replace("durationNanos: 125000000", "durationNanos: 9"));

        IOException failure = assertThrows(IOException.class, () -> codec.readDraft(file));

        assertTrue(failure.getMessage().contains("durationNanos"));
    }

    @Test
    void rejectsUnknownCompilerVersionsInsteadOfSilentlyReinterpretingTheCurve() throws IOException {
        RecordingYamlCodec codec = new RecordingYamlCodec();
        String yaml = new String(codec.encodeDraft(draft(UUID.randomUUID(), "future")),
                StandardCharsets.UTF_8);
        Path file = this.temporaryDirectory.resolve("future.yml");
        Files.writeString(file, yaml.replace("compiler: 1", "compiler: 999"));

        IOException failure = assertThrows(IOException.class, () -> codec.readDraft(file));

        assertTrue(failure.getMessage().contains("compiler version"));
    }

    @Test
    void draftSnapshotAndEncodedBytesAreDefensive() {
        RecordingDraft draft = draft(UUID.randomUUID(), "immutable");
        RecordingYamlCodec codec = new RecordingYamlCodec();
        byte[] first = codec.encodeDraft(draft);
        byte[] second = codec.encodeDraft(draft);

        first[0] = (byte) (first[0] + 1);

        assertArrayEquals(second, codec.encodeDraft(draft));
        assertThrows(UnsupportedOperationException.class,
                () -> draft.rawSamples().add(draft.rawSamples().get(0)));
    }

    static RecordingDraft draft(UUID id, String routeName) {
        RecordingMetadata metadata = new RecordingMetadata(
                id,
                routeName,
                UUID.fromString("87a0dd67-9f13-4b7f-a5f1-4b1ee4438c8d"),
                "CameraOperator",
                UUID.fromString("72ef12ab-4be8-4d56-8029-5280c17f9ea8"),
                "world",
                1_774_321_234_567L,
                FixedRateSampleGate.DEFAULT_INTERVAL_NANOS,
                new RecordingTolerances(0.05, 0.5, 0.5),
                RecordingYamlCodec.CURRENT_COMPILER_VERSION
        );
        return new RecordingDraft(metadata, List.of(
                new RecordingSample(0L, 1.0, 64.0, 2.0, 350.0, -5.0),
                new RecordingSample(50_000_000L, 2.0, 65.0, 4.0, 450.0, 10.0),
                new RecordingSample(125_000_000L, 4.0, 66.0, 8.0, 540.0, 15.0)
        ));
    }
}
