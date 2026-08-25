package com.melluh.servertours.util.protocol;

import com.melluh.servertours.playback.CraftTouringPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacketUtilTest {
    private final CraftTouringPlayer touringPlayer = mock(CraftTouringPlayer.class);

    @Test
    void unmountedPlayerInputCanExitDisplayCamera() {
        when(this.touringPlayer.isExitByMoving()).thenReturn(true);

        assertTrue(PacketUtil.shouldExitFromPlayerInput(this.touringPlayer, true, false, false, false));
        assertTrue(PacketUtil.shouldExitFromPlayerInput(this.touringPlayer, false, false, true, false));
    }

    @Test
    void idleInputAndDisabledMovementExitDoNotStopTour() {
        when(this.touringPlayer.isExitByMoving()).thenReturn(true);
        assertFalse(PacketUtil.shouldExitFromPlayerInput(this.touringPlayer, false, false, false, false));

        when(this.touringPlayer.isExitByMoving()).thenReturn(false);
        assertFalse(PacketUtil.shouldExitFromPlayerInput(this.touringPlayer, true, true, true, true));
    }
}
