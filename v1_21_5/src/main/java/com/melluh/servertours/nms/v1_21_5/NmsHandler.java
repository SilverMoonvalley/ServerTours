package com.melluh.servertours.nms.v1_21_5;

import com.melluh.servertours.nms.ModernMovementNmsHandler;
import com.melluh.servertours.nms.TemporaryEntity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport;
import net.minecraft.network.protocol.game.PacketPlayOutVehicleMove;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R4.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_21_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Objects;

public class NmsHandler implements com.melluh.servertours.nms.NmsHandler, ModernMovementNmsHandler {
    @Override
    public TemporaryEntity spawnTemporaryEntity(Location location) {
        WorldServer handle = Objects.requireNonNull((CraftWorld) location.getWorld(), "world is null").getHandle();
        NmsTemporaryEntity nmsTemporaryEntity = new NmsTemporaryEntity(handle, location.getX(), location.getY(), location.getZ());
        handle.b(nmsTemporaryEntity);
        return nmsTemporaryEntity;
    }

    @Override
    public void rotatePlayerHead(Player player, float yaw, float pitch) {
        this.sendPacket(player, new ClientboundPlayerRotationPacket(yaw, pitch));
    }

    @Override
    public void sendMoveVehiclePacket(Player player, TemporaryEntity vehicleEntity) {
        if (player == null || vehicleEntity == null) {
            return;
        }
        this.sendPacket(player, PacketPlayOutVehicleMove.a(((CraftEntity) vehicleEntity.getBukkitEntity()).getHandle()));
    }

    @Override
    public void sendEntityTeleportPacket(Player player, int entityId, Location location) {
        this.sendPacket(player, new PacketPlayOutEntityTeleport(entityId, new PositionMoveRotation(new Vec3D(location.getX(), location.getY(), location.getZ()), Vec3D.c, 0.0f, 0.0f), Collections.emptySet(), false));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        Objects.requireNonNull((CraftPlayer) player, "player is null").getHandle().f.b(packet);
    }
}
