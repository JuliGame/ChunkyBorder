package org.popcraft.chunkyborder.integration;

import org.popcraft.chunky.integration.Integration;
import org.popcraft.chunky.platform.World;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.Shape;

import java.util.List;

public interface MapIntegration extends Integration {
    void addShapeMarker(World world, Shape shape);

    void addMergedPolygonMarker(World world, List<List<Vector2>> polygons);

    void removeShapeMarker(World world);

    void removeAllShapeMarkers();

    void setOptions(String label, String color, boolean hideByDefault, int priority, int weight);
}
