package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.nms.NmsAdapter;

import java.util.List;

record HologramCachedViewerState(
        long animationStep,
        HologramPlaceholderValues placeholderValues,
        List<NmsAdapter.HologramLine> baseLines
) {
}
