package org.popcraft.chunkyborder.command;

import org.popcraft.chunky.Selection;
import org.popcraft.chunky.command.ChunkyCommand;
import org.popcraft.chunky.command.CommandArguments;
import org.popcraft.chunky.platform.Sender;
import org.popcraft.chunky.util.Formatting;
import org.popcraft.chunky.util.TranslationKey;
import org.popcraft.chunkyborder.BorderData;
import org.popcraft.chunkyborder.ChunkyBorder;

import java.util.List;
import java.util.Map;

public class ListCommand implements ChunkyCommand {
    private final ChunkyBorder chunkyBorder;

    public ListCommand(final ChunkyBorder chunkyBorder) {
        this.chunkyBorder = chunkyBorder;
    }

    @Override
    public void execute(final Sender sender, final CommandArguments arguments) {
        final Map<String, BorderData> borders = chunkyBorder.getBorders();
        final Map<String, List<BorderData>> customRegions = chunkyBorder.getCustomRegionsMap();
        if (!borders.isEmpty() || !customRegions.isEmpty()) {
            sender.sendMessagePrefixed(TranslationKey.FORMAT_BORDER_LIST);
            borders.values().forEach(border -> {
                final Selection borderSelection = border.asSelection().build();
                sender.sendMessage(TranslationKey.FORMAT_BORDER_LIST_BORDER,
                        border.getWorld(),
                        border.getShape(),
                        Formatting.number(border.getCenterX()),
                        Formatting.number(border.getCenterZ()),
                        Formatting.radius(borderSelection)
                );
            });
            customRegions.forEach((world, regions) -> {
                for (int i = 0; i < regions.size(); i++) {
                    final BorderData region = regions.get(i);
                    final int pointCount = region.getCustomPointsX() != null ? region.getCustomPointsX().length : 0;
                    sender.sendMessage("format_border_list_custom_region",
                            world,
                            String.valueOf(i + 1),
                            String.valueOf(pointCount)
                    );
                }
            });
        } else {
            sender.sendMessagePrefixed(TranslationKey.FORMAT_BORDER_LIST_NONE);
        }
    }

    @Override
    public List<String> suggestions(final CommandArguments commandArguments) {
        return List.of();
    }
}
