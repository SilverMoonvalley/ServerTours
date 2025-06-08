package com.melluh.servertours.util.math;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class CardinalSpline implements Spline {
    private final List<Double> lengths;
    private Location p0;
    private Location p1;
    private Location p2;
    private Location p3;
    private double totalLength;

    public CardinalSpline() {
        this.lengths = new ArrayList<>();
    }

    @Override
    public void initialize(Location p4, Location p5, Location p6, Location p7) {
        this.p0 = p4;
        this.p1 = p5;
        this.p2 = p6;
        this.p3 = p7;
        this.calculateLengths();
        this.totalLength = this.lengths.stream().mapToDouble(Double::doubleValue).sum();
    }

    private void calculateLengths() {
        this.lengths.clear();
        List<Location> calculateCurve = this.calculateCurve();
        for (int i = 0; i < calculateCurve.size(); ++i) {
            this.lengths.add((i > 0) ? calculateCurve.get(i).distance(calculateCurve.get(i - 1)) : 0.0);
        }
    }

    private List<Location> calculateCurve() {
        List<Location> list = new ArrayList<>();
        for (float n = 0.0f; n <= 1.0f; n += 0.005f) {
            list.add(this.calculate(n));
        }
        return list;
    }

    @Override
    public Location calculate(float n) {
        float n2 = 1.0f;
        float n3 = n * n;
        float n4 = n * n * n;
        float n5 = 2.0f * n4 - 3.0f * n3 + 1.0f;
        float n6 = -2.0f * n4 + 3.0f * n3;
        float n7 = n2 * (n4 - 2.0f * n3 + n);
        float n8 = n2 * (n4 - n3);
        return new Location(this.p1.getWorld(), n5 * this.p1.getX() + n6 * this.p2.getX() + n7 * (this.p2.getX() - this.p0.getX()) + n8 * (this.p3.getX() - this.p1.getX()), n5 * this.p1.getY() + n6 * this.p2.getY() + n7 * (this.p2.getY() - this.p0.getY()) + n8 * (this.p3.getY() - this.p1.getY()), n5 * this.p1.getZ() + n6 * this.p2.getZ() + n7 * (this.p2.getZ() - this.p0.getZ()) + n8 * (this.p3.getZ() - this.p1.getZ()));
    }

    @Override
    public Location calculateNormalized(float n) {
        double n2 = n * this.totalLength;
        double n3 = 0.0;
        int n4 = 0;
        while (n3 <= n2) {
            double doubleValue = this.lengths.get(n4);
            if (n3 + doubleValue > n2) {
                break;
            }
            n3 += doubleValue;
            if (n4 >= this.lengths.size() - 1) {
                break;
            }
            ++n4;
        }
        double doubleValue2 = this.lengths.get(n4);
        double n5 = n2 - n3;
        return this.calculate((float) (n4 - 1 + ((doubleValue2 > 0.0) ? (n5 / doubleValue2) : 0.0)) / this.lengths.size());
    }

    @Override
    public double getTotalLength() {
        return this.totalLength;
    }
}
