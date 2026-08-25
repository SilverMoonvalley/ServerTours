package com.melluh.servertours.recording.storage;

import com.melluh.servertours.file.PersistenceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Disk-backed cache of ready camera recordings and resumable raw drafts.
 *
 * <p>The repository never mutates its cache until the corresponding atomic disk operation
 * succeeds. All methods are synchronized so an asynchronous finalization job can safely save
 * assets while the main thread services commands.</p>
 */
public final class RecordingRepository {
    private static final Comparator<RecordingDraft> DRAFT_ORDER = Comparator
            .comparing((RecordingDraft draft) -> draft.metadata().routeName())
            .thenComparing(draft -> draft.metadata().id());
    private static final Comparator<CameraRecording> READY_ORDER = Comparator
            .comparing((CameraRecording recording) -> recording.metadata().routeName())
            .thenComparing(recording -> recording.metadata().id());

    private final Path readyDirectory;
    private final Path draftDirectory;
    private final RecordingYamlCodec codec;
    private final Map<UUID, CameraRecording> ready = new HashMap<>();
    private final Map<UUID, RecordingDraft> drafts = new HashMap<>();
    private final Map<String, UUID> draftsByRouteName = new HashMap<>();

    /** Uses {@code dataDirectory/recordings} and its {@code drafts} child. */
    public RecordingRepository(Path dataDirectory) {
        this(dataDirectory, new RecordingYamlCodec());
    }

    public RecordingRepository(Path dataDirectory, RecordingYamlCodec codec) {
        Objects.requireNonNull(dataDirectory, "dataDirectory may not be null");
        this.readyDirectory = dataDirectory.toAbsolutePath().normalize().resolve("recordings");
        this.draftDirectory = this.readyDirectory.resolve("drafts");
        this.codec = Objects.requireNonNull(codec, "codec may not be null");
    }

    /** Reloads both caches, isolating malformed or conflicting documents in the report. */
    public synchronized LoadReport load() throws IOException {
        Files.createDirectories(this.readyDirectory);
        Files.createDirectories(this.draftDirectory);

        Map<UUID, CameraRecording> loadedReady = new LinkedHashMap<>();
        Map<UUID, RecordingDraft> loadedDrafts = new LinkedHashMap<>();
        Map<String, UUID> loadedDraftNames = new HashMap<>();
        List<LoadFailure> failures = new ArrayList<>();

        for (Path file : yamlFiles(this.readyDirectory)) {
            try {
                UUID fileId = idFromFile(file);
                CameraRecording recording = this.codec.readReady(file);
                requireMatchingId(fileId, recording.metadata().id());
                if (loadedReady.putIfAbsent(fileId, recording) != null) {
                    throw new IllegalArgumentException("Duplicate ready recording UUID " + fileId);
                }
            } catch (Exception exception) {
                failures.add(new LoadFailure(file, message(exception), exception));
            }
        }

        for (Path file : yamlFiles(this.draftDirectory)) {
            try {
                UUID fileId = idFromFile(file);
                RecordingDraft draft = this.codec.readDraft(file);
                requireMatchingId(fileId, draft.metadata().id());
                if (loadedDrafts.containsKey(fileId)) {
                    throw new IllegalArgumentException("Duplicate recording draft UUID " + fileId);
                }
                UUID conflictingId = loadedDraftNames.putIfAbsent(
                        draft.metadata().routeName(), fileId);
                if (conflictingId != null && !conflictingId.equals(fileId)) {
                    throw new IllegalArgumentException("Duplicate draft route name '"
                            + draft.metadata().routeName() + "' also used by " + conflictingId);
                }
                loadedDrafts.put(fileId, draft);
            } catch (Exception exception) {
                failures.add(new LoadFailure(file, message(exception), exception));
            }
        }

        this.ready.clear();
        this.ready.putAll(loadedReady);
        this.drafts.clear();
        this.drafts.putAll(loadedDrafts);
        this.draftsByRouteName.clear();
        this.draftsByRouteName.putAll(loadedDraftNames);
        return new LoadReport(this.ready.size(), this.drafts.size(), failures);
    }

    public synchronized Optional<CameraRecording> getReady(UUID id) {
        return Optional.ofNullable(this.ready.get(Objects.requireNonNull(id, "id may not be null")));
    }

    public synchronized Optional<RecordingDraft> getDraft(UUID id) {
        return Optional.ofNullable(this.drafts.get(Objects.requireNonNull(id, "id may not be null")));
    }

    public synchronized Optional<RecordingDraft> getDraftByRouteName(String routeName) {
        UUID id = this.draftsByRouteName.get(RecordingMetadata.normalizeRouteName(routeName));
        return id == null ? Optional.empty() : Optional.ofNullable(this.drafts.get(id));
    }

    public synchronized List<CameraRecording> listReady() {
        return this.ready.values().stream().sorted(READY_ORDER).toList();
    }

    public synchronized List<RecordingDraft> listDrafts() {
        return this.drafts.values().stream().sorted(DRAFT_ORDER).toList();
    }

    public synchronized boolean hasDraftRouteName(String routeName) {
        return this.draftsByRouteName.containsKey(RecordingMetadata.normalizeRouteName(routeName));
    }

    /** Atomically persists the raw source before publishing it in the draft cache. */
    public synchronized void saveDraft(RecordingDraft draft) throws IOException {
        Objects.requireNonNull(draft, "draft may not be null");
        UUID id = draft.metadata().id();
        String routeName = draft.metadata().routeName();
        UUID conflictingId = this.draftsByRouteName.get(routeName);
        if (conflictingId != null && !conflictingId.equals(id)) {
            throw new IllegalStateException("A recording draft for route '" + routeName
                    + "' already exists as " + conflictingId);
        }

        PersistenceManager.writeAtomically(draftPath(id), this.codec.encodeDraft(draft));
        RecordingDraft previous = this.drafts.put(id, draft);
        if (previous != null && !previous.metadata().routeName().equals(routeName)) {
            this.draftsByRouteName.remove(previous.metadata().routeName(), id);
        }
        this.draftsByRouteName.put(routeName, id);
    }

    /** Atomically persists a ready asset before publishing it in the ready cache. */
    public synchronized void saveReady(CameraRecording recording) throws IOException {
        Objects.requireNonNull(recording, "recording may not be null");
        UUID id = recording.metadata().id();
        PersistenceManager.writeAtomically(readyPath(id), this.codec.encodeReady(recording));
        this.ready.put(id, recording);
    }

    public synchronized boolean deleteDraft(UUID id) throws IOException {
        Objects.requireNonNull(id, "id may not be null");
        boolean deleted = Files.deleteIfExists(draftPath(id));
        RecordingDraft removed = this.drafts.remove(id);
        if (removed != null) {
            this.draftsByRouteName.remove(removed.metadata().routeName(), id);
        }
        return deleted || removed != null;
    }

    public synchronized boolean deleteReady(UUID id) throws IOException {
        Objects.requireNonNull(id, "id may not be null");
        boolean deleted = Files.deleteIfExists(readyPath(id));
        return this.ready.remove(id) != null || deleted;
    }

    public Path readyDirectory() {
        return this.readyDirectory;
    }

    public Path draftDirectory() {
        return this.draftDirectory;
    }

    private Path readyPath(UUID id) {
        return this.readyDirectory.resolve(id + ".yml");
    }

    private Path draftPath(UUID id) {
        return this.draftDirectory.resolve(id + ".yml");
    }

    private static List<Path> yamlFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static UUID idFromFile(Path path) {
        String fileName = path.getFileName().toString();
        String rawId = fileName.substring(0, fileName.length() - ".yml".length());
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Recording filename must be a UUID: " + fileName,
                    exception);
        }
    }

    private static void requireMatchingId(UUID fileId, UUID documentId) {
        if (!fileId.equals(documentId)) {
            throw new IllegalArgumentException("Recording filename UUID " + fileId
                    + " does not match document UUID " + documentId);
        }
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record LoadReport(int readyLoaded, int draftsLoaded, List<LoadFailure> failures) {
        public LoadReport {
            failures = List.copyOf(failures);
        }
    }

    public record LoadFailure(Path file, String message, Exception cause) {
        public LoadFailure {
            file = Objects.requireNonNull(file, "file may not be null");
            message = Objects.requireNonNull(message, "message may not be null");
            cause = Objects.requireNonNull(cause, "cause may not be null");
        }
    }
}
