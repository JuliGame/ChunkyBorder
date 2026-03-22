package org.popcraft.chunkyborder.integration;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerDescription;
import org.dynmap.markers.MarkerSet;
import org.popcraft.chunky.platform.World;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.Shape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynmapIntegration extends AbstractMapIntegration {
    private static final String MARKER_SET_ID = "chunky.markerset";
    private final MarkerAPI markerAPI;
    private MarkerSet markerSet;
    private final Map<String, List<MarkerDescription>> markers;

    public DynmapIntegration(final DynmapCommonAPI dynmapAPI) {
        this.markerAPI = dynmapAPI.getMarkerAPI();
        this.markerSet = getOrCreateMarkerSet();
        this.markers = new HashMap<>();
    }

    private MarkerSet getOrCreateMarkerSet() {
        MarkerSet set = markerAPI.getMarkerSet(MARKER_SET_ID);
        if (set == null) {
            set = markerAPI.createMarkerSet(MARKER_SET_ID, this.label, null, false);
        }
        return set;
    }

    @Override
    public void addShapeMarker(final World world, final Shape shape) {
        // Not used when merged polygons are available
    }

    @Override
    public void addMergedPolygonMarker(final World world, final List<List<Vector2>> polygons) {
        final String dynmapWorldName = adaptWorldName(world.getName());
        for (final List<Vector2> points : polygons) {
            final int size = points.size();
            final double[] pointsX = new double[size];
            final double[] pointsZ = new double[size];
            for (int i = 0; i < size; ++i) {
                pointsX[i] = points.get(i).getX();
                pointsZ[i] = points.get(i).getZ();
            }
            final AreaMarker marker = markerSet.createAreaMarker(null, this.label, false, dynmapWorldName, pointsX, pointsZ, false);
            marker.setLineStyle(this.weight, 1f, color);
            marker.setFillStyle(0f, 0x000000);
            markers.computeIfAbsent(world.getName(), k -> new ArrayList<>()).add(marker);
        }
    }

    @Override
    public void removeShapeMarker(final World world) {
        final List<MarkerDescription> worldMarkers = markers.remove(world.getName());
        if (worldMarkers != null) {
            worldMarkers.forEach(MarkerDescription::deleteMarker);
        }
    }

    @Override
    public void removeAllShapeMarkers() {
        if (markerSet != null) {
            markerSet.deleteMarkerSet();
        }
        markers.clear();
        markerSet = getOrCreateMarkerSet();
    }

    @Override
    public void setOptions(final String label, final String color, final boolean hideByDefault, final int priority, final int weight) {
        super.setOptions(label, color, hideByDefault, priority, weight);
        if (markerSet != null) {
            markerSet.setHideByDefault(hideByDefault);
            markerSet.setLayerPriority(priority);
        }
    }

    private String adaptWorldName(final String worldName) {
        return switch (worldName) {
            case "minecraft:overworld" -> "world";
            case "minecraft:the_nether" -> "DIM-1";
            case "minecraft:the_end" -> "DIM1";
            default -> worldName.indexOf(':') < 0 ? worldName : worldName.replace(':', '_');
        };
    }
}
