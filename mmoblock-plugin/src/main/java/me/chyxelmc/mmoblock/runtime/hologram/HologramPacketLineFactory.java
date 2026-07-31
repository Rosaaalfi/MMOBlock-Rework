package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.utils.HologramAnimationUtil;
import me.chyxelmc.mmoblock.utils.TextColor;

import java.util.ArrayList;
import java.util.List;

final class HologramPacketLineFactory {

    List<NmsAdapter.HologramLine> toPacketLines(
            final List<RenderedHologramLine> lines,
            final long animationStep
    ) {
        final List<NmsAdapter.HologramLine> packetLines = new ArrayList<>(lines.size());
        for (final RenderedHologramLine line : lines) {
            switch (line.type()) {
                case TEXT -> {
                    final String resolved = HologramAnimationUtil.resolveAnimations(line.text(), animationStep);
                    packetLines.add(NmsAdapter.HologramLine.text(TextColor.toLegacySection(resolved), line.offsetY()));
                }
                case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), line.offsetY()));
                case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), line.offsetY()));
            }
        }
        return packetLines;
    }

    boolean hasAnimatedText(final List<RenderedHologramLine> lines) {
        for (final RenderedHologramLine line : lines) {
            if (line.type() == RenderedHologramLine.Type.TEXT
                    && HologramAnimationUtil.containsAnimationTag(line.text())) {
                return true;
            }
        }
        return false;
    }

    boolean hasPlaceholderApiTokens(final List<RenderedHologramLine> lines) {
        for (final RenderedHologramLine line : lines) {
            if (line.type() != RenderedHologramLine.Type.TEXT || line.text() == null) {
                continue;
            }
            final String text = line.text();
            final int first = text.indexOf('%');
            if (first >= 0) {
                final int second = text.indexOf('%', first + 1);
                if (second > first + 1) {
                    return true;
                }
            }
            if (text.contains("{condition_")) {
                return true;
            }
            if (text.contains(HologramRuntimeService.I18N_PREFIX)) {
                return true;
            }
            if (text.contains("{node_arg:")) {
                return true;
            }
        }
        return false;
    }
}
