package com.melluh.servertours.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class PlayerRestoreWrapper {
    private final Player player;
    private ItemStack[] inventory;
    private GameMode gameMode;
    private Integer level;
    private Float experience;
    private Double health;
    private Boolean collidable;
    private Boolean allowFlight;
    private Boolean flying;

    public PlayerRestoreWrapper(Player player) {
        this.player = player;
    }

    public void clearInventory() {
        if (this.inventory == null) {
            this.inventory = this.player.getInventory().getContents();
        }
        this.player.getInventory().clear();
    }

    public void setGameMode(GameMode gameMode) {
        if (this.gameMode == null) {
            this.gameMode = this.player.getGameMode();
        }
        this.player.setGameMode(gameMode);
    }

    public void setLevel(int level) {
        if (this.level == null) {
            this.level = this.player.getLevel();
        }
        this.player.setLevel(level);
    }

    public void setExperience(float exp) {
        if (this.experience == null) {
            this.experience = this.player.getExp();
        }
        this.player.setExp(exp);
    }

    public void setMaxHealth() {
        double value = Objects.requireNonNull(this.player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        if (this.player.getHealth() < value) {
            this.setHealth(value);
        }
    }

    public void setHealth(double health) {
        if (this.health == null) {
            this.health = this.player.getHealth();
        }
        this.player.setHealth(health);
    }

    public void setCollidable(boolean collidable) {
        if (this.collidable == null) {
            this.collidable = this.player.isCollidable();
        }
        this.player.setCollidable(collidable);
    }

    public void setAllowFlight(boolean allowFlight) {
        if (this.allowFlight == null) {
            this.allowFlight = this.player.getAllowFlight();
        }
        this.player.setAllowFlight(allowFlight);
    }

    public void setFlying(boolean flying) {
        if (this.flying == null) {
            this.flying = this.player.isFlying();
        }
        this.player.setFlying(flying);
    }

    public void restore() {
        if (this.inventory != null) {
            this.player.getInventory().setContents(this.inventory);
        }
        if (this.level != null) {
            this.player.setLevel(this.level);
        }
        if (this.experience != null) {
            this.player.setExp(this.experience);
        }
        if (this.health != null) {
            this.player.setHealth(Math.min(this.health, Objects.requireNonNull(this.player.getAttribute(Attribute.MAX_HEALTH)).getValue()));
        }
        if (this.collidable != null) {
            this.player.setCollidable(this.collidable);
        }
        if (this.allowFlight != null) {
            this.player.setAllowFlight(this.allowFlight);
        }
        if (this.flying != null) {
            this.player.setFlying(this.flying);
        }
        if (this.gameMode != null) {
            this.player.setGameMode(this.gameMode);
        }
    }
}
