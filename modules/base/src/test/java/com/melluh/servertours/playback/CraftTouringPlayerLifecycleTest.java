package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.event.RoutePlaybackBeginEvent;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.playback.PauseReason;
import com.melluh.servertours.api.playback.PlaybackState;
import com.melluh.servertours.api.playback.track.EventTrackRuntime;
import com.melluh.servertours.api.playback.track.TimelineEvent;
import com.melluh.servertours.api.playback.track.TrackFactory;
import com.melluh.servertours.api.playback.track.TrackRuntime;
import com.melluh.servertours.editmode.EditModeManager;
import com.melluh.servertours.playback.camera.MovementHandler;
import com.melluh.servertours.playback.timeline.NanoClock;
import com.melluh.servertours.playback.track.TrackFactoryRegistration;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.protocol.PacketUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Session-level contract tests. Bukkit is mocked only at its static boundary. */
class CraftTouringPlayerLifecycleTest {
    private final List<Event> publishedEvents = new ArrayList<>();
    private MockedStatic<ServerTours> serverToursStatic;
    private MockedStatic<Bukkit> bukkitStatic;
    private MockedStatic<PacketUtil> packetUtilStatic;
    private ServerTours plugin;
    private PluginManager pluginManager;

    @BeforeEach
    void setUpStatics() {
        this.plugin = mock(ServerTours.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Logger logger = Logger.getLogger("ServerToursLifecycleTest");
        logger.setUseParentHandlers(false);
        when(this.plugin.getConfig()).thenReturn(config);
        when(this.plugin.getName()).thenReturn("ServerTours");
        when(this.plugin.getLogger()).thenReturn(logger);
        when(this.plugin.isBedrockPlayer(any())).thenReturn(false);
        when(this.plugin.getEditModeManager()).thenReturn(mock(EditModeManager.class));

        this.serverToursStatic = mockStatic(ServerTours.class);
        this.serverToursStatic.when(ServerTours::getInstance).thenReturn(this.plugin);

        this.pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            this.publishedEvents.add(invocation.getArgument(0));
            return null;
        }).when(this.pluginManager).callEvent(any(Event.class));
        this.bukkitStatic = mockStatic(Bukkit.class);
        this.bukkitStatic.when(Bukkit::getPluginManager).thenReturn(this.pluginManager);
        this.bukkitStatic.when(Bukkit::isPrimaryThread).thenReturn(true);

        this.packetUtilStatic = mockStatic(PacketUtil.class);
    }

    @AfterEach
    void tearDownStatics() {
        this.packetUtilStatic.close();
        this.bukkitStatic.close();
        this.serverToursStatic.close();
    }

    @Test
    void disabledTrackOwnerRejectsStartupAndRollbackPublishesNoBeginEvent() {
        Fixture fixture = this.fixture(2, 2);
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("DisabledTrackPlugin");
        when(owner.isEnabled()).thenReturn(false);
        TrackFactory factory = mock(TrackFactory.class);
        TrackFactoryRegistration registration = new TrackFactoryRegistration(
                owner, new NamespacedKey("test", "disabled"), 10, 1L, factory);
        CraftTouringPlayer session = fixture.session(List.of(registration));

        IllegalStateException failure = assertThrows(IllegalStateException.class, session::initialize);
        session.abortStart(failure);

        assertTrue(failure.getMessage().contains("was disabled before session startup"));
        assertEquals(PlaybackState.STOPPED, session.getPlaybackState());
        assertTrue(session.getRestoreWrapper().isRestored());
        assertFalse(this.publishedEvents.stream().anyMatch(RoutePlaybackBeginEvent.class::isInstance));
        verify(factory, never()).create(any());
        verify(fixture.manager).onSessionStopped(session, RoutePlaybackEndEvent.EndReason.ERROR);
    }

    @Test
    void forwardSeekSkipsIntermediateEventsAndBackwardSeekRearmsThem() {
        Fixture fixture = this.fixture(2, 2, 2);
        List<String> calls = new ArrayList<>();
        EventTrackRuntime runtime = eventTrack(6L, List.of(
                new TimelineEvent("one", 1L, (context, frame) -> calls.add("one")),
                new TimelineEvent("three", 3L, (context, frame) -> calls.add("three")),
                new TimelineEvent("five", 5L, (context, frame) -> calls.add("five"))
        ));
        CraftTouringPlayer session = fixture.startedSession(List.of(fixture.registration(runtime)));

        fixture.clock.advanceFrames(1L);
        session.tick();
        assertEquals(List.of("one"), calls);

        session.setCurrentPoint(2);
        assertEquals(List.of("one"), calls, "a forward seek must not replay crossed third-party events");

        session.setCurrentPoint(0);
        fixture.clock.advanceFrames(3L);
        session.tick();
        assertEquals(List.of("one", "one", "three"), calls,
                "a backward seek must re-arm events strictly after the target entry frame");
    }

    @Test
    void naturalFinishRendersFinalStateBeforeEndAndNeverExitsLastPoint() {
        Fixture fixture = this.fixture(1, 1);
        List<String> order = new ArrayList<>();
        AtomicReference<CraftTouringPlayer> sessionRef = new AtomicReference<>();
        doAnswer(invocation -> {
            order.add("state:" + sessionRef.get().getCurrentFrame());
            return null;
        }).when(fixture.movement).move(any(), any());
        doAnswer(invocation -> {
            CraftTouringPlayer active = sessionRef.get();
            order.add(active == null ? "state:setup" : "state:" + active.getCurrentFrame());
            return null;
        }).when(fixture.movement).rebase(any(), any(), any());
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            this.publishedEvents.add(event);
            if (event instanceof RoutePlaybackEndEvent) {
                order.add("end");
            }
            return null;
        }).when(this.pluginManager).callEvent(any(Event.class));
        doAnswer(invocation -> {
            order.add("first:" + invocation.getArgument(1, RoutePointCommand.CommandTrigger.class));
            return null;
        }).when(fixture.points.get(0)).executeCommands(any(), any());
        doAnswer(invocation -> {
            order.add("last:" + invocation.getArgument(1, RoutePointCommand.CommandTrigger.class));
            return null;
        }).when(fixture.points.get(1)).executeCommands(any(), any());

        CraftTouringPlayer session = fixture.startedSession(List.of());
        sessionRef.set(session);
        assertFalse(order.contains("first:EXIT"), "the initial entry bundle must not execute EXIT");

        fixture.clock.advanceFrames(1L);
        session.tick();
        fixture.clock.advanceFrames(1L);
        session.tick();

        assertEquals(PlaybackState.STOPPED, session.getPlaybackState());
        assertTrue(order.indexOf("state:2") >= 0 && order.indexOf("state:2") < order.indexOf("end"),
                "the final camera state must render before FINISHED is published");
        assertEquals(1L, order.stream().filter("first:EXIT"::equals).count());
        assertFalse(order.contains("last:EXIT"), "natural finish must not execute the last point EXIT");
        assertEquals(1L, order.stream().filter("last:QUIT"::equals).count());
    }

    @Test
    void cancellableEndPausesButForcedErrorIgnoresCancellationAndCleansUp() {
        Fixture fixture = this.fixture(4);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            this.publishedEvents.add(event);
            if (event instanceof RoutePlaybackEndEvent endEvent) {
                endEvent.setCancelled(true);
            }
            return null;
        }).when(this.pluginManager).callEvent(any(Event.class));
        CraftTouringPlayer session = fixture.startedSession(List.of());

        session.exit(RoutePlaybackEndEvent.EndReason.API);

        assertEquals(PlaybackState.PAUSED, session.getPlaybackState());
        assertEquals(PauseReason.END_CANCELLED, session.getPauseReason());
        assertFalse(session.getRestoreWrapper().isRestored());
        verify(fixture.movement, never()).cleanup();
        verify(fixture.manager, never()).onSessionStopped(any(), any());

        session.exit(RoutePlaybackEndEvent.EndReason.ERROR);

        assertEquals(PlaybackState.STOPPED, session.getPlaybackState());
        assertTrue(session.getRestoreWrapper().isRestored());
        verify(fixture.movement).cleanup();
        verify(fixture.manager).onSessionStopped(session, RoutePlaybackEndEvent.EndReason.ERROR);
    }

    private Fixture fixture(int... durations) {
        return new Fixture(durations);
    }

    private static EventTrackRuntime eventTrack(long endFrame, List<TimelineEvent> events) {
        return new EventTrackRuntime() {
            @Override
            public long getEndFrame() {
                return endFrame;
            }

            @Override
            public List<TimelineEvent> events() {
                return events;
            }
        };
    }

    private final class Fixture {
        private final Player player = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final World world = mock(World.class);
        private final CraftRoute route = mock(CraftRoute.class);
        private final CraftPlaybackManager manager = mock(CraftPlaybackManager.class);
        private final MovementHandler movement = mock(MovementHandler.class);
        private final MutableNanoClock clock = new MutableNanoClock();
        private final List<CraftRoutePoint> points = new ArrayList<>();

        private Fixture(int... durations) {
            when(this.player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(this.player.getName()).thenReturn("Viewer");
            when(this.player.getWorld()).thenReturn(this.world);
            when(this.player.getLocation()).thenReturn(new Location(this.world, 0, 64, 0));
            when(this.player.getInventory()).thenReturn(this.inventory);
            when(this.inventory.getStorageContents()).thenReturn(new ItemStack[0]);
            when(this.inventory.getArmorContents()).thenReturn(new ItemStack[0]);
            when(this.inventory.getExtraContents()).thenReturn(new ItemStack[0]);
            when(this.player.getGameMode()).thenReturn(GameMode.ADVENTURE);
            when(this.player.getVelocity()).thenReturn(new Vector());
            when(this.player.getHealth()).thenReturn(20.0);
            when(this.player.getMaxHealth()).thenReturn(20.0);
            when(this.player.teleport(any(Location.class))).thenReturn(true);
            when(this.player.isOnline()).thenReturn(true);
            when(this.player.spigot()).thenReturn(mock(Player.Spigot.class));

            for (int index = 0; index < durations.length; index++) {
                CraftRoutePoint point = mock(CraftRoutePoint.class);
                Location location = new Location(this.world, index * 10.0, 64, 0);
                when(point.getTicksVisible()).thenReturn(durations[index]);
                when(point.getLocation()).thenReturn(location);
                when(point.getPlaybackLocation(anyFloat(), any())).thenReturn(location);
                this.points.add(point);
            }
            when(this.route.getName()).thenReturn("lifecycle");
            when(this.route.getPoints()).thenReturn(this.points);
            for (int index = 0; index < this.points.size(); index++) {
                when(this.route.getPoint(index)).thenReturn(this.points.get(index));
                when(this.route.indexOf(this.points.get(index))).thenReturn(index);
            }
            when(this.manager.ownsLifecycle(any())).thenReturn(true);
            when(this.manager.hasPendingStart(any())).thenReturn(false);
        }

        private TrackFactoryRegistration registration(TrackRuntime runtime) {
            Plugin owner = mock(Plugin.class);
            when(owner.isEnabled()).thenReturn(true);
            when(owner.getName()).thenReturn("TrackPlugin");
            return new TrackFactoryRegistration(owner, new NamespacedKey("test", "events"),
                    10, 1L, context -> Optional.of(runtime));
        }

        private CraftTouringPlayer session(List<TrackFactoryRegistration> registrations) {
            return new CraftTouringPlayer(this.player, this.route, this.movement, this.manager,
                    1L, registrations, this.clock);
        }

        private CraftTouringPlayer startedSession(List<TrackFactoryRegistration> registrations) {
            CraftTouringPlayer session = this.session(registrations);
            session.initialize();
            session.activate();
            session.beginPlayback();
            return session;
        }
    }

    private static final class MutableNanoClock implements NanoClock {
        private long nanos;

        @Override
        public long now() {
            return this.nanos;
        }

        void advanceFrames(long frames) {
            this.nanos += frames * 50_000_000L;
        }
    }
}
