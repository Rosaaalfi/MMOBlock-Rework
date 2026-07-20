package me.chyxelmc.mmoblock.api.model;

/**
 * Configuration for the particle beam effect on dropped items.
 *
 * @param enabled whether the beam effect is enabled
 * @param color   the beam color name (e.g. "white", "red", "rainbow")
 * @param particle the particle type name (e.g. "REDSTONE"), defaults to REDSTONE
 */
public record DropBeam(
        boolean enabled,
        String color,
        String particle
) {
}
