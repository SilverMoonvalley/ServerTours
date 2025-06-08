package com.melluh.servertours.route;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.Route;
import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.route.point.CraftInterpolatePoint;
import com.melluh.servertours.route.point.CraftOrbitPoint;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.route.point.CraftStationaryPoint;
import com.melluh.servertours.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.stream.Collectors;

public class CraftRoute implements Route {
    private final String name;
    private final List<CraftRoutePoint> points;
    @Getter
    private boolean usePlayerWorld;

    public CraftRoute(String name) {
        this.points = new ArrayList<>();
        this.name = name;
    }

    public CraftRoute(ConfigurationSection configurationSection) {
        this.points = new ArrayList<>();
        this.name = configurationSection.getString("name");
        this.usePlayerWorld = configurationSection.getBoolean("usePlayerWorld");
        for (ConfigurationSection configurationSection2 : Utils.sectionsAsList(configurationSection.getConfigurationSection("points"))) {
            CraftRoutePoint instantiatePoint = this.instantiatePoint(RoutePointType.valueOf(configurationSection2.getString("type")));
            instantiatePoint.loadFrom(configurationSection2);
            this.points.add(instantiatePoint);
        }
        this.updateIndexes();
    }

    public void saveTo(ConfigurationSection configurationSection) {
        configurationSection.set("name", this.name);
        configurationSection.set("usePlayerWorld", this.usePlayerWorld);
        configurationSection.set("versions.plugin", ServerTours.getInstance().getDescription().getVersion());
        configurationSection.set("versions.schema", 1);
        ConfigurationSection section = configurationSection.createSection("points");
        int i = 0;
        for (CraftRoutePoint point : this.points) {
            point.saveTo(section.createSection(Integer.toString(i)));
            ++i;
        }
    }

    private CraftRoutePoint instantiatePoint(RoutePointType routePointType) {
        return switch (routePointType) {
            case STATIONARY -> new CraftStationaryPoint(this);
            case INTERPOLATE -> new CraftInterpolatePoint(this);
            case ORBIT -> new CraftOrbitPoint(this);
        };
    }

    @Override
    public CraftRoutePoint createPoint(Location location, RoutePointType routePointType) {
        return this.insertPoint(this.points.size(), location, routePointType);
    }

    @Override
    public CraftRoutePoint insertPoint(int n, Location location, RoutePointType routePointType) {
        CraftRoutePoint instantiatePoint = this.instantiatePoint(routePointType);
        instantiatePoint.setLocation(location);
        this.points.add(n, instantiatePoint);
        this.updateIndexes();
        return instantiatePoint;
    }

    @Override
    public void removePoint(int n) {
        if (n < 0 || n >= this.points.size()) {
            return;
        }
        CraftRoutePoint craftRoutePoint = this.points.get(n);
        if (craftRoutePoint != null) {
            this.removePoint(craftRoutePoint);
        }
    }

    @Override
    public void removePoint(RoutePoint obj) {
        Objects.requireNonNull(obj, "point may not be null");
        if (obj instanceof CraftRoutePoint craftRoutePoint) {
            this.removePoint(craftRoutePoint);
            return;
        }
        throw new IllegalArgumentException("point must be an instance of CraftRoutePoint");
    }

    public void removePoint(CraftRoutePoint craftRoutePoint) {
        if (!this.points.remove(craftRoutePoint)) {
            return;
        }
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(this);
        if (editingPlayer != null) {
            editingPlayer.removePoint(craftRoutePoint);
        }
        this.updateIndexes();
    }

    public void swapPoints(int i, int j) {
        Collections.swap(this.points, i, j);
        this.updateIndexes();
    }

    public void replacePoint(CraftRoutePoint craftRoutePoint, RoutePointType routePointType) {
        int index = this.indexOf(craftRoutePoint);
        if (index == -1) {
            return;
        }
        CraftRoutePoint instantiatePoint = this.instantiatePoint(routePointType);
        instantiatePoint.copyFrom(craftRoutePoint);
        this.points.set(index, instantiatePoint);
        this.updateIndexes();
        this.getEditingPlayer().ifPresent(editingPlayer -> {
            boolean b = editingPlayer.getSelectedPoint() == craftRoutePoint;
            editingPlayer.removePoint(craftRoutePoint);
            editingPlayer.showPoint(instantiatePoint);
            if (b) {
                editingPlayer.setSelectedPoint(instantiatePoint);
            }
        });
    }

    public void updateIndexes() {
        this.recalculateSplines();
        this.getEditingPlayer().ifPresent(editingPlayer -> {
            editingPlayer.updateNames();
            editingPlayer.getPointSelectMenu().refresh();
            ServerTours.getInstance().getEditModeManager().updateChatMenu(editingPlayer.getPlayer());
        });
    }

    public void recalculateSplines() {
        this.getPointsOfType(CraftInterpolatePoint.class).forEach(CraftInterpolatePoint::recalculateSpline);
    }

    public Optional<EditingPlayer> getEditingPlayer() {
        return Optional.ofNullable(ServerTours.getInstance().getEditModeManager().getEditingPlayer(this));
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public CraftRoutePoint getPoint(int n) {
        if (n < 0 || n >= this.points.size()) {
            return null;
        }
        return this.points.get(n);
    }

    public CraftRoutePoint getPointByEntity(int n) {
        return this.points.stream().filter(craftRoutePoint -> craftRoutePoint.getEditorEntityId() == n).findFirst().orElse(null);
    }

    @Override
    public int getNumPoints() {
        return this.points.size();
    }

    @Override
    public List<CraftRoutePoint> getPoints() {
        return Collections.unmodifiableList(this.points);
    }

    public <T extends CraftRoutePoint> List<T> getPointsOfType(Class<T> clazz) {
        return (List<T>) this.points.stream().filter(craftRoutePoint -> clazz.isAssignableFrom(craftRoutePoint.getClass())).collect(Collectors.toList()).reversed();
    }

    @Override
    public int indexOf(RoutePoint obj) {
        Objects.requireNonNull(obj, "point may not be null");
        if (obj instanceof CraftRoutePoint craftRoutePoint) {
            return this.indexOf(craftRoutePoint);
        }
        throw new IllegalArgumentException("point must be an instance of CraftRoutePoint");
    }

    public int indexOf(CraftRoutePoint craftRoutePoint) {
        return this.points.indexOf(craftRoutePoint);
    }

    @Override
    public void saveToDisk() {
        ServerTours.getInstance().getPersistenceManager().saveRoute(this);
    }
}
