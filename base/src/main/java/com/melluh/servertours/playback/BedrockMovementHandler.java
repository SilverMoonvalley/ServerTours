package com.melluh.servertours.playback;

import com.melluh.servertours.util.PlayerRestoreWrapper;
import org.bukkit.Location;

public class BedrockMovementHandler implements MovementHandler {
    @Override
    public void initialize(CraftTouringPlayer craftTouringPlayer, Location location) {
        PlayerRestoreWrapper restoreWrapper = craftTouringPlayer.getRestoreWrapper();
        restoreWrapper.setAllowFlight(true);
        restoreWrapper.setFlying(true);
    }

    @Override
    public void move(CraftTouringPlayer craftTouringPlayer, Location location) {
        craftTouringPlayer.getPlayer().teleport(location);
    }

    @Override
    public void cleanup() {
    }
}
