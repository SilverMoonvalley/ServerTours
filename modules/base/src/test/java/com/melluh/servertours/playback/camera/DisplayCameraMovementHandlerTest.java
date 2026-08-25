package com.melluh.servertours.playback.camera;

import com.melluh.servertours.api.playback.track.StateRebaseReason;
import com.melluh.servertours.nms.NmsHandler;
import com.melluh.servertours.nms.TemporaryDisplayCamera;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DisplayCameraMovementHandlerTest {
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final PlayerRestoreWrapper restoreWrapper = mock(PlayerRestoreWrapper.class);
    private final CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);
    private final NmsHandler nmsHandler = mock(NmsHandler.class);
    private final TemporaryDisplayCamera firstCamera = mock(TemporaryDisplayCamera.class);

    @BeforeEach
    void setup() {
        when(this.touringPlayer.getPlayer()).thenReturn(this.player);
        when(this.touringPlayer.getRestoreWrapper()).thenReturn(this.restoreWrapper);
        when(this.player.getEyeHeight(false)).thenReturn(1.62);
        when(this.player.isOnline()).thenReturn(true);
        when(this.player.teleport(any(Location.class))).thenReturn(true);
        when(this.player.getLocation()).thenReturn(this.location(0.0, 64.0, 0.0));
        when(this.nmsHandler.createTemporaryDisplayCamera(any(Location.class), anyInt()))
                .thenReturn(this.firstCamera);
    }

    @Test
    void initializeSpawnsViewerCameraAtEyeHeightAndAnchorsPlayer() {
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);

        assertEquals(3, handler.presentationLeadFrames());

        handler.initialize(this.touringPlayer, this.location(2.0, 70.0, 4.0));

        verify(this.restoreWrapper).setAllowFlight(true);
        verify(this.restoreWrapper).setFlying(true);
        verify(this.player).teleport(this.location(2.0, 70.0, 4.0));
        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(this.nmsHandler).createTemporaryDisplayCamera(locationCaptor.capture(), eq(3));
        assertEquals(71.62, locationCaptor.getValue().getY(), 1.0e-9);
        verify(this.firstCamera).nmsSpawn(this.player);
        verify(this.firstCamera).nmsSetCamera(this.player);
    }

    @Test
    void continuousMoveUpdatesExistingCameraAndSuppressesDuplicateLocation() {
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        Location initial = this.location(0.0, 64.0, 0.0);
        Location target = this.location(1.0, 65.0, 2.0);
        handler.initialize(this.touringPlayer, initial);

        handler.move(this.touringPlayer, target);
        handler.move(this.touringPlayer, target.clone());

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(this.firstCamera, times(1)).nmsMove(eq(this.player), locationCaptor.capture());
        assertEquals(66.62, locationCaptor.getValue().getY(), 1.0e-9);
        verify(this.nmsHandler, times(1)).createTemporaryDisplayCamera(any(), anyInt());
    }

    @Test
    void explicitRebaseSwitchesToFreshEntityBeforeRemovingOldOne() {
        TemporaryDisplayCamera replacement = mock(TemporaryDisplayCamera.class);
        when(this.nmsHandler.createTemporaryDisplayCamera(any(Location.class), anyInt()))
                .thenReturn(this.firstCamera, replacement);
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));

        handler.rebase(this.touringPlayer, this.location(20.0, 70.0, 20.0),
                StateRebaseReason.EXPLICIT_SEEK);

        verify(replacement).nmsSpawn(this.player);
        verify(replacement).nmsSetCamera(this.player);
        verify(this.firstCamera).nmsRemove(this.player);
        verify(this.firstCamera, never()).nmsResetCamera(this.player);
        verify(this.player, times(2)).teleport(any(Location.class));
    }

    @Test
    void crossWorldMoveResetsOldCameraAndCreatesNewOne() {
        World otherWorld = mock(World.class);
        TemporaryDisplayCamera replacement = mock(TemporaryDisplayCamera.class);
        when(this.nmsHandler.createTemporaryDisplayCamera(any(Location.class), anyInt()))
                .thenReturn(this.firstCamera, replacement);
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));

        handler.move(this.touringPlayer, new Location(otherWorld, 4.0, 80.0, 5.0));

        verify(this.firstCamera).nmsResetCamera(this.player);
        verify(this.firstCamera).nmsRemove(this.player);
        verify(replacement).nmsSpawn(this.player);
        verify(replacement).nmsSetCamera(this.player);
    }

    @Test
    void anchorIntervalReassertsPlayerAndCamera() {
        DisplayCameraMovementHandler handler = this.handler(3, 2, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));
        clearInvocations(this.player, this.firstCamera);

        handler.move(this.touringPlayer, this.location(1.0, 64.0, 0.0));
        handler.move(this.touringPlayer, this.location(2.0, 64.0, 0.0));
        handler.move(this.touringPlayer, this.location(3.0, 64.0, 0.0));

        verify(this.player, times(1)).teleport(any(Location.class));
        verify(this.firstCamera, times(1)).nmsSetCamera(this.player);
    }

    @Test
    void anchorDistanceReassertsPlayerAndCamera() {
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));
        clearInvocations(this.player, this.firstCamera);

        handler.move(this.touringPlayer, this.location(25.01, 64.0, 0.0));

        verify(this.player).teleport(any(Location.class));
        verify(this.firstCamera).nmsSetCamera(this.player);
    }

    @Test
    void reassertCameraResendsCurrentDisplayTarget() {
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));
        clearInvocations(this.firstCamera);

        handler.reassertCamera(this.touringPlayer);

        verify(this.firstCamera).nmsSetCamera(this.player);
        verify(this.firstCamera, never()).nmsMove(any(), any());
        verify(this.firstCamera, never()).nmsSpawn(any());
    }

    @Test
    void cleanupRemovesEntityEvenWhenCameraResetFailsAndIsIdempotent() {
        DisplayCameraMovementHandler handler = this.handler(3, 10, 25.0);
        handler.initialize(this.touringPlayer, this.location(0.0, 64.0, 0.0));
        doThrow(new IllegalStateException("reset failed"))
                .when(this.firstCamera).nmsResetCamera(this.player);

        assertThrows(IllegalStateException.class, handler::cleanup);
        handler.cleanup();

        verify(this.firstCamera, times(1)).nmsResetCamera(this.player);
        verify(this.firstCamera, times(1)).nmsRemove(this.player);
    }

    private DisplayCameraMovementHandler handler(int interpolationTicks, int anchorInterval, double maxDistance) {
        return new DisplayCameraMovementHandler(this.nmsHandler,
                new CameraPlaybackSettings(interpolationTicks, anchorInterval, maxDistance));
    }

    private Location location(double x, double y, double z) {
        return new Location(this.world, x, y, z, 15.0f, -5.0f);
    }
}
