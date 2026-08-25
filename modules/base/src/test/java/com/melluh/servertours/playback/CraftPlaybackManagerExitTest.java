package com.melluh.servertours.playback;

import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraftPlaybackManagerExitTest {
    @Test
    void deniedExitCancelsEventAndReassertsCamera() {
        CraftPlaybackManager manager = spy(new CraftPlaybackManager());
        Player player = mock(Player.class);
        CraftTouringPlayer session = mock(CraftTouringPlayer.class);
        when(session.isActive()).thenReturn(true);
        when(session.canExit()).thenReturn(false);
        doReturn(session).when(manager).getTouringPlayer(player);
        PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(player, true);

        manager.onPlayerSneak(event);

        assertTrue(event.isCancelled());
        verify(session).reassertCamera();
    }

    @Test
    void cancelledExitedEndKeepsEventCancelledAndReassertsCamera() {
        CraftPlaybackManager manager = spy(new CraftPlaybackManager());
        Player player = mock(Player.class);
        CraftTouringPlayer session = mock(CraftTouringPlayer.class);
        when(session.isActive()).thenReturn(true);
        when(session.canExit()).thenReturn(true);
        doReturn(session).when(manager).getTouringPlayer(player);
        PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(player, true);

        manager.onPlayerSneak(event);

        verify(session).exit(RoutePlaybackEndEvent.EndReason.EXITED);
        assertTrue(event.isCancelled());
        verify(session).reassertCamera();
    }
}
