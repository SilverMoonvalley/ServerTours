package com.melluh.servertours.recording;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.CameraSource;
import com.melluh.servertours.editmode.EditModeManager;
import com.melluh.servertours.file.PersistenceManager;
import com.melluh.servertours.playback.CraftPlaybackManager;
import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.recording.storage.CameraRecording;
import com.melluh.servertours.recording.storage.RecordingDraft;
import com.melluh.servertours.recording.storage.RecordingRepository;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.CraftRouteManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingManagerTest {
    private static final long FRAME_NANOS = 50_000_000L;

    @TempDir
    Path tempDirectory;

    private Harness harness;

    @BeforeEach
    void setUp() throws IOException {
        this.harness = new Harness(this.tempDirectory);
    }

    @AfterEach
    void tearDown() {
        this.harness.close();
    }

    @Test
    void newRecordingCancelRestoresPlayerAndReleasesTheNameWithoutCreatingADraft() {
        Location original = this.harness.location();

        assertTrue(this.harness.manager.start(this.harness.player, "opening-shot"));
        assertTrue(this.harness.manager.isRecording(this.harness.player));
        assertTrue(this.harness.manager.isRouteNameReserved("opening-shot"));
        verify(this.harness.player).setGameMode(GameMode.SPECTATOR);
        verify(this.harness.player).setVelocity(new Vector());
        verify(this.harness.player).setFallDistance(0.0F);

        this.harness.moveTo(20.0, 90.0, -5.0, 120.0F, -20.0F);
        assertTrue(this.harness.manager.cancel(this.harness.player));

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertFalse(this.harness.manager.isRouteNameReserved("opening-shot"));
        assertTrue(this.harness.repository.listDrafts().isEmpty());
        assertTrue(this.harness.repository.listReady().isEmpty());
        assertLocationEquals(original, this.harness.location());
        verify(this.harness.player).setGameMode(GameMode.SURVIVAL);
        assertFalse(this.harness.manager.cancel(this.harness.player));
    }

    @Test
    void failedStartRollsBackPartialPlayerSetupAndDoesNotReserveTheRouteName() {
        Location original = this.harness.location();
        doThrow(new IllegalStateException("velocity rejected"))
                .when(this.harness.player).setVelocity(any(Vector.class));

        assertFalse(this.harness.manager.start(this.harness.player, "broken-start"));

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertFalse(this.harness.manager.isRouteNameReserved("broken-start"));
        assertTrue(this.harness.repository.listDrafts().isEmpty());
        assertLocationEquals(original, this.harness.location());
        verify(this.harness.player).setGameMode(GameMode.SPECTATOR);
        verify(this.harness.player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void playerGoingOfflineDuringSetupRollsBackTheUnpublishedSession() {
        Location original = this.harness.location();
        doAnswer(invocation -> {
            when(this.harness.player.isOnline()).thenReturn(false);
            return null;
        }).when(this.harness.player).setFallDistance(0.0F);

        assertFalse(this.harness.manager.start(this.harness.player, "offline-during-start"));

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertFalse(this.harness.manager.isRouteNameReserved("offline-during-start"));
        assertTrue(this.harness.repository.listDrafts().isEmpty());
        assertLocationEquals(original, this.harness.location());
        verify(this.harness.player).setGameMode(GameMode.SPECTATOR);
        verify(this.harness.player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void publicRecordingStateFailsFastOffThePrimaryThread() {
        this.harness.bukkitStatic.when(Bukkit::isPrimaryThread).thenReturn(false);
        try {
            assertThrows(IllegalStateException.class,
                    () -> this.harness.manager.isRecording(this.harness.player));
            assertThrows(IllegalStateException.class,
                    () -> this.harness.manager.start(this.harness.player, "async-start"));
        } finally {
            this.harness.bukkitStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
        }

        assertFalse(this.harness.manager.isRouteNameReserved("async-start"));
        verify(this.harness.player, never()).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void stopPersistsRawDraftBeforeCompilationThenCommitsReadyRouteOnMainThread() throws IOException {
        assertTrue(this.harness.manager.start(this.harness.player, "flythrough"));
        this.harness.clock.advance(FRAME_NANOS * 2L);
        this.harness.moveTo(8.0, 70.0, 3.0, -170.0F, 12.0F);

        assertTrue(this.harness.manager.stop(this.harness.player));

        List<RecordingDraft> draftsBeforeCompile = this.harness.repository.listDrafts();
        assertEquals(1, draftsBeforeCompile.size());
        assertEquals("flythrough", draftsBeforeCompile.get(0).metadata().routeName());
        assertEquals(List.of(0L, FRAME_NANOS * 2L), draftsBeforeCompile.get(0).rawSamples().stream()
                .map(sample -> sample.timeNanos()).toList());
        assertTrue(this.harness.repository.listReady().isEmpty());
        assertEquals(1, this.harness.compilerExecutor.pendingCount());
        assertEquals(0, this.harness.primaryTasks.size());

        this.harness.compilerExecutor.runNext();

        assertEquals(1, this.harness.repository.listDrafts().size());
        assertEquals(1, this.harness.repository.listReady().size());
        CameraRecording ready = this.harness.repository.listReady().get(0);
        assertEquals(1, this.harness.primaryTasks.size());
        verify(this.harness.persistenceManager, never()).saveRouteChecked(any());

        this.harness.runNextPrimaryTask();

        assertEquals(ready, this.harness.repository.listReady().get(0));
        assertTrue(this.harness.repository.listDrafts().isEmpty());
        assertFalse(this.harness.manager.isRouteNameReserved("flythrough"));
        ArgumentCaptor<CraftRoute> routeCaptor = ArgumentCaptor.forClass(CraftRoute.class);
        verify(this.harness.persistenceManager).saveRouteChecked(routeCaptor.capture());
        CraftRoute route = routeCaptor.getValue();
        assertEquals("flythrough", route.getName());
        assertEquals(ready.metadata().id(), route.getCameraRecordingId().orElseThrow());
        assertEquals(ready.endFrame(), route.getPoint(0).getTicksVisible());
        verify(this.harness.routeManager).registerNewRoute(route);
    }

    @Test
    void externalTeleportIsCancelledAndLeavesAResumableDraft() {
        Location original = this.harness.location();
        assertTrue(this.harness.manager.start(this.harness.player, "teleport-draft"));
        this.harness.clock.advance(FRAME_NANOS);
        this.harness.moveTo(4.0, 66.0, 2.0, 35.0F, 5.0F);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(this.harness.player);

        this.harness.manager.onPlayerTeleport(event);

        verify(event).setCancelled(true);
        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertEquals(1, this.harness.repository.listDrafts().size());
        assertTrue(this.harness.manager.isRouteNameReserved("teleport-draft"));
        assertLocationEquals(original, this.harness.location());
    }

    @Test
    void cancellingAResumedDraftKeepsTheOriginalDraftUnchanged() throws IOException {
        assertTrue(this.harness.manager.start(this.harness.player, "resume-cancel"));
        this.harness.clock.advance(FRAME_NANOS);
        this.harness.moveTo(3.0, 65.0, 1.0, 30.0F, 4.0F);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(this.harness.player);
        this.harness.manager.onPlayerQuit(quit);
        RecordingDraft originalDraft = this.harness.repository.listDrafts().get(0);

        this.harness.clock.advance(TimeUnit.MINUTES.toNanos(5L));
        assertTrue(this.harness.manager.resume(this.harness.player, "resume-cancel"));
        this.harness.clock.advance(FRAME_NANOS * 3L);
        this.harness.moveTo(30.0, 100.0, 8.0, 90.0F, 0.0F);
        assertTrue(this.harness.manager.cancel(this.harness.player));

        RecordingDraft retained = this.harness.repository.getDraft(originalDraft.metadata().id()).orElseThrow();
        assertEquals(originalDraft.rawSamples(), retained.rawSamples());
        assertTrue(this.harness.manager.isRouteNameReserved("resume-cancel"));
        assertTrue(this.harness.compilerExecutor.isEmpty());
    }

    @Test
    void failedResumePublicationRestoresPlayerAndKeepsTheOriginalDraftReserved() throws IOException {
        assertTrue(this.harness.manager.start(this.harness.player, "resume-offline"));
        this.harness.clock.advance(FRAME_NANOS);
        this.harness.moveTo(6.0, 70.0, -3.0, 45.0F, 8.0F);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(this.harness.player);
        this.harness.manager.onPlayerQuit(quit);
        RecordingDraft originalDraft = this.harness.repository.listDrafts().get(0);
        Location beforeResume = this.harness.location();

        doAnswer(invocation -> {
            when(this.harness.player.isOnline()).thenReturn(false);
            return null;
        }).when(this.harness.player).setFallDistance(0.0F);

        assertFalse(this.harness.manager.resume(this.harness.player, "resume-offline"));

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertTrue(this.harness.manager.isRouteNameReserved("resume-offline"));
        assertEquals(originalDraft,
                this.harness.repository.getDraft(originalDraft.metadata().id()).orElseThrow());
        assertLocationEquals(beforeResume, this.harness.location());
    }

    @Test
    void quitStopsCaptureAndSynchronouslySavesRawDraft() {
        assertTrue(this.harness.manager.start(this.harness.player, "quit-draft"));
        this.harness.clock.advance(FRAME_NANOS * 3L);
        this.harness.moveTo(-6.0, 80.0, 11.0, -40.0F, 20.0F);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(this.harness.player);

        this.harness.manager.onPlayerQuit(event);

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        RecordingDraft draft = this.harness.repository.listDrafts().get(0);
        assertEquals(FRAME_NANOS * 3L, draft.durationNanos());
        assertTrue(this.harness.compilerExecutor.isEmpty());
    }

    @Test
    void rejectsBedrockPlaybackEditAndInvalidPlayerStateConflictsWithoutMutation() {
        when(this.harness.plugin.isBedrockPlayer(this.harness.player)).thenReturn(true);
        assertFalse(this.harness.manager.start(this.harness.player, "bedrock"));

        when(this.harness.plugin.isBedrockPlayer(this.harness.player)).thenReturn(false);
        when(this.harness.playbackManager.isTouringPlayer(this.harness.player)).thenReturn(true);
        assertFalse(this.harness.manager.start(this.harness.player, "playing"));

        when(this.harness.playbackManager.isTouringPlayer(this.harness.player)).thenReturn(false);
        when(this.harness.editModeManager.isEditing(this.harness.player)).thenReturn(true);
        assertFalse(this.harness.manager.start(this.harness.player, "editing"));

        when(this.harness.editModeManager.isEditing(this.harness.player)).thenReturn(false);
        when(this.harness.player.isDead()).thenReturn(true);
        assertFalse(this.harness.manager.start(this.harness.player, "dead"));

        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertTrue(this.harness.repository.listDrafts().isEmpty());
        assertTrue(this.harness.repository.listReady().isEmpty());
        verify(this.harness.player, never()).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void duplicateStartIsRejectedWithoutReplacingTheActiveSession() {
        assertTrue(this.harness.manager.start(this.harness.player, "first"));

        assertFalse(this.harness.manager.start(this.harness.player, "second"));

        assertTrue(this.harness.manager.isRecording(this.harness.player));
        assertTrue(this.harness.manager.isRouteNameReserved("first"));
        assertFalse(this.harness.manager.isRouteNameReserved("second"));
        assertTrue(this.harness.manager.cancel(this.harness.player));
    }

    @Test
    void shutdownCancelsCapturePersistsEveryActiveSessionAndIsIdempotent() {
        this.harness.manager.startRunnable();
        assertNotNull(this.harness.timerTask);
        assertTrue(this.harness.manager.start(this.harness.player, "shutdown-draft"));
        this.harness.clock.advance(FRAME_NANOS * 2L);
        this.harness.moveTo(7.0, 72.0, -9.0, 55.0F, -8.0F);

        this.harness.manager.shutdown();
        this.harness.manager.shutdown();

        verify(this.harness.timerTask, times(1)).cancel();
        assertTrue(this.harness.compilerExecutor.isShutdown());
        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertEquals(1, this.harness.repository.listDrafts().size());
        assertTrue(this.harness.repository.listReady().isEmpty());
        verify(this.harness.player, atLeastOnce()).setGameMode(GameMode.SURVIVAL);
        assertFalse(this.harness.manager.start(this.harness.player, "after-shutdown"));
    }

    @Test
    void changingRouteCameraSourceRequiresTheManagePermissionAtTheManagerBoundary() throws IOException {
        String denial = "missing recording management permission";
        this.harness.serverToursStatic.when(() -> ServerTours.translate(
                "commands.errors.noPermission", "servertours.commands.record.manage"))
                .thenReturn(denial);
        CraftRoute route = new CraftRoute("source-route");
        this.harness.registeredRoute.set(route);

        when(this.harness.player.hasPermission("servertours.commands.record.manage")).thenReturn(false);
        assertFalse(this.harness.manager.setSource(
                this.harness.player, route.getName(), CameraSource.POINTS));
        verify(this.harness.player).sendMessage(denial);
        verify(this.harness.persistenceManager, never()).saveRouteChecked(any());

        when(this.harness.player.hasPermission("servertours.commands.record.manage")).thenReturn(true);
        assertTrue(this.harness.manager.setSource(
                this.harness.player, route.getName(), CameraSource.POINTS));
        verify(this.harness.persistenceManager).saveRouteChecked(route);
    }

    @Test
    void shutdownContinuesAfterEachActiveSessionFailureAndAlwaysStopsTheCompiler() {
        Player secondPlayer = this.harness.createAdditionalPlayer(
                new UUID(0L, 2L), "SecondRecorder");
        assertTrue(this.harness.manager.start(this.harness.player, "shutdown-failure-one"));
        assertTrue(this.harness.manager.start(secondPlayer, "shutdown-failure-two"));
        this.harness.serverToursStatic.when(() -> ServerTours.translate(
                "commands.record.draftSaved", "shutdown-failure-one"))
                .thenThrow(new IllegalStateException("first notification failed"));
        this.harness.serverToursStatic.when(() -> ServerTours.translate(
                "commands.record.draftSaved", "shutdown-failure-two"))
                .thenThrow(new IllegalStateException("second notification failed"));

        this.harness.manager.shutdown();

        assertTrue(this.harness.compilerExecutor.isShutdown());
        assertFalse(this.harness.manager.isRecording(this.harness.player));
        assertFalse(this.harness.manager.isRecording(secondPlayer));
        assertEquals(2, this.harness.repository.listDrafts().size());
        verify(this.harness.player, atLeastOnce()).setGameMode(GameMode.SURVIVAL);
        verify(secondPlayer, atLeastOnce()).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void rejectedCompilationNotifiesTheOnlineCreatorAndKeepsTheDraft() {
        String failure = "compiler unavailable";
        this.harness.serverToursStatic.when(() -> ServerTours.translate(
                "commands.record.errors.compileFailed")).thenReturn(failure);
        assertTrue(this.harness.manager.start(this.harness.player, "rejected-compile"));
        this.harness.clock.advance(FRAME_NANOS);
        this.harness.compilerExecutor.shutdown();

        assertTrue(this.harness.manager.stop(this.harness.player));

        verify(this.harness.player).sendMessage(failure);
        assertEquals(1, this.harness.repository.listDrafts().size());
        assertTrue(this.harness.repository.listReady().isEmpty());
        assertTrue(this.harness.manager.isRouteNameReserved("rejected-compile"));
    }

    private static void assertLocationEquals(Location expected, Location actual) {
        assertEquals(expected.getWorld(), actual.getWorld());
        assertEquals(expected.getX(), actual.getX());
        assertEquals(expected.getY(), actual.getY());
        assertEquals(expected.getZ(), actual.getZ());
        assertEquals(expected.getYaw(), actual.getYaw());
        assertEquals(expected.getPitch(), actual.getPitch());
    }

    private static final class Harness implements AutoCloseable {
        private final UUID playerId = UUID.randomUUID();
        private final UUID worldId = UUID.randomUUID();
        private final World world = mock(World.class);
        private final Player player = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final Player.Spigot spigot = mock(Player.Spigot.class);
        private final ServerTours plugin = mock(ServerTours.class);
        private final CraftRouteManager routeManager = mock(CraftRouteManager.class);
        private final CraftPlaybackManager playbackManager = mock(CraftPlaybackManager.class);
        private final EditModeManager editModeManager = mock(EditModeManager.class);
        private final PersistenceManager persistenceManager = mock(PersistenceManager.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final MutableNanoClock clock = new MutableNanoClock(1_000_000_000L);
        private final QueuedExecutorService compilerExecutor = new QueuedExecutorService();
        private final Deque<Runnable> primaryTasks = new ArrayDeque<>();
        private final AtomicReference<Location> currentLocation = new AtomicReference<>();
        private final AtomicReference<GameMode> currentGameMode = new AtomicReference<>(GameMode.SURVIVAL);
        private final AtomicReference<CraftRoute> registeredRoute = new AtomicReference<>();
        private final RecordingRepository repository;
        private final RecordingManager manager;
        private final MockedStatic<ServerTours> serverToursStatic;
        private final MockedStatic<Bukkit> bukkitStatic;
        private BukkitTask timerTask;

        private Harness(Path tempDirectory) throws IOException {
            this.currentLocation.set(new Location(this.world, 1.5, 64.0, -2.5, 10.0F, -4.0F));
            when(this.world.getUID()).thenReturn(this.worldId);
            when(this.world.getName()).thenReturn("world");
            when(this.player.getUniqueId()).thenReturn(this.playerId);
            when(this.player.getName()).thenReturn("Recorder");
            when(this.player.getLocation()).thenAnswer(ignored -> this.currentLocation.get().clone());
            when(this.player.getInventory()).thenReturn(this.inventory);
            when(this.inventory.getStorageContents()).thenReturn(new ItemStack[0]);
            when(this.inventory.getArmorContents()).thenReturn(new ItemStack[0]);
            when(this.inventory.getExtraContents()).thenReturn(new ItemStack[0]);
            when(this.inventory.getHeldItemSlot()).thenReturn(2);
            when(this.player.getGameMode()).thenAnswer(ignored -> this.currentGameMode.get());
            doAnswer(invocation -> {
                this.currentGameMode.set(invocation.getArgument(0));
                return null;
            }).when(this.player).setGameMode(any(GameMode.class));
            when(this.player.getLevel()).thenReturn(7);
            when(this.player.getExp()).thenReturn(0.25F);
            when(this.player.getTotalExperience()).thenReturn(123);
            when(this.player.getHealth()).thenReturn(18.0D);
            when(this.player.getMaxHealth()).thenReturn(20.0D);
            when(this.player.isCollidable()).thenReturn(true);
            when(this.player.getAllowFlight()).thenReturn(false);
            when(this.player.isFlying()).thenReturn(false);
            when(this.player.getVelocity()).thenReturn(new Vector(0.25D, 0.0D, -0.5D));
            when(this.player.getFallDistance()).thenReturn(3.0F);
            when(this.player.isOnline()).thenReturn(true);
            when(this.player.spigot()).thenReturn(this.spigot);
            when(this.player.teleport(any(Location.class))).thenAnswer(invocation -> {
                this.currentLocation.set(invocation.<Location>getArgument(0).clone());
                return true;
            });

            FileConfiguration config = mock(FileConfiguration.class);
            when(config.getLong("recording.maxDurationSeconds", 300L)).thenReturn(300L);
            when(config.getDouble("recording.positionTolerance", 0.05D)).thenReturn(0.05D);
            when(config.getDouble("recording.rotationToleranceDegrees", 0.5D)).thenReturn(0.5D);
            Logger logger = Logger.getLogger("RecordingManagerTest-" + this.playerId);
            logger.setUseParentHandlers(false);
            when(this.plugin.getConfig()).thenReturn(config);
            when(this.plugin.getLogger()).thenReturn(logger);
            when(this.plugin.getName()).thenReturn("ServerTours");
            when(this.plugin.getRouteManager()).thenReturn(this.routeManager);
            when(this.plugin.getPlaybackManager()).thenReturn(this.playbackManager);
            when(this.plugin.getEditModeManager()).thenReturn(this.editModeManager);
            when(this.plugin.getPersistenceManager()).thenReturn(this.persistenceManager);
            when(this.plugin.isEnabled()).thenReturn(true);
            when(this.plugin.isBedrockPlayer(this.player)).thenReturn(false);
            when(this.routeManager.getRoutes()).thenReturn(Set.of());
            when(this.routeManager.getRoute(any(String.class))).thenAnswer(invocation -> {
                CraftRoute route = this.registeredRoute.get();
                return route != null && route.getName().equals(invocation.getArgument(0)) ? route : null;
            });
            doAnswer(invocation -> {
                this.registeredRoute.set(invocation.getArgument(0));
                return null;
            }).when(this.routeManager).registerNewRoute(any(CraftRoute.class));

            when(this.scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
                this.primaryTasks.addLast(invocation.getArgument(1));
                return mock(BukkitTask.class);
            });
            when(this.scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class),
                    anyLong(), anyLong())).thenAnswer(invocation -> {
                this.timerTask = mock(BukkitTask.class);
                return this.timerTask;
            });

            this.serverToursStatic = mockStatic(ServerTours.class);
            this.serverToursStatic.when(ServerTours::getInstance).thenReturn(this.plugin);
            this.bukkitStatic = mockStatic(Bukkit.class);
            this.bukkitStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            this.bukkitStatic.when(Bukkit::getScheduler).thenReturn(this.scheduler);
            this.bukkitStatic.when(() -> Bukkit.getWorld(this.worldId)).thenReturn(this.world);
            this.bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(this.world);
            this.bukkitStatic.when(() -> Bukkit.getPlayer(this.playerId)).thenReturn(this.player);

            this.repository = new RecordingRepository(tempDirectory);
            this.repository.load();
            this.manager = new RecordingManager(this.plugin, this.repository,
                    this.clock, this.compilerExecutor);
            this.manager.load();
            this.manager.reconcileRoutes();
        }

        private Location location() {
            return this.currentLocation.get().clone();
        }

        private void moveTo(double x, double y, double z, float yaw, float pitch) {
            this.currentLocation.set(new Location(this.world, x, y, z, yaw, pitch));
        }

        private Player createAdditionalPlayer(UUID id, String name) {
            Player additional = mock(Player.class);
            PlayerInventory additionalInventory = mock(PlayerInventory.class);
            Player.Spigot additionalSpigot = mock(Player.Spigot.class);
            AtomicReference<Location> location = new AtomicReference<>(
                    new Location(this.world, 4.0, 68.0, 2.0, 0.0F, 0.0F));
            AtomicReference<GameMode> gameMode = new AtomicReference<>(GameMode.SURVIVAL);
            when(additional.getUniqueId()).thenReturn(id);
            when(additional.getName()).thenReturn(name);
            when(additional.getLocation()).thenAnswer(ignored -> location.get().clone());
            when(additional.getInventory()).thenReturn(additionalInventory);
            when(additionalInventory.getStorageContents()).thenReturn(new ItemStack[0]);
            when(additionalInventory.getArmorContents()).thenReturn(new ItemStack[0]);
            when(additionalInventory.getExtraContents()).thenReturn(new ItemStack[0]);
            when(additional.getGameMode()).thenAnswer(ignored -> gameMode.get());
            doAnswer(invocation -> {
                gameMode.set(invocation.getArgument(0));
                return null;
            }).when(additional).setGameMode(any(GameMode.class));
            when(additional.getVelocity()).thenReturn(new Vector());
            when(additional.isOnline()).thenReturn(true);
            when(additional.spigot()).thenReturn(additionalSpigot);
            when(additional.teleport(any(Location.class))).thenAnswer(invocation -> {
                location.set(invocation.<Location>getArgument(0).clone());
                return true;
            });
            this.bukkitStatic.when(() -> Bukkit.getPlayer(id)).thenReturn(additional);
            return additional;
        }

        private void runNextPrimaryTask() {
            Runnable task = this.primaryTasks.removeFirst();
            task.run();
        }

        @Override
        public void close() {
            if (!this.compilerExecutor.isShutdown()) {
                this.manager.shutdown();
            }
            this.bukkitStatic.close();
            this.serverToursStatic.close();
        }
    }

    private static final class MutableNanoClock implements NanoClock {
        private long now;

        private MutableNanoClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return this.now;
        }

        private void advance(long nanos) {
            this.now += nanos;
        }
    }

    private static final class QueuedExecutorService extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            this.shutdown = true;
            List<Runnable> pending = List.copyOf(this.tasks);
            this.tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown && this.tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return this.isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (this.shutdown) {
                throw new IllegalStateException("executor is shut down");
            }
            this.tasks.addLast(command);
        }

        private int pendingCount() {
            return this.tasks.size();
        }

        private boolean isEmpty() {
            return this.tasks.isEmpty();
        }

        private void runNext() {
            this.tasks.removeFirst().run();
        }
    }
}
