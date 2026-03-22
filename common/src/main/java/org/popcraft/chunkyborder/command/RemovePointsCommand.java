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

public class RemovePointsCommand implements ChunkyCommand {
    private final ChunkyBorder chunkyBorder;

    public RemovePointsCommand(final ChunkyBorder chunkyBorder) {
        this.chunkyBorder = chunkyBorder;
    }

    @Override
    public void execute(final Sender sender, final CommandArguments arguments) {
        final Chunky chunky = chunkyBorder.getChunky();
        if (arguments.size() < 2) {
            sender.sendMessagePrefixed(TranslationKey.HELP_BORDER);
            return;
        }
        final Optional<World> world = arguments.next().flatMap(arg -> Input.tryWorld(chunky, arg));
        if (world.isEmpty()) {
            sender.sendMessagePrefixed(TranslationKey.HELP_BORDER);
            return;
        }
        final String worldName = world.get().getName();
        final Map<String, List<BorderData>> customRegions = chunkyBorder.getCustomRegionsMap();
        final List<BorderData> regions = customRegions.get(worldName);
        if (regions == null || regions.isEmpty()) {
            sender.sendMessagePrefixed("format_border_removepoints_none", worldName);
            return;
        }
        final Optional<String> indexArg = arguments.next();
        if (indexArg.isPresent()) {
            try {
                final int index = Integer.parseInt(indexArg.get()) - 1;
                if (index < 0 || index >= regions.size()) {
                    sender.sendMessagePrefixed("format_border_removepoints_invalid_index", String.valueOf(index + 1), String.valueOf(regions.size()));
                    return;
                }
                regions.remove(index);
                if (regions.isEmpty()) {
                    customRegions.remove(worldName);
                }
                sender.sendMessagePrefixed("format_border_removepoints_one", String.valueOf(index + 1), worldName);
            } catch (NumberFormatException e) {
                sender.sendMessagePrefixed("format_border_removepoints_invalid_index", indexArg.get(), String.valueOf(regions.size()));
                return;
            }
        } else {
            final int count = regions.size();
            customRegions.remove(worldName);
            sender.sendMessagePrefixed("format_border_removepoints_all", String.valueOf(count), worldName);
        }
        chunkyBorder.saveCustomRegions();
        // Refresh map markers with merged polygons
        chunkyBorder.getMapIntegrations().forEach(org.popcraft.chunkyborder.integration.MapIntegration::removeAllShapeMarkers);
        chunkyBorder.addBorders();
        chunkyBorder.getChunky().getEventBus().call(new BorderChangeEvent(world.get(), null));
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
