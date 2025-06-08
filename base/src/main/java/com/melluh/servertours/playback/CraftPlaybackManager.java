package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.PlaybackManager;
import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.event.RoutePlaybackBeginEvent;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.route.CraftRoute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CraftPlaybackManager implements PlaybackManager, Listener {
    private final Map<UUID, CraftTouringPlayer> touringPlayers;

    public CraftPlaybackManager() {
        this.touringPlayers = new ConcurrentHashMap<>();
    }

    @Override
    public TouringPlayer showTour(Player player, Route route) {
        if (route instanceof CraftRoute craftRoute) {
            return this.showTour(player, craftRoute);
        }
        throw new IllegalArgumentException("route must be an instance of CraftRoute");
    }

    public CraftTouringPlayer showTour(Player obj, CraftRoute obj2) {
        Objects.requireNonNull(obj, "player may not be null");
        Objects.requireNonNull(obj2, "route may not be null");
        if (obj2.getNumPoints() < 1) {
            obj.sendMessage(ServerTours.translate("commands.errors.noPoints"));
            return null;
        }
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(obj);
        if (touringPlayer != null) {
            touringPlayer.exit(RoutePlaybackEndEvent.EndReason.EXITED);
        }
        CraftTouringPlayer craftTouringPlayer = new CraftTouringPlayer(obj, obj2, this.createMovementHandler(obj));
        Bukkit.getPluginManager().callEvent(new RoutePlaybackBeginEvent(craftTouringPlayer));
        craftTouringPlayer.initialize();
        this.touringPlayers.put(obj.getUniqueId(), craftTouringPlayer);
        return craftTouringPlayer;
    }

    private MovementHandler createMovementHandler(Player player) {
        return ServerTours.getInstance().isBedrockPlayer(player) ? new BedrockMovementHandler() : new JavaMovementHandler();
    }

    public boolean isTouringPlayer(Player player) {
        return this.getTouringPlayer(player) != null;
    }

    @Override
    public CraftTouringPlayer getTouringPlayer(Player player) {
        if (player.getClass().getName().endsWith("TemporaryPlayer")) {
            return null;
        }
        return this.touringPlayers.get(player.getUniqueId());
    }

    @Override
    public List<CraftTouringPlayer> getTouringPlayers(Route route) {
        return this.touringPlayers.values().stream().filter(craftTouringPlayer -> craftTouringPlayer.getRoute() == route).toList();
    }

    @Override
    public List<CraftTouringPlayer> getTouringPlayers() {
        return this.touringPlayers.values().stream().toList();
    }

    public void unregister(CraftTouringPlayer value) {
        this.touringPlayers.remove(value.getPlayer().getUniqueId(), value);
    }

    public void stopAllTouring() {
        this.touringPlayers.values().forEach(craftTouringPlayer -> craftTouringPlayer.exit(RoutePlaybackEndEvent.EndReason.PLUGIN_DISABLED));
        this.touringPlayers.clear();
    }

    public void startRunnable() {
        Bukkit.getScheduler().runTaskTimer(ServerTours.getInstance(), this::tickPlayers, 1L, 1L);
    }

    private void tickPlayers() {
        this.touringPlayers.values().forEach(CraftTouringPlayer::tick);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.getAction() != Action.LEFT_CLICK_AIR && playerInteractEvent.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        CraftTouringPlayer craftTouringPlayer = this.touringPlayers.get(playerInteractEvent.getPlayer().getUniqueId());
        if (craftTouringPlayer == null || !craftTouringPlayer.isWaitingForConfirmation() || craftTouringPlayer.getCurrentPoint().getConfirmMode() != ConfirmMode.MOUSE) {
            return;
        }
        craftTouringPlayer.onConfirm();
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent playerSwapHandItemsEvent) {
        CraftTouringPlayer craftTouringPlayer = this.touringPlayers.get(playerSwapHandItemsEvent.getPlayer().getUniqueId());
        if (craftTouringPlayer == null || !craftTouringPlayer.isWaitingForConfirmation() || craftTouringPlayer.getCurrentPoint().getConfirmMode() != ConfirmMode.KEYBOARD) {
            return;
        }
        playerSwapHandItemsEvent.setCancelled(true);
        craftTouringPlayer.onConfirm();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(playerQuitEvent.getPlayer());
        if (touringPlayer != null) {
            touringPlayer.exit(RoutePlaybackEndEvent.EndReason.QUIT);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.getEntityType() != EntityType.PLAYER) {
            return;
        }
        if (this.isTouringPlayer((Player) entityDamageEvent.getEntity())) {
            entityDamageEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (!ServerTours.getInstance().getConfig().getBoolean("playMode.disableCommands")) {
            return;
        }
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (this.isTouringPlayer(player) && !playerCommandPreprocessEvent.getMessage().startsWith("/tour")) {
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage(ServerTours.translate("commands.errors.commandsDisabled"));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent asyncPlayerChatEvent) {
        if (!ServerTours.getInstance().getConfig().getBoolean("playMode.disableChat")) {
            return;
        }
        asyncPlayerChatEvent.getRecipients().removeIf(this::isTouringPlayer);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent entityTargetEvent) {
        Entity target = entityTargetEvent.getTarget();
        if (target instanceof Player player) {
            if (this.isTouringPlayer(player)) {
                entityTargetEvent.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeGameMode(PlayerGameModeChangeEvent playerGameModeChangeEvent) {
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(playerGameModeChangeEvent.getPlayer());
        if (touringPlayer != null && touringPlayer.isGamemodeLocked()) {
            playerGameModeChangeEvent.setCancelled(true);
            ServerTours.getInstance().getLogger().warning("Cannot change player gamemode while viewing a tour");
        }
    }

    @EventHandler
    public void onPlayerDismount(EntityDismountEvent entityDismountEvent) {
        Entity entity = entityDismountEvent.getEntity();
        if (entity instanceof Player player) {
            this.handlePlayerExit(player, entityDismountEvent);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent playerToggleSneakEvent) {
        this.handlePlayerExit(playerToggleSneakEvent.getPlayer(), playerToggleSneakEvent);
    }

    private void handlePlayerExit(Player player, Cancellable cancellable) {
        CraftTouringPlayer touringPlayer = this.getTouringPlayer(player);
        if (touringPlayer != null) {
            if (!touringPlayer.canExit()) {
                cancellable.setCancelled(true);
                return;
            }
            touringPlayer.exit(RoutePlaybackEndEvent.EndReason.EXITED);
        }
    }
}
