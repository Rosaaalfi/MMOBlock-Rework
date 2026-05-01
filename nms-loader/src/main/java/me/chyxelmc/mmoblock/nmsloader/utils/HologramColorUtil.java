package me.chyxelmc.mmoblock.nmsloader.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Map;

/**
 * Utility for converting hologram color inputs (ampersand, legacy §, MiniMessage) into
 * Adventure Components or MiniMessage-friendly strings. Placed in nms-loader so all
 * NMS adapters can reuse the same logic.
 */
public final class HologramColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final Map<Character, String> LEGACY_TO_MINI_MESSAGE = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private HologramColorUtil() {}

    public static String ampersandToMiniMessage(final String input) {
        if (input == null || input.isEmpty()) return "";
        final StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            final char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                final char next = Character.toLowerCase(input.charAt(i + 1));
                if (next == '#' && i + 7 < input.length()) {
                    final String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append("<#").append(hex.toLowerCase(Locale.ROOT)).append('>');
                        i += 7;
                        continue;
                    }
                }
                final String tag = LEGACY_TO_MINI_MESSAGE.get(next);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    public static Component toComponent(final String input) {
        final String safe = input == null ? "" : input;
        if (safe.isEmpty()) return Component.empty();
        if (safe.indexOf('§') >= 0 && safe.indexOf('<') < 0) {
            return LEGACY_SECTION.deserialize(safe);
        }
        final String mini = ampersandToMiniMessage(safe);
        try {
            return MINI_MESSAGE.deserialize(mini);
        } catch (final RuntimeException ex) {
            return LEGACY_SECTION.deserialize(safe);
        }
    }
}

