package org.popcraft.chunkyborder;

import org.popcraft.chunky.Selection;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunky.shape.ShapeFactory;
import org.popcraft.chunkyborder.shape.CustomPolygonShape;

import java.io.Serializable;

public class BorderData implements Serializable {
    private String world;
    private double centerX, centerZ;
    private double radiusX, radiusZ;
    private String shape;
    private String wrap;
    private double[] customPointsX;
    private double[] customPointsZ;
    private transient Shape border;

    public BorderData() {
    }

    public BorderData(final Selection selection) {
        this.world = selection.world().getName();
        this.centerX = selection.centerX();
        this.centerZ = selection.centerZ();
        this.radiusX = selection.radiusX();
        this.radiusZ = selection.radiusZ();
        this.shape = selection.shape();
    }

    public BorderData(final String world, final double[] customPointsX, final double[] customPointsZ) {
        this.world = world;
        this.customPointsX = customPointsX;
        this.customPointsZ = customPointsZ;
        this.shape = "custom";
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < customPointsX.length; i++) {
            minX = Math.min(minX, customPointsX[i]);
            maxX = Math.max(maxX, customPointsX[i]);
            minZ = Math.min(minZ, customPointsZ[i]);
            maxZ = Math.max(maxZ, customPointsZ[i]);
        }
        this.centerX = (minX + maxX) / 2;
        this.centerZ = (minZ + maxZ) / 2;
        this.radiusX = (maxX - minX) / 2;
        this.radiusZ = (maxZ - minZ) / 2;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(final String world) {
        this.world = world;
    }

    public double getCenterX() {
        return centerX;
    }

    public void setCenterX(final double centerX) {
        this.centerX = centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(final double centerZ) {
        this.centerZ = centerZ;
    }

    public double getRadiusX() {
        return radiusX;
    }

    public void setRadiusX(final double radiusX) {
        this.radiusX = radiusX;
    }

    public double getRadiusZ() {
        return radiusZ;
    }

    public void setRadiusZ(final double radiusZ) {
        this.radiusZ = radiusZ;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(final String shape) {
        this.shape = shape;
    }

    public String getWrap() {
        return BorderWrapType.fromString(wrap).name().toLowerCase();
    }

    public void setWrap(final String wrap) {
        this.wrap = BorderWrapType.fromString(wrap).name().toLowerCase();
    }

    public BorderWrapType getWrapType() {
        return BorderWrapType.fromString(getWrap());
    }

    public Shape getBorder() {
        if (border == null) {
            if (isCustomPolygon()) {
                this.border = new CustomPolygonShape(customPointsX, customPointsZ);
                this.shape = "custom";
            } else {
                this.border = ShapeFactory.getShape(asSelection().build(), false);
                this.shape = border.name();
            }
            this.wrap = BorderWrapType.fromString(wrap).name().toLowerCase();
        }
        return border;
    }

    public boolean isCustomPolygon() {
        return customPointsX != null && customPointsZ != null && customPointsX.length > 0;
    }

    public double[] getCustomPointsX() {
        return customPointsX;
    }

    public double[] getCustomPointsZ() {
        return customPointsZ;
    }

    public void setBorder(final Shape border) {
        this.border = border;
    }

    public Selection.Builder asSelection() {
        return Selection.builder(null, null).center(centerX, centerZ).radiusX(radiusX).radiusZ(radiusZ).shape(shape);
    }
}
