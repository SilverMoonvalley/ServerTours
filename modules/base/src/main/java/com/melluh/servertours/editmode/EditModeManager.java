package com.melluh.servertours.editmode;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.CustomItem;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditModeManager implements Listener {
    private final Map<UUID, EditingPlayer> editingPlayers;

    public EditModeManager() {
        this.editingPlayers = new HashMap<>();
    }

    public void startEditing(Player player, CraftRoute craftRoute) {
        this.stopEditing(player);
        EditingPlayer editingPlayer = new EditingPlayer(player, craftRoute);
        editingPlayer.setup();
        this.editingPlayers.put(player.getUniqueId(), editingPlayer);
    }

    public void stopEditing(Player player) {
        EditingPlayer editingPlayer = this.editingPlayers.get(player.getUniqueId());
        if (editingPlayer == null) {
            return;
        }
        this.stopEditing(editingPlayer);
    }

    public void stopEditing(EditingPlayer editingPlayer) {
        editingPlayer.restore();
        this.editingPlayers.remove(editingPlayer.getUniqueId());
    }

    public void handleRouteRemoval(CraftRoute craftRoute) {
        for (EditingPlayer editingPlayer : this.editingPlayers.values()) {
            if (editingPlayer.getEditingRoute() == craftRoute) {
                this.stopEditing(editingPlayer);
            }
        }
    }

    public void stopAllEditing() {
        for (EditingPlayer editingPlayer : this.editingPlayers.values()) {
            editingPlayer.restore();
        }
        this.editingPlayers.clear();
    }

    public void updateChatMenu(Player player) {
        EditingPlayer editingPlayer = this.editingPlayers.get(player.getUniqueId());
        if (editingPlayer != null && editingPlayer.getChatMenu() != null) {
            editingPlayer.getChatMenu().send(player);
        }
    }

    public boolean isEditing(Player player) {
        return this.editingPlayers.containsKey(player.getUniqueId());
    }

    public EditingPlayer getEditingPlayer(Player player) {
        return this.editingPlayers.get(player.getUniqueId());
    }

    public EditingPlayer getEditingPlayer(CraftRoute craftRoute) {
        for (EditingPlayer editingPlayer : this.editingPlayers.values()) {
            if (editingPlayer.getEditingRoute() == craftRoute) {
                return editingPlayer;
            }
        }
        return null;
    }

    public void startRunnable() {
        new BukkitRunnable() {
            public void run() {
                Arrays.stream(EditModeManager.this.editingPlayers.values().toArray(new EditingPlayer[0])).forEach(EditingPlayer::tick);
            }
        }.runTaskTimer(ServerTours.getInstance(), 5L, 5L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = playerInteractEvent.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        CustomItem fromItemStack = CustomItem.fromItemStack(playerInteractEvent.getItem());
        if (fromItemStack == null) {
            return;
        }
        playerInteractEvent.setCancelled(true);
        EditingPlayer editingPlayer = this.editingPlayers.get(playerInteractEvent.getPlayer().getUniqueId());
        if (editingPlayer != null) {
            editingPlayer.handleItemUse(fromItemStack);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        Player player = playerQuitEvent.getPlayer();
        if (this.isEditing(player)) {
            this.stopEditing(player);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent playerDropItemEvent) {
        if (this.isEditing(playerDropItemEvent.getPlayer())) {
            playerDropItemEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent entityPickupItemEvent) {
        if (entityPickupItemEvent.getEntityType() != EntityType.PLAYER) {
            return;
        }
        if (this.isEditing((Player) entityPickupItemEvent.getEntity())) {
            entityPickupItemEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent inventoryClickEvent) {
        Player player = (Player) inventoryClickEvent.getWhoClicked();
        EditingPlayer editingPlayer = this.getEditingPlayer(player);
        if (editingPlayer != null) {
            inventoryClickEvent.setCancelled(true);
            if (editingPlayer.getPointSelectMenu().getInventory().equals(inventoryClickEvent.getClickedInventory())) {
                if (inventoryClickEvent.getSlot() == 45) {
                    editingPlayer.getPointSelectMenu().setPage(editingPlayer.getPointSelectMenu().getCurrentPage() - 1);
                    return;
                }
                if (inventoryClickEvent.getSlot() == 53) {
                    editingPlayer.getPointSelectMenu().setPage(editingPlayer.getPointSelectMenu().getCurrentPage() + 1);
                    return;
                }
                CraftRoutePoint clickedPoint = editingPlayer.getPointSelectMenu().getClickedPoint(inventoryClickEvent.getSlot());
                if (clickedPoint != null) {
                    player.closeInventory();
                    editingPlayer.playConfirmationSound();
                    editingPlayer.setSelectedPoint(clickedPoint);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent playerChangedWorldEvent) {
        EditingPlayer editingPlayer = this.getEditingPlayer(playerChangedWorldEvent.getPlayer());
        if (editingPlayer != null) {
            editingPlayer.refreshEntities();
        }
    }
}
