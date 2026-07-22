package me.chyxelmc.mmoblock.runtime.hologram;

record HologramPacketLayout(
        double textSpacing,
        double itemSpacing,
        double blockSpacing,
        double textOffset,
        double itemOffset,
        double blockOffset
) {

    double spacing(final RenderedHologramLine.Type type) {
        return switch (type) {
            case TEXT -> this.textSpacing;
            case ITEM -> this.itemSpacing;
            case BLOCK -> this.blockSpacing;
        };
    }

    double offset(final RenderedHologramLine.Type type) {
        return switch (type) {
            case TEXT -> this.textOffset;
            case ITEM -> this.itemOffset;
            case BLOCK -> this.blockOffset;
        };
    }
}
