package com.melluh.servertours.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Captures the player state owned by playback and restores it exactly once.
 *
 * <p>The snapshot is taken eagerly so playback code can freely update the
 * player without accidentally replacing the original values. Item stacks and
 * mutable vectors are cloned both when captured and when restored.</p>
 */
public class PlayerRestoreWrapper {
    private final Player player;
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] extraContents;
    private final ItemStack offHandItem;
    private final int heldItemSlot;
    private final GameMode gameMode;
    private final int level;
    private final float experience;
    private final int totalExperience;
    private final double health;
    private final boolean collidable;
    private final boolean allowFlight;
    private final boolean flying;
    private final Vector velocity;
    private final float fallDistance;

    private volatile boolean restored;
    private volatile List<RestoreFailure> restoreFailures = Collections.emptyList();

    public PlayerRestoreWrapper(Player player) {
        this.player = Objects.requireNonNull(player, "player");

        PlayerInventory inventory = player.getInventory();
        this.inventoryContents = cloneItems(inventory.getStorageContents());
        this.armorContents = cloneItems(inventory.getArmorContents());
        this.extraContents = cloneItems(inventory.getExtraContents());
        this.offHandItem = cloneItem(inventory.getItemInOffHand());
        this.heldItemSlot = inventory.getHeldItemSlot();
        this.gameMode = player.getGameMode();
        this.level = player.getLevel();
        this.experience = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.health = player.getHealth();
        this.collidable = player.isCollidable();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.velocity = player.getVelocity().clone();
        this.fallDistance = player.getFallDistance();
    }

    public void clearInventory() {
        this.player.getInventory().clear();
    }

    public void setGameMode(GameMode gameMode) {
        this.player.setGameMode(gameMode);
    }

    public void setLevel(int level) {
        this.player.setLevel(level);
    }

    public void setExperience(float exp) {
        this.player.setExp(exp);
    }

    public void setMaxHealth() {
        double value = this.player.getMaxHealth();
        if (this.player.getHealth() < value) {
            this.setHealth(value);
        }
    }

    public void setHealth(double health) {
        this.player.setHealth(health);
    }

    public void setCollidable(boolean collidable) {
        this.player.setCollidable(collidable);
    }

    public void setAllowFlight(boolean allowFlight) {
        this.player.setAllowFlight(allowFlight);
    }

    public void setFlying(boolean flying) {
        this.player.setFlying(flying);
    }

    /**
     * Restores every captured property at most once. A failure restoring one
     * property does not prevent the remaining properties from being restored.
     * Call {@link #getRestoreFailures()} afterwards to inspect any failures.
     */
    public synchronized void restore() {
        if (this.restored) {
            return;
        }
        this.restored = true;

        List<RestoreFailure> failures = new ArrayList<>();
        PlayerInventory inventory = null;
        try {
            inventory = this.player.getInventory();
        } catch (Throwable throwable) {
            failures.add(new RestoreFailure("inventory access", throwable));
        }
        if (inventory != null) {
            PlayerInventory targetInventory = inventory;
            restoreSafely(failures, "inventory", () -> targetInventory.setStorageContents(cloneItems(this.inventoryContents)));
            restoreSafely(failures, "armor", () -> targetInventory.setArmorContents(cloneItems(this.armorContents)));
            restoreSafely(failures, "extra inventory", () -> targetInventory.setExtraContents(cloneItems(this.extraContents)));
            restoreSafely(failures, "off-hand item", () -> targetInventory.setItemInOffHand(cloneItem(this.offHandItem)));
            restoreSafely(failures, "held item slot", () -> targetInventory.setHeldItemSlot(this.heldItemSlot));
        }

        // Restore all three experience representations explicitly. Level and
        // bar progress go last so they remain exact on implementations that
        // derive either value while applying total experience.
        restoreSafely(failures, "total experience", () -> this.player.setTotalExperience(this.totalExperience));
        restoreSafely(failures, "level", () -> this.player.setLevel(this.level));
        restoreSafely(failures, "experience progress", () -> this.player.setExp(this.experience));
        restoreSafely(failures, "health", () -> this.player.setHealth(Math.min(this.health, this.player.getMaxHealth())));
        restoreSafely(failures, "collidable", () -> this.player.setCollidable(this.collidable));

        // Game mode can modify flight permissions, so the captured flight
        // state must be applied after it and in permission-before-flying order.
        restoreSafely(failures, "game mode", () -> this.player.setGameMode(this.gameMode));
        restoreSafely(failures, "allow flight", () -> this.player.setAllowFlight(this.allowFlight));
        restoreSafely(failures, "flying", () -> this.player.setFlying(this.flying));

        restoreSafely(failures, "velocity", () -> this.player.setVelocity(this.velocity.clone()));
        restoreSafely(failures, "fall distance", () -> this.player.setFallDistance(this.fallDistance));

        this.restoreFailures = Collections.unmodifiableList(failures);
    }

    public boolean isRestored() {
        return this.restored;
    }

    /**
     * Returns failures from the first restore attempt in restoration order.
     */
    public List<RestoreFailure> getRestoreFailures() {
        return this.restoreFailures;
    }

    private static void restoreSafely(List<RestoreFailure> failures, String operation, RestoreAction action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failures.add(new RestoreFailure(operation, throwable));
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] cloned = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            cloned[i] = cloneItem(items[i]);
        }
        return cloned;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    @FunctionalInterface
    private interface RestoreAction {
        void run();
    }

    public static final class RestoreFailure {
        private final String operation;
        private final Throwable cause;

        private RestoreFailure(String operation, Throwable cause) {
            this.operation = operation;
            this.cause = cause;
        }

        public String getOperation() {
            return this.operation;
        }

        public Throwable getCause() {
            return this.cause;
        }
    }
}
