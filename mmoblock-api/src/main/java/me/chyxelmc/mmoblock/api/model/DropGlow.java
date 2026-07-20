package me.chyxelmc.mmoblock.api.model;

/**
 * Configuration for the colored glow effect on dropped items.
 *
 * @param enabled whether the glow effect is enabled
 * @param color   the glow color name (e.g. "white", "red", "rainbow"), parsed by the NMS adapter
 */
public record DropGlow(
        boolean enabled,
        String color
) {
}
