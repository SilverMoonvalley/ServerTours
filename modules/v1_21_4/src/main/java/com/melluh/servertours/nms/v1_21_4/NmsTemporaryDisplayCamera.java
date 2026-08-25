package com.melluh.servertours.nms.v1_21_4;

import com.melluh.servertours.nms.TemporaryDisplayCamera;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutCamera;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Objects;

/** Packet-only text display used as a Java client camera target. */
final class NmsTemporaryDisplayCamera implements TemporaryDisplayCamera {
    private final Display.TextDisplay entity;
    private Location location;

    NmsTemporaryDisplayCamera(Location location, int interpolationTicks) {
        if (interpolationTicks < 0 || interpolationTicks > 59) {
            throw new IllegalArgumentException("interpolationTicks must be between 0 and 59");
        }
        this.location = cloneLocation(location);
        this.entity = new Display.TextDisplay(EntityTypes.bu,
                ((CraftWorld) Objects.requireNonNull(location.getWorld(), "world is null")).getHandle());
        this.setEntityLocation(this.location);
        this.entity.au().a(Display.r, interpolationTicks);
    }

    @Override
    public int getEntityId() {
        return this.entity.ar();
    }

    @Override
    public void nmsSpawn(Player viewer) {
        this.send(viewer, new PacketPlayOutSpawnEntity(
                this.getEntityId(), this.entity.cG(),
                this.location.getX(), this.location.getY(), this.location.getZ(),
                this.location.getPitch(), this.location.getYaw(), EntityTypes.bu, 0, Vec3D.c,
                this.location.getYaw()));
        this.send(viewer, new PacketPlayOutEntityMetadata(this.getEntityId(),
                Objects.requireNonNullElse(this.entity.au().c(), Collections.emptyList())));
    }

    @Override
    public void nmsSetCamera(Player viewer) {
        this.send(viewer, new PacketPlayOutCamera(this.entity));
    }

    @Override
    public void nmsMove(Player viewer, Location location) {
        this.location = cloneLocation(location);
        this.setEntityLocation(this.location);
        this.send(viewer, new PacketPlayOutEntityTeleport(this.getEntityId(),
                new PositionMoveRotation(
                        new Vec3D(location.getX(), location.getY(), location.getZ()),
                        Vec3D.c, location.getYaw(), location.getPitch()),
                Collections.emptySet(), false));
    }

    @Override
    public void nmsResetCamera(Player viewer) {
        this.send(viewer, new PacketPlayOutCamera(((CraftPlayer) Objects.requireNonNull(viewer, "viewer is null")).getHandle()));
    }

    @Override
    public void nmsRemove(Player viewer) {
        this.send(viewer, new PacketPlayOutEntityDestroy(this.getEntityId()));
    }

    private void setEntityLocation(Location location) {
        this.entity.a_(location.getX(), location.getY(), location.getZ());
        this.entity.b(location.getYaw(), location.getPitch());
    }

    private void send(Player viewer, Packet<?> packet) {
        ((CraftPlayer) Objects.requireNonNull(viewer, "viewer is null")).getHandle().f.b(packet);
    }

    private static Location cloneLocation(Location location) {
        Objects.requireNonNull(location, "location is null");
        Objects.requireNonNull(location.getWorld(), "world is null");
        return location.clone();
    }
}
