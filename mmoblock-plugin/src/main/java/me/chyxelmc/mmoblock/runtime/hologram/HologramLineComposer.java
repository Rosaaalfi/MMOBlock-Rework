package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.DisplayLine;
import org.bukkit.Material;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HologramLineComposer {

    private static final String PH_MAX_PROGRESS = "%mmoblock_max_progress%";
    private static final String PH_RESPAWN_TIME = "%mmoblock_respawn_time%";

    List<RenderedHologramLine> compose(
            final List<DisplayLine> displayLines,
            final HologramPacketLayout layout,
            final HologramRenderState state,
            final String progressBar,
            final String respawnTime,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
    ) {
        double cumulativeOffset = 0.0D;
        final java.util.ArrayList<RenderedHologramLine> renderedLines = new java.util.ArrayList<>();
        for (final DisplayLine line : sorted(displayLines)) {
            final RenderedHologramLine.Type slotType = resolveSlotType(line);
            final RenderedHologramLine rendered = resolveLine(
                    line,
                    state,
                    progressBar,
                    respawnTime,
                    progress,
                    maxProgress,
                    respawnTimeSeconds
            );
            if (rendered != null) {
                renderedLines.add(rendered.withOffsetY(cumulativeOffset + layout.offset(rendered.type())));
            }
            cumulativeOffset += layout.spacing(slotType);
        }
        return renderedLines;
    }

    private static List<DisplayLine> sorted(final List<DisplayLine> displayLines) {
        return displayLines.stream()
                .sorted(Comparator.comparingInt(DisplayLine::line))
                .toList();
    }

    private RenderedHologramLine resolveLine(
            final DisplayLine line,
            final HologramRenderState state,
            final String progressBar,
            final String respawnTime,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
    ) {
        final String effectiveValue = effectiveValue(line, state);
        if (isHideValue(effectiveValue)) {
            return null;
        }

        if (line.item() != null && !line.item().isBlank()) {
            final Material material = parseDisplayMaterial(line.item(), false);
            return material == null ? null : RenderedHologramLine.item(material);
        }
        if (line.block() != null && !line.block().isBlank()) {
            final Material material = parseDisplayMaterial(line.block(), true);
            return material == null ? null : RenderedHologramLine.block(material);
        }
        if (effectiveValue == null || effectiveValue.isBlank()) {
            return null;
        }

        final String renderedText = switch (state) {
            case PROGRESS -> effectiveValue.replace("{progress_bar}", progressBar);
            case DEAD -> effectiveValue.replace("{respawn_time}", respawnTime);
            case ACTIVE -> effectiveValue;
        };
        return RenderedHologramLine.text(renderedText
                .replace("%mmoblock_progress%", String.valueOf(progress))
                .replace(PH_MAX_PROGRESS, String.valueOf(maxProgress))
                .replace(PH_RESPAWN_TIME, String.valueOf(respawnTimeSeconds)));
    }

    private static String effectiveValue(final DisplayLine line, final HologramRenderState state) {
        return switch (state) {
            case ACTIVE -> line.text();
            case PROGRESS -> fallback(line.click(), line.text());
            case DEAD -> fallback(line.dead(), line.text());
        };
    }

    private static String fallback(final String preferred, final String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static boolean isHideValue(final String value) {
        if (value == null) {
            return false;
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("hide") || normalized.equals("true") || normalized.equals("none");
    }

    private static Material parseDisplayMaterial(final String raw, final boolean requireBlock) {
        final String normalized = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        final Material material = Material.matchMaterial(normalized, false);
        if (material == null || (requireBlock && !material.isBlock())) {
            return null;
        }
        return material;
    }

    private static RenderedHologramLine.Type resolveSlotType(final DisplayLine line) {
        if (line.item() != null && !line.item().isBlank()) {
            return RenderedHologramLine.Type.ITEM;
        }
        if (line.block() != null && !line.block().isBlank()) {
            return RenderedHologramLine.Type.BLOCK;
        }
        return RenderedHologramLine.Type.TEXT;
    }
}
