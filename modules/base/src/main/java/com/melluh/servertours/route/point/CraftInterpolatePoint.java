package com.melluh.servertours.route.point;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.InterpolatePoint;
import com.melluh.servertours.api.object.PositionInterpolationMode;
import com.melluh.servertours.api.object.RoutePoint;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.api.object.RotationInterpolationMode;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import com.melluh.servertours.util.math.CardinalSpline;
import com.melluh.servertours.util.math.CatmullRomRotationSpline;
import com.melluh.servertours.util.math.CentripetalCatmullRomSpline;
import com.melluh.servertours.util.math.EasingFunction;
import com.melluh.servertours.util.math.Spline;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class CraftInterpolatePoint extends CraftRoutePoint implements InterpolatePoint {
    private final CatmullRomRotationSpline rotationSpline;
    private Spline spline;
    private Validity validity;
    private EasingFunction.Mode easingMode;

    public CraftInterpolatePoint(CraftRoute craftRoute) {
        super(craftRoute, RoutePointType.INTERPOLATE);
        this.rotationSpline = new CatmullRomRotationSpline();
        this.spline = this.createPositionSpline();
        this.validity = Validity.NO_NEXT_POINT;
        this.easingMode = EasingFunction.Mode.NONE;
    }

    public void recalculateSpline() {
        CraftRoute route = this.getRoute();
        this.spline = this.createPositionSpline();
        int index = this.getIndex();
        CraftRoutePoint point = route.getPoint(index + 1);
        if (point == null) {
            this.validity = Validity.NO_NEXT_POINT;
            return;
        }
        if (point.getLocation().getWorld() != this.getLocation().getWorld()) {
            this.validity = Validity.DIFFERENT_WORLD;
            return;
        }
        this.validity = Validity.VALID;
        Location location = point.getLocation();
        CraftRoutePoint point2 = route.getPoint(index - 1);
        boolean b = !this.isInterpolatePoint(point2);
        Location location2 = b ? this.extrapolate(this.getLocation(), location, true) : point2.getLocation();
        CraftRoutePoint point3 = route.getPoint(index + 2);
        boolean b2 = !this.isInterpolatePoint(point);
        Location location3 = (point3 != null && !b2)
                ? point3.getLocation()
                : this.extrapolate(this.getLocation(), location, false);
        this.spline.initialize(location2, this.getLocation(), location, location3);
        this.rotationSpline.initialize(location2, this.getLocation(), location, location3);
        boolean b3 = b || this.isConfirmRequired();
        boolean b4 = b2 || point.isConfirmRequired();
        if (b3 && b4) {
            this.easingMode = EasingFunction.Mode.IN_OUT;
        } else if (b3) {
            this.easingMode = EasingFunction.Mode.IN;
        } else if (b4) {
            this.easingMode = EasingFunction.Mode.OUT;
        } else {
            this.easingMode = EasingFunction.Mode.NONE;
        }
    }

    private boolean isInterpolatePoint(RoutePoint routePoint) {
        return routePoint != null && routePoint.getType() == RoutePointType.INTERPOLATE;
    }

    private Location extrapolate(Location location, Location location2, boolean b) {
        double n = location2.getX() - location.getX();
        double n2 = location2.getY() - location.getY();
        double n3 = location2.getZ() - location.getZ();
        float yawDelta = Utils.getYawDelta(location.getYaw(), location2.getYaw());
        float pitchDelta = location2.getPitch() - location.getPitch();
        float yaw = b ? location.getYaw() - yawDelta : location2.getYaw();
        float pitch = b ? location.getPitch() - pitchDelta : location2.getPitch();
        return new Location(
                location.getWorld(),
                location.getX() + (b ? (-n) : n),
                location.getY() + (b ? (-n2) : n2),
                location.getZ() + (b ? (-n3) : n3),
                yaw,
                pitch
        );
    }

    @Override
    public Validity getValidity() {
        return this.validity;
    }

    @Override
    public boolean isValid() {
        return this.validity == Validity.VALID;
    }

    @Override
    public void showParticles(Player player) {
        if (!this.isValid() || player.getWorld() != this.getLocation().getWorld()) {
            return;
        }
        double totalLength = this.spline.getTotalLength();
        if (totalLength <= 0.0) {
            player.spawnParticle(Particle.END_ROD,
                    this.spline.calculateNormalized(1.0f).add(0.0, 1.62, 0.0), 0);
            return;
        }
        int intervals = Math.max(1, (int) Math.ceil(totalLength));
        for (int sample = 0; sample <= intervals; sample++) {
            float progress = sample == intervals ? 1.0f : (float) sample / (float) intervals;
            player.spawnParticle(Particle.END_ROD,
                    this.spline.calculateNormalized(progress).add(0.0, 1.62, 0.0), 0);
        }
    }

    @Override
    public Location getPlaybackLocation(float n, EasingFunction easingFunction) {
        if (!this.isValid()) {
            return this.getLocation();
        }
        float time = easingFunction.getTime(n, this.easingMode);
        Location calculateNormalized = this.spline.calculateNormalized(time);
        Location location = this.getNextPoint().getLocation();
        if (this.getRoute().getRotationInterpolationMode() == RotationInterpolationMode.CATMULL_ROM) {
            this.rotationSpline.apply(calculateNormalized, time);
        } else {
            calculateNormalized.setYaw(this.getLocation().getYaw()
                    + Utils.getYawDelta(this.getLocation().getYaw(), location.getYaw()) * time);
            calculateNormalized.setPitch(this.getLocation().getPitch()
                    - (this.getLocation().getPitch() - location.getPitch()) * time);
        }
        return calculateNormalized;
    }

    @Override
    public List<String> getWarnings() {
        if (!ServerTours.getInstance().getConfig().getBoolean("editMode.enableWarnings")) {
            return Collections.emptyList();
        }
        if (this.validity == Validity.NO_NEXT_POINT) {
            return Collections.singletonList(ServerTours.translate("warnings.interpolateNotSet"));
        }
        if (this.validity == Validity.DIFFERENT_WORLD) {
            return Collections.singletonList(ServerTours.translate("warnings.interpolateWorlds"));
        }
        return Collections.emptyList();
    }

    @Override
    public void setConfirmRequired(boolean confirmRequired) {
        super.setConfirmRequired(confirmRequired);
        this.getRoute().recalculateSplines();
    }

    private Spline createPositionSpline() {
        return this.getRoute().getPositionInterpolationMode() == PositionInterpolationMode.LEGACY_CARDINAL
                ? new CardinalSpline()
                : new CentripetalCatmullRomSpline();
    }
}
