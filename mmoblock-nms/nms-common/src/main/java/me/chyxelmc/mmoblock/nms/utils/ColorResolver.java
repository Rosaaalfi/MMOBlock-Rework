package me.chyxelmc.mmoblock.nms.utils;

import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.Locale;
import java.util.Map;

/**
 * Shared utility for mapping color names (e.g. "red", "dark_blue", "gold") to
 * Bukkit {@link ChatColor} (for glow team packets) or {@link Color} (for
 * particle beams). Consolidates previously duplicated switch logic in
 * {@code NmsAdapter.resolveGlowChatColor()} and {@code DropService.resolveBeamColor()}.
 */
public final class ColorResolver {

    // Maps normalized color name → (r, g, b) int triple
    private static final Map<String, int[]> COLOR_MAP = Map.ofEntries(
            Map.entry("black",         new int[]{0x00, 0x00, 0x00}),
            Map.entry("navy",          new int[]{0x00, 0x00, 0xAA}),
            Map.entry("dark_blue",     new int[]{0x00, 0x00, 0xAA}),
            Map.entry("dark_green",    new int[]{0x00, 0xAA, 0x00}),
            Map.entry("teal",          new int[]{0x00, 0xAA, 0xAA}),
            Map.entry("dark_aqua",     new int[]{0x00, 0xAA, 0xAA}),
            Map.entry("dark_cyan",     new int[]{0x00, 0xAA, 0xAA}),
            Map.entry("maroon",        new int[]{0xAA, 0x00, 0x00}),
            Map.entry("dark_red",      new int[]{0xAA, 0x00, 0x00}),
            Map.entry("purple",        new int[]{0xAA, 0x00, 0xAA}),
            Map.entry("dark_purple",   new int[]{0xAA, 0x00, 0xAA}),
            Map.entry("orange",        new int[]{0xFF, 0xAA, 0x00}),
            Map.entry("gold",          new int[]{0xFF, 0xAA, 0x00}),
            Map.entry("silver",        new int[]{0xAA, 0xAA, 0xAA}),
            Map.entry("gray",          new int[]{0xAA, 0xAA, 0xAA}),
            Map.entry("grey",          new int[]{0xAA, 0xAA, 0xAA}),
            Map.entry("dark_gray",     new int[]{0x55, 0x55, 0x55}),
            Map.entry("dark_grey",     new int[]{0x55, 0x55, 0x55}),
            Map.entry("blue",          new int[]{0x55, 0x55, 0xFF}),
            Map.entry("lime",          new int[]{0x55, 0xFF, 0x55}),
            Map.entry("green",         new int[]{0x55, 0xFF, 0x55}),
            Map.entry("cyan",          new int[]{0x55, 0xFF, 0xFF}),
            Map.entry("aqua",          new int[]{0x55, 0xFF, 0xFF}),
            Map.entry("red",           new int[]{0xFF, 0x55, 0x55}),
            Map.entry("pink",          new int[]{0xFF, 0x55, 0xFF}),
            Map.entry("fuchsia",       new int[]{0xFF, 0x55, 0xFF}),
            Map.entry("magenta",       new int[]{0xFF, 0x55, 0xFF}),
            Map.entry("light_purple",  new int[]{0xFF, 0x55, 0xFF}),
            Map.entry("yellow",        new int[]{0xFF, 0xFF, 0x55}),
            Map.entry("olive",         new int[]{0xFF, 0xFF, 0x55}),
            Map.entry("white",         new int[]{0xFF, 0xFF, 0xFF})
    );

    private static final int[] WHITE_RGB = new int[]{0xFF, 0xFF, 0xFF};

    private ColorResolver() {
    }

    /**
     * Normalizes a raw color name: trims, lowercases, replaces hyphens/spaces with underscores.
     */
    private static String normalize(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    /**
     * Returns the RGB triple for the given color name, or white as fallback.
     */
    private static int[] lookupRgb(final String normalized) {
        if (normalized.isEmpty()) {
            return WHITE_RGB;
        }
        final int[] rgb = COLOR_MAP.get(normalized);
        return rgb != null ? rgb : WHITE_RGB;
    }

    /**
     * Maps a color name string to a Bukkit {@link ChatColor}.
     * <p>
     * Supports standard Bukkit {@code ChatColor} enum names plus common aliases
     * (e.g. "navy", "teal", "orange", "cyan", "maroon").
     *
     * @param raw the color name (may be null or blank)
     * @return the resolved ChatColor, never null (defaults to {@link ChatColor#WHITE})
     */
    @SuppressWarnings("deprecation")
    public static ChatColor resolveChatColor(final String raw) {
        final String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return ChatColor.WHITE;
        }
        // Try direct ChatColor enum lookup first (handles "RED", "DARK_BLUE", etc.)
        try {
            return ChatColor.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            // Fall through to alias map
        }
        // Alias → ChatColor mapping via ordinal/RGB
        final int[] rgb = lookupRgb(normalized);
        return rgbToChatColor(rgb);
    }

    /**
     * Maps a color name string to a Bukkit {@link Color}.
     * <p>
     * Supports the same names and aliases as {@link #resolveChatColor(String)}.
     *
     * @param raw the color name (may be null or blank)
     * @return the resolved Color, never null (defaults to {@link Color#WHITE})
     */
    public static Color resolveBukkitColor(final String raw) {
        final int[] rgb = lookupRgb(normalize(raw));
        return Color.fromRGB(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Best-effort conversion from an RGB triple to the closest Bukkit ChatColor.
     */
    @SuppressWarnings("deprecation")
    private static ChatColor rgbToChatColor(final int[] rgb) {
        // Scan all ChatColor values for an exact RGB match
        ChatColor closest = ChatColor.WHITE;
        int closestDistance = Integer.MAX_VALUE;
        for (final ChatColor cc : ChatColor.values()) {
            if (!cc.isColor()) continue;
            // ChatColor.getColor() returns the legacy ordinal-based color;
            // use the known mapping instead via the color name.
            final int[] mapped = COLOR_MAP.get(cc.name().toLowerCase(Locale.ROOT));
            if (mapped == null) continue;
            final int dr = rgb[0] - mapped[0];
            final int dg = rgb[1] - mapped[1];
            final int db = rgb[2] - mapped[2];
            final int dist = dr * dr + dg * dg + db * db;
            if (dist == 0) return cc;
            if (dist < closestDistance) {
                closestDistance = dist;
                closest = cc;
            }
        }
        return closest;
    }
}
