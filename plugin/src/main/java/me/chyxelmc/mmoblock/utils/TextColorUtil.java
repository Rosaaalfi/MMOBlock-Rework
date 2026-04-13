package me.chyxelmc.mmoblock.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Map;

/**
 * Text color utility that accepts legacy '&' codes and emits MiniMessage/Adventure-safe output.
 */
public final class TextColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
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

    private static final Map<String, String> DECENT_HOLOGRAM_TAGS = Map.ofEntries(
        Map.entry("<black>", hex(NamedTextColor.BLACK)),
        Map.entry("</black>", closeHex(NamedTextColor.BLACK)),
        Map.entry("<dark_blue>", hex(NamedTextColor.DARK_BLUE)),
        Map.entry("</dark_blue>", closeHex(NamedTextColor.DARK_BLUE)),
        Map.entry("<dark_green>", hex(NamedTextColor.DARK_GREEN)),
        Map.entry("</dark_green>", closeHex(NamedTextColor.DARK_GREEN)),
        Map.entry("<dark_aqua>", hex(NamedTextColor.DARK_AQUA)),
        Map.entry("</dark_aqua>", closeHex(NamedTextColor.DARK_AQUA)),
        Map.entry("<dark_red>", hex(NamedTextColor.DARK_RED)),
        Map.entry("</dark_red>", closeHex(NamedTextColor.DARK_RED)),
        Map.entry("<dark_purple>", hex(NamedTextColor.DARK_PURPLE)),
        Map.entry("</dark_purple>", closeHex(NamedTextColor.DARK_PURPLE)),
        Map.entry("<gold>", hex(NamedTextColor.GOLD)),
        Map.entry("</gold>", closeHex(NamedTextColor.GOLD)),
        Map.entry("<gray>", hex(NamedTextColor.GRAY)),
        Map.entry("</gray>", closeHex(NamedTextColor.GRAY)),
        Map.entry("<dark_gray>", hex(NamedTextColor.DARK_GRAY)),
        Map.entry("</dark_gray>", closeHex(NamedTextColor.DARK_GRAY)),
        Map.entry("<blue>", hex(NamedTextColor.BLUE)),
        Map.entry("</blue>", closeHex(NamedTextColor.BLUE)),
        Map.entry("<green>", hex(NamedTextColor.GREEN)),
        Map.entry("</green>", closeHex(NamedTextColor.GREEN)),
        Map.entry("<aqua>", hex(NamedTextColor.AQUA)),
        Map.entry("</aqua>", closeHex(NamedTextColor.AQUA)),
        Map.entry("<red>", hex(NamedTextColor.RED)),
        Map.entry("</red>", closeHex(NamedTextColor.RED)),
        Map.entry("<light_purple>", hex(NamedTextColor.LIGHT_PURPLE)),
        Map.entry("</light_purple>", closeHex(NamedTextColor.LIGHT_PURPLE)),
        Map.entry("<yellow>", hex(NamedTextColor.YELLOW)),
        Map.entry("</yellow>", closeHex(NamedTextColor.YELLOW)),
        Map.entry("<white>", hex(NamedTextColor.WHITE)),
        Map.entry("</white>", closeHex(NamedTextColor.WHITE)),
        Map.entry("<bold>", "&l"),
        Map.entry("</bold>", "&r"),
        Map.entry("<italic>", "&o"),
        Map.entry("</italic>", "&r"),
        Map.entry("<underlined>", "&n"),
        Map.entry("</underlined>", "&r"),
        Map.entry("<strikethrough>", "&m"),
        Map.entry("</strikethrough>", "&r"),
        Map.entry("<obfuscated>", "&k"),
        Map.entry("</obfuscated>", "&r"),
        Map.entry("<reset>", "&r")
    );

    private TextColorUtil() {
    }

    public static String ampersandToMiniMessage(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

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
                final String tag = LEGACY_TAGS.get(next);
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
        return MINI_MESSAGE.deserialize(ampersandToMiniMessage(input));
    }

    public static String toLegacySection(final String input) {
        return LEGACY_SECTION.serialize(toComponent(input));
    }

    public static String toLegacySection(final Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    public static String toDecentHologramsText(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String normalized = ampersandToMiniMessage(input);
        for (final Map.Entry<String, String> entry : DECENT_HOLOGRAM_TAGS.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private static String hex(final NamedTextColor color) {
        return "<#" + String.format(Locale.ROOT, "%06x", color.value()) + ">";
    }

    private static String closeHex(final NamedTextColor color) {
        return "</#" + String.format(Locale.ROOT, "%06x", color.value()) + ">";
    }
}

