package com.melluh.servertours.recording;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.CameraSource;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.recording.math.FixedRateSampleGate;
import com.melluh.servertours.recording.math.RecordingCompiler;
import com.melluh.servertours.recording.model.RecordingSample;
import com.melluh.servertours.recording.storage.CameraRecording;
import com.melluh.servertours.recording.storage.RecordingDraft;
import com.melluh.servertours.recording.storage.RecordingMetadata;
import com.melluh.servertours.recording.storage.RecordingRepository;
import com.melluh.servertours.recording.storage.RecordingYamlCodec;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Captures, drafts, compiles and commits Java camera recordings. */
public final class RecordingManager implements Listener {
    private static final long MIN_RECORDING_NANOS = FixedRateSampleGate.DEFAULT_INTERVAL_NANOS;
    private static final int ACTION_BAR_PERIOD_TICKS = 5;
    private static final String MANAGE_PERMISSION = "servertours.commands.record.manage";

    private final ServerTours plugin;
    private final RecordingRepository repository;
    private final NanoClock clock;
    private final ExecutorService compilerExecutor;
    private final Map<UUID, ActiveRecordingSession> active = new HashMap<>();
    private final Map<String, UUID> reservedRouteNames = new HashMap<>();
    private final Map<UUID, RecordingDraft> volatileDrafts = new HashMap<>();
    private final Set<UUID> playerLeases = new HashSet<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> internalGameModeChanges = new HashSet<>();
    private final Set<UUID> finalizing = ConcurrentHashMap.newKeySet();

    private BukkitTask captureTask;
    private int actionBarTicks;
    private boolean ready;
    private volatile boolean shuttingDown;

    public RecordingManager(ServerTours plugin) {
        this(plugin,
                new RecordingRepository(Objects.requireNonNull(plugin, "plugin may not be null")
                        .getDataFolder().toPath()),
                NanoClock.system(),
                Executors.newSingleThreadExecutor(new RecordingThreadFactory()));
    }

    RecordingManager(ServerTours plugin, RecordingRepository repository, NanoClock clock,
                     ExecutorService compilerExecutor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin may not be null");
        this.repository = Objects.requireNonNull(repository, "repository may not be null");
        this.clock = Objects.requireNonNull(clock, "clock may not be null");
        this.compilerExecutor = Objects.requireNonNull(compilerExecutor, "compilerExecutor may not be null");
    }

    public void load() {
        try {
            RecordingRepository.LoadReport report = this.repository.load();
            for (RecordingRepository.LoadFailure failure : report.failures()) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Could not load camera recording " + failure.file() + ": " + failure.message(),
                        failure.cause());
            }
            this.reservedRouteNames.clear();
            for (RecordingDraft draft : this.repository.listDrafts()) {
                this.reservedRouteNames.putIfAbsent(draft.metadata().routeName(), draft.metadata().id());
            }
            this.plugin.getLogger().info("Loaded " + report.readyLoaded() + " camera recordings and "
                    + report.draftsLoaded() + " recording drafts");
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not initialize camera recording storage", exception);
        }
    }

    /** Repairs interrupted multi-file commits after routes have been loaded. */
    public void reconcileRoutes() {
        try {
            Map<UUID, Route> referenced = new HashMap<>();
            for (Route route : this.plugin.getRouteManager().getRoutes()) {
                route.getCameraRecordingId().ifPresent(id -> referenced.put(id, route));
            }

            for (CameraRecording recording : this.repository.listReady()) {
                UUID id = recording.metadata().id();
                if (referenced.containsKey(id)) {
                    try {
                        this.repository.deleteDraft(id);
                        this.reservedRouteNames.remove(recording.metadata().routeName(), id);
                    } catch (IOException exception) {
                        this.plugin.getLogger().log(Level.WARNING,
                                "Could not remove committed recording draft " + id, exception);
                    }
                    continue;
                }

                try {
                    if (this.repository.getDraft(id).isEmpty()) {
                        this.repository.saveDraft(new RecordingDraft(
                                recording.metadata(), recording.compiled().rawSamples()));
                    }
                    this.repository.deleteReady(id);
                    this.reservedRouteNames.put(recording.metadata().routeName(), id);
                    this.plugin.getLogger().warning("Recovered uncommitted camera recording '"
                            + recording.metadata().routeName() + "' as a draft");
                } catch (IOException exception) {
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not recover uncommitted camera recording " + id, exception);
                }
            }
        } finally {
            this.ready = true;
        }
    }

    public void startRunnable() {
        if (this.captureTask != null) {
            return;
        }
        this.captureTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    public RecordingRepository getRepository() {
        return this.repository;
    }

    public boolean isRecording(Player player) {
        requirePrimaryThread();
        return player != null && (this.active.containsKey(player.getUniqueId())
                || this.playerLeases.contains(player.getUniqueId()));
    }

    public boolean isRouteNameReserved(String routeName) {
        requirePrimaryThread();
        try {
            return this.reservedRouteNames.containsKey(RecordingMetadata.normalizeRouteName(routeName));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean start(Player player, String requestedRouteName) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player may not be null");
        String routeName;
        try {
            routeName = RecordingMetadata.normalizeRouteName(requestedRouteName);
        } catch (RuntimeException exception) {
            player.sendMessage(ServerTours.translate("commands.record.errors.invalidName"));
            return false;
        }
        if (!this.canStart(player)) {
            return false;
        }
        if (this.plugin.getRouteManager().getRoute(routeName) != null
                || this.reservedRouteNames.containsKey(routeName)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.nameReserved", routeName));
            return false;
        }

        RecordingSettings settings;
        try {
            settings = RecordingSettings.load(this.plugin.getConfig());
        } catch (RuntimeException exception) {
            player.sendMessage(ServerTours.translate("commands.record.errors.invalidSettings"));
            this.plugin.getLogger().log(Level.SEVERE, "Invalid camera recording settings", exception);
            return false;
        }

        Location start = player.getLocation();
        World world = Objects.requireNonNull(start.getWorld(), "player world may not be null");
        RecordingMetadata metadata = new RecordingMetadata(
                UUID.randomUUID(), routeName, player.getUniqueId(), player.getName(),
                world.getUID(), world.getName(), System.currentTimeMillis(),
                FixedRateSampleGate.DEFAULT_INTERVAL_NANOS, settings.tolerances(),
                RecordingYamlCodec.CURRENT_COMPILER_VERSION
        );
        UUID playerId = player.getUniqueId();
        if (!this.playerLeases.add(playerId)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.alreadyRecording"));
            return false;
        }
        UUID conflictingReservation = this.reservedRouteNames.putIfAbsent(routeName, metadata.id());
        if (conflictingReservation != null) {
            this.playerLeases.remove(playerId);
            player.sendMessage(ServerTours.translate("commands.record.errors.nameReserved", routeName));
            return false;
        }
        ActiveRecordingSession session = null;
        try {
            session = this.createSession(
                    player, metadata, List.of(), null, settings.maxDurationNanos());
            this.requirePublishableSession(player);
            this.active.put(playerId, session);
        } catch (Throwable throwable) {
            this.rollbackUnpublishedSession(playerId, session, metadata, true);
            player.sendMessage(ServerTours.translate("commands.record.errors.setupFailed"));
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not start camera recording '" + routeName + "' for " + player.getName(), throwable);
            return false;
        }
        this.sendMessageSafely(player, ServerTours.translate("commands.record.started", routeName));
        return true;
    }

    public boolean resume(Player player, String requestedRouteName) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player may not be null");
        if (!this.canStart(player)) {
            return false;
        }
        RecordingDraft draft = this.findDraft(requestedRouteName).orElse(null);
        if (draft == null) {
            player.sendMessage(ServerTours.translate("commands.record.errors.draftNotFound", requestedRouteName));
            return false;
        }
        if (!this.canManage(player, draft)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.notDraftOwner"));
            return false;
        }
        if (this.finalizing.contains(draft.metadata().id())) {
            player.sendMessage(ServerTours.translate("commands.record.errors.processing"));
            return false;
        }
        if (this.plugin.getRouteManager().getRoute(draft.metadata().routeName()) != null) {
            player.sendMessage(ServerTours.translate("commands.record.errors.nameReserved",
                    draft.metadata().routeName()));
            return false;
        }

        RecordingSettings settings;
        try {
            settings = RecordingSettings.load(this.plugin.getConfig());
        } catch (RuntimeException exception) {
            player.sendMessage(ServerTours.translate("commands.record.errors.invalidSettings"));
            this.plugin.getLogger().log(Level.SEVERE, "Invalid camera recording settings", exception);
            return false;
        }

        World world = this.resolveWorld(draft.metadata());
        if (world == null) {
            player.sendMessage(ServerTours.translate("commands.record.errors.worldUnavailable",
                    draft.metadata().worldName()));
            return false;
        }
        RecordingSample last = draft.rawSamples().get(draft.rawSamples().size() - 1);
        Location resumeLocation = new Location(world, last.x(), last.y(), last.z(),
                wrapYaw(last.yawUnwrapped()), (float) last.pitch());
        UUID playerId = player.getUniqueId();
        if (!this.playerLeases.add(playerId)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.alreadyRecording"));
            return false;
        }
        UUID conflictingReservation = this.reservedRouteNames.putIfAbsent(
                draft.metadata().routeName(), draft.metadata().id());
        if (conflictingReservation != null && !conflictingReservation.equals(draft.metadata().id())) {
            this.playerLeases.remove(playerId);
            player.sendMessage(ServerTours.translate("commands.record.errors.nameReserved",
                    draft.metadata().routeName()));
            return false;
        }
        ActiveRecordingSession session = null;
        try {
            session = this.createSession(
                    player, draft.metadata(), draft.rawSamples(), resumeLocation,
                    settings.maxDurationNanos());
            this.requirePublishableSession(player);
            this.active.put(playerId, session);
        } catch (Throwable throwable) {
            this.rollbackUnpublishedSession(playerId, session, draft.metadata(), false);
            player.sendMessage(ServerTours.translate("commands.record.errors.setupFailed"));
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not resume camera recording draft '" + draft.metadata().routeName()
                            + "' for " + player.getName(), throwable);
            return false;
        }
        this.sendMessageSafely(player, ServerTours.translate("commands.record.resumed",
                draft.metadata().routeName()));
        return true;
    }

    public boolean stop(Player player) {
        requirePrimaryThread();
        ActiveRecordingSession session = this.active.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ServerTours.translate("commands.record.errors.notRecording"));
            return false;
        }
        if (session.elapsedNanos() < MIN_RECORDING_NANOS) {
            player.sendMessage(ServerTours.translate("commands.record.errors.tooShort"));
            return false;
        }
        return this.finish(session, FinishMode.COMMIT, true);
    }

    public boolean cancel(Player player) {
        requirePrimaryThread();
        ActiveRecordingSession session = this.active.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ServerTours.translate("commands.record.errors.notRecording"));
            return false;
        }
        return this.finish(session, FinishMode.CANCEL, false);
    }

    public void listDrafts(CommandSender sender) {
        requirePrimaryThread();
        List<RecordingDraft> drafts = this.allDrafts().stream()
                .filter(draft -> this.canManage(sender, draft))
                .sorted(Comparator.comparing(draft -> draft.metadata().routeName()))
                .toList();
        if (drafts.isEmpty()) {
            sender.sendMessage(ServerTours.translate("commands.record.noDrafts"));
            return;
        }
        sender.sendMessage(ServerTours.translate("commands.record.draftsHeader"));
        for (RecordingDraft draft : drafts) {
            sender.sendMessage(ServerTours.translate("commands.record.draftLine",
                    draft.metadata().routeName(), formatSeconds(draft.durationNanos()),
                    draft.rawSamples().size()));
        }
    }

    public boolean discardDraft(CommandSender sender, String routeName) {
        requirePrimaryThread();
        RecordingDraft draft = this.findDraft(routeName).orElse(null);
        if (draft == null) {
            sender.sendMessage(ServerTours.translate("commands.record.errors.draftNotFound", routeName));
            return false;
        }
        if (!this.canManage(sender, draft)) {
            sender.sendMessage(ServerTours.translate("commands.record.errors.notDraftOwner"));
            return false;
        }
        if (this.finalizing.contains(draft.metadata().id())
                || this.active.values().stream().anyMatch(session -> session.metadata().id().equals(draft.metadata().id()))) {
            sender.sendMessage(ServerTours.translate("commands.record.errors.processing"));
            return false;
        }
        try {
            this.repository.deleteDraft(draft.metadata().id());
            this.volatileDrafts.remove(draft.metadata().id());
            this.reservedRouteNames.remove(draft.metadata().routeName(), draft.metadata().id());
            sender.sendMessage(ServerTours.translate("commands.record.discarded",
                    draft.metadata().routeName()));
            return true;
        } catch (IOException exception) {
            sender.sendMessage(ServerTours.translate("commands.record.errors.saveFailed"));
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not delete recording draft " + draft.metadata().id(), exception);
            return false;
        }
    }

    public boolean setSource(CommandSender sender, String routeName, CameraSource source) {
        requirePrimaryThread();
        Objects.requireNonNull(sender, "sender may not be null");
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            sender.sendMessage(ServerTours.translate("commands.errors.noPermission", MANAGE_PERMISSION));
            return false;
        }
        Objects.requireNonNull(source, "source may not be null");
        CraftRoute route = this.plugin.getRouteManager().getRoute(routeName);
        if (route == null) {
            sender.sendMessage(ServerTours.translate("commands.errors.routeNotFound", routeName));
            return false;
        }
        if (source == CameraSource.RECORDED) {
            UUID recordingId = route.getCameraRecordingId().orElse(null);
            if (recordingId == null || this.repository.getReady(recordingId).isEmpty()) {
                sender.sendMessage(ServerTours.translate("commands.record.errors.recordingUnavailable"));
                return false;
            }
        }

        CameraSource previous = route.getCameraSource();
        route.setCameraSource(source);
        try {
            this.plugin.getPersistenceManager().saveRouteChecked(route);
            sender.sendMessage(ServerTours.translate("commands.record.sourceChanged",
                    route.getName(), source.name().toLowerCase(Locale.ROOT)));
            return true;
        } catch (IOException exception) {
            route.setCameraSource(previous);
            sender.sendMessage(ServerTours.translate("commands.record.errors.saveFailed"));
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not save camera source for route '" + route.getName() + "'", exception);
            return false;
        }
    }

    public List<String> draftNamesFor(CommandSender sender) {
        requirePrimaryThread();
        return this.allDrafts().stream()
                .filter(draft -> this.canManage(sender, draft))
                .map(draft -> draft.metadata().routeName())
                .sorted()
                .toList();
    }

    public void shutdown() {
        requirePrimaryThread();
        if (this.shuttingDown) {
            return;
        }
        this.shuttingDown = true;
        try {
            if (this.captureTask != null) {
                try {
                    this.captureTask.cancel();
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not cancel the camera recording capture task during shutdown", throwable);
                } finally {
                    this.captureTask = null;
                }
            }
            for (ActiveRecordingSession session : new ArrayList<>(this.active.values())) {
                try {
                    this.finish(session, FinishMode.DRAFT, true);
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not finish a camera recording during plugin shutdown", throwable);
                    this.recoverFailedShutdownSession(session, throwable);
                }
            }
            for (RecordingDraft draft : new ArrayList<>(this.volatileDrafts.values())) {
                try {
                    this.repository.saveDraft(draft);
                    this.volatileDrafts.remove(draft.metadata().id());
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not persist camera recording draft during plugin shutdown "
                                    + draft.metadata().id(), throwable);
                }
            }
        } finally {
            try {
                this.compilerExecutor.shutdownNow();
            } catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Could not stop the camera recording compiler during plugin shutdown", throwable);
            }
        }
    }

    private void recoverFailedShutdownSession(ActiveRecordingSession session, Throwable originalFailure) {
        UUID playerId = null;
        try {
            playerId = session.player().getUniqueId();
            this.active.remove(playerId, session);
        } catch (Throwable cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        try {
            this.restoreSession(session);
        } catch (Throwable cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        if (playerId != null) {
            this.playerLeases.remove(playerId);
        }
    }

    private ActiveRecordingSession createSession(Player player, RecordingMetadata metadata,
                                                   List<RecordingSample> baseline,
                                                   Location resumeLocation,
                                                   long maxDurationNanos) {
        PlayerRestoreWrapper restoreWrapper = new PlayerRestoreWrapper(player);
        Location returnLocation = player.getLocation().clone();
        try {
            this.runWithInternalGameModeChange(player, () -> restoreWrapper.setGameMode(GameMode.SPECTATOR));
            if (player.getGameMode() != GameMode.SPECTATOR) {
                throw new IllegalStateException("recording player could not enter spectator mode");
            }
            player.setVelocity(new Vector());
            player.setFallDistance(0.0f);
            if (resumeLocation != null) {
                this.teleportInternally(player, resumeLocation);
            }
            return new ActiveRecordingSession(player, metadata, baseline,
                    restoreWrapper, returnLocation, this.clock, maxDurationNanos);
        } catch (Throwable throwable) {
            try {
                this.teleportInternally(player, returnLocation);
            } catch (Throwable restoreFailure) {
                throwable.addSuppressed(restoreFailure);
            }
            try {
                this.runWithInternalGameModeChange(player, restoreWrapper::restore);
            } catch (Throwable restoreFailure) {
                throwable.addSuppressed(restoreFailure);
            }
            throw throwable;
        }
    }

    private void requirePublishableSession(Player player) {
        if (this.shuttingDown || !this.plugin.isEnabled() || !player.isOnline()
                || player.getGameMode() != GameMode.SPECTATOR) {
            throw new IllegalStateException("recording session became invalid during setup");
        }
    }

    private void rollbackUnpublishedSession(UUID playerId, ActiveRecordingSession session,
                                            RecordingMetadata metadata,
                                            boolean releaseRouteReservation) {
        if (session != null) {
            this.restoreSession(session);
        }
        this.playerLeases.remove(playerId);
        if (releaseRouteReservation) {
            this.reservedRouteNames.remove(metadata.routeName(), metadata.id());
        }
    }

    private boolean canStart(Player player) {
        if (this.shuttingDown) {
            player.sendMessage(ServerTours.translate("commands.record.errors.processing"));
            return false;
        }
        if (!this.ready) {
            player.sendMessage(ServerTours.translate("commands.record.errors.notReady"));
            return false;
        }
        if (this.plugin.isBedrockPlayer(player)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.javaOnly"));
            return false;
        }
        if (this.isRecording(player)) {
            player.sendMessage(ServerTours.translate("commands.record.errors.alreadyRecording"));
            return false;
        }
        if (this.plugin.getPlaybackManager().isTouringPlayer(player)) {
            player.sendMessage(ServerTours.translate("commands.errors.alreadyWatching"));
            return false;
        }
        if (this.plugin.getEditModeManager().isEditing(player)) {
            player.sendMessage(ServerTours.translate("commands.errors.alreadyEditing"));
            return false;
        }
        if (player.isDead() || player.isInsideVehicle()) {
            player.sendMessage(ServerTours.translate("commands.record.errors.invalidPlayerState"));
            return false;
        }
        return true;
    }

    private void tick() {
        boolean updateActionBar = this.actionBarTicks++ % ACTION_BAR_PERIOD_TICKS == 0;
        for (ActiveRecordingSession session : new ArrayList<>(this.active.values())) {
            try {
                session.tick();
            } catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Camera recording tick failed for " + session.player().getName(), throwable);
                this.finish(session, FinishMode.DRAFT, false);
                continue;
            }
            if (updateActionBar) {
                try {
                    this.sendActionBar(session);
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.WARNING,
                            "Could not update camera recording action bar for "
                                    + session.player().getName(), throwable);
                }
            }
            if (session.elapsedNanos() >= session.maxDurationNanos()) {
                this.finish(session, FinishMode.COMMIT, true);
            }
        }
    }

    private boolean finish(ActiveRecordingSession session, FinishMode mode, boolean captureFinal) {
        UUID playerId = session.player().getUniqueId();
        if (!this.active.remove(playerId, session)) {
            return false;
        }
        try {
            RecordingDraft snapshot;
            try {
                snapshot = session.stopAndSnapshot(captureFinal);
            } catch (Throwable captureFailure) {
                this.plugin.getLogger().log(Level.WARNING,
                        "Could not capture the final camera recording pose for " + session.player().getName(),
                        captureFailure);
                try {
                    snapshot = session.stopAndSnapshot(false);
                } catch (Throwable snapshotFailure) {
                    captureFailure.addSuppressed(snapshotFailure);
                    this.restoreSession(session);
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not freeze camera recording for " + session.player().getName(), captureFailure);
                    return false;
                }
            }
            this.restoreSession(session);

            if (mode == FinishMode.CANCEL) {
                if (session.baselineSamples().isEmpty()) {
                    this.reservedRouteNames.remove(session.metadata().routeName(), session.metadata().id());
                }
                this.sendMessageSafely(session.player(),
                        ServerTours.translate("commands.record.cancelled"));
                return true;
            }

            if (!this.persistDraft(snapshot)) {
                this.sendMessageSafely(session.player(),
                        ServerTours.translate("commands.record.errors.saveFailed"));
                return false;
            }
            if (mode == FinishMode.DRAFT || this.shuttingDown) {
                if (session.player().isOnline()) {
                    this.sendMessageSafely(session.player(),
                            ServerTours.translate("commands.record.draftSaved",
                                    snapshot.metadata().routeName()));
                }
                return true;
            }

            this.sendMessageSafely(session.player(),
                    ServerTours.translate("commands.record.processing", snapshot.metadata().routeName()));
            this.finalizeAsync(snapshot);
            return true;
        } finally {
            this.playerLeases.remove(playerId);
        }
    }

    private boolean persistDraft(RecordingDraft draft) {
        try {
            this.repository.saveDraft(draft);
            this.volatileDrafts.remove(draft.metadata().id());
            this.reservedRouteNames.put(draft.metadata().routeName(), draft.metadata().id());
            return true;
        } catch (IOException exception) {
            this.volatileDrafts.put(draft.metadata().id(), draft);
            this.reservedRouteNames.put(draft.metadata().routeName(), draft.metadata().id());
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not persist camera recording draft " + draft.metadata().id(), exception);
            return false;
        }
    }

    private void finalizeAsync(RecordingDraft draft) {
        UUID id = draft.metadata().id();
        if (!this.finalizing.add(id)) {
            return;
        }
        try {
            this.compilerExecutor.execute(() -> {
                try {
                    CameraRecording ready = draft.toReady(
                            new RecordingCompiler(draft.metadata().tolerances()).compile(draft.rawSamples()));
                    if (this.shuttingDown || Thread.currentThread().isInterrupted()) {
                        this.finalizing.remove(id);
                        return;
                    }
                    this.repository.saveReady(ready);
                    if (!this.plugin.isEnabled() || this.shuttingDown) {
                        this.finalizing.remove(id);
                        return;
                    }
                    Bukkit.getScheduler().runTask(this.plugin, () -> this.commitReady(ready));
                } catch (Throwable throwable) {
                    this.finalizing.remove(id);
                    if (this.shuttingDown && Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    this.plugin.getLogger().log(Level.SEVERE,
                            "Could not compile camera recording '" + draft.metadata().routeName() + "'", throwable);
                    if (this.plugin.isEnabled() && !this.shuttingDown) {
                        try {
                            Bukkit.getScheduler().runTask(this.plugin, () -> {
                                Player creator = Bukkit.getPlayer(draft.metadata().creatorId());
                                if (creator != null && creator.isOnline()) {
                                    this.sendMessageSafely(creator,
                                            ServerTours.translate("commands.record.errors.compileFailed"));
                                }
                            });
                        } catch (RuntimeException schedulingFailure) {
                            throwable.addSuppressed(schedulingFailure);
                        }
                    }
                }
            });
        } catch (RuntimeException rejected) {
            this.finalizing.remove(id);
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not schedule camera recording compilation '"
                            + draft.metadata().routeName() + "'", rejected);
            Player creator = Bukkit.getPlayer(draft.metadata().creatorId());
            if (creator != null && creator.isOnline()) {
                this.sendMessageSafely(creator,
                        ServerTours.translate("commands.record.errors.compileFailed"));
            }
        }
    }

    private void commitReady(CameraRecording recording) {
        UUID id = recording.metadata().id();
        boolean routeWritten = false;
        boolean published = false;
        try {
            if (this.shuttingDown || !this.plugin.isEnabled()) {
                return;
            }
            String routeName = recording.metadata().routeName();
            if (this.plugin.getRouteManager().getRoute(routeName) != null) {
                throw new IllegalStateException("Route with that name already exists");
            }
            World world = this.resolveWorld(recording.metadata());
            if (world == null) {
                throw new IllegalStateException("Recording world is unavailable: "
                        + recording.metadata().worldName());
            }

            RecordingSample first = recording.compiled().rawSamples().get(0);
            CraftRoute route = new CraftRoute(routeName);
            CraftRoutePoint anchor = route.createPoint(new Location(
                    world, first.x(), first.y(), first.z(),
                    wrapYaw(first.yawUnwrapped()), (float) first.pitch()), RoutePointType.STATIONARY);
            anchor.setTicksVisible((int) Math.max(1L,
                    Math.min(Integer.MAX_VALUE, recording.endFrame())));
            route.setCameraRecordingId(id);
            route.setCameraSource(CameraSource.RECORDED);

            this.plugin.getPersistenceManager().saveRouteChecked(route);
            routeWritten = true;
            try {
                this.plugin.getRouteManager().registerNewRoute(route);
            } catch (Throwable eventFailure) {
                if (this.plugin.getRouteManager().getRoute(routeName) != route) {
                    if (eventFailure instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (eventFailure instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException("RouteCreateEvent listener failed", eventFailure);
                }
                this.plugin.getLogger().log(Level.WARNING,
                        "Route '" + routeName + "' was published, but a RouteCreateEvent listener failed",
                        eventFailure);
            }
            if (this.plugin.getRouteManager().getRoute(routeName) != route) {
                throw new IllegalStateException("Route publication was synchronously removed or replaced");
            }
            published = true;
            try {
                this.repository.deleteDraft(id);
            } catch (IOException exception) {
                this.plugin.getLogger().log(Level.WARNING,
                        "Could not remove committed recording draft " + id, exception);
            }
            this.volatileDrafts.remove(id);
            this.reservedRouteNames.remove(routeName, id);
        } catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not commit camera recording '" + recording.metadata().routeName() + "'", throwable);
            if (!routeWritten) {
                try {
                    this.repository.deleteReady(id);
                } catch (IOException cleanupFailure) {
                    throwable.addSuppressed(cleanupFailure);
                }
            }
        } finally {
            this.finalizing.remove(id);
        }

        Player creator = Bukkit.getPlayer(recording.metadata().creatorId());
        if (creator != null && creator.isOnline()) {
            String message = published
                    ? ServerTours.translate("commands.record.saved",
                    recording.metadata().routeName(), recording.compiled().rawSamples().size(),
                    recording.compiled().keyframeIndices().size(),
                    formatSeconds(recording.durationNanos()))
                    : ServerTours.translate("commands.record.errors.commitFailed");
            this.sendMessageSafely(creator, message);
        }
    }

    private void restoreSession(ActiveRecordingSession session) {
        try {
            this.runWithInternalGameModeChange(session.player(),
                    () -> session.restore(location -> this.teleportInternally(session.player(), location)));
        } catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not fully restore player after camera recording: "
                            + session.player().getName(), throwable);
        }
        for (PlayerRestoreWrapper.RestoreFailure failure : session.restoreWrapper().getRestoreFailures()) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "Could not restore recording player " + session.player().getName()
                            + " property " + failure.getOperation(), failure.getCause());
        }
        try {
            session.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent());
        } catch (Throwable ignored) {
        }
    }

    private void teleportInternally(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        this.internalTeleports.add(playerId);
        try {
            if (!player.teleport(location)) {
                throw new IllegalStateException("recording player teleport was cancelled or rejected");
            }
        } finally {
            this.internalTeleports.remove(playerId);
        }
    }

    private void runWithInternalGameModeChange(Player player, Runnable action) {
        UUID playerId = player.getUniqueId();
        this.internalGameModeChanges.add(playerId);
        try {
            action.run();
        } finally {
            this.internalGameModeChanges.remove(playerId);
        }
    }

    private void sendMessageSafely(Player player, String message) {
        try {
            player.sendMessage(message);
        } catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.WARNING,
                    "Could not send camera recording status to " + player.getName(), throwable);
        }
    }

    private Optional<RecordingDraft> findDraft(String routeName) {
        String normalized;
        try {
            normalized = RecordingMetadata.normalizeRouteName(routeName);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        for (RecordingDraft draft : this.volatileDrafts.values()) {
            if (draft.metadata().routeName().equals(normalized)) {
                return Optional.of(draft);
            }
        }
        return this.repository.getDraftByRouteName(normalized);
    }

    private List<RecordingDraft> allDrafts() {
        Map<UUID, RecordingDraft> combined = new LinkedHashMap<>();
        for (RecordingDraft draft : this.repository.listDrafts()) {
            combined.put(draft.metadata().id(), draft);
        }
        combined.putAll(this.volatileDrafts);
        return List.copyOf(combined.values());
    }

    private boolean canManage(CommandSender sender, RecordingDraft draft) {
        return sender.hasPermission("servertours.commands.record.manage")
                || sender instanceof Player player
                && player.getUniqueId().equals(draft.metadata().creatorId());
    }

    private World resolveWorld(RecordingMetadata metadata) {
        World world = Bukkit.getWorld(metadata.worldId());
        return world != null ? world : Bukkit.getWorld(metadata.worldName());
    }

    private void sendActionBar(ActiveRecordingSession session) {
        String text = ServerTours.translate("commands.record.actionBar",
                session.metadata().routeName(), formatSeconds(session.elapsedNanos()),
                session.sampleCount());
        session.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    private static String formatSeconds(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000_000.0D);
    }

    private static float wrapYaw(double yaw) {
        double wrapped = yaw % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return (float) wrapped;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Camera recording state may only be accessed from the primary server thread");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ActiveRecordingSession session = this.active.get(playerId);
        if (session == null || this.internalTeleports.contains(playerId)) {
            return;
        }
        event.setCancelled(true);
        this.finish(session, FinishMode.DRAFT, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ActiveRecordingSession session = this.active.get(event.getPlayer().getUniqueId());
        if (session != null) {
            this.finish(session, FinishMode.DRAFT, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (this.isRecording(event.getPlayer())
                && !this.internalGameModeChanges.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && this.isRecording((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private enum FinishMode {
        COMMIT,
        DRAFT,
        CANCEL
    }

    private static final class RecordingThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "ServerTours-RecordingCompiler-" + this.sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
