package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.event.RoutePlaybackPointEvent;
import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.PlaybackFrame;
import com.melluh.servertours.api.playback.PlaybackState;
import com.melluh.servertours.api.playback.track.EventTrackRuntime;
import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.api.playback.track.TimelineEvent;
import com.melluh.servertours.api.playback.track.TrackRuntime;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.hook.HookHandler;
import com.melluh.servertours.hook.VentureChatHook;
import com.melluh.servertours.playback.camera.MovementHandler;
import com.melluh.servertours.playback.camera.RouteCameraTrackRuntime;
import com.melluh.servertours.playback.event.PlaybackEventQueue;
import com.melluh.servertours.playback.event.ScheduledPlaybackEvent;
import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.playback.timeline.RouteTimeline;
import com.melluh.servertours.playback.timeline.SceneClock;
import com.melluh.servertours.playback.track.CraftTrackContext;
import com.melluh.servertours.playback.track.ManagedTrackRuntime;
import com.melluh.servertours.playback.track.TrackFactoryRegistration;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import com.melluh.servertours.util.math.EasingFunction;
import com.melluh.servertours.util.math.SineEasingFunction;
import com.melluh.servertours.util.protocol.PacketUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/** Compatibility facade around the absolute-time playback session. */
public class CraftTouringPlayer implements TouringPlayer {
    private static final int ACTION_BAR_INITIAL_DELAY = 5;
    private static final int ACTION_BAR_PERIOD = 10;
    private static final int BUILTIN_TRACK_PRIORITY = 0;
    private static final long BUILTIN_TRACK_ORDER = -1L;

    private final Player player;
    private final PlayerRestoreWrapper restoreWrapper;
    private final MovementHandler movementHandler;
    private final CraftRoute route;
    private final EasingFunction easingFunction;
    private final CraftPlaybackManager playbackManager;
    private final boolean lifecycleManaged;
    private final long generation;
    private final List<TrackFactoryRegistration> factoryRegistrations;
    private final RouteTimeline routeTimeline;
    private final SceneClock sceneClock;
    private final List<ManagedTrackRuntime> tracks = new ArrayList<>();

    private CraftTrackContext trackContext;
    private RouteCameraTrackRuntime cameraTrack;
    private PlaybackEventQueue eventQueue;
    private PlaybackState playbackState = PlaybackState.CREATED;
    private PauseReason pauseReason;
    private CraftRoutePoint currentPoint;
    private int currentPointIndex;
    private long currentFrame;
    private long durationFrames;
    private int actionBarTimeLeft;
    private boolean waitingForConfirmation;
    private boolean manualConfirmation;
    private boolean isGamemodeLocked;
    private boolean progressBarEnabled;
    private boolean actionBarEnabled;
    private boolean canExit;
    private boolean exitByMoving;
    private boolean stopInProgress;
    private boolean cleanupStarted;
    private boolean cleanupFinished;
    private boolean previewApplied;
    private boolean chatDisabled;
    private boolean invisibilityApplied;
    private boolean pointLifecycleEntered;
    private long mutationVersion;
    private int callbackDepth;
    private RoutePlaybackEndEvent.EndReason deferredEndReason;
    private RoutePlaybackEndEvent.EndReason pendingForcedReason;
    private DeferredPlaybackControl deferredPlaybackControl;
    private DeferredConfirmationControl deferredConfirmationControl;
    private CraftRoutePoint deferredSeekPoint;
    private boolean drainingDeferredRequests;
    private int deferredDrainSuppression;
    private Long dispatchFrame;
    private int seekCameraPointIndex = -1;
    private long seekCameraStartFrame = -1L;
    private Location exitLocation;

    public CraftTouringPlayer(Player player, CraftRoute route, MovementHandler movementHandler) {
        this(player, route, movementHandler, ServerTours.getInstance().getPlaybackManager(),
                System.nanoTime(), List.of(), NanoClock.system(), false);
    }

    CraftTouringPlayer(Player player, CraftRoute route, MovementHandler movementHandler,
                       CraftPlaybackManager playbackManager, long generation,
                       List<TrackFactoryRegistration> factoryRegistrations, NanoClock nanoClock) {
        this(player, route, movementHandler, playbackManager, generation, factoryRegistrations, nanoClock, true);
    }

    private CraftTouringPlayer(Player player, CraftRoute route, MovementHandler movementHandler,
                               CraftPlaybackManager playbackManager, long generation,
                               List<TrackFactoryRegistration> factoryRegistrations, NanoClock nanoClock,
                               boolean lifecycleManaged) {
        this.player = Objects.requireNonNull(player, "player may not be null");
        this.route = Objects.requireNonNull(route, "route may not be null");
        this.movementHandler = Objects.requireNonNull(movementHandler, "movementHandler may not be null");
        this.playbackManager = Objects.requireNonNull(playbackManager, "playbackManager may not be null");
        this.lifecycleManaged = lifecycleManaged;
        this.factoryRegistrations = List.copyOf(factoryRegistrations);
        this.generation = generation;
        this.easingFunction = new SineEasingFunction();
        this.routeTimeline = new RouteTimeline(route);
        this.sceneClock = new SceneClock(Objects.requireNonNull(nanoClock, "nanoClock may not be null"));
        this.restoreWrapper = new PlayerRestoreWrapper(player);
        this.currentPoint = Objects.requireNonNull(route.getPoint(0), "route must contain at least one point");
        this.currentPointIndex = 0;
        this.durationFrames = this.routeTimeline.cameraDuration();
        this.progressBarEnabled = ServerTours.getInstance().getConfig().getBoolean("playMode.xpBarProgress");
        this.actionBarEnabled = ServerTours.getInstance().getConfig().getBoolean("playMode.actionBarEnabled");
        this.canExit = ServerTours.getInstance().getConfig().getBoolean("playMode.allowExit");
    }

    /** Applies player and track resources without starting scene time. */
    public void initialize() {
        if (this.playbackState != PlaybackState.CREATED) {
            throw new IllegalStateException("playback session has already been initialized");
        }
        this.playbackState = PlaybackState.STARTING;
        this.exitLocation = this.player.getLocation().clone();

        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(this.player);
        if (editingPlayer != null) {
            this.previewApplied = true;
            if (!this.runStartingStep(() -> editingPlayer.setPreviewing(true))) {
                return;
            }
        }

        CraftRoutePoint firstPoint = this.routeTimeline.point(0);
        if (this.player.getWorld() != firstPoint.getLocation().getWorld() && !this.route.isUsePlayerWorld()) {
            if (!this.runStartingStep(() -> this.teleportOrThrow(firstPoint.getLocation(), "initial route teleport"))) {
                return;
            }
        }

        if (!this.runStartingStep(() -> this.restoreWrapper.setGameMode(this.getPlaybackGameMode()))
                || !this.runStartingStep(this.restoreWrapper::clearInventory)
                || !this.runStartingStep(() -> this.restoreWrapper.setLevel(0))
                || !this.runStartingStep(() -> this.restoreWrapper.setExperience(0.0f))
                || !this.runStartingStep(this.restoreWrapper::setMaxHealth)
                || !this.runStartingStep(() -> this.restoreWrapper.setCollidable(false))) {
            return;
        }
        this.isGamemodeLocked = true;

        this.invisibilityApplied = true;
        if (!this.runStartingStep(() -> PacketUtil.setInvisible(this.player.getEntityId(), true))) {
            return;
        }
        if (ServerTours.getInstance().getConfig().getBoolean("playMode.disableChat")) {
            if (!this.runStartingStep(() -> HookHandler.get(VentureChatHook.class).ifPresent(hook -> {
                this.chatDisabled = true;
                hook.disableBungeeChat(this.player.getUniqueId());
            }))) {
                return;
            }
        }

        this.buildTracksAndTimeline();
        if (!this.canContinueStarting()) {
            return;
        }
        for (ManagedTrackRuntime track : this.tracks) {
            try {
                this.invokeExternal(track::setup);
            } catch (Exception exception) {
                throw new IllegalStateException("failed to setup playback track " + track.key(), exception);
            }
            if (!this.canContinueStarting()) {
                return;
            }
        }
        this.actionBarTimeLeft = ACTION_BAR_INITIAL_DELAY;
    }

    void activate() {
        if (this.playbackState != PlaybackState.STARTING) {
            throw new IllegalStateException("playback session is not ready to activate");
        }
        this.currentFrame = 0L;
        this.sceneClock.startAt(0L);
        this.playbackState = PlaybackState.RUNNING;
    }

    void beginPlayback() {
        if (this.playbackState == PlaybackState.RUNNING) {
            this.processTargetFrame(0L);
        }
    }

    void abortStart(Throwable cause) {
        if (this.cleanupFinished) {
            return;
        }
        this.logFailure("Playback startup failed", cause);
        this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
    }

    private void buildTracksAndTimeline() {
        this.trackContext = new CraftTrackContext(this, this.routeTimeline.cameraDuration());
        this.cameraTrack = new RouteCameraTrackRuntime(this, this.movementHandler, this.routeTimeline, this.easingFunction);
        TrackFactoryRegistration cameraRegistration = new TrackFactoryRegistration(
                ServerTours.getInstance(), new NamespacedKey(ServerTours.getInstance(), "route-camera"),
                BUILTIN_TRACK_PRIORITY, BUILTIN_TRACK_ORDER, ignored -> Optional.empty());
        this.tracks.add(new ManagedTrackRuntime(cameraRegistration, this.cameraTrack, this.trackContext,
                this.cameraTrack.getEndFrame()));

        for (TrackFactoryRegistration registration : this.factoryRegistrations) {
            if (!this.canContinueStarting()) {
                return;
            }
            if (!registration.owner().isEnabled()) {
                throw new IllegalStateException("track factory owner "
                        + registration.owner().getName() + " was disabled before session startup: "
                        + registration.key());
            }
            Optional<TrackRuntime> optionalRuntime = Objects.requireNonNull(this.invokeExternal(
                    () -> registration.factory().create(this.trackContext)),
                    "track factory " + registration.key() + " returned a null Optional");
            if (!this.canContinueStarting()) {
                return;
            }
            if (optionalRuntime.isEmpty()) {
                continue;
            }
            TrackRuntime runtime = Objects.requireNonNull(optionalRuntime.get(), "track runtime may not be null");
            long endFrame = this.invokeExternal(runtime::getEndFrame);
            if (!this.canContinueStarting()) {
                return;
            }
            if (endFrame < 0L) {
                throw new IllegalArgumentException("track " + registration.key() + " has a negative end frame");
            }
            this.tracks.add(new ManagedTrackRuntime(registration, runtime, this.trackContext, endFrame));
        }

        this.tracks.sort(Comparator.comparingInt(ManagedTrackRuntime::priority)
                .thenComparingLong(ManagedTrackRuntime::registrationOrder));
        for (ManagedTrackRuntime track : this.tracks) {
            this.durationFrames = Math.max(this.durationFrames, track.endFrame());
        }

        List<ScheduledPlaybackEvent> events = new ArrayList<>();
        this.addRouteEvents(events);
        for (ManagedTrackRuntime track : this.tracks) {
            EventTrackRuntime eventRuntime = track.eventRuntime();
            if (eventRuntime == null) {
                continue;
            }
            List<TimelineEvent> timelineEvents = List.copyOf(Objects.requireNonNull(this.invokeExternal(
                    eventRuntime::events), "event track " + track.key() + " returned null"));
            if (!this.canContinueStarting()) {
                return;
            }
            Set<String> localIds = new HashSet<>();
            for (int eventIndex = 0; eventIndex < timelineEvents.size(); eventIndex++) {
                TimelineEvent timelineEvent = Objects.requireNonNull(timelineEvents.get(eventIndex),
                        "event track " + track.key() + " contains null");
                if (!localIds.add(timelineEvent.id())) {
                    throw new IllegalArgumentException("duplicate event id '" + timelineEvent.id()
                            + "' in track " + track.key());
                }
                if (timelineEvent.frame() > track.endFrame()) {
                    throw new IllegalArgumentException("event '" + timelineEvent.id() + "' exceeds track end frame");
                }
                events.add(new ScheduledPlaybackEvent(
                        timelineEvent.frame(), track.priority(), track.registrationOrder(), eventIndex,
                        track.key() + "/" + timelineEvent.id(), ScheduledPlaybackEvent.Barrier.NONE,
                        frame -> timelineEvent.action().execute(this.trackContext, frame)));
            }
        }
        this.eventQueue = new PlaybackEventQueue(events);
    }

    private boolean canContinueStarting() {
        return this.playbackState == PlaybackState.STARTING
                && !this.playbackManager.hasPendingStart(this)
                && (!this.lifecycleManaged || this.playbackManager.ownsLifecycle(this));
    }

    private boolean runStartingStep(ExternalAction action) {
        this.invokeExternal(action);
        return this.canContinueStarting();
    }

    private void addRouteEvents(List<ScheduledPlaybackEvent> events) {
        int eventOrder = 0;
        events.add(this.routeEvent(0L, eventOrder++, "route/enter/0",
                ScheduledPlaybackEvent.Barrier.NONE, ignored -> this.enterInitialPoint()));
        CraftRoutePoint first = this.routeTimeline.point(0);
        if (first.isConfirmRequired() && first.getType().isConfirmUponEnter()) {
            events.add(this.confirmationEvent(0L, eventOrder++, 0));
        }

        for (int pointIndex = 0; pointIndex < this.routeTimeline.pointCount(); pointIndex++) {
            CraftRoutePoint point = this.routeTimeline.point(pointIndex);
            long boundaryFrame = this.routeTimeline.pointEnd(pointIndex);
            if (point.isConfirmRequired() && !point.getType().isConfirmUponEnter()) {
                events.add(this.confirmationEvent(boundaryFrame, eventOrder++, pointIndex));
            }
            int nextPointIndex = pointIndex + 1;
            if (nextPointIndex >= this.routeTimeline.pointCount()) {
                continue;
            }
            int targetIndex = nextPointIndex;
            events.add(this.routeEvent(boundaryFrame, eventOrder++,
                    "route/transition/" + pointIndex + "-" + targetIndex,
                    ScheduledPlaybackEvent.Barrier.NONE, ignored -> this.transitionToPoint(targetIndex)));
            CraftRoutePoint nextPoint = this.routeTimeline.point(targetIndex);
            if (nextPoint.isConfirmRequired() && nextPoint.getType().isConfirmUponEnter()) {
                events.add(this.confirmationEvent(boundaryFrame, eventOrder++, targetIndex));
            }
        }
    }

    private ScheduledPlaybackEvent confirmationEvent(long frame, int eventOrder, int pointIndex) {
        return this.routeEvent(frame, eventOrder, "route/confirm/" + pointIndex + "/" + eventOrder,
                ScheduledPlaybackEvent.Barrier.CONFIRMATION,
                ignored -> this.pauseForConfirmation(this.routeTimeline.point(pointIndex), false));
    }

    private ScheduledPlaybackEvent routeEvent(long frame, int eventOrder, String id,
                                               ScheduledPlaybackEvent.Barrier barrier,
                                               ScheduledPlaybackEvent.Action action) {
        return new ScheduledPlaybackEvent(frame, BUILTIN_TRACK_PRIORITY, BUILTIN_TRACK_ORDER,
                eventOrder, id, barrier, action);
    }

    private GameMode getPlaybackGameMode() {
        return !ServerTours.getInstance().isBedrockPlayer(this.player)
                && ServerTours.getInstance().getConfig().getBoolean("playMode.useSpectator")
                ? GameMode.SPECTATOR : GameMode.ADVENTURE;
    }

    public void tick() {
        if (this.playbackState == PlaybackState.STOPPED || this.playbackState == PlaybackState.STOPPING) {
            return;
        }
        this.tickHousekeeping();
        if (this.playbackState == PlaybackState.STOPPED || this.playbackState == PlaybackState.STOPPING) {
            return;
        }
        if (this.playbackState != PlaybackState.RUNNING) {
            this.updateProgressBar();
            return;
        }
        this.processTargetFrame(this.sceneClock.currentTarget(this.durationFrames));
    }

    private void tickHousekeeping() {
        --this.actionBarTimeLeft;
        if (this.actionBarTimeLeft <= 0) {
            this.sendActionbar();
            this.actionBarTimeLeft = ACTION_BAR_PERIOD;
        }
    }

    private void updateProgressBar() {
        if (this.progressBarEnabled && this.player.getGameMode() != GameMode.SPECTATOR) {
            this.restoreWrapper.setExperience((float) Math.min(this.getRouteProgress(), 1.0));
        }
    }

    private void processTargetFrame(long candidateTarget) {
        this.processTargetFrame(candidateTarget, null);
    }

    private void processTargetFrame(long candidateTarget, @Nullable StateRebaseReason rebaseReason) {
        if (this.playbackState != PlaybackState.RUNNING) {
            return;
        }
        long effectiveTarget = this.eventQueue.clampToBarrier(Math.max(this.currentFrame, candidateTarget));
        this.currentFrame = effectiveTarget;
        PlaybackFrame targetFrame = this.frame(effectiveTarget);
        long renderVersion = this.mutationVersion;
        this.renderStateTracks(targetFrame, null, rebaseReason);
        if (this.playbackState != PlaybackState.RUNNING || renderVersion != this.mutationVersion) {
            return;
        }
        this.updateProgressBar();

        ScheduledPlaybackEvent due;
        while ((due = this.eventQueue.peekDue(effectiveTarget)) != null) {
            ScheduledPlaybackEvent event = this.eventQueue.consume();
            long eventVersion = this.mutationVersion;
            Long previousDispatchFrame = this.dispatchFrame;
            this.dispatchFrame = event.frame();
            try {
                this.invokeExternal(() -> event.execute(this.frame(event.frame())));
            } catch (Exception exception) {
                throw new IllegalStateException("timeline event failed: " + event.id(), exception);
            } finally {
                this.dispatchFrame = previousDispatchFrame;
            }
            if (this.playbackState != PlaybackState.RUNNING || eventVersion != this.mutationVersion) {
                return;
            }
        }

        if (this.currentFrame >= this.durationFrames && this.playbackState == PlaybackState.RUNNING) {
            this.exit(RoutePlaybackEndEvent.EndReason.FINISHED);
        }
    }

    private void renderStateTracks(PlaybackFrame targetFrame, @Nullable Integer explicitPointIndex,
                                   @Nullable StateRebaseReason rebaseReason) {
        PlaybackState expectedState = this.playbackState;
        long expectedVersion = this.mutationVersion;
        for (ManagedTrackRuntime track : this.tracks) {
            if (track.stateRuntime() == null) {
                continue;
            }
            long trackFrameIndex = Math.min(targetFrame.index(), track.endFrame());
            PlaybackFrame trackFrame = this.frame(trackFrameIndex);
            if (explicitPointIndex != null && track.runtime() == this.cameraTrack) {
                this.invokeExternal(() -> track.rebaseState(trackFrame, StateRebaseReason.EXPLICIT_SEEK,
                        () -> this.cameraTrack.rebasePointStart(explicitPointIndex, trackFrame,
                                StateRebaseReason.EXPLICIT_SEEK)));
            } else if (track.runtime() == this.cameraTrack && this.seekCameraPointIndex >= 0
                    && trackFrame.index() == this.seekCameraStartFrame) {
                if (rebaseReason == null) {
                    this.invokeExternal(() -> track.renderState(trackFrame));
                } else {
                    this.invokeExternal(() -> track.rebaseState(trackFrame, rebaseReason,
                            () -> this.cameraTrack.rebasePointStart(this.seekCameraPointIndex, trackFrame,
                                    rebaseReason)));
                }
            } else {
                if (track.runtime() == this.cameraTrack && trackFrame.index() > this.seekCameraStartFrame) {
                    this.seekCameraPointIndex = -1;
                    this.seekCameraStartFrame = -1L;
                }
                if (rebaseReason == null) {
                    this.invokeExternal(() -> track.renderState(trackFrame));
                } else {
                    this.invokeExternal(() -> track.rebaseState(trackFrame, rebaseReason));
                }
            }
            if (this.playbackState != expectedState || this.mutationVersion != expectedVersion) {
                return;
            }
        }
    }

    private PlaybackFrame frame(long frameIndex) {
        long nanos;
        try {
            nanos = Math.multiplyExact(frameIndex, SceneClock.FRAME_NANOS);
        } catch (ArithmeticException ignored) {
            nanos = Long.MAX_VALUE;
        }
        return new PlaybackFrame(frameIndex, nanos, this.durationFrames);
    }

    private void enterInitialPoint() {
        this.currentPointIndex = 0;
        this.currentPoint = this.routeTimeline.point(0);
        this.pointLifecycleEntered = true;
        this.sendPointEntry(this.currentPoint, this.mutationVersion);
    }

    private void transitionToPoint(int pointIndex) {
        long transitionVersion = this.mutationVersion;
        if (this.currentPoint != null) {
            this.executePointCommands(this.currentPoint, RoutePointCommand.CommandTrigger.EXIT);
            if (!this.canContinueCue(transitionVersion, this.currentPoint)) {
                return;
            }
        }
        this.currentPointIndex = pointIndex;
        this.currentPoint = this.routeTimeline.point(pointIndex);
        this.pointLifecycleEntered = true;
        this.sendPointEntry(this.currentPoint, transitionVersion);
    }

    private void sendPointEntry(CraftRoutePoint point, long entryVersion) {
        if (point.getTitle() != null) {
            String[] split = ChatColor.translateAlternateColorCodes('&',
                    ServerTours.placeholders(this.player, point.getTitle())).split("\\\\n");
            this.player.sendTitle(split.length > 0 ? split[0] : "", split.length > 1 ? split[1] : "",
                    (int) Math.floor(point.getTitleFadeInTime() * 20.0f),
                    (int) Math.floor(point.getTitleStayTime() * 20.0f),
                    (int) Math.floor(point.getTitleFadeOutTime() * 20.0f));
        }
        if (point.getDescription() != null) {
            boolean dashes = ServerTours.getInstance().getConfig().getBoolean("playMode.sendDescriptionDashes");
            this.player.sendMessage(dashes ? ChatColor.AQUA + "------------------------" : "");
            Arrays.stream(ChatColor.translateAlternateColorCodes('&',
                            ServerTours.placeholders(this.player, point.getDescription())).split("\\\\n"))
                    .forEach(line -> this.player.sendMessage(ChatColor.BOLD + line));
            this.player.sendMessage(dashes ? ChatColor.AQUA + "------------------------" : "");
        }
        if (!this.canContinueCue(entryVersion, point)) {
            return;
        }
        this.executePointCommands(point, RoutePointCommand.CommandTrigger.ENTER);
        if (!this.canContinueCue(entryVersion, point)) {
            return;
        }
        this.invokeExternal(() -> Bukkit.getPluginManager().callEvent(new RoutePlaybackPointEvent(this, point)));
    }

    private void executePointCommands(CraftRoutePoint point, RoutePointCommand.CommandTrigger trigger) {
        this.invokeExternal(() -> point.executeCommands(this.player, trigger));
    }

    private boolean canContinueCue(long expectedVersion, CraftRoutePoint expectedPoint) {
        return !this.cleanupStarted
                && this.playbackState != PlaybackState.STOPPING
                && this.playbackState != PlaybackState.STOPPED
                && this.mutationVersion == expectedVersion
                && this.currentPoint == expectedPoint;
    }

    @Override
    public void pause() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ServerTours.getInstance(), this::pause);
            return;
        }
        if (this.callbackDepth > 0) {
            if (this.playbackState == PlaybackState.RUNNING) {
                this.deferredPlaybackControl = DeferredPlaybackControl.PAUSE;
            }
            return;
        }
        if (this.playbackState != PlaybackState.RUNNING) {
            return;
        }
        try {
            this.pauseAtCurrentFrame(PauseReason.MANUAL);
        } catch (Throwable throwable) {
            this.logFailure("Track pause callback failed", throwable);
            this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
        }
    }

    @Override
    public void resume() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ServerTours.getInstance(), this::resume);
            return;
        }
        if (this.callbackDepth > 0) {
            if (this.playbackState == PlaybackState.PAUSED && !this.waitingForConfirmation
                    && this.pauseReason != PauseReason.CONFIRMATION
                    && !(this.pauseReason == PauseReason.END_CANCELLED
                    && this.currentFrame >= this.durationFrames)) {
                this.deferredPlaybackControl = DeferredPlaybackControl.RESUME;
            }
            return;
        }
        if (this.playbackState != PlaybackState.PAUSED || this.waitingForConfirmation
                || this.pauseReason == PauseReason.CONFIRMATION) {
            return;
        }
        if (this.pauseReason == PauseReason.END_CANCELLED && this.currentFrame >= this.durationFrames) {
            return;
        }
        try {
            long resumeVersion = this.resumeAtCurrentFrame();
            if (this.playbackState == PlaybackState.RUNNING && this.mutationVersion == resumeVersion) {
                this.processTargetFrame(this.currentFrame, StateRebaseReason.RESUME_RECOVERY);
            }
        } catch (Throwable throwable) {
            this.logFailure("Track resume callback failed", throwable);
            this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
        }
    }

    private void pauseAtCurrentFrame(PauseReason reason) throws Exception {
        this.sceneClock.pauseAt(this.currentFrame);
        this.playbackState = PlaybackState.PAUSED;
        this.pauseReason = reason;
        ++this.mutationVersion;
        ++this.deferredDrainSuppression;
        try {
            for (ManagedTrackRuntime track : this.tracks) {
                this.invokeExternal(() -> track.pause(reason));
                if (this.playbackState != PlaybackState.PAUSED) {
                    return;
                }
            }
        } finally {
            --this.deferredDrainSuppression;
            this.drainDeferredRequests();
        }
    }

    private long resumeAtCurrentFrame() throws Exception {
        this.sceneClock.resume();
        this.playbackState = PlaybackState.RUNNING;
        this.pauseReason = null;
        ++this.mutationVersion;
        long resumeVersion = this.mutationVersion;
        ++this.deferredDrainSuppression;
        try {
            for (ManagedTrackRuntime track : this.tracks) {
                this.invokeExternal(track::resume);
                if (this.playbackState != PlaybackState.RUNNING) {
                    return resumeVersion;
                }
            }
        } finally {
            --this.deferredDrainSuppression;
            this.drainDeferredRequests();
        }
        return resumeVersion;
    }

    private void pauseForConfirmation(CraftRoutePoint point, boolean manual) throws Exception {
        if (this.playbackState != PlaybackState.RUNNING) {
            return;
        }
        this.waitingForConfirmation = true;
        this.manualConfirmation = manual;
        this.pauseAtCurrentFrame(PauseReason.CONFIRMATION);
        if (this.playbackState == PlaybackState.PAUSED && this.waitingForConfirmation
                && (this.pauseReason == PauseReason.CONFIRMATION
                || this.pauseReason == PauseReason.END_CANCELLED)) {
            this.askConfirmation(point);
        }
    }

    private void askConfirmation(CraftRoutePoint point) {
        ConfirmMode confirmMode = point.getConfirmMode();
        if (confirmMode == ConfirmMode.MOUSE) {
            this.player.sendMessage(ServerTours.translate("continueConfirm.mouse"));
        } else if (confirmMode == ConfirmMode.CHAT) {
            this.player.sendMessage("");
            this.player.spigot().sendMessage(new ComponentBuilder(ServerTours.translate("continueConfirm.button.text"))
                    .color(ChatColor.GREEN)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder(ServerTours.translate("continueConfirm.button.instruction")).create()))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tour continue")).create());
            this.player.sendMessage("");
        } else if (confirmMode == ConfirmMode.KEYBOARD) {
            this.player.sendMessage(ServerTours.translate("continueConfirm.keyboard"));
        }
    }

    public void onConfirm() {
        if (this.callbackDepth > 0) {
            if (this.waitingForConfirmation && this.playbackState == PlaybackState.PAUSED) {
                this.deferredConfirmationControl = DeferredConfirmationControl.ACCEPT;
                ++this.mutationVersion;
            }
            return;
        }
        if (!this.waitingForConfirmation || this.playbackState != PlaybackState.PAUSED
                || (this.pauseReason != PauseReason.CONFIRMATION
                && this.pauseReason != PauseReason.END_CANCELLED)) {
            return;
        }
        boolean advanceManually = this.manualConfirmation && !this.currentPoint.getType().isConfirmUponEnter();
        this.waitingForConfirmation = false;
        this.manualConfirmation = false;
        this.clearChat();
        try {
            long resumeVersion = this.resumeAtCurrentFrame();
            if (advanceManually) {
                CraftRoutePoint nextPoint = this.currentPoint.getNextPoint();
                if (nextPoint == null) {
                    this.exit(RoutePlaybackEndEvent.EndReason.FINISHED);
                } else {
                    this.setCurrentPoint(nextPoint);
                }
                return;
            }
            if (this.playbackState == PlaybackState.RUNNING && this.mutationVersion == resumeVersion) {
                this.processTargetFrame(this.currentFrame, StateRebaseReason.RESUME_RECOVERY);
            }
        } catch (Throwable throwable) {
            this.logFailure("Failed to resume from confirmation", throwable);
            this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
        }
    }

    @Override
    public void exit() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ServerTours.getInstance(), () -> this.exit());
            return;
        }
        this.exit(RoutePlaybackEndEvent.EndReason.API);
    }

    public void exit(RoutePlaybackEndEvent.EndReason requestedReason) {
        Objects.requireNonNull(requestedReason, "end reason may not be null");
        if (this.cleanupFinished || this.playbackState == PlaybackState.STOPPED) {
            return;
        }
        if (this.cleanupStarted || this.stopInProgress) {
            this.recordForcedReason(requestedReason);
            return;
        }
        if (this.callbackDepth > 0) {
            this.deferredEndReason = selectEndReason(this.deferredEndReason, requestedReason);
            ++this.mutationVersion;
            return;
        }
        if (this.deferredEndReason != null) {
            requestedReason = selectEndReason(requestedReason, this.deferredEndReason);
            this.deferredEndReason = null;
        }

        this.stopInProgress = true;
        this.playbackState = PlaybackState.STOPPING;
        ++this.mutationVersion;
        RoutePlaybackEndEvent.EndReason reason = requestedReason;
        RoutePlaybackEndEvent endEvent = new RoutePlaybackEndEvent(this, reason);
        try {
            Bukkit.getPluginManager().callEvent(endEvent);
        } catch (Throwable throwable) {
            this.logFailure("Failed to publish playback end event", throwable);
            reason = RoutePlaybackEndEvent.EndReason.ERROR;
        }
        reason = this.consumePendingForcedReason(reason);

        if (endEvent.isCancelled() && !isForced(reason)) {
            try {
                if (this.sceneClock.isStarted() && !this.sceneClock.isPaused()) {
                    this.sceneClock.pauseAt(this.currentFrame);
                }
                this.playbackState = PlaybackState.PAUSED;
                this.pauseReason = PauseReason.END_CANCELLED;
                for (ManagedTrackRuntime track : this.tracks) {
                    this.invokeExternal(() -> track.pause(PauseReason.END_CANCELLED));
                }
            } catch (Throwable throwable) {
                this.logFailure("Failed to pause a cancelled end request", throwable);
                this.recordForcedReason(RoutePlaybackEndEvent.EndReason.ERROR);
            }
            reason = this.consumePendingForcedReason(reason);
            if (isForced(reason)) {
                this.cleanup(reason);
                return;
            }
            this.stopInProgress = false;
            this.playbackManager.processPendingAfterCancelledStop(this);
            return;
        }
        this.cleanup(reason);
    }

    private static boolean isForced(RoutePlaybackEndEvent.EndReason reason) {
        return reason == RoutePlaybackEndEvent.EndReason.QUIT
                || reason == RoutePlaybackEndEvent.EndReason.PLUGIN_DISABLED
                || reason == RoutePlaybackEndEvent.EndReason.ERROR
                || reason == RoutePlaybackEndEvent.EndReason.REPLACED;
    }

    private void cleanup(RoutePlaybackEndEvent.EndReason reason) {
        if (this.cleanupFinished) {
            return;
        }
        if (this.cleanupStarted) {
            this.recordForcedReason(reason);
            return;
        }
        this.cleanupStarted = true;
        this.stopInProgress = true;
        this.playbackState = PlaybackState.STOPPING;

        List<CleanupFailure> failures = new ArrayList<>();
        if (this.previewApplied) {
            this.cleanupSafely(failures, "editing preview", () -> {
                EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(this.player);
                if (editingPlayer != null) {
                    editingPlayer.setPreviewing(false);
                }
            });
        }
        this.cleanupSafely(failures, "action bar", this::clearActionBar);
        this.isGamemodeLocked = false;

        List<ManagedTrackRuntime> reversedTracks = new ArrayList<>(this.tracks);
        Collections.reverse(reversedTracks);
        for (ManagedTrackRuntime track : reversedTracks) {
            this.cleanupSafely(failures, "track teardown " + track.key(),
                    () -> this.invokeExternal(track::teardown));
        }

        if (this.exitLocation != null) {
            this.cleanupSafely(failures, "exit teleport",
                    () -> this.teleportOrThrow(this.exitLocation, "exit teleport"));
        }
        this.cleanupSafely(failures, "player state restore", this.restoreWrapper::restore);
        for (PlayerRestoreWrapper.RestoreFailure restoreFailure : this.restoreWrapper.getRestoreFailures()) {
            failures.add(new CleanupFailure("restore " + restoreFailure.getOperation(), restoreFailure.getCause()));
        }
        if (this.chatDisabled) {
            this.cleanupSafely(failures, "chat restore", () -> HookHandler.get(VentureChatHook.class)
                    .ifPresent(hook -> hook.restoreBungeeChat(this.player.getUniqueId())));
        }
        if (this.pointLifecycleEntered && this.currentPoint != null) {
            this.cleanupSafely(failures, "QUIT commands",
                    () -> this.executePointCommands(this.currentPoint, RoutePointCommand.CommandTrigger.QUIT));
        }
        RoutePlaybackEndEvent.EndReason terminalReason = this.consumePendingForcedReason(reason);
        if (this.invisibilityApplied) {
            this.restoreVisibility(terminalReason, failures);
        }

        this.waitingForConfirmation = false;
        this.pauseReason = null;
        this.playbackState = PlaybackState.STOPPED;
        this.stopInProgress = false;
        this.cleanupFinished = true;
        for (CleanupFailure failure : failures) {
            this.logFailure("Cleanup step failed: " + failure.operation(), failure.cause());
        }
        this.playbackManager.onSessionStopped(this, terminalReason);
    }

    private void restoreVisibility(RoutePlaybackEndEvent.EndReason reason, List<CleanupFailure> failures) {
        if (reason == RoutePlaybackEndEvent.EndReason.PLUGIN_DISABLED) {
            this.cleanupSafely(failures, "visibility restore",
                    () -> PacketUtil.setInvisible(this.player.getEntityId(), false));
            return;
        }
        this.cleanupSafely(failures, "visibility restore scheduling",
                () -> this.playbackManager.scheduleVisibilityRestore(this.player, this.generation));
    }

    private void recordForcedReason(RoutePlaybackEndEvent.EndReason reason) {
        if (isForced(reason)) {
            this.pendingForcedReason = selectEndReason(this.pendingForcedReason, reason);
        }
    }

    private RoutePlaybackEndEvent.EndReason consumePendingForcedReason(
            RoutePlaybackEndEvent.EndReason fallback) {
        RoutePlaybackEndEvent.EndReason result = selectEndReason(fallback, this.pendingForcedReason);
        this.pendingForcedReason = null;
        return result;
    }

    private static RoutePlaybackEndEvent.EndReason selectEndReason(
            @Nullable RoutePlaybackEndEvent.EndReason current,
            @Nullable RoutePlaybackEndEvent.EndReason incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        int currentPriority = forcedPriority(current);
        int incomingPriority = forcedPriority(incoming);
        if (incomingPriority > currentPriority) {
            return incoming;
        }
        return current;
    }

    private static int forcedPriority(RoutePlaybackEndEvent.EndReason reason) {
        return switch (reason) {
            case REPLACED -> 1;
            case ERROR -> 2;
            case QUIT -> 3;
            case PLUGIN_DISABLED -> 4;
            default -> 0;
        };
    }

    private void cleanupSafely(List<CleanupFailure> failures, String operation, CleanupAction action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failures.add(new CleanupFailure(operation, throwable));
        }
    }

    private void teleportOrThrow(Location location, String operation) {
        if (!this.player.teleport(location)) {
            throw new IllegalStateException(operation + " was cancelled or rejected");
        }
    }

    private void sendActionbar() {
        if (!this.actionBarEnabled) {
            return;
        }
        if (ServerTours.getInstance().getConfig().getBoolean("playMode.allowExit")) {
            this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new ComponentBuilder(ServerTours.translate("actionBar.watching"))
                            .color(ChatColor.AQUA).bold(true)
                            .append(new ComponentBuilder(ServerTours.translate("actionBar.shiftToExit"))
                                    .color(ChatColor.GRAY).bold(false).create()).create());
        } else {
            this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new ComponentBuilder(ServerTours.translate("actionBar.watching"))
                            .color(ChatColor.AQUA).bold(true).create());
        }
    }

    private void clearActionBar() {
        this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent());
    }

    private void clearChat() {
        for (int index = 0; index < 20; index++) {
            this.player.sendMessage("");
        }
    }

    @Override
    public float getPointProgress() {
        long duration = this.routeTimeline.pointDuration(this.currentPointIndex);
        if (duration == 0L) {
            return 1.0f;
        }
        long elapsed = Math.max(0L, Math.min(duration,
                this.reportedFrame() - this.routeTimeline.pointStart(this.currentPointIndex)));
        return (float) ((double) elapsed / (double) duration);
    }

    @Override
    public float getRouteProgress() {
        long cameraDuration = this.routeTimeline.cameraDuration();
        if (cameraDuration == 0L) {
            return 1.0f;
        }
        return (float) ((double) Math.min(this.reportedFrame(), cameraDuration) / (double) cameraDuration);
    }

    @Override
    public PlaybackState getPlaybackState() {
        return this.playbackState;
    }

    @Override
    public long getCurrentFrame() {
        return this.reportedFrame();
    }

    @Override
    public long getDurationFrames() {
        return this.durationFrames;
    }

    @Override
    public double getSceneProgress() {
        return this.durationFrames == 0L ? 1.0D
                : Math.min(1.0D, (double) this.reportedFrame() / (double) this.durationFrames);
    }

    @Override
    public @Nullable PauseReason getPauseReason() {
        return this.pauseReason;
    }

    @Override
    public boolean isPaused() {
        return this.playbackState == PlaybackState.PAUSED;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public CraftRoute getRoute() {
        return this.route;
    }

    @Override
    public boolean isWaitingForConfirmation() {
        return this.waitingForConfirmation;
    }

    @Override
    public void setWaitingForConfirmation(boolean waiting) {
        if (this.callbackDepth > 0) {
            if ((waiting && !this.waitingForConfirmation && this.playbackState == PlaybackState.RUNNING)
                    || (!waiting && this.waitingForConfirmation)) {
                this.deferredConfirmationControl = waiting
                        ? DeferredConfirmationControl.ENTER : DeferredConfirmationControl.ACCEPT;
                ++this.mutationVersion;
            }
            return;
        }
        if (waiting && !this.waitingForConfirmation && this.playbackState == PlaybackState.RUNNING) {
            try {
                this.pauseForConfirmation(this.currentPoint, true);
            } catch (Exception exception) {
                this.logFailure("Failed to enter confirmation pause", exception);
                this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
            }
        } else if (!waiting && this.waitingForConfirmation) {
            this.onConfirm();
        }
    }

    @Override
    public CraftRoutePoint getCurrentPoint() {
        return this.currentPoint;
    }

    @Override
    public void setCurrentPoint(int pointIndex) {
        CraftRoutePoint point = this.route.getPoint(pointIndex);
        if (point == null) {
            throw new IndexOutOfBoundsException("point index out of range: " + pointIndex);
        }
        this.setCurrentPoint(point);
    }

    @Override
    public void setCurrentPoint(RoutePoint point) {
        Objects.requireNonNull(point, "point may not be null");
        if (point instanceof CraftRoutePoint craftRoutePoint) {
            this.setCurrentPoint(craftRoutePoint);
            return;
        }
        throw new IllegalArgumentException("point must be an instance of CraftRoutePoint");
    }

    public void setCurrentPoint(CraftRoutePoint point) {
        Objects.requireNonNull(point, "point may not be null");
        int pointIndex = this.routeTimeline.indexOf(point);
        if (pointIndex == -1) {
            throw new IllegalArgumentException("point must be in touring player's route");
        }
        if (pointIndex == this.currentPointIndex) {
            return;
        }
        if (this.playbackState != PlaybackState.RUNNING && this.playbackState != PlaybackState.PAUSED) {
            throw new IllegalStateException("route point can only be changed for an active playback session");
        }
        if (this.callbackDepth > 0) {
            this.deferredSeekPoint = point;
            ++this.mutationVersion;
            return;
        }

        boolean paused = this.playbackState == PlaybackState.PAUSED;
        PauseReason previousPauseReason = this.pauseReason;
        if (paused && this.waitingForConfirmation) {
            this.waitingForConfirmation = false;
            this.manualConfirmation = false;
            this.clearChat();
            if (previousPauseReason == PauseReason.CONFIRMATION) {
                previousPauseReason = PauseReason.MANUAL;
            }
        }
        try {
            long seekVersion = this.mutationVersion;
            if (this.pointLifecycleEntered && this.currentPoint != null) {
                CraftRoutePoint outgoingPoint = this.currentPoint;
                this.executePointCommands(outgoingPoint, RoutePointCommand.CommandTrigger.EXIT);
                if (!this.canContinueCue(seekVersion, outgoingPoint)) {
                    return;
                }
            }
            this.currentPointIndex = pointIndex;
            this.currentPoint = point;
            this.pointLifecycleEntered = true;
            this.currentFrame = this.routeTimeline.pointStart(pointIndex);
            String entryEventId = pointIndex == 0
                    ? "route/enter/0"
                    : "route/transition/" + (pointIndex - 1) + "-" + pointIndex;
            this.eventQueue.seekToRouteEntry(this.currentFrame, entryEventId);
            this.sceneClock.startAt(this.currentFrame);
            if (paused) {
                this.sceneClock.pauseAt(this.currentFrame);
            }
            ++this.mutationVersion;
            long entryVersion = this.mutationVersion;
            this.seekCameraPointIndex = pointIndex;
            this.seekCameraStartFrame = this.currentFrame;
            this.renderStateTracks(this.frame(this.currentFrame), pointIndex,
                    StateRebaseReason.EXPLICIT_SEEK);
            if (!this.canContinueCue(entryVersion, point)) {
                return;
            }
            this.sendPointEntry(point, entryVersion);
            if (!this.canContinueCue(entryVersion, point)) {
                return;
            }
            this.playbackState = paused ? PlaybackState.PAUSED : PlaybackState.RUNNING;
            this.pauseReason = paused ? previousPauseReason : null;
            if (!paused) {
                this.processTargetFrame(this.currentFrame);
            } else {
                this.updateProgressBar();
            }
        } catch (Throwable throwable) {
            this.logFailure("Explicit route seek failed", throwable);
            this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
        }
    }

    public PlayerRestoreWrapper getRestoreWrapper() {
        return this.restoreWrapper;
    }

    @Override
    public boolean isProgressBarEnabled() {
        return this.progressBarEnabled;
    }

    @Override
    public void setProgressBarEnabled(boolean progressBarEnabled) {
        this.progressBarEnabled = progressBarEnabled;
        if (!progressBarEnabled) {
            this.restoreWrapper.setExperience(0.0f);
        }
    }

    @Override
    public boolean isActionBarEnabled() {
        return this.actionBarEnabled;
    }

    @Override
    public void setActionBarEnabled(boolean actionBarEnabled) {
        this.actionBarEnabled = actionBarEnabled;
        if (actionBarEnabled) {
            this.sendActionbar();
        } else {
            this.clearActionBar();
        }
    }

    @Override
    public boolean canExit() {
        return this.canExit;
    }

    @Override
    public void setCanExit(boolean canExit) {
        this.canExit = canExit;
    }

    @Override
    public Location getExitLocation() {
        return this.exitLocation == null ? null : this.exitLocation.clone();
    }

    @Override
    public void setExitLocation(Location exitLocation) {
        this.exitLocation = Objects.requireNonNull(exitLocation, "exitLocation may not be null").clone();
    }

    @Override
    public boolean isExitByMoving() {
        return this.exitByMoving;
    }

    @Override
    public void setExitByMoving(boolean exitByMoving) {
        this.exitByMoving = exitByMoving;
    }

    public boolean isGamemodeLocked() {
        return this.isGamemodeLocked;
    }

    boolean isStopInProgress() {
        return this.stopInProgress;
    }

    long getGeneration() {
        return this.generation;
    }

    void inheritInvisibilityLease() {
        this.invisibilityApplied = true;
    }

    boolean isActive() {
        return this.playbackState == PlaybackState.RUNNING || this.playbackState == PlaybackState.PAUSED;
    }

    void reassertCamera() {
        if (!this.isActive() || this.cleanupStarted || this.cleanupFinished) {
            return;
        }
        try {
            this.movementHandler.reassertCamera(this);
        } catch (Throwable throwable) {
            this.logFailure("Failed to reassert playback camera", throwable);
            this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
        }
    }

    boolean usesTrackOwner(Plugin plugin) {
        return this.tracks.stream().anyMatch(track -> track.owner() == plugin)
                || this.factoryRegistrations.stream().anyMatch(registration -> registration.owner() == plugin);
    }

    private long reportedFrame() {
        return this.dispatchFrame != null ? this.dispatchFrame : this.currentFrame;
    }

    private void invokeExternal(ExternalAction action) {
        this.invokeExternal(() -> {
            action.run();
            return null;
        });
    }

    private <T> T invokeExternal(ExternalSupplier<T> supplier) {
        ++this.callbackDepth;
        Throwable failure = null;
        try {
            return supplier.get();
        } catch (RuntimeException | Error throwable) {
            failure = throwable;
            throw throwable;
        } catch (Exception exception) {
            failure = exception;
            throw new IllegalStateException("playback callback failed", exception);
        } finally {
            --this.callbackDepth;
            if (failure != null) {
                this.deferredEndReason = selectEndReason(
                        this.deferredEndReason, RoutePlaybackEndEvent.EndReason.ERROR);
            }
            if (this.callbackDepth == 0 && this.deferredDrainSuppression == 0) {
                this.drainDeferredRequests();
            }
        }
    }

    private void drainDeferredRequests() {
        if (this.callbackDepth != 0 || this.deferredDrainSuppression != 0 || this.drainingDeferredRequests) {
            return;
        }
        this.drainingDeferredRequests = true;
        int transitions = 0;
        try {
            while (!this.cleanupFinished) {
                if (++transitions > 16) {
                    this.deferredPlaybackControl = null;
                    this.deferredEndReason = null;
                    this.exit(RoutePlaybackEndEvent.EndReason.ERROR);
                    return;
                }
                if (this.deferredEndReason != null) {
                    RoutePlaybackEndEvent.EndReason deferred = this.deferredEndReason;
                    this.deferredEndReason = null;
                    this.deferredPlaybackControl = null;
                    this.deferredConfirmationControl = null;
                    this.deferredSeekPoint = null;
                    this.exit(deferred);
                    continue;
                }
                CraftRoutePoint seekPoint = this.deferredSeekPoint;
                this.deferredSeekPoint = null;
                if (seekPoint != null && !this.cleanupStarted && !this.stopInProgress) {
                    this.setCurrentPoint(seekPoint);
                    continue;
                }
                DeferredConfirmationControl confirmationControl = this.deferredConfirmationControl;
                this.deferredConfirmationControl = null;
                if (confirmationControl != null && !this.cleanupStarted && !this.stopInProgress) {
                    if (confirmationControl == DeferredConfirmationControl.ENTER) {
                        this.setWaitingForConfirmation(true);
                    } else {
                        this.onConfirm();
                    }
                    continue;
                }
                DeferredPlaybackControl control = this.deferredPlaybackControl;
                this.deferredPlaybackControl = null;
                if (control == null || this.cleanupStarted || this.stopInProgress) {
                    return;
                }
                if (control == DeferredPlaybackControl.PAUSE) {
                    this.pause();
                } else {
                    this.resume();
                }
            }
        } finally {
            this.drainingDeferredRequests = false;
        }
    }

    private void logFailure(String message, Throwable throwable) {
        ServerTours.getInstance().getLogger().log(Level.SEVERE,
                message + " for player " + this.player.getName() + " on route '" + this.route.getName() + "'",
                throwable);
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ExternalAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ExternalSupplier<T> {
        T get() throws Exception;
    }

    private enum DeferredPlaybackControl {
        PAUSE,
        RESUME
    }

    private enum DeferredConfirmationControl {
        ENTER,
        ACCEPT
    }

    private record CleanupFailure(String operation, Throwable cause) {
    }
}
