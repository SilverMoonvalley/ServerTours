package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.PlaybackManager;
import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.event.RoutePlaybackBeginEvent;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.playback.PlaybackState;
import com.melluh.servertours.api.playback.track.TrackFactory;
import com.melluh.servertours.api.playback.track.TrackRegistration;
import com.melluh.servertours.playback.camera.BedrockMovementHandler;
import com.melluh.servertours.playback.camera.CameraPlaybackSettings;
import com.melluh.servertours.playback.camera.DisplayCameraMovementHandler;
import com.melluh.servertours.playback.camera.JavaCameraBackend;
import com.melluh.servertours.playback.camera.JavaMovementHandler;
import com.melluh.servertours.playback.camera.MovementHandler;
import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.playback.track.CraftTrackRegistration;
import com.melluh.servertours.playback.track.TrackFactoryRegistration;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.protocol.PacketUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CraftPlaybackManager implements PlaybackManager, Listener {
    private final Map<UUID, CraftTouringPlayer> touringPlayers;
    private final Map<UUID, CraftTouringPlayer> lifecycleSessions;
    private final Map<UUID, PendingStart> pendingStarts;
    private final Map<UUID, Long> generations;
    private final Map<UUID, PendingVisibilityRestore> pendingVisibilityRestores;
    private final Map<NamespacedKey, TrackFactoryRegistration> trackFactories;
    private long nextRegistrationOrder;
    private BukkitTask playbackTask;
    private boolean stoppingAll;

    public CraftPlaybackManager() {
        this.touringPlayers = new ConcurrentHashMap<>();
        this.lifecycleSessions = new ConcurrentHashMap<>();
        this.pendingStarts = new HashMap<>();
        this.generations = new HashMap<>();
        this.pendingVisibilityRestores = new HashMap<>();
        this.trackFactories = new LinkedHashMap<>();
    }

    @Override
    public synchronized @NotNull TrackRegistration registerTrackFactory(@NotNull Plugin plugin,
                                                                          @NotNull NamespacedKey key,
                                                                          int priority,
                                                                          @NotNull TrackFactory factory) {
        Objects.requireNonNull(plugin, "plugin may not be null");
        Objects.requireNonNull(key, "key may not be null");
        Objects.requireNonNull(factory, "factory may not be null");
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("cannot register a playback track for a disabled plugin");
        }
        NamespacedKey cameraTrackKey = new NamespacedKey(ServerTours.getInstance(), "route-camera");
        if (key.equals(cameraTrackKey)) {
            throw new IllegalArgumentException(key + " is reserved for the built-in camera track");
        }
        if (this.trackFactories.containsKey(key)) {
            throw new IllegalArgumentException("a playback track factory is already registered as " + key);
        }
        TrackFactoryRegistration registration = new TrackFactoryRegistration(
                plugin, key, priority, this.nextRegistrationOrder++, factory
        );
        this.trackFactories.put(key, registration);
        return new CraftTrackRegistration(this, registration);
    }

    public synchronized void unregisterTrackFactory(TrackFactoryRegistration registration) {
        this.trackFactories.remove(registration.key(), registration);
    }

    public synchronized boolean isTrackFactoryRegistered(TrackFactoryRegistration registration) {
        return this.trackFactories.get(registration.key()) == registration;
    }

    private synchronized List<TrackFactoryRegistration> snapshotTrackFactories() {
        List<TrackFactoryRegistration> registrations = new ArrayList<>(this.trackFactories.values());
        registrations.sort(null);
        return List.copyOf(registrations);
    }

    @Override
    public TouringPlayer showTour(Player player, Route route) {
        if (route instanceof CraftRoute craftRoute) {
            return this.showTour(player, craftRoute);
        }
        throw new IllegalArgumentException("route must be an instance of CraftRoute");
    }

    public CraftTouringPlayer showTour(Player obj, CraftRoute obj2) {
        Objects.requireNonNull(obj, "player may not be null");
        Objects.requireNonNull(obj2, "route may not be null");
        if (obj2.getNumPoints() < 1) {
            obj.sendMessage(ServerTours.translate("commands.errors.noPoints"));
            return null;
        }
        if (this.stoppingAll) {
            return null;
        }
        UUID playerId = obj.getUniqueId();
        CraftTouringPlayer touringPlayer = this.lifecycleSessions.get(playerId);
        if (touringPlayer != null) {
            this.pendingStarts.put(playerId, new PendingStart(obj, obj2));
            if (touringPlayer.isStopInProgress()
                    || touringPlayer.getPlaybackState() == PlaybackState.STARTING) {
                return touringPlayer;
            }
            touringPlayer.exit(RoutePlaybackEndEvent.EndReason.REPLACED);
            return this.getTouringPlayer(obj);
        }
        return this.startTour(obj, obj2);
    }

    private CraftTouringPlayer startTour(Player player, CraftRoute route) {
        UUID playerId = player.getUniqueId();
        long previousGeneration = this.generations.getOrDefault(playerId, 0L);
        long generation = previousGeneration == Long.MAX_VALUE ? 1L : previousGeneration + 1L;
        CraftTouringPlayer session = null;
        try {
            session = new CraftTouringPlayer(
                    player,
                    route,
                    this.createMovementHandler(player),
                    this,
                    generation,
                    this.snapshotTrackFactories(),
                    NanoClock.system()
            );
            if (this.claimPendingVisibility(player)) {
                session.inheritInvisibilityLease();
            }
            this.generations.put(playerId, generation);
            this.lifecycleSessions.put(playerId, session);
            session.initialize();
            if (this.lifecycleSessions.get(playerId) != session
                    || session.getPlaybackState() != PlaybackState.STARTING) {
                if (session.getPlaybackState() != PlaybackState.STOPPED) {
                    session.exit(RoutePlaybackEndEvent.EndReason.ERROR);
                }
                return this.touringPlayers.get(playerId);
            }
            if (this.pendingStarts.containsKey(playerId)) {
                session.exit(RoutePlaybackEndEvent.EndReason.REPLACED);
                return this.touringPlayers.get(playerId);
            }
            if (!player.isOnline()) {
                session.exit(RoutePlaybackEndEvent.EndReason.QUIT);
                return null;
            }
            this.touringPlayers.put(playerId, session);
            session.activate();
            Bukkit.getPluginManager().callEvent(new RoutePlaybackBeginEvent(session));
            if (this.touringPlayers.get(playerId) == session && session.isActive()) {
                session.beginPlayback();
            }
            CraftTouringPlayer current = this.touringPlayers.get(playerId);
            return current != null ? current : session;
        } catch (Throwable throwable) {
            ServerTours.getInstance().getLogger().severe("Failed to start route '" + route.getName()
                    + "' for " + player.getName() + ": " + throwable.getMessage());
            if (session != null) {
                session.abortStart(throwable);
                this.touringPlayers.remove(playerId, session);
                this.lifecycleSessions.remove(playerId, session);
            } else {
                this.generations.compute(playerId,
                        (ignored, current) -> Objects.equals(current, generation)
                                ? (previousGeneration == 0L ? null : previousGeneration) : current);
            }
            return this.touringPlayers.get(playerId);
        }
    }

    private boolean claimPendingVisibility(Player player) {
        PendingVisibilityRestore pending = this.pendingVisibilityRestores.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        pending.task().cancel();
        if (pending.entityId() == player.getEntityId()) {
            return true;
        }
        this.restoreVisibilityEntity(pending.entityId(), pending.playerName());
        return false;
    }

    private MovementHandler createMovementHandler(Player player) {
        if (ServerTours.getInstance().isBedrockPlayer(player)) {
            return new BedrockMovementHandler();
        }
        CameraPlaybackSettings settings = CameraPlaybackSettings.load(ServerTours.getInstance().getConfig());
        return settings.javaBackend() == JavaCameraBackend.DISPLAY
                ? new DisplayCameraMovementHandler(settings)
                : new JavaMovementHandler();
    }

    public boolean isTouringPlayer(Player player) {
        return this.getTouringPlayer(player) != null;
    }

    @Override
    public CraftTouringPlayer getTouringPlayer(Player player) {
        if (player.getClass().getName().endsWith("TemporaryPlayer")) {
            return null;
        }
        return this.touringPlayers.get(player.getUniqueId());
    }

    @Override
    public List<CraftTouringPlayer> getTouringPlayers(Route route) {
        return this.touringPlayers.values().stream().filter(craftTouringPlayer -> craftTouringPlayer.getRoute() == route).toList();
    }

    @Override
    public List<CraftTouringPlayer> getTouringPlayers() {
        return this.touringPlayers.values().stream().toList();
    }

    public void unregister(CraftTouringPlayer value) {
        this.touringPlayers.remove(value.getPlayer().getUniqueId(), value);
    }

    void onSessionStopped(CraftTouringPlayer session, RoutePlaybackEndEvent.EndReason reason) {
        UUID playerId = session.getPlayer().getUniqueId();
        this.unregister(session);
        this.lifecycleSessions.remove(playerId, session);
        if (this.stoppingAll || reason == RoutePlaybackEndEvent.EndReason.QUIT
                || reason == RoutePlaybackEndEvent.EndReason.PLUGIN_DISABLED) {
            this.pendingStarts.remove(playerId);
            this.releaseGenerationIfIdle(playerId, session.getGeneration());
            if (this.stoppingAll && this.lifecycleSessions.isEmpty()) {
                this.generations.clear();
            }
            return;
        }
        PendingStart pending = this.pendingStarts.remove(playerId);
        if (pending != null && pending.player().isOnline()) {
            CraftTouringPlayer current = this.touringPlayers.get(playerId);
            if (current != null) {
                current.exit(RoutePlaybackEndEvent.EndReason.REPLACED);
            }
            if (!this.touringPlayers.containsKey(playerId)) {
                this.startTour(pending.player(), pending.route());
            }
        }
        this.releaseGenerationIfIdle(playerId, session.getGeneration());
    }

    void processPendingAfterCancelledStop(CraftTouringPlayer session) {
        UUID playerId = session.getPlayer().getUniqueId();
        if (!this.pendingStarts.containsKey(playerId)) {
            return;
        }
        session.exit(RoutePlaybackEndEvent.EndReason.REPLACED);
    }

    boolean hasPendingStart(CraftTouringPlayer session) {
        UUID playerId = session.getPlayer().getUniqueId();
        return this.lifecycleSessions.get(playerId) == session && this.pendingStarts.containsKey(playerId);
    }

    boolean ownsLifecycle(CraftTouringPlayer session) {
        return this.lifecycleSessions.get(session.getPlayer().getUniqueId()) == session && !this.stoppingAll;
    }

    boolean isCurrentGeneration(Player player, long generation) {
        return this.generations.getOrDefault(player.getUniqueId(), -1L) == generation;
    }

    void scheduleVisibilityRestore(Player player, long generation) {
        UUID playerId = player.getUniqueId();
        int entityId = player.getEntityId();
        PendingVisibilityRestore previous = this.pendingVisibilityRestores.remove(playerId);
        if (previous != null) {
            previous.task().cancel();
            if (previous.entityId() != entityId) {
                this.restoreVisibilityEntity(previous.entityId(), previous.playerName());
            }
        }
        try {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(ServerTours.getInstance(),
                    () -> this.completeVisibilityRestore(playerId, generation), 5L);
            this.pendingVisibilityRestores.put(playerId,
                    new PendingVisibilityRestore(entityId, player.getName(), generation, task));
        } catch (Throwable throwable) {
            this.restoreVisibilityEntity(entityId, player.getName());
            this.releaseGenerationIfIdle(playerId, generation);
            throw throwable;
        }
    }

    private void completeVisibilityRestore(UUID playerId, long generation) {
        PendingVisibilityRestore pending = this.pendingVisibilityRestores.get(playerId);
        if (pending == null || pending.generation() != generation) {
            return;
        }
        this.pendingVisibilityRestores.remove(playerId, pending);
        try {
            if (this.generations.getOrDefault(playerId, -1L) == generation
                    && !this.lifecycleSessions.containsKey(playerId)) {
                this.restoreVisibilityEntity(pending.entityId(), pending.playerName());
            }
        } finally {
            this.releaseGenerationIfIdle(playerId, generation);
        }
    }

    private void restorePendingVisibilityNow() {
        for (PendingVisibilityRestore pending : new ArrayList<>(this.pendingVisibilityRestores.values())) {
            pending.task().cancel();
            this.restoreVisibilityEntity(pending.entityId(), pending.playerName());
        }
        this.pendingVisibilityRestores.clear();
    }

    private void restoreVisibilityEntity(int entityId, String playerName) {
        try {
            PacketUtil.setInvisible(entityId, false);
        } catch (Throwable throwable) {
            ServerTours.getInstance().getLogger().severe("Failed to restore delayed player visibility for "
                    + playerName + " (entity " + entityId + "): " + throwable.getMessage());
        }
    }

    private void releaseGenerationIfIdle(UUID playerId, long generation) {
        if (!this.lifecycleSessions.containsKey(playerId)
                && !this.pendingStarts.containsKey(playerId)
                && !this.pendingVisibilityRestores.containsKey(playerId)) {
            this.generations.remove(playerId, generation);
        }
    }

    public void stopAllTouring() {
        this.stoppingAll = true;
        if (this.playbackTask != null) {
            this.playbackTask.cancel();
            this.playbackTask = null;
        }
        this.pendingStarts.clear();
        this.restorePendingVisibilityNow();
        new ArrayList<>(this.lifecycleSessions.values()).forEach(
                craftTouringPlayer -> craftTouringPlayer.exit(RoutePlaybackEndEvent.EndReason.PLUGIN_DISABLED)
        );
        if (this.lifecycleSessions.isEmpty()) {
            this.generations.clear();
        }
    }

    public void startRunnable() {
        this.playbackTask = Bukkit.getScheduler().runTaskTimer(ServerTours.getInstance(), this::tickPlayers, 1L, 1L);
    }

    private void tickPlayers() {
        for (CraftTouringPlayer touringPlayer : new ArrayList<>(this.lifecycleSessions.values())) {
            try {
                touringPlayer.tick();
            } catch (Throwable throwable) {
                ServerTours.getInstance().getLogger().severe("Playback tick failed for "
                        + touringPlayer.getPlayer().getName() + " on route '"
                        + touringPlayer.getRoute().getName() + "': " + throwable.getMessage());
                try {
                    touringPlayer.exit(RoutePlaybackEndEvent.EndReason.ERROR);
                } catch (Throwable cleanupFailure) {
                    ServerTours.getInstance().getLogger().severe("Playback error cleanup also failed for "
                            + touringPlayer.getPlayer().getName() + ": " + cleanupFailure.getMessage());
                    this.unregister(touringPlayer);
                    this.lifecycleSessions.remove(touringPlayer.getPlayer().getUniqueId(), touringPlayer);
                }
            }
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabledPlugin = event.getPlugin();
        if (disabledPlugin == ServerTours.getInstance()) {
            return;
        }
        synchronized (this) {
            this.trackFactories.values().removeIf(registration -> registration.owner() == disabledPlugin);
        }
        for (CraftTouringPlayer touringPlayer : new ArrayList<>(this.lifecycleSessions.values())) {
            if (touringPlayer.usesTrackOwner(disabledPlugin)) {
                touringPlayer.exit(RoutePlaybackEndEvent.EndReason.ERROR);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.getAction() != Action.LEFT_CLICK_AIR && playerInteractEvent.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        CraftTouringPlayer craftTouringPlayer = this.touringPlayers.get(playerInteractEvent.getPlayer().getUniqueId());
        if (craftTouringPlayer == null || !craftTouringPlayer.isWaitingForConfirmation() || craftTouringPlayer.getCurrentPoint().getConfirmMode() != ConfirmMode.MOUSE) {
            return;
        }
        craftTouringPlayer.onConfirm();
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent playerSwapHandItemsEvent) {
        CraftTouringPlayer craftTouringPlayer = this.touringPlayers.get(playerSwapHandItemsEvent.getPlayer().getUniqueId());
        if (craftTouringPlayer == null || !craftTouringPlayer.isWaitingForConfirmation() || craftTouringPlayer.getCurrentPoint().getConfirmMode() != ConfirmMode.KEYBOARD) {
            return;
        }
        playerSwapHandItemsEvent.setCancelled(true);
        craftTouringPlayer.onConfirm();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        CraftTouringPlayer touringPlayer = this.lifecycleSessions.get(playerQuitEvent.getPlayer().getUniqueId());
        if (touringPlayer != null) {
            touringPlayer.exit(RoutePlaybackEndEvent.EndReason.QUIT);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.getEntityType() != EntityType.PLAYER) {
            return;
        }
        if (this.isTouringPlayer((Player) entityDamageEvent.getEntity())) {
            entityDamageEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (!ServerTours.getInstance().getConfig().getBoolean("playMode.disableCommands")) {
            return;
        }
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (this.isTouringPlayer(player) && !playerCommandPreprocessEvent.getMessage().startsWith("/tour")) {
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage(ServerTours.translate("commands.errors.commandsDisabled"));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent asyncPlayerChatEvent) {
        if (!ServerTours.getInstance().getConfig().getBoolean("playMode.disableChat")) {
            return;
        }
        asyncPlayerChatEvent.getRecipients().removeIf(this::isTouringPlayer);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent entityTargetEvent) {
        Entity target = entityTargetEvent.getTarget();
        if (target instanceof Player player) {
            if (this.isTouringPlayer(player)) {
                entityTargetEvent.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeGameMode(PlayerGameModeChangeEvent playerGameModeChangeEvent) {
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(playerGameModeChangeEvent.getPlayer());
        if (touringPlayer != null && touringPlayer.isGamemodeLocked()) {
            playerGameModeChangeEvent.setCancelled(true);
            ServerTours.getInstance().getLogger().warning("Cannot change player gamemode while viewing a tour");
        }
    }

    @EventHandler
    public void onPlayerDismount(EntityDismountEvent entityDismountEvent) {
        Entity entity = entityDismountEvent.getEntity();
        if (entity instanceof Player player) {
            this.handlePlayerExit(player, entityDismountEvent);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent playerToggleSneakEvent) {
        this.handlePlayerExit(playerToggleSneakEvent.getPlayer(), playerToggleSneakEvent);
    }

    private void handlePlayerExit(Player player, Cancellable cancellable) {
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(player);
        if (touringPlayer == null || !touringPlayer.isActive()) {
            return;
        }
        if (!touringPlayer.canExit()) {
            cancellable.setCancelled(true);
            // With useSpectator enabled, Shift makes the vanilla client reset
            // its camera target locally before Bukkit can reject the exit.
            // Cancelling the event therefore also has to re-send the target.
            touringPlayer.reassertCamera();
            return;
        }
        touringPlayer.exit(RoutePlaybackEndEvent.EndReason.EXITED);
        CraftTouringPlayer current = this.getTouringPlayer(player);
        if (current != null && current.isActive()) {
            cancellable.setCancelled(true);
            if (current == touringPlayer) {
                current.reassertCamera();
            }
        }
    }

    private record PendingStart(Player player, CraftRoute route) {
    }

    private record PendingVisibilityRestore(int entityId, String playerName, long generation, BukkitTask task) {
    }
}
