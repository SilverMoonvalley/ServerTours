package com.melluh.servertours.nms.v1_21_4;

import com.melluh.servertours.nms.ModernMovementNmsHandler;
import com.melluh.servertours.nms.TemporaryDisplayCamera;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Objects;

public class NmsHandler implements com.melluh.servertours.nms.NmsHandler, ModernMovementNmsHandler {
    @Override
    public TemporaryDisplayCamera createTemporaryDisplayCamera(Location location, int interpolationTicks) {
        return new NmsTemporaryDisplayCamera(location, interpolationTicks);
    }

    @Override
    public void sendEntityTeleportPacket(Player player, int entityId, Location location) {
        this.sendPacket(player, new PacketPlayOutEntityTeleport(entityId, new PositionMoveRotation(new Vec3D(location.getX(), location.getY(), location.getZ()), Vec3D.c, 0.0f, 0.0f), Collections.emptySet(), false));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        Objects.requireNonNull((CraftPlayer) player, "player is null").getHandle().f.b(packet);
    }
}
