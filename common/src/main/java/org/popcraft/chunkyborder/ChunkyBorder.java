package org.popcraft.chunkyborder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.event.EventBus;
import org.popcraft.chunky.event.command.ReloadCommandEvent;
import org.popcraft.chunky.platform.Player;
import org.popcraft.chunky.platform.World;
import org.popcraft.chunky.platform.util.Location;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.platform.util.Vector3;
import org.popcraft.chunky.shape.AbstractEllipse;
import org.popcraft.chunky.shape.AbstractPolygon;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunky.shape.ShapeUtil;
import org.popcraft.chunky.util.TranslationKey;
import org.popcraft.chunky.util.Translator;
import org.popcraft.chunky.util.Version;
import org.popcraft.chunkyborder.event.border.BorderChangeEvent;
import org.popcraft.chunkyborder.event.server.BlockBreakEvent;
import org.popcraft.chunkyborder.event.server.BlockPlaceEvent;
import org.popcraft.chunkyborder.event.server.CreatureSpawnEvent;
import org.popcraft.chunkyborder.event.server.PlayerQuitEvent;
import org.popcraft.chunkyborder.event.server.PlayerTeleportEvent;
import org.popcraft.chunkyborder.event.server.WorldLoadEvent;
import org.popcraft.chunkyborder.event.server.WorldUnloadEvent;
import org.popcraft.chunkyborder.integration.MapIntegration;
import org.popcraft.chunkyborder.platform.Config;
import org.popcraft.chunkyborder.platform.MapIntegrationLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.popcraft.chunky.util.Translator.translate;

public class ChunkyBorder {
    private static final Logger LOGGER = LogManager.getLogger(ChunkyBorder.class.getSimpleName());
    private final Chunky chunky;
    private final Config config;
    private final MapIntegrationLoader mapIntegrationLoader;
    private final List<MapIntegration> mapIntegrations = new ArrayList<>();
    private final Map<String, BorderData> borders;
    private final Map<String, List<BorderData>> customRegions;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Version version, targetVersion;

    public ChunkyBorder(final Chunky chunky, final Config config, final MapIntegrationLoader mapIntegrationLoader) {
        this.chunky = chunky;
        this.config = config;
        this.mapIntegrationLoader = mapIntegrationLoader;
        this.borders = loadBorders();
        this.customRegions = loadCustomRegions();
        this.version = loadVersion();
        this.targetVersion = loadTargetVersion();
        registerCustomTranslations();
        subscribeEvents();
        ChunkyBorderProvider.register(this);
    }

    public void disable() {
        final List<MapIntegration> maps = getMapIntegrations();
        maps.forEach(MapIntegration::removeAllShapeMarkers);
        maps.clear();
        saveBorders();
        saveCustomRegions();
        ChunkyBorderProvider.unregister();
    }

    private void registerCustomTranslations() {
        Translator.addCustomTranslation("format_border_addpoints_success", "&aCustom region added to &e%s &awith &e%s &apoints (region #%s)");
        Translator.addCustomTranslation("format_border_addpoints_min", "&cA custom region requires at least %s points");
        Translator.addCustomTranslation("format_border_addpoints_invalid", "&cInvalid coordinate pair: %s");
        Translator.addCustomTranslation("format_border_removepoints_none", "&cNo custom regions found for world &e%s");
        Translator.addCustomTranslation("format_border_removepoints_one", "&aRemoved custom region #%s from world &e%s");
        Translator.addCustomTranslation("format_border_removepoints_all", "&aRemoved &e%s &acustom region(s) from world &e%s");
        Translator.addCustomTranslation("format_border_removepoints_invalid_index", "&cInvalid region index: %s (max: %s)");
        Translator.addCustomTranslation("format_border_list_custom_region", "  &e%s &7- custom region #%s (%s points)");
    }

    private void subscribeEvents() {
        final EventBus eventBus = chunky.getEventBus();
        eventBus.subscribe(ReloadCommandEvent.class, e -> {
            getConfig().reload();
            reloadBorders();
            Translator.addCustomTranslation("custom_border_message", config.message());
        });
        eventBus.subscribe(PlayerTeleportEvent.class, e -> {
            final String worldName = e.getLocation().getWorld().getName();
            if (!hasAnyBorder(worldName)) {
                return;
            }
            final boolean insideAny = isInsideAnyBorder(worldName, e.getLocation().getX(), e.getLocation().getZ());
            if (insideAny || e.getPlayer().hasPermission("chunkyborder.bypass.move") || this.getPlayerData(e.getPlayer().getUUID()).isBypassing()) {
                return;
            }
            final Optional<BorderData> borderData = getBorder(worldName);
            e.redirect(borderData.map(BorderData::getBorder)
                    .map(border -> {
                        final Vector2 center = Vector2.of(borderData.get().getCenterX(), borderData.get().getCenterZ());
                        final World world = e.getLocation().getWorld();
                        final Vector3 locationVector = e.getLocation().toVector();
                        final Vector2 to = Vector2.of(locationVector.getX(), locationVector.getZ());
                        final List<Vector2> intersections = new ArrayList<>();
                        if (border instanceof final AbstractPolygon polygon) {
                            final List<Vector2> points = polygon.points();
                            final int size = points.size();
                            for (int i = 0; i < size; ++i) {
                                final Vector2 p1 = points.get(i);
                                final Vector2 p2 = points.get(i == size - 1 ? 0 : i + 1);
                                ShapeUtil.intersection(center.getX(), center.getZ(), to.getX(), to.getZ(), p1.getX(), p1.getZ(), p2.getX(), p2.getZ()).ifPresent(intersections::add);
                            }
                        } else if (border instanceof final AbstractEllipse ellipse) {
                            final Vector2 radii = ellipse.radii();
                            final double angle = Math.atan2(to.getZ() - center.getX(), to.getX() - center.getZ());
                            intersections.add(ShapeUtil.pointOnEllipse(center.getX(), center.getZ(), radii.getX(), radii.getZ(), angle));
                        }
                        if (intersections.isEmpty()) {
                            return world.getSpawn();
                        }
                        final Vector3 centerDirection = new Vector3(center.getX() - to.getX(), 0, center.getZ() - to.getZ()).normalize().multiply(3);
                        Vector2 closest = intersections.get(0);
                        double shortestDistance = Double.MAX_VALUE;
                        for (final Vector2 intersection : intersections) {
                            final double distance = to.distanceSquared(intersection);
                            if (distance < shortestDistance && border.isBounding(intersection.getX() + centerDirection.getX(), intersection.getZ() + centerDirection.getZ())) {
                                closest = intersection;
                                shortestDistance = distance;
                            }
                        }
                        if (shortestDistance == Double.MAX_VALUE) {
                            return world.getSpawn();
                        }
                        final Location insideBorder = new Location(world, closest.getX(), 0, closest.getZ());
                        insideBorder.add(centerDirection);
                        insideBorder.setDirection(centerDirection);
                        final int elevation = world.getElevation((int) insideBorder.getX(), (int) insideBorder.getZ());
                        if (elevation >= world.getMaxElevation()) {
                            return world.getSpawn();
                        }
                        insideBorder.setY(elevation);
                        final Player player = e.getPlayer();
                        if (config.hasMessage()) {
                            if (config.useActionBar()) {
                                player.sendActionBar("custom_border_message");
                            } else {
                                player.sendMessage("custom_border_message");
                            }
                        }
                        getPlayerData(player.getUUID()).setLastLocation(insideBorder);
                        return insideBorder;
                    })
                    .orElse(e.getLocation().getWorld().getSpawn()));
        });
        eventBus.subscribe(WorldLoadEvent.class, e -> {
            final String worldName = e.world().getName();
            final List<Shape> allShapes = getAllBorderShapes(worldName);
            if (!allShapes.isEmpty()) {
                final List<List<Vector2>> merged = org.popcraft.chunkyborder.util.PolygonUnion.union(allShapes);
                mapIntegrations.forEach(mapIntegration -> mapIntegration.addMergedPolygonMarker(e.world(), merged));
            }
        });
        eventBus.subscribe(WorldUnloadEvent.class, e -> {
            if (hasAnyBorder(e.world().getName())) {
                mapIntegrations.forEach(mapIntegration -> mapIntegration.removeShapeMarker(e.world()));
            }
        });
        eventBus.subscribe(CreatureSpawnEvent.class, e -> {
            final String worldName = e.getLocation().getWorld().getName();
            if (!hasAnyBorder(worldName)) {
                return;
            }
            e.setCancelled(!isInsideAnyBorder(worldName, e.getLocation().getX(), e.getLocation().getZ()));
        });
        eventBus.subscribe(BlockPlaceEvent.class, e -> {
            final Location location = e.getLocation();
            final String worldName = location.getWorld().getName();
            if (!hasAnyBorder(worldName)) {
                return;
            }
            final double x = ((int) location.getX()) + 0.5;
            final double z = ((int) location.getZ()) + 0.5;
            if (!isInsideAnyBorder(worldName, x, z) && !e.getPlayer().hasPermission("chunkyborder.bypass.place")) {
                e.setCancelled(true);
            }
        });
        eventBus.subscribe(BlockBreakEvent.class, e -> {
            final Location location = e.getLocation();
            final String worldName = location.getWorld().getName();
            if (!hasAnyBorder(worldName)) {
                return;
            }
            final double x = ((int) location.getX()) + 0.5;
            final double z = ((int) location.getZ()) + 0.5;
            if (!isInsideAnyBorder(worldName, x, z) && !e.getPlayer().hasPermission("chunkyborder.bypass.break")) {
                e.setCancelled(true);
            }
        });
        eventBus.subscribe(PlayerQuitEvent.class, e -> players.remove(e.player().getUUID()));
    }

    public boolean hasCompatibleChunkyVersion() {
        final Version currentVersion = chunky.getVersion();
        final Version requiredVersion = getTargetVersion();
        return currentVersion.isValid() && requiredVersion.isValid() && currentVersion.isHigherThanOrEqualTo(requiredVersion);
    }

    private Version loadVersion() {
        try (final InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            final Properties properties = new Properties();
            properties.load(input);
            return new Version(properties.getProperty("version"));
        } catch (IOException e) {
            return Version.INVALID;
        }
    }

    private Version loadTargetVersion() {
        try (final InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            final Properties properties = new Properties();
            properties.load(input);
            return new Version(properties.getProperty("target"));
        } catch (IOException e) {
            return Version.INVALID;
        }
    }

    public Chunky getChunky() {
        return chunky;
    }

    public Config getConfig() {
        return config;
    }

    public MapIntegrationLoader getMapIntegrationLoader() {
        return mapIntegrationLoader;
    }

    public List<MapIntegration> getMapIntegrations() {
        return mapIntegrations;
    }

    public Optional<BorderData> getBorder(final String world) {
        return Optional.ofNullable(borders.get(world));
    }

    public Map<String, BorderData> getBorders() {
        return borders;
    }

    public Map<UUID, PlayerData> getPlayers() {
        return players;
    }

    public PlayerData getPlayerData(final UUID uuid) {
        return this.players.computeIfAbsent(uuid, x -> new PlayerData(uuid));
    }

    public Version getVersion() {
        return version;
    }

    public Version getTargetVersion() {
        return targetVersion;
    }

    public boolean isInsideAnyBorder(final String world, final double x, final double z) {
        final Optional<BorderData> mainBorder = getBorder(world);
        if (mainBorder.isPresent() && mainBorder.get().getBorder().isBounding(x, z)) {
            return true;
        }
        for (final BorderData region : getCustomRegions(world)) {
            if (region.getBorder().isBounding(x, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyBorder(final String world) {
        return getBorder(world).isPresent() || !getCustomRegions(world).isEmpty();
    }

    public List<BorderData> getCustomRegions(final String world) {
        return customRegions.getOrDefault(world, List.of());
    }

    public Map<String, List<BorderData>> getCustomRegionsMap() {
        return customRegions;
    }

    public List<Shape> getAllBorderShapes(final String world) {
        final List<Shape> shapes = new ArrayList<>();
        getBorder(world).map(BorderData::getBorder).ifPresent(shapes::add);
        for (final BorderData region : getCustomRegions(world)) {
            shapes.add(region.getBorder());
        }
        return shapes;
    }

    public Map<String, BorderData> loadBorders() {
        try (final FileReader fileReader = new FileReader(new File(config.getDirectory().toFile(), "borders.json"))) {
            final Map<String, BorderData> loadedBorders = new Gson().fromJson(fileReader, new TypeToken<Map<String, BorderData>>() {
            }.getType());
            if (loadedBorders != null) {
                return loadedBorders;
            }
        } catch (IOException e) {
            LOGGER.warn(() -> translate(TranslationKey.BORDER_LOAD_FAILED));
        }
        return new HashMap<>();
    }

    public void saveBorders() {
        if (borders == null) {
            return;
        }
        try (final FileWriter fileWriter = new FileWriter(new File(config.getDirectory().toFile(), "borders.json"))) {
            fileWriter.write(new GsonBuilder().setPrettyPrinting().create().toJson(borders));
        } catch (IOException e) {
            LOGGER.warn(() -> translate(TranslationKey.BORDER_SAVE_FAILED));
        }
    }

    public Map<String, List<BorderData>> loadCustomRegions() {
        try (final FileReader fileReader = new FileReader(new File(config.getDirectory().toFile(), "custom_regions.json"))) {
            final Map<String, List<BorderData>> loadedRegions = new Gson().fromJson(fileReader, new TypeToken<Map<String, List<BorderData>>>() {
            }.getType());
            if (loadedRegions != null) {
                return loadedRegions;
            }
        } catch (IOException e) {
            // File may not exist yet, this is fine
        }
        return new HashMap<>();
    }

    public void saveCustomRegions() {
        if (customRegions == null) {
            return;
        }
        try (final FileWriter fileWriter = new FileWriter(new File(config.getDirectory().toFile(), "custom_regions.json"))) {
            fileWriter.write(new GsonBuilder().setPrettyPrinting().create().toJson(customRegions));
        } catch (IOException e) {
            LOGGER.warn("Failed to save custom regions");
        }
    }

    public void addBorders() {
        // Collect all worlds that have any border
        final Map<String, World> worldsByName = new HashMap<>();
        for (final BorderData borderData : borders.values()) {
            final String worldName = borderData.getWorld();
            if (worldName == null) {
                continue;
            }
            final World world = chunky.getServer().getWorld(worldName).orElse(null);
            if (world == null) {
                continue;
            }
            worldsByName.put(worldName, world);
            chunky.getEventBus().call(new BorderChangeEvent(world, borderData.getBorder()));
        }
        for (final String worldName : customRegions.keySet()) {
            if (!worldsByName.containsKey(worldName)) {
                chunky.getServer().getWorld(worldName).ifPresent(w -> worldsByName.put(worldName, w));
            }
        }
        // For each world, compute merged polygon union and add single merged marker
        for (final Map.Entry<String, World> entry : worldsByName.entrySet()) {
            final String worldName = entry.getKey();
            final World world = entry.getValue();
            final List<Shape> allShapes = getAllBorderShapes(worldName);
            if (allShapes.isEmpty()) {
                continue;
            }
            final List<List<Vector2>> merged = org.popcraft.chunkyborder.util.PolygonUnion.union(allShapes);
            mapIntegrations.forEach(mapIntegration -> mapIntegration.addMergedPolygonMarker(world, merged));
        }
    }

    public void reloadBorders() {
        mapIntegrations.forEach(MapIntegration::removeAllShapeMarkers);
        borders.clear();
        borders.putAll(loadBorders());
        customRegions.clear();
        customRegions.putAll(loadCustomRegions());
        addBorders();
    }

    public Logger getLogger() {
        return LOGGER;
    }
}
