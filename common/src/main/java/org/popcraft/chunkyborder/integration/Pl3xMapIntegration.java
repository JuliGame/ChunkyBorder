package org.popcraft.chunkyborder.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.layer.WorldBorderLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.util.Colors;
import net.pl3x.map.core.world.World;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.Shape;

import java.util.List;

public class Pl3xMapIntegration extends AbstractMapIntegration {
    private static final String CHUNKY_KEY = "chunky";
    private final Map<World, Layer> defaultLayers = new HashMap<>();
    private int markerCounter = 0;
    private boolean hideByDefault;
    private int priority;
    private Options markerOptions;

    public Pl3xMapIntegration() {
    }

    @Override
    public void addShapeMarker(final org.popcraft.chunky.platform.World world, final Shape shape) {
        // Not used when merged polygons are available
    }

    @Override
    public void addMergedPolygonMarker(final org.popcraft.chunky.platform.World world, final List<List<Vector2>> polygons) {
        getWorld(world).ifPresent(pl3xmapWorld -> {
            for (final List<Vector2> points : polygons) {
                final String markerId = CHUNKY_KEY + "_" + (markerCounter++);
                final Marker<?> marker = Marker.polyline(markerId, points.stream()
                        .map(point -> Point.of(point.getX(), point.getZ()))
                        .toList()).loop();
                getLayer(pl3xmapWorld).addMarker(marker.setOptions(this.markerOptions));
            }
        });
    }

    @Override
    public void removeShapeMarker(final org.popcraft.chunky.platform.World world) {
        getWorld(world).ifPresent(this::resetLayer);
    }

    @Override
    public void removeAllShapeMarkers() {
        Pl3xMap.api().getWorldRegistry().values().forEach(this::resetLayer);
    }

    @Override
    public void setOptions(final String label, final String color, final boolean hideByDefault, final int priority, final int weight) {
        super.setOptions(label, color, hideByDefault, priority, weight);
        this.hideByDefault = hideByDefault;
        this.priority = priority;
        this.markerOptions = Options.builder()
                .strokeWeight(this.weight)
                .strokeColor(Colors.setAlpha(0xFF, this.color))
                .fill(false)
                .tooltipContent(this.label)
                .tooltipSticky(true)
                .build();
    }

    private SimpleLayer getLayer(final World world) {
        final Layer defaultLayer = world.getLayerRegistry().unregister(WorldBorderLayer.KEY);
        if (!this.defaultLayers.containsKey(world)) {
            this.defaultLayers.put(world, defaultLayer);
        }
        Layer chunkyLayer = world.getLayerRegistry().get(CHUNKY_KEY);
        if (chunkyLayer == null) {
            chunkyLayer = new SimpleLayer(CHUNKY_KEY, () -> this.label)
                    .setDefaultHidden(this.hideByDefault)
                    .setPriority(1)
                    .setZIndex(this.priority);
            world.getLayerRegistry().register(chunkyLayer);
        }
        return (SimpleLayer) chunkyLayer;
    }

    private void resetLayer(final World world) {
        world.getLayerRegistry().unregister(CHUNKY_KEY);
        final Layer defaultLayer = this.defaultLayers.get(world);
        if (defaultLayer != null) {
            world.getLayerRegistry().register(defaultLayer);
            this.defaultLayers.remove(world);
        }
    }

    private Optional<World> getWorld(final org.popcraft.chunky.platform.World world) {
        return Optional.ofNullable(Pl3xMap.api().getWorldRegistry().get(world.getName())).filter(World::isEnabled);
    }
}
