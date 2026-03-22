package org.popcraft.chunkyborder.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.AbstractEllipse;
import org.popcraft.chunky.shape.AbstractPolygon;
import org.popcraft.chunky.shape.Shape;

import java.util.ArrayList;
import java.util.List;

public class PolygonUnion {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int ELLIPSE_SEGMENTS = 120;

    private PolygonUnion() {
    }

    public static List<List<Vector2>> union(final List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return List.of();
        }
        Geometry combined = null;
        for (final Shape shape : shapes) {
            final Polygon jtsPolygon = shapeToJtsPolygon(shape);
            if (jtsPolygon == null) {
                continue;
            }
            if (combined == null) {
                combined = jtsPolygon;
            } else {
                combined = combined.union(jtsPolygon);
            }
        }
        if (combined == null) {
            return List.of();
        }
        return geometryToPointLists(combined);
    }

    private static Polygon shapeToJtsPolygon(final Shape shape) {
        if (shape instanceof final AbstractPolygon polygon) {
            final List<Vector2> points = polygon.points();
            final Coordinate[] coords = new Coordinate[points.size() + 1];
            for (int i = 0; i < points.size(); i++) {
                coords[i] = new Coordinate(points.get(i).getX(), points.get(i).getZ());
            }
            coords[points.size()] = coords[0];
            final LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coords);
            return GEOMETRY_FACTORY.createPolygon(ring);
        } else if (shape instanceof final AbstractEllipse ellipse) {
            final Vector2 center = ellipse.center();
            final Vector2 radii = ellipse.radii();
            final Coordinate[] coords = new Coordinate[ELLIPSE_SEGMENTS + 1];
            for (int i = 0; i < ELLIPSE_SEGMENTS; i++) {
                final double angle = 2 * Math.PI * i / ELLIPSE_SEGMENTS;
                coords[i] = new Coordinate(
                        center.getX() + radii.getX() * Math.cos(angle),
                        center.getZ() + radii.getZ() * Math.sin(angle)
                );
            }
            coords[ELLIPSE_SEGMENTS] = coords[0];
            final LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coords);
            return GEOMETRY_FACTORY.createPolygon(ring);
        }
        return null;
    }

    private static List<List<Vector2>> geometryToPointLists(final Geometry geometry) {
        final List<List<Vector2>> result = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            final Geometry part = geometry.getGeometryN(i);
            if (part instanceof final Polygon polygon) {
                final Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
                final List<Vector2> points = new ArrayList<>(coords.length - 1);
                for (int j = 0; j < coords.length - 1; j++) {
                    points.add(Vector2.of(coords[j].x, coords[j].y));
                }
                result.add(points);
            }
        }
        return result;
    }
}
