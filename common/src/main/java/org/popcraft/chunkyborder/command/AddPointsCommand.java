package org.popcraft.chunkyborder.command;

import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.command.ChunkyCommand;
import org.popcraft.chunky.command.CommandArguments;
import org.popcraft.chunky.platform.Sender;
import org.popcraft.chunky.platform.World;
import org.popcraft.chunky.util.Input;
import org.popcraft.chunky.util.TranslationKey;
import org.popcraft.chunkyborder.BorderData;
import org.popcraft.chunkyborder.ChunkyBorder;
import org.popcraft.chunkyborder.event.border.BorderChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AddPointsCommand implements ChunkyCommand {
    private final ChunkyBorder chunkyBorder;

    public AddPointsCommand(final ChunkyBorder chunkyBorder) {
        this.chunkyBorder = chunkyBorder;
    }

    @Override
    public void execute(final Sender sender, final CommandArguments arguments) {
        final Chunky chunky = chunkyBorder.getChunky();
        if (arguments.size() < 3) {
            sender.sendMessagePrefixed(TranslationKey.HELP_BORDER);
            return;
        }
        final Optional<World> world = arguments.next().flatMap(arg -> Input.tryWorld(chunky, arg));
        if (world.isEmpty()) {
            sender.sendMessagePrefixed(TranslationKey.HELP_BORDER);
            return;
        }
        final Optional<String> pointsArg = arguments.next();
        if (pointsArg.isEmpty()) {
            sender.sendMessagePrefixed(TranslationKey.HELP_BORDER);
            return;
        }
        final String pointsString = pointsArg.get();
        final String[] pairs = pointsString.split(";");
        if (pairs.length < 3) {
            sender.sendMessagePrefixed("format_border_addpoints_min", "3");
            return;
        }
        final double[] pointsX = new double[pairs.length];
        final double[] pointsZ = new double[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            final String[] coords = pairs[i].split(",");
            if (coords.length != 2) {
                sender.sendMessagePrefixed("format_border_addpoints_invalid", pairs[i]);
                return;
            }
            try {
                pointsX[i] = Double.parseDouble(coords[0].trim());
                pointsZ[i] = Double.parseDouble(coords[1].trim());
            } catch (NumberFormatException e) {
                sender.sendMessagePrefixed("format_border_addpoints_invalid", pairs[i]);
                return;
            }
        }
        final String worldName = world.get().getName();
        final BorderData regionData = new BorderData(worldName, pointsX, pointsZ);
        final Map<String, List<BorderData>> customRegions = chunkyBorder.getCustomRegionsMap();
        customRegions.computeIfAbsent(worldName, k -> new ArrayList<>()).add(regionData);
        final int regionIndex = customRegions.get(worldName).size();
        sender.sendMessagePrefixed("format_border_addpoints_success", worldName, String.valueOf(pairs.length), String.valueOf(regionIndex));
        chunkyBorder.saveCustomRegions();
        chunkyBorder.getChunky().getEventBus().call(new BorderChangeEvent(world.get(), regionData.getBorder()));
        // Refresh map markers with merged polygons
        chunkyBorder.getMapIntegrations().forEach(org.popcraft.chunkyborder.integration.MapIntegration::removeAllShapeMarkers);
        chunkyBorder.addBorders();
    }

    @Override
    public List<String> suggestions(final CommandArguments commandArguments) {
        if (commandArguments.size() == 2) {
            final List<String> suggestions = new ArrayList<>();
            chunkyBorder.getChunky().getServer().getWorlds().forEach(world -> suggestions.add(world.getName()));
            return suggestions;
        }
        return List.of();
    }
}
