package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallySavesLoadsListsAndDeletesDraftsAndReadyAssets() throws IOException {
        RecordingRepository repository = new RecordingRepository(this.temporaryDirectory);
        RecordingDraft second = RecordingYamlCodecTest.draft(UUID.randomUUID(), "zeta");
        RecordingDraft first = RecordingYamlCodecTest.draft(UUID.randomUUID(), "alpha");
        repository.saveDraft(second);
        repository.saveDraft(first);
        CameraRecording ready = first.toReady(new CompiledRecording(
                first.rawSamples(), List.of(0, 2), List.of()));
        repository.saveReady(ready);

        assertEquals(List.of("alpha", "zeta"), repository.listDrafts().stream()
                .map(draft -> draft.metadata().routeName()).toList());
        assertEquals(first, repository.getDraftByRouteName("ALPHA").orElseThrow());
        assertEquals(ready, repository.getReady(first.metadata().id()).orElseThrow());
        try (Stream<Path> files = Files.list(repository.draftDirectory())) {
            assertEquals(0L, files.filter(path -> path.getFileName().toString().startsWith("."))
                    .count());
        }
    }

    @Test
    void newRepositoryReloadsSavedDocumentsAndDeletionUpdatesDiskAndCache() throws IOException {
        RecordingRepository writer = new RecordingRepository(this.temporaryDirectory);
        RecordingDraft draft = RecordingYamlCodecTest.draft(UUID.randomUUID(), "reload");
        CameraRecording ready = draft.toReady(new CompiledRecording(
                draft.rawSamples(), List.of(0, 1, 2), List.of(1)));
        writer.saveDraft(draft);
        writer.saveReady(ready);

        RecordingRepository reader = new RecordingRepository(this.temporaryDirectory);
        RecordingRepository.LoadReport report = reader.load();

        assertEquals(1, report.readyLoaded());
        assertEquals(1, report.draftsLoaded());
        assertTrue(report.failures().isEmpty());
        assertEquals(draft, reader.getDraft(draft.metadata().id()).orElseThrow());
        assertEquals(ready.metadata(), reader.getReady(ready.metadata().id()).orElseThrow().metadata());

        assertTrue(reader.deleteDraft(draft.metadata().id()));
        assertTrue(reader.deleteReady(ready.metadata().id()));
        assertFalse(reader.getDraft(draft.metadata().id()).isPresent());
        assertFalse(reader.getReady(ready.metadata().id()).isPresent());
        assertFalse(Files.exists(reader.draftDirectory().resolve(draft.metadata().id() + ".yml")));
        assertFalse(Files.exists(reader.readyDirectory().resolve(ready.metadata().id() + ".yml")));
    }

    @Test
    void malformedAndFilenameMismatchedDocumentsAreIsolatedDuringLoad() throws IOException {
        RecordingYamlCodec codec = new RecordingYamlCodec();
        Path ready = this.temporaryDirectory.resolve("recordings");
        Path drafts = ready.resolve("drafts");
        Files.createDirectories(drafts);
        RecordingDraft valid = RecordingYamlCodecTest.draft(UUID.randomUUID(), "mismatch");
        CameraRecording recording = valid.toReady(new CompiledRecording(
                valid.rawSamples(), List.of(0, 2), List.of()));
        Files.write(ready.resolve(UUID.randomUUID() + ".yml"), codec.encodeReady(recording));
        Files.writeString(drafts.resolve(UUID.randomUUID() + ".yml"), "kind: DRAFT\ninvalid: [");

        RecordingRepository repository = new RecordingRepository(this.temporaryDirectory);
        RecordingRepository.LoadReport report = repository.load();

        assertEquals(0, report.readyLoaded());
        assertEquals(0, report.draftsLoaded());
        assertEquals(2, report.failures().size());
        assertTrue(report.failures().stream().anyMatch(failure ->
                failure.message().contains("does not match document UUID")));
    }

    @Test
    void failedDiskWriteDoesNotPublishToCache() throws IOException {
        Path blockedDataDirectory = this.temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockedDataDirectory, "blocked");
        RecordingRepository repository = new RecordingRepository(blockedDataDirectory);
        RecordingDraft draft = RecordingYamlCodecTest.draft(UUID.randomUUID(), "blocked");

        assertThrows(IOException.class, () -> repository.saveDraft(draft));

        assertTrue(repository.listDrafts().isEmpty());
        assertFalse(repository.getDraft(draft.metadata().id()).isPresent());
    }

    @Test
    void routeNameIndexRejectsASecondDraftWithoutWritingIt() throws IOException {
        RecordingRepository repository = new RecordingRepository(this.temporaryDirectory);
        RecordingDraft first = RecordingYamlCodecTest.draft(UUID.randomUUID(), "reserved");
        RecordingDraft conflict = RecordingYamlCodecTest.draft(UUID.randomUUID(), "RESERVED");
        repository.saveDraft(first);

        assertThrows(IllegalStateException.class, () -> repository.saveDraft(conflict));

        assertEquals(first, repository.getDraftByRouteName("reserved").orElseThrow());
        assertFalse(Files.exists(repository.draftDirectory().resolve(
                conflict.metadata().id() + ".yml")));
    }

    @Test
    void overwritesTheSameDraftAndReadyIdAtomicallyForResumeAndRecompile() throws IOException {
        RecordingRepository repository = new RecordingRepository(this.temporaryDirectory);
        RecordingDraft original = RecordingYamlCodecTest.draft(UUID.randomUUID(), "overwrite");
        List<RecordingSample> resumedSamples = new java.util.ArrayList<>(original.rawSamples());
        resumedSamples.add(new RecordingSample(200_000_000L, 9.0, 70.0, 10.0, 600.0, 20.0));
        RecordingDraft resumed = new RecordingDraft(original.metadata(), resumedSamples);
        repository.saveDraft(original);
        repository.saveDraft(resumed);
        CameraRecording ready = resumed.toReady(new CompiledRecording(
                resumed.rawSamples(), List.of(0, resumed.rawSamples().size() - 1), List.of(0)));
        repository.saveReady(ready);
        repository.saveReady(ready);

        RecordingRepository reloaded = new RecordingRepository(this.temporaryDirectory);
        RecordingRepository.LoadReport report = reloaded.load();

        assertTrue(report.failures().isEmpty());
        assertEquals(200_000_000L, reloaded.getDraft(original.metadata().id())
                .orElseThrow().durationNanos());
        assertEquals(200_000_000L, reloaded.getReady(original.metadata().id())
                .orElseThrow().durationNanos());
    }
}
