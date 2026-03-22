package org.popcraft.chunkyborder.integration;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import org.popcraft.chunky.platform.World;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunkyborder.ChunkyBorderProvider;

import java.util.ArrayList;
import java.util.List;

public class BlueMapIntegration extends AbstractMapIntegration {
    private static final String MARKER_SET_ID = "chunky";
    private final List<Runnable> pendingMarkers = new ArrayList<>();
    private int markerCounter = 0;
    private BlueMapAPI blueMapAPI;
    private boolean reloading;

    public BlueMapIntegration() {
        BlueMapAPI.onEnable(blueMap -> {
            this.blueMapAPI = blueMap;
            pendingMarkers.forEach(Runnable::run);
            pendingMarkers.clear();
            if (reloading) {
                ChunkyBorderProvider.get().addBorders();
            }
        });
        BlueMapAPI.onDisable(blueMap -> {
            this.blueMapAPI = null;
            this.reloading = true;
        });
    }

    @Override
    public void addShapeMarker(final World world, final Shape shape) {
        // Not used when merged polygons are available
    }

    @Override
    public void addMergedPolygonMarker(final World world, final List<List<Vector2>> polygons) {
        if (blueMapAPI == null) {
            this.pendingMarkers.add(() -> this.addMergedPolygonMarker(world, polygons));
            return;
        }
        final MarkerSet markerSet = MarkerSet.builder().label(this.label).build();
        for (final List<Vector2> points : polygons) {
            final de.bluecolored.bluemap.api.math.Shape.Builder shapeBuilder = de.bluecolored.bluemap.api.math.Shape.builder();
            points.forEach(p -> shapeBuilder.addPoint(Vector2d.from(p.getX(), p.getZ())));
            final String markerId = MARKER_SET_ID + "_" + (markerCounter++);
            final ShapeMarker marker = ShapeMarker.builder()
                    .label(this.label)
                    .shape(shapeBuilder.build(), world.getSeaLevel())
                    .lineColor(new Color(this.color, 1f))
                    .fillColor(new Color(0))
                    .lineWidth(this.weight)
                    .depthTestEnabled(false)
                    .build();
            markerSet.getMarkers().put(markerId, marker);
        }
        blueMapAPI.getWorld(world.getName())
                .map(BlueMapWorld::getMaps)
                .ifPresent(maps -> maps.forEach(map -> map.getMarkerSets().put(MARKER_SET_ID, markerSet)));
    }

    @Override
    public void removeShapeMarker(final World world) {
        if (blueMapAPI == null) {
            return;
        }
        blueMapAPI.getWorld(world.getName())
                .map(BlueMapWorld::getMaps)
                .ifPresent(maps -> maps.forEach(map -> map.getMarkerSets().remove(MARKER_SET_ID)));
    }

    @Override
    public void removeAllShapeMarkers() {
        if (blueMapAPI == null) {
            return;
        }
        blueMapAPI.getMaps().forEach(map -> map.getMarkerSets().remove(MARKER_SET_ID));
    }
}
