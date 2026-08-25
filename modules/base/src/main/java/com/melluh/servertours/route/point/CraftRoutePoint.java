package com.melluh.servertours.route.point;

import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.playback.ConfirmMode;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.route.RoutePointCommand;
import com.melluh.servertours.util.Utils;
import com.melluh.servertours.util.math.EasingFunction;
import com.melluh.servertours.util.protocol.PacketUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class CraftRoutePoint implements RoutePoint {
    private final CraftRoute route;
    private final RoutePointType type;
    @Getter
    private final int editorEntityId;
    @Getter
    private final List<RoutePointCommand> commands;
    @Setter
    private Location location;
    @Getter
    private float secondsVisible;
    private boolean isConfirmRequired;
    @Setter
    @Getter
    private ConfirmMode confirmMode;
    private String title;
    private String description;
    private String label;
    private float titleFadeIn;
    private float titleStay;
    private float titleFadeOut;

    public CraftRoutePoint(CraftRoute route, RoutePointType type) {
        this.editorEntityId = PacketUtil.generateEntityId();
        this.commands = new ArrayList<>();
        this.secondsVisible = 5.0f;
        this.isConfirmRequired = false;
        this.confirmMode = ConfirmMode.MOUSE;
        this.route = route;
        this.type = type;
    }

    public void copyFrom(CraftRoutePoint craftRoutePoint) {
        this.location = craftRoutePoint.location.clone();
        this.secondsVisible = craftRoutePoint.secondsVisible;
        this.isConfirmRequired = craftRoutePoint.isConfirmRequired;
        this.confirmMode = craftRoutePoint.confirmMode;
        this.title = craftRoutePoint.title;
        this.description = craftRoutePoint.description;
        this.label = craftRoutePoint.label;
        this.commands.addAll(craftRoutePoint.commands);
    }

    public void loadFrom(ConfigurationSection configurationSection) {
        this.location = Utils.loadLocation(Objects.requireNonNull(configurationSection.getConfigurationSection("loc"), "loc is required"));
        this.secondsVisible = (float) configurationSection.getDouble("visibleTime");
        this.isConfirmRequired = configurationSection.getBoolean("confirmRequired");
        this.confirmMode = ConfirmMode.valueOf(configurationSection.getString("confirmMode", "MOUSE"));
        this.title = configurationSection.getString("title");
        this.description = configurationSection.getString("description");
        this.label = configurationSection.getString("label");
        this.titleFadeIn = (float) configurationSection.getDouble("titleTimings.fadeIn", 0.5);
        this.titleStay = (float) configurationSection.getDouble("titleTimings.stay", 2.0);
        this.titleFadeOut = (float) configurationSection.getDouble("titleTimings.fadeOut", 0.5);
        ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection("commands");
        if (configurationSection2 != null) {
            for (String string : configurationSection2.getKeys(false)) {
                ConfigurationSection configurationSection3 = configurationSection2.getConfigurationSection(string);
                if (configurationSection3 == null) {
                    continue;
                }
                this.commands.add(new RoutePointCommand(configurationSection3.contains("executor") ? RoutePointCommand.CommandExecutorType.valueOf(configurationSection3.getString("executor")) : RoutePointCommand.CommandExecutorType.CONSOLE, configurationSection3.contains("trigger") ? RoutePointCommand.CommandTrigger.valueOf(configurationSection3.getString("trigger")) : RoutePointCommand.CommandTrigger.EXIT, configurationSection3.getString("cmd")));
            }
        }
    }

    public void saveTo(ConfigurationSection configurationSection) {
        configurationSection.set("type", this.type.name());
        Utils.saveLocation(this.location, configurationSection.createSection("loc"));
        configurationSection.set("visibleTime", this.secondsVisible);
        configurationSection.set("confirmRequired", this.isConfirmRequired);
        configurationSection.set("confirmMode", this.confirmMode.name());
        configurationSection.set("title", this.title);
        configurationSection.set("description", this.description);
        configurationSection.set("label", this.label);
        configurationSection.set("titleTimings.fadeIn", this.titleFadeIn);
        configurationSection.set("titleTimings.stay", this.titleStay);
        configurationSection.set("titleTimings.fadeOut", this.titleFadeOut);
        ConfigurationSection section = configurationSection.createSection("commands");
        for (int i = 0; i < this.commands.size(); ++i) {
            RoutePointCommand routePointCommand = this.commands.get(i);
            ConfigurationSection section2 = section.createSection(Integer.toString(i));
            section2.set("executor", routePointCommand.getExecutorType().name());
            section2.set("trigger", routePointCommand.getTriggerType().name());
            section2.set("cmd", routePointCommand.getCommand());
        }
    }

    public void executeCommands(Player player, RoutePointCommand.CommandTrigger commandTrigger) {
        this.commands.stream().filter(routePointCommand -> routePointCommand.getTriggerType() == commandTrigger).forEach(routePointCommand2 -> routePointCommand2.execute(player));
    }

    public void addCommand(RoutePointCommand routePointCommand) {
        this.commands.add(routePointCommand);
    }

    public void removeCommand(int n) {
        if (n < 0 || n >= this.commands.size()) {
            return;
        }
        this.commands.remove(n);
    }

    public abstract void showParticles(Player p0);

    public abstract Location getPlaybackLocation(float p0, EasingFunction p1);

    @Override
    public void move(Location location) {
        this.location = location;
        this.route.getEditingPlayer().ifPresent(editingPlayer -> editingPlayer.movePoint(this));
        this.route.updateIndexes();
    }

    public List<String> getWarnings() {
        return Collections.emptyList();
    }

    @Override
    public void clearLabel() {
        this.setLabel(null);
    }

    @Override
    public String getLabel() {
        return this.label;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public void clearTitle() {
        this.setTitle(null);
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public float getTitleFadeInTime() {
        return this.titleFadeIn;
    }

    @Override
    public void setTitleFadeInTime(float a) {
        this.titleFadeIn = Math.max(a, 0.0f);
    }

    @Override
    public float getTitleStayTime() {
        return this.titleStay;
    }

    @Override
    public void setTitleStayTime(float a) {
        this.titleStay = Math.max(a, 0.0f);
    }

    @Override
    public float getTitleFadeOutTime() {
        return this.titleFadeOut;
    }

    @Override
    public void setTitleFadeOutTime(float a) {
        this.titleFadeOut = Math.max(a, 0.0f);
    }

    @Override
    public void clearDescription() {
        this.setDescription(null);
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConfirmRequired() {
        return this.isConfirmRequired;
    }

    public void setConfirmRequired(boolean isConfirmRequired) {
        this.isConfirmRequired = isConfirmRequired;
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public int getTicksVisible() {
        return (int) (this.secondsVisible * 20.0f);
    }

    @Override
    public void setTicksVisible(int n) {
        int targetTicks = Math.max(n, 0);
        float seconds = targetTicks / 20.0f;
        // Preserve the integer API exactly despite values such as 7/20 being
        // represented a fraction below 0.35f and truncating back to 6 ticks.
        while ((int) (seconds * 20.0f) < targetTicks) {
            seconds = Math.nextUp(seconds);
        }
        this.setSecondsVisible(seconds);
    }

    public void setSecondsVisible(float a) {
        this.secondsVisible = Math.max(a, 0.0f);
    }

    @Override
    public RoutePointType getType() {
        return this.type;
    }

    public CraftRoutePoint getNextPoint() {
        return this.route.getPoint(this.route.indexOf(this) + 1);
    }

    @Override
    public CraftRoute getRoute() {
        return this.route;
    }
}
