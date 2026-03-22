package org.popcraft.chunkyborder.shape;

import org.popcraft.chunky.Selection;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.AbstractPolygon;

import java.util.ArrayList;
import java.util.List;

public class CustomPolygonShape extends AbstractPolygon {
    private final List<Vector2> points;

    public CustomPolygonShape(final double[] pointsX, final double[] pointsZ) {
        super(buildSelection(pointsX, pointsZ), false);
        final List<Vector2> pts = new ArrayList<>();
        for (int i = 0; i < pointsX.length; i++) {
            pts.add(Vector2.of(pointsX[i], pointsZ[i]));
        }
        this.points = List.copyOf(pts);
    }

    private static Selection buildSelection(final double[] pointsX, final double[] pointsZ) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < pointsX.length; i++) {
            minX = Math.min(minX, pointsX[i]);
            maxX = Math.max(maxX, pointsX[i]);
            minZ = Math.min(minZ, pointsZ[i]);
            maxZ = Math.max(maxZ, pointsZ[i]);
        }
        final double centerX = (minX + maxX) / 2;
        final double centerZ = (minZ + maxZ) / 2;
        final double radiusX = (maxX - minX) / 2;
        final double radiusZ = (maxZ - minZ) / 2;
        return Selection.builder(null, null)
                .center(centerX, centerZ)
                .radiusX(radiusX)
                .radiusZ(radiusZ)
                .shape("custom")
                .build();
    }

    @Override
    public List<Vector2> points() {
        return points;
    }

    @Override
    public boolean isBounding(final double x, final double z) {
        boolean inside = false;
        final int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            final double xi = points.get(i).getX(), zi = points.get(i).getZ();
            final double xj = points.get(j).getX(), zj = points.get(j).getZ();
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public String name() {
        return "custom";
    }
}
