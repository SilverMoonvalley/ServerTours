package com.melluh.servertours.util.math;

import org.bukkit.Location;

public class CardinalSpline extends ArcLengthParameterizedSpline {
    @Override
    protected Location interpolate(float n) {
        double progress = n;
        double n3 = progress * progress;
        double n4 = n3 * progress;
        double n5 = 2.0 * n4 - 3.0 * n3 + 1.0;
        double n6 = -2.0 * n4 + 3.0 * n3;
        double n7 = n4 - 2.0 * n3 + progress;
        double n8 = n4 - n3;
        return new Location(this.p1.getWorld(), n5 * this.p1.getX() + n6 * this.p2.getX() + n7 * (this.p2.getX() - this.p0.getX()) + n8 * (this.p3.getX() - this.p1.getX()), n5 * this.p1.getY() + n6 * this.p2.getY() + n7 * (this.p2.getY() - this.p0.getY()) + n8 * (this.p3.getY() - this.p1.getY()), n5 * this.p1.getZ() + n6 * this.p2.getZ() + n7 * (this.p2.getZ() - this.p0.getZ()) + n8 * (this.p3.getZ() - this.p1.getZ()));
    }

}
