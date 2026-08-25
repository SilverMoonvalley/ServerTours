package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.math.RecordingTolerances;
import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Schema-versioned YAML codec for raw drafts and playback-ready recordings. */
public final class RecordingYamlCodec {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int CURRENT_COMPILER_VERSION = 1;

    private static final String KIND_DRAFT = "DRAFT";
    private static final String KIND_READY = "READY";

    public RecordingDraft readDraft(Path path) throws IOException {
        return decodeDraft(readConfiguration(path), path.toString());
    }

    public CameraRecording readReady(Path path) throws IOException {
        return decodeReady(readConfiguration(path), path.toString());
    }

    public byte[] encodeDraft(RecordingDraft draft) {
        Objects.requireNonNull(draft, "draft may not be null");
        YamlConfiguration yaml = new YamlConfiguration();
        writeCommon(yaml, KIND_DRAFT, draft.metadata(), draft.rawSamples(), draft.durationNanos());
        return yaml.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] encodeReady(CameraRecording recording) {
        Objects.requireNonNull(recording, "recording may not be null");
        YamlConfiguration yaml = new YamlConfiguration();
        CompiledRecording compiled = recording.compiled();
        writeCommon(yaml, KIND_READY, recording.metadata(), compiled.rawSamples(),
                compiled.durationNanos());
        yaml.set("compiled.keyframeIndices", compiled.keyframeIndices());
        yaml.set("compiled.linearSegments", compiled.linearSegments());
        return yaml.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    private static RecordingDraft decodeDraft(YamlConfiguration yaml, String source)
            throws IOException {
        try {
            requireKind(yaml, KIND_DRAFT);
            RecordingMetadata metadata = readMetadata(yaml);
            List<RecordingSample> raw = readRawSamples(yaml);
            RecordingDraft draft = new RecordingDraft(metadata, raw);
            requireDuration(yaml, draft.durationNanos());
            if (yaml.contains("compiled")) {
                throw new IllegalArgumentException("A DRAFT document cannot contain compiled data");
            }
            return draft;
        } catch (RuntimeException exception) {
            throw invalid(source, exception);
        }
    }

    private static CameraRecording decodeReady(YamlConfiguration yaml, String source)
            throws IOException {
        try {
            requireKind(yaml, KIND_READY);
            RecordingMetadata metadata = readMetadata(yaml);
            List<RecordingSample> raw = readRawSamples(yaml);
            CompiledRecording compiled = new CompiledRecording(
                    raw,
                    readIntegerList(yaml, "compiled.keyframeIndices"),
                    readIntegerList(yaml, "compiled.linearSegments")
            );
            requireDuration(yaml, compiled.durationNanos());
            return new CameraRecording(metadata, compiled);
        } catch (RuntimeException exception) {
            throw invalid(source, exception);
        }
    }

    private static YamlConfiguration readConfiguration(Path path) throws IOException {
        Objects.requireNonNull(path, "path may not be null");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
            return yaml;
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid recording YAML in " + path, exception);
        }
    }

    private static void writeCommon(YamlConfiguration yaml, String kind,
                                    RecordingMetadata metadata,
                                    List<RecordingSample> rawSamples,
                                    long durationNanos) {
        yaml.set("versions.schema", CURRENT_SCHEMA_VERSION);
        yaml.set("versions.compiler", metadata.compilerVersion());
        yaml.set("kind", kind);
        yaml.set("id", metadata.id().toString());
        yaml.set("routeName", metadata.routeName());
        yaml.set("creator.uuid", metadata.creatorId().toString());
        yaml.set("creator.name", metadata.creatorName());
        yaml.set("world.uuid", metadata.worldId().toString());
        yaml.set("world.name", metadata.worldName());
        yaml.set("createdAtEpochMillis", metadata.createdAtEpochMillis());
        yaml.set("timing.durationNanos", durationNanos);
        yaml.set("timing.sampleIntervalNanos", metadata.sampleIntervalNanos());
        yaml.set("processing.tolerances.position", metadata.tolerances().position());
        yaml.set("processing.tolerances.yaw", metadata.tolerances().yaw());
        yaml.set("processing.tolerances.pitch", metadata.tolerances().pitch());

        List<Map<String, Object>> encodedSamples = new ArrayList<>(rawSamples.size());
        for (RecordingSample sample : rawSamples) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("timeNanos", sample.timeNanos());
            encoded.put("x", sample.x());
            encoded.put("y", sample.y());
            encoded.put("z", sample.z());
            encoded.put("yawUnwrapped", sample.yawUnwrapped());
            encoded.put("pitch", sample.pitch());
            encodedSamples.add(encoded);
        }
        yaml.set("rawSamples", encodedSamples);
    }

    private static RecordingMetadata readMetadata(YamlConfiguration yaml) {
        int schema = requiredInt(yaml, "versions.schema");
        if (schema != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported recording schema version: " + schema);
        }
        int compilerVersion = requiredInt(yaml, "versions.compiler");
        if (compilerVersion != CURRENT_COMPILER_VERSION) {
            throw new IllegalArgumentException("Unsupported recording compiler version: "
                    + compilerVersion);
        }
        return new RecordingMetadata(
                requiredUuid(yaml, "id"),
                requiredString(yaml, "routeName"),
                requiredUuid(yaml, "creator.uuid"),
                requiredString(yaml, "creator.name"),
                requiredUuid(yaml, "world.uuid"),
                requiredString(yaml, "world.name"),
                requiredLong(yaml, "createdAtEpochMillis"),
                requiredLong(yaml, "timing.sampleIntervalNanos"),
                new RecordingTolerances(
                        requiredDouble(yaml, "processing.tolerances.position"),
                        requiredDouble(yaml, "processing.tolerances.yaw"),
                        requiredDouble(yaml, "processing.tolerances.pitch")
                ),
                compilerVersion
        );
    }

    private static List<RecordingSample> readRawSamples(YamlConfiguration yaml) {
        List<?> values = yaml.getList("rawSamples");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("rawSamples must contain at least one sample");
        }
        List<RecordingSample> samples = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("rawSamples[" + index + "] must be a map");
            }
            samples.add(new RecordingSample(
                    mapLong(map, index, "timeNanos"),
                    mapDouble(map, index, "x"),
                    mapDouble(map, index, "y"),
                    mapDouble(map, index, "z"),
                    mapDouble(map, index, "yawUnwrapped"),
                    mapDouble(map, index, "pitch")
            ));
        }
        return List.copyOf(samples);
    }

    private static List<Integer> readIntegerList(YamlConfiguration yaml, String path) {
        List<?> values = yaml.getList(path);
        if (values == null) {
            throw new IllegalArgumentException(path + " is required");
        }
        List<Integer> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(path + "[" + index + "] must be an integer");
            }
            long converted = number.longValue();
            if (converted < Integer.MIN_VALUE || converted > Integer.MAX_VALUE
                    || number.doubleValue() != converted) {
                throw new IllegalArgumentException(path + "[" + index + "] must be an integer");
            }
            result.add((int) converted);
        }
        return List.copyOf(result);
    }

    private static void requireKind(YamlConfiguration yaml, String expected) {
        String kind = requiredString(yaml, "kind");
        if (!expected.equals(kind)) {
            throw new IllegalArgumentException("Expected recording kind " + expected + " but found " + kind);
        }
    }

    private static void requireDuration(YamlConfiguration yaml, long actualDuration) {
        long storedDuration = requiredLong(yaml, "timing.durationNanos");
        if (storedDuration != actualDuration) {
            throw new IllegalArgumentException("timing.durationNanos does not match the final raw sample");
        }
    }

    private static String requiredString(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(path + " must be a non-blank string");
        }
        return string;
    }

    private static UUID requiredUuid(YamlConfiguration yaml, String path) {
        String value = requiredString(yaml, path);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + " must be a UUID", exception);
        }
    }

    private static int requiredInt(YamlConfiguration yaml, String path) {
        long value = requiredLong(yaml, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " is outside the integer range");
        }
        return (int) value;
    }

    private static long requiredLong(YamlConfiguration yaml, String path) {
        return integralNumber(yaml.get(path), path);
    }

    private static double requiredDouble(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        return number.doubleValue();
    }

    private static long mapLong(Map<?, ?> map, int index, String key) {
        return integralNumber(map.get(key), "rawSamples[" + index + "]." + key);
    }

    private static double mapDouble(Map<?, ?> map, int index, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("rawSamples[" + index + "]." + key + " must be a number");
        }
        return number.doubleValue();
    }

    private static long integralNumber(Object value, String path) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        long converted = number.longValue();
        if (number.doubleValue() != converted) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return converted;
    }

    private static IOException invalid(String source, RuntimeException exception) {
        return new IOException("Invalid recording document " + source + ": "
                + exception.getMessage(), exception);
    }
}
