package com.melluh.servertours.nms.v1_21_5;

import com.melluh.servertours.nms.TemporaryEntity;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R4.entity.CraftEntity;

import java.util.Objects;

public class NmsTemporaryEntity extends EntityArmorStand implements TemporaryEntity {
    public NmsTemporaryEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
        super.f(true);
        super.k(true);
        super.e(true);
        super.t(true);
        super.d(0.0f);
        Objects.requireNonNull(super.g(GenericAttributes.s)).a(0.0);
        super.persist = false;
        super.collides = false;
    }

    @Override
    public void nmsAddPassenger(org.bukkit.entity.Entity entity) {
        ((CraftEntity) Objects.requireNonNull(entity, "entity is null")).getHandle().a(this, true);
    }

    @Override
    public void nmsMove(Location location) {
        super.b(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void nmsSetLocation(Location location) {
        super.a_(location.getX(), location.getY(), location.getZ());
        super.b(location.getYaw(), location.getPitch());
    }

    @Override
    public void nmsRemove() {
        super.a(RemovalReason.a);
    }

    public void g() {
    }

    public void inactiveTick() {
    }

    public boolean cC() {
        return true;
    }

    public boolean a(WorldServer world, DamageSource source) {
        return true;
    }

    public EnumInteractionResult a(EntityHuman player, EnumHand hand) {
        return EnumInteractionResult.e;
    }

    public EnumInteractionResult a(EntityHuman player, Vec3D hitPos, EnumHand hand) {
        return EnumInteractionResult.e;
    }

    public void c(WorldServer world) {
    }

    @Override
    public void a(RemovalReason reason) {
    }
}
