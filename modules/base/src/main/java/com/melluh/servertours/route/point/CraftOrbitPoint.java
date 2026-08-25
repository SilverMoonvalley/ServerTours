package com.melluh.servertours.route.point;

import com.melluh.servertours.api.object.OrbitPoint;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.math.EasingFunction;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CraftOrbitPoint extends CraftRoutePoint implements OrbitPoint {
    private float distance;
    private float speed;
    private float height;
    private float startingPoint;

    public CraftOrbitPoint(CraftRoute craftRoute) {
        super(craftRoute, RoutePointType.ORBIT);
        this.distance = 5.0f;
        this.speed = 3.0f;
    }

    @Override
    public void loadFrom(ConfigurationSection configurationSection) {
        super.loadFrom(configurationSection);
        ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection("orbit");
        if (configurationSection2 != null) {
            this.distance = (float) configurationSection2.getDouble("distance");
            this.speed = (float) configurationSection2.getDouble("speed");
            this.height = (float) configurationSection2.getDouble("height");
            this.startingPoint = (float) configurationSection2.getDouble("startingPoint");
        }
    }

    @Override
    public void saveTo(ConfigurationSection configurationSection) {
        super.saveTo(configurationSection);
        ConfigurationSection section = configurationSection.createSection("orbit");
        section.set("distance", this.distance);
        section.set("speed", this.speed);
        section.set("height", this.height);
        section.set("startingPoint", this.startingPoint);
    }

    @Override
    public void showParticles(Player player) {
        int n = 5 * Math.round(this.startingPoint / 5.0f);
        for (double angdeg = 0.0; angdeg < 360.0; angdeg += 5.0) {
            double radians = Math.toRadians(angdeg);
            boolean b = angdeg == n;
            player.spawnParticle(b ? Particle.HAPPY_VILLAGER : Particle.END_ROD, this.getLocation().clone().add(Math.cos(radians) * this.distance, this.height + 1.62 - (b ? 0.1 : 0.0), Math.sin(radians) * this.distance), 0);
        }
    }

    @Override
    public Location getPlaybackLocation(float n, EasingFunction easingFunction) {
        double radians = Math.toRadians(this.startingPoint + this.speed * (int) (n * this.getTicksVisible()));
        Location add = this.getLocation().clone().add(Math.cos(radians) * this.distance, this.height, Math.sin(radians) * this.distance);
        Vector subtract = this.getLocation().toVector().subtract(add.toVector());
        float n2 = (float) Math.toDegrees(Math.atan2(subtract.getZ(), subtract.getX()));
        float n3 = (float) Math.toDegrees(Math.asin(subtract.getY() / this.distance));
        add.setYaw(n2 - 90.0f);
        add.setPitch(-n3);
        return add;
    }

    @Override
    public float getStartingPoint() {
        return this.startingPoint;
    }

    @Override
    public void setStartingPoint(float startingPoint) {
        if (startingPoint < 0.0f) {
            startingPoint += 360.0f;
        }
        if (startingPoint >= 360.0f) {
            startingPoint -= 360.0f;
        }
        this.startingPoint = startingPoint;
    }

    @Override
    public float getDistance() {
        return this.distance;
    }

    @Override
    public void setDistance(float b) {
        this.distance = Math.max(1.0f, b);
    }

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public void setSpeed(float b) {
        this.speed = Math.max(0.1f, b);
    }

    @Override
    public float getHeight() {
        return this.height;
    }

    @Override
    public void setHeight(float height) {
        this.height = height;
    }
}
