package me.chyxelmc.mmoblock.runtime.hologram;

import org.bukkit.Material;

record RenderedHologramLine(Type type, String text, Material material, double offsetY) {

    static RenderedHologramLine text(final String text) {
        return new RenderedHologramLine(Type.TEXT, text, null, 0.0D);
    }

    static RenderedHologramLine item(final Material material) {
        return new RenderedHologramLine(Type.ITEM, null, material, 0.0D);
    }

    static RenderedHologramLine block(final Material material) {
        return new RenderedHologramLine(Type.BLOCK, null, material, 0.0D);
    }

    RenderedHologramLine withOffsetY(final double offsetY) {
        return new RenderedHologramLine(this.type, this.text, this.material, offsetY);
    }

    enum Type {
        TEXT,
        ITEM,
        BLOCK
    }
}
