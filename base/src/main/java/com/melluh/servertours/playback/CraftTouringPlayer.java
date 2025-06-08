package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.TouringPlayer;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.event.RoutePlaybackPointEvent;
import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.hook.HookHandler;
import com.melluh.servertours.hook.VentureChatHook;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.PlayerRestoreWrapper;
import com.melluh.servertours.util.math.EasingFunction;
import com.melluh.servertours.util.math.SineEasingFunction;
import com.melluh.servertours.util.protocol.PacketUtil;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Objects;

public class CraftTouringPlayer implements TouringPlayer {
    private static final int ACTION_BAR_INITIAL_DELAY = 5;
    private static final int ACTION_BAR_PERIOD = 10;
    private final Player player;
    @Getter
    private final PlayerRestoreWrapper restoreWrapper;
    private final MovementHandler movementHandler;
    private final CraftRoute route;
    private final EasingFunction easingFunction;
    private CraftRoutePoint currentPoint;
    private int currentPointIndex;
    private boolean isWaitingForConfirmation;
    private int timeLeft;
    private int actionBarTimeLeft;
    private boolean isGamemodeLocked;
    private boolean hasExited;
    private boolean progressBarEnabled;
    private boolean actionBarEnabled;
    private boolean canExit;
    private boolean exitByMoving;
    private Location exitLocation;

    public CraftTouringPlayer(Player obj, CraftRoute obj2, MovementHandler obj3) {
        this.easingFunction = new SineEasingFunction();
        this.player = Objects.requireNonNull(obj, "player may not be null");
        this.route = Objects.requireNonNull(obj2, "route may not be null");
        this.currentPoint = Objects.requireNonNull(obj2.getPoint(0), "route must contain at least one point");
        this.movementHandler = Objects.requireNonNull(obj3, "movementHandler may not be null");
        this.restoreWrapper = new PlayerRestoreWrapper(obj);
        this.progressBarEnabled = ServerTours.getInstance().getConfig().getBoolean("playMode.xpBarProgress");
        this.actionBarEnabled = ServerTours.getInstance().getConfig().getBoolean("playMode.actionBarEnabled");
        this.canExit = ServerTours.getInstance().getConfig().getBoolean("playMode.allowExit");
    }

    public void initialize() {
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(this.player);
        if (editingPlayer != null) {
            editingPlayer.setPreviewing(true);
        }
        this.exitLocation = this.player.getLocation();
        CraftRoutePoint point = this.route.getPoint(0);
        if (this.player.getWorld() != point.getLocation().getWorld() && !this.route.isUsePlayerWorld()) {
            this.player.teleport(point.getLocation());
        }
        this.restoreWrapper.setGameMode(this.getGameMode());
        this.restoreWrapper.clearInventory();
        this.restoreWrapper.setLevel(0);
        this.restoreWrapper.setExperience(0.0f);
        this.restoreWrapper.setMaxHealth();
        this.restoreWrapper.setCollidable(false);
        this.isGamemodeLocked = true;
        PacketUtil.setInvisible(this.player.getEntityId(), true);
        if (ServerTours.getInstance().getConfig().getBoolean("playMode.disableChat")) {
            HookHandler.get(VentureChatHook.class).ifPresent(ventureChatHook -> ventureChatHook.disableBungeeChat(this.player.getUniqueId()));
        }
        this.movementHandler.initialize(this, point.getPlaybackLocation(0.0f, this.easingFunction));
        this.actionBarTimeLeft = 5;
        this.setCurrentPoint(point);
    }

    private GameMode getGameMode() {
        return (!ServerTours.getInstance().isBedrockPlayer(this.player) && ServerTours.getInstance().getConfig().getBoolean("playMode.useSpectator")) ? GameMode.SPECTATOR : GameMode.ADVENTURE;
    }

    @Override
    public void exit() {
        this.exit(RoutePlaybackEndEvent.EndReason.API);
    }

    public void exit(RoutePlaybackEndEvent.EndReason endReason) {
        if (this.hasExited) {
            return;
        }
        RoutePlaybackEndEvent routePlaybackEndEvent = new RoutePlaybackEndEvent(this, endReason);
        Bukkit.getPluginManager().callEvent(routePlaybackEndEvent);
        if (routePlaybackEndEvent.isCancelled() && endReason != RoutePlaybackEndEvent.EndReason.QUIT) {
            this.setCurrentPoint(0);
            return;
        }
        this.hasExited = true;
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(this.player);
        if (editingPlayer != null) {
            editingPlayer.setPreviewing(false);
        } else {
            this.clearActionBar();
        }
        this.isGamemodeLocked = false;
        this.movementHandler.cleanup();
        this.restoreWrapper.restore();
        this.player.teleport(this.exitLocation);
        HookHandler.get(VentureChatHook.class).ifPresent(ventureChatHook -> ventureChatHook.restoreBungeeChat(this.player.getUniqueId()));
        Bukkit.getScheduler().runTaskLater(ServerTours.getInstance(), () -> {
            if (this.player.isValid() && !ServerTours.getInstance().getPlaybackManager().isTouringPlayer(this.player)) {
                PacketUtil.setInvisible(this.player.getEntityId(), false);
            }
        }, 5L);
        if (this.currentPoint != null) {
            this.currentPoint.executeCommands(this.player, RoutePointCommand.CommandTrigger.QUIT);
        }
        ServerTours.getInstance().getPlaybackManager().unregister(this);
    }

    public void onConfirm() {
        this.isWaitingForConfirmation = false;
        this.clearChat();
        if (!this.currentPoint.getType().isConfirmUponEnter()) {
            CraftRoutePoint nextPoint = this.currentPoint.getNextPoint();
            if (nextPoint == null) {
                this.exit(RoutePlaybackEndEvent.EndReason.FINISHED);
                return;
            }
            this.setCurrentPoint(nextPoint);
        }
    }

    private void askConfirmation() {
        this.isWaitingForConfirmation = true;
        ConfirmMode confirmMode = this.currentPoint.getConfirmMode();
        if (confirmMode == ConfirmMode.MOUSE) {
            this.player.sendMessage(ServerTours.translate("continueConfirm.mouse"));
        } else if (confirmMode == ConfirmMode.CHAT) {
            this.player.sendMessage("");
            this.player.spigot().sendMessage(new ComponentBuilder(ServerTours.translate("continueConfirm.button.text")).color(ChatColor.GREEN).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ServerTours.translate("continueConfirm.button.instruction")).create())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tour continue")).create());
            this.player.sendMessage("");
        } else if (confirmMode == ConfirmMode.KEYBOARD) {
            this.player.sendMessage(ServerTours.translate("continueConfirm.keyboard"));
        }
    }

    public void tick() {
        --this.actionBarTimeLeft;
        if (this.actionBarTimeLeft <= 0) {
            this.sendActionbar();
            this.actionBarTimeLeft = 10;
        }
        if (this.progressBarEnabled && this.player.getGameMode() != GameMode.SPECTATOR) {
            this.restoreWrapper.setExperience((float) Math.min(this.getRouteProgress(), 1.0));
        }
        if (this.isWaitingForConfirmation) {
            return;
        }
        --this.timeLeft;
        if (this.timeLeft >= 0) {
            this.movementHandler.move(this, this.currentPoint.getPlaybackLocation(this.getPointProgress(), this.easingFunction));
            return;
        }
        if (this.currentPoint.isConfirmRequired() && !this.currentPoint.getType().isConfirmUponEnter()) {
            this.askConfirmation();
            return;
        }
        CraftRoutePoint nextPoint = this.currentPoint.getNextPoint();
        if (nextPoint == null) {
            this.exit(RoutePlaybackEndEvent.EndReason.FINISHED);
            return;
        }
        this.setCurrentPoint(nextPoint);
        if (nextPoint.isConfirmRequired() && nextPoint.getType().isConfirmUponEnter()) {
            this.askConfirmation();
        }
    }

    private void sendActionbar() {
        if (!this.actionBarEnabled) {
            return;
        }
        if (ServerTours.getInstance().getConfig().getBoolean("playMode.allowExit")) {
            this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(ServerTours.translate("actionBar.watching")).color(ChatColor.AQUA).bold(true).append(new ComponentBuilder(ServerTours.translate("actionBar.shiftToExit")).color(ChatColor.GRAY).bold(false).create()).create());
        } else {
            this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(ServerTours.translate("actionBar.watching")).color(ChatColor.AQUA).bold(true).create());
        }
    }

    private void clearActionBar() {
        this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent());
    }

    @Override
    public float getPointProgress() {
        int ticksVisible = this.currentPoint.getTicksVisible();
        return (ticksVisible - this.timeLeft) / (float) ticksVisible;
    }

    @Override
    public float getRouteProgress() {
        int sum = this.route.getPoints().stream().map(CraftRoutePoint::getTicksVisible).mapToInt(Integer::intValue).sum();
        if (sum == 0) {
            return 1.0f;
        }
        int n = 0;
        for (int i = 0; i < this.currentPointIndex; ++i) {
            n += this.route.getPoint(i).getTicksVisible();
        }
        return (n + (this.currentPoint.getTicksVisible() - this.timeLeft)) / (float) sum;
    }

    private void clearChat() {
        for (int i = 0; i < 20; ++i) {
            this.player.sendMessage("");
        }
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public CraftRoute getRoute() {
        return this.route;
    }

    @Override
    public boolean isWaitingForConfirmation() {
        return this.isWaitingForConfirmation;
    }

    @Override
    public void setWaitingForConfirmation(boolean b) {
        if (b && !this.isWaitingForConfirmation) {
            this.askConfirmation();
        } else if (!b && this.isWaitingForConfirmation) {
            this.onConfirm();
        }
    }

    @Override
    public CraftRoutePoint getCurrentPoint() {
        return this.currentPoint;
    }

    @Override
    public void setCurrentPoint(int n) {
        this.setCurrentPoint(this.route.getPoint(n));
    }

    @Override
    public void setCurrentPoint(RoutePoint obj) {
        Objects.requireNonNull(obj, "point may not be null");
        if (obj instanceof CraftRoutePoint craftRoutePoint) {
            this.setCurrentPoint(craftRoutePoint);
            return;
        }
        throw new IllegalArgumentException("point must be an instance of CraftRoutePoint");
    }

    public void setCurrentPoint(CraftRoutePoint craftRoutePoint) {
        Objects.requireNonNull(craftRoutePoint, "point may not be null");
        if (this.route.indexOf(craftRoutePoint) == -1) {
            throw new IllegalArgumentException("point must be in touring player's route");
        }
        if (this.currentPoint != null) {
            this.currentPoint.executeCommands(this.player, RoutePointCommand.CommandTrigger.EXIT);
        }
        this.currentPoint = craftRoutePoint;
        this.currentPointIndex = this.route.indexOf(craftRoutePoint);
        this.timeLeft = this.currentPoint.getTicksVisible();
        if (craftRoutePoint.getTitle() != null) {
            String[] split = ChatColor.translateAlternateColorCodes('&', ServerTours.placeholders(this.player, craftRoutePoint.getTitle())).split("\\\\n");
            this.player.sendTitle((split.length > 0) ? split[0] : "", (split.length > 1) ? split[1] : "", (int) Math.floor(craftRoutePoint.getTitleFadeInTime() * 20.0f), (int) Math.floor(craftRoutePoint.getTitleStayTime() * 20.0f), (int) Math.floor(craftRoutePoint.getTitleFadeOutTime() * 20.0f));
        }
        if (craftRoutePoint.getDescription() != null) {
            boolean boolean1 = ServerTours.getInstance().getConfig().getBoolean("playMode.sendDescriptionDashes");
            this.player.sendMessage(boolean1 ? (ChatColor.AQUA + "------------------------") : "");
            Arrays.stream(ChatColor.translateAlternateColorCodes('&', ServerTours.placeholders(this.player, craftRoutePoint.getDescription())).split("\\\\n")).forEach(s -> this.player.sendMessage(ChatColor.BOLD + s));
            this.player.sendMessage(boolean1 ? (ChatColor.AQUA + "------------------------") : "");
        }
        this.currentPoint.executeCommands(this.player, RoutePointCommand.CommandTrigger.ENTER);
        Bukkit.getPluginManager().callEvent(new RoutePlaybackPointEvent(this, craftRoutePoint));
    }

    @Override
    public boolean isProgressBarEnabled() {
        return this.progressBarEnabled;
    }

    @Override
    public void setProgressBarEnabled(boolean progressBarEnabled) {
        if (!(this.progressBarEnabled = progressBarEnabled)) {
            this.restoreWrapper.setExperience(0.0f);
        }
    }

    @Override
    public boolean isActionBarEnabled() {
        return this.actionBarEnabled;
    }

    @Override
    public void setActionBarEnabled(boolean actionBarEnabled) {
        this.actionBarEnabled = actionBarEnabled;
        if (actionBarEnabled) {
            this.sendActionbar();
        } else {
            this.clearActionBar();
        }
    }

    @Override
    public boolean canExit() {
        return this.canExit;
    }

    @Override
    public void setCanExit(boolean canExit) {
        this.canExit = canExit;
    }

    @Override
    public Location getExitLocation() {
        return this.exitLocation;
    }

    @Override
    public void setExitLocation(Location obj) {
        this.exitLocation = Objects.requireNonNull(obj, "exitLocation may not be null");
    }

    @Override
    public boolean isExitByMoving() {
        return this.exitByMoving;
    }

    @Override
    public void setExitByMoving(boolean exitByMoving) {
        this.exitByMoving = exitByMoving;
    }

    public boolean isGamemodeLocked() {
        return this.isGamemodeLocked;
    }

}
