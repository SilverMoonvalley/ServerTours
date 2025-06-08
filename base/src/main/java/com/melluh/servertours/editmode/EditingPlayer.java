package com.melluh.servertours.editmode;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.base.Preconditions;
import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.event.RoutePlaybackEndEvent;
import com.melluh.servertours.api.object.OrbitPoint;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.chat.*;
import com.melluh.servertours.playback.ConfirmMode;
import com.melluh.servertours.playback.CraftTouringPlayer;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.CustomItem;
import com.melluh.servertours.util.protocol.PacketUtil;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class EditingPlayer {
    @Getter
    private final Player player;
    @Getter
    private final CraftRoute editingRoute;
    @Getter
    private final PointSelectMenu pointSelectMenu;
    private ItemStack[] hotbarContents;
    @Getter
    private CraftRoutePoint selectedPoint;
    private CraftRoutePoint prevSelectedPoint;
    @Getter
    private ChatMenu chatMenu;
    private boolean isPreviewing;
    private boolean areParticlesEnabled;
    private int tickCounter;

    public EditingPlayer(Player player, CraftRoute craftRoute) {
        Preconditions.checkNotNull(player);
        Preconditions.checkNotNull(craftRoute);
        this.player = player;
        this.editingRoute = craftRoute;
        this.pointSelectMenu = new PointSelectMenu(this);
        this.areParticlesEnabled = ServerTours.getInstance().getConfig().getBoolean("editMode.particles.enabledByDefault");
    }

    public void tick() {
        ++this.tickCounter;
        if (this.tickCounter == 4) {
            this.tickCounter = 0;
            this.sendActionbar();
        }
        if (this.tickCounter == 2 && this.areParticlesEnabled && !this.isPreviewing) {
            if (ServerTours.getInstance().getConfig().getBoolean("editMode.particles.showSelectedOnly")) {
                if (this.selectedPoint != null) {
                    this.selectedPoint.showParticles(this.player);
                }
            } else {
                this.editingRoute.getPoints().forEach(craftRoutePoint -> craftRoutePoint.showParticles(this.player));
            }
        }
    }

    public void setPreviewing(boolean isPreviewing) {
        this.isPreviewing = isPreviewing;
        if (isPreviewing) {
            this.removeEntities();
            this.prevSelectedPoint = this.selectedPoint;
            this.setSelectedPoint(null);
        } else {
            this.addEntities();
            this.sendActionbar();
            this.setSelectedPoint(this.prevSelectedPoint);
            this.prevSelectedPoint = null;
        }
    }

    public void refreshEntities() {
        this.removeEntities();
        if (!this.isPreviewing) {
            this.addEntities();
        }
    }

    private void addEntities() {
        for (CraftRoutePoint craftRoutePoint : this.editingRoute.getPoints()) {
            this.showPoint(craftRoutePoint);
        }
    }

    public void showPoint(CraftRoutePoint craftRoutePoint) {
        if (this.player.getWorld() != craftRoutePoint.getLocation().getWorld()) {
            return;
        }
        PacketUtil.sendStand(this.player, craftRoutePoint.getLocation(), craftRoutePoint.getEditorEntityId());
        this.updateStandEquipment(craftRoutePoint);
        this.updateStandData(craftRoutePoint, this.selectedPoint == craftRoutePoint);
    }

    private void removeEntities() {
        for (CraftRoutePoint craftRoutePoint : this.editingRoute.getPoints()) {
            PacketUtil.removeClientEntity(this.player, craftRoutePoint.getEditorEntityId());
        }
    }

    public void movePoint(CraftRoutePoint craftRoutePoint) {
        PacketUtil.sendEntityTeleport(this.player, craftRoutePoint.getEditorEntityId(), craftRoutePoint.getLocation());
        PacketUtil.sendStandHeadPose(this.player, craftRoutePoint.getEditorEntityId(), (craftRoutePoint.getType() == RoutePointType.ORBIT) ? 0.0f : craftRoutePoint.getLocation().getPitch(), (craftRoutePoint.getType() == RoutePointType.ORBIT) ? 0.0f : craftRoutePoint.getLocation().getYaw(), 0.0f);
    }

    public void removePoint(CraftRoutePoint craftRoutePoint) {
        PacketUtil.removeClientEntity(this.player, craftRoutePoint.getEditorEntityId());
        this.setSelectedPoint(null);
        for (CraftRoutePoint craftRoutePoint2 : this.editingRoute.getPoints()) {
            PacketUtil.sendEntityData(this.player, craftRoutePoint2.getEditorEntityId(), true, ServerTours.translate("points.unselectedName", this.editingRoute.indexOf(craftRoutePoint2) + 1));
        }
    }

    public void updateNames() {
        for (CraftRoutePoint craftRoutePoint : this.editingRoute.getPoints()) {
            this.updateStandData(craftRoutePoint, this.selectedPoint == craftRoutePoint);
        }
    }

    public void setup() {
        this.hotbarContents = Arrays.copyOfRange(this.player.getInventory().getContents(), 0, 9);
        this.setupHotbar();
        this.playConfirmationSound();
        this.sendActionbar();
        this.addEntities();
    }

    public void restore() {
        if (this.isPreviewing) {
            CraftTouringPlayer touringPlayer = ServerTours.getInstance().getPlaybackManager().getTouringPlayer(this.player);
            if (touringPlayer != null) {
                touringPlayer.exit(RoutePlaybackEndEvent.EndReason.EXITED);
            }
        }
        PlayerInventory inventory = this.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            inventory.setItem(i, this.hotbarContents[i]);
        }
        this.playConfirmationSound();
        this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent());
        this.removeEntities();
        if (this.chatMenu != null) {
            this.chatMenu.sendExit(this.player);
        }
        this.getEditingRoute().saveToDisk();
    }

    private ChatMenu buildMenu(CraftRoutePoint obj) {
        ChatMenu.Builder append = new ChatMenu.Builder().append(new MenuItemHeader(this.editingRoute, obj)).append(new MenuSpacer());
        String translate = ServerTours.translate("chatMenu.labels.label");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append2 = append.append(new MenuItemString(translate, obj::getLabel).setCommand(this.getCommand("label")).setInstruction(ServerTours.translate("chatMenu.instructions.label"))).append(new MenuSpacer());
        String translate2 = ServerTours.translate("chatMenu.labels.pointType");
        Class<RoutePointType> clazz = RoutePointType.class;
        Objects.requireNonNull(obj);
        ChatMenu.Builder append3 = append2.append(new MenuItemEnum(translate2, clazz, obj::getType).setCommand(this.getCommand("type"))).append(new MenuItemNumber(ServerTours.translate("chatMenu.labels.distance"), () -> ((OrbitPoint) obj).getDistance()).setUnit(ServerTours.translate("chatMenu.units.blocks")).setCommand(this.getCommand("orbitDistance")), () -> obj.getType() == RoutePointType.ORBIT).append(new MenuItemNumber(ServerTours.translate("chatMenu.labels.speed"), () -> ((OrbitPoint) obj).getSpeed()).setUnit(ServerTours.translate("chatMenu.units.degreesPerSecond")).setCommand(this.getCommand("orbitSpeed")), () -> obj.getType() == RoutePointType.ORBIT).append(new MenuItemNumber(ServerTours.translate("chatMenu.labels.startingPoint"), () -> ((OrbitPoint) obj).getStartingPoint()).setUnit(ServerTours.translate("chatMenu.units.degrees")).setCommand(this.getCommand("orbitStartingPoint")), () -> obj.getType() == RoutePointType.ORBIT).append(new MenuItemNumber(ServerTours.translate("chatMenu.labels.heightOffset"), () -> ((OrbitPoint) obj).getHeight()).setUnit(ServerTours.translate("chatMenu.units.blocks")).setShowPlus(true).setCommand(this.getCommand("orbitHeight")), () -> obj.getType() == RoutePointType.ORBIT).append(new MenuSpacer());
        String translate3 = ServerTours.translate("chatMenu.labels.timeVisible");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append4 = append3.append(new MenuItemNumber(translate3, obj::getSecondsVisible).setUnit(ServerTours.translate("chatMenu.units.second")).setCommand(this.getCommand("secondsVisible")));
        String translate4 = ServerTours.translate("chatMenu.labels.title");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append5 = append4.append(new MenuItemString(translate4, obj::getTitle).setCommand(this.getCommand("title")).setInstruction(ServerTours.translate("chatMenu.instructions.title")));
        String translate5 = ServerTours.translate("chatMenu.labels.fadeIn");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append6 = append5.append(new MenuItemNumber(translate5, obj::getTitleFadeInTime).setUnit(ServerTours.translate("chatMenu.units.second")).setCommand(this.getCommand("titleFadeIn")), () -> obj.getTitle() != null);
        String translate6 = ServerTours.translate("chatMenu.labels.stay");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append7 = append6.append(new MenuItemNumber(translate6, obj::getTitleStayTime).setUnit(ServerTours.translate("chatMenu.units.second")).setCommand(this.getCommand("titleStay")), () -> obj.getTitle() != null);
        String translate7 = ServerTours.translate("chatMenu.labels.fadeOut");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append8 = append7.append(new MenuItemNumber(translate7, obj::getTitleFadeOutTime).setUnit(ServerTours.translate("chatMenu.units.second")).setCommand(this.getCommand("titleFadeOut")), () -> obj.getTitle() != null);
        String translate8 = ServerTours.translate("chatMenu.labels.description");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append9 = append8.append(new MenuItemString(translate8, obj::getDescription).setCommand(this.getCommand("description")).setInstruction(ServerTours.translate("chatMenu.instructions.description")));
        String translate9 = ServerTours.translate("chatMenu.labels.confirmToContinue");
        Objects.requireNonNull(obj);
        ChatMenu.Builder append10 = append9.append(new MenuItemBoolean(translate9, obj::isConfirmRequired).setDescription(ServerTours.translate("chatMenu.instructions.confirmToContinue")).setCommand(this.getCommand("confirmRequired")));
        String translate10 = ServerTours.translate("chatMenu.labels.confirmMode");
        Class<ConfirmMode> clazz2 = ConfirmMode.class;
        Objects.requireNonNull(obj);
        MenuItemEnum setCommand = new MenuItemEnum(translate10, clazz2, obj::getConfirmMode).setCommand(this.getCommand("confirmMode"));
        Objects.requireNonNull(obj);
        return append10.append(setCommand, obj::isConfirmRequired).append(new MenuItemCommands(obj)).append(new MenuSpacer()).append(new MenuItemWarnings(obj)).get();
    }

    private String getCommand(String s) {
        return "/tour pointsetting " + s + " {}";
    }

    private void updateStandData(CraftRoutePoint craftRoutePoint, boolean b) {
        PacketUtil.sendEntityData(this.player, craftRoutePoint.getEditorEntityId(), true, ServerTours.translate(b ? "points.selectedName" : "points.unselectedName", this.editingRoute.indexOf(craftRoutePoint) + 1));
    }

    public void updateStandEquipment(CraftRoutePoint craftRoutePoint) {
        PacketUtil.sendEntityEquipment(this.player, craftRoutePoint.getEditorEntityId(), EnumWrappers.ItemSlot.HEAD, new ItemStack((craftRoutePoint.getType() == RoutePointType.ORBIT) ? Material.DIAMOND_BLOCK : Material.PLAYER_HEAD));
        PacketUtil.sendStandHeadPose(this.player, craftRoutePoint.getEditorEntityId(), (craftRoutePoint.getType() == RoutePointType.ORBIT) ? 0.0f : craftRoutePoint.getLocation().getPitch(), (craftRoutePoint.getType() == RoutePointType.ORBIT) ? 0.0f : craftRoutePoint.getLocation().getYaw(), 0.0f);
    }

    public void setSelectedPoint(CraftRoutePoint selectedPoint) {
        if (this.selectedPoint != null) {
            this.updateStandData(this.selectedPoint, false);
            if (this.chatMenu != null && selectedPoint == null) {
                this.chatMenu.sendExit(this.player);
                this.chatMenu = null;
            }
        }
        if (selectedPoint != null) {
            this.updateStandData(selectedPoint, true);
            (this.chatMenu = this.buildMenu(selectedPoint)).send(this.player);
        }
        this.selectedPoint = selectedPoint;
    }

    public void placePoint() {
        CraftRoutePoint point = this.editingRoute.createPoint(this.player.getLocation(), RoutePointType.STATIONARY);
        this.showPoint(point);
        this.playConfirmationSound();
        if (this.chatMenu != null) {
            this.chatMenu.send(this.player);
        }
        if (ServerTours.getInstance().getConfig().getBoolean("editMode.selectPlacedPoint")) {
            this.setSelectedPoint(point);
        }
    }

    public void preview(int currentPoint) {
        CraftTouringPlayer showTour = ServerTours.getInstance().getPlaybackManager().showTour(this.player, this.editingRoute);
        if (showTour != null && currentPoint != 0) {
            showTour.setCurrentPoint(currentPoint);
        }
    }

    public void setParticlesEnabled(boolean areParticlesEnabled) {
        this.areParticlesEnabled = areParticlesEnabled;
        this.setupHotbar();
    }

    public boolean areParticlesEnabled() {
        return this.areParticlesEnabled;
    }

    public void exit() {
        ServerTours.getInstance().getEditModeManager().stopEditing(this.player);
    }

    public void handleItemUse(CustomItem customItem) {
        switch (customItem) {
            case CREATE_ROUTE_POINT: {
                this.placePoint();
                break;
            }
            case PREVIEW_ROUTE: {
                this.preview(0);
                break;
            }
            case SELECT_ROUTE_POINT: {
                this.pointSelectMenu.refresh();
                this.pointSelectMenu.open(this.player);
                break;
            }
            case TOGGLE_PARTICLES_OFF:
            case TOGGLE_PARTICLES_ON: {
                this.setParticlesEnabled(!this.areParticlesEnabled);
                this.playConfirmationSound();
                break;
            }
            case EXIT_EDIT_MODE: {
                this.exit();
                break;
            }
        }
    }

    private void setupHotbar() {
        PlayerInventory inventory = this.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            inventory.setItem(i, null);
        }
        inventory.setItem(1, CustomItem.CREATE_ROUTE_POINT.getItemStack());
        inventory.setItem(2, CustomItem.PREVIEW_ROUTE.getItemStack());
        inventory.setItem(3, CustomItem.SELECT_ROUTE_POINT.getItemStack());
        inventory.setItem(6, (this.areParticlesEnabled ? CustomItem.TOGGLE_PARTICLES_ON : CustomItem.TOGGLE_PARTICLES_OFF).getItemStack());
        inventory.setItem(7, CustomItem.EXIT_EDIT_MODE.getItemStack());
    }

    public void sendActionbar() {
        if (this.isPreviewing || !ServerTours.getInstance().getConfig().getBoolean("editMode.actionBarEnabled")) {
            return;
        }
        this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(ServerTours.translate("actionBar.editing")).color(ChatColor.AQUA).bold(true).append(new ComponentBuilder(ServerTours.translate("actionBar.editingDesc", this.editingRoute.getName())).color(ChatColor.GRAY).bold(false).create()).create());
    }

    public void playConfirmationSound() {
        this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 5.0f, 10.0f);
    }

    public UUID getUniqueId() {
        return this.player.getUniqueId();
    }

}
