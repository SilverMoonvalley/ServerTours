package com.melluh.servertours.playback.camera;

import com.melluh.servertours.nms.NmsHandler;
import com.melluh.servertours.nms.TemporaryEntity;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.util.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

public class JavaMovementHandler implements MovementHandler {
    private TemporaryEntity vehicleEntity;

    @Override
    public void initialize(CraftTouringPlayer craftTouringPlayer, Location location) {
        Player player = craftTouringPlayer.getPlayer();
        if (!this.isMovementPossible(player, player.getLocation(), location)) {
            this.teleportOrThrow(player, location);
        }
        this.spawnVehicle(player, location);
    }

    @Override
    public void move(CraftTouringPlayer craftTouringPlayer, Location location) {
        Player player = craftTouringPlayer.getPlayer();
        if (!this.isMovementPossible(player, this.vehicleEntity.getBukkitEntity().getLocation(), location)) {
            boolean canExit = craftTouringPlayer.canExit();
            craftTouringPlayer.setCanExit(false);
            this.despawnVehicle();
            try {
                this.teleportOrThrow(player, location);
                this.spawnVehicle(player, location);
            } finally {
                craftTouringPlayer.setCanExit(canExit);
            }
            return;
        }
        this.vehicleEntity.nmsSetLocation(location);
        NmsHandler handler = NmsAdapter.getHandler();
        handler.sendMoveVehiclePacket(player, this.vehicleEntity);
        handler.rotatePlayerHead(player, location.getYaw(), location.getPitch());
    }

    private boolean isMovementPossible(Player player, Location location, Location location2) {
        Objects.requireNonNull(location.getWorld(), "origin world is null");
        Objects.requireNonNull(location2.getWorld(), "destination world is null");
        return location.getWorld() == location2.getWorld() && location2.getWorld().isChunkLoaded(location2.getBlockX() >> 4, location2.getBlockZ() >> 4) && location.distance(location2) < Math.min(player.getClientViewDistance(), Bukkit.getViewDistance()) * 16;
    }

    @Override
    public void cleanup() {
        this.despawnVehicle();
    }

    private void spawnVehicle(Player player, Location location) {
        (this.vehicleEntity = NmsAdapter.getHandler().spawnTemporaryEntity(location)).nmsAddPassenger(player);
    }

    private void teleportOrThrow(Player player, Location location) {
        if (!player.teleport(location)) {
            throw new IllegalStateException("camera teleport was cancelled or rejected");
        }
    }

    private void despawnVehicle() {
        if (this.vehicleEntity != null) {
            this.vehicleEntity.nmsRemove();
            this.vehicleEntity = null;
        }
    }
}
