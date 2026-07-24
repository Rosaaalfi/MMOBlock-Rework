package me.chyxelmc.mmoblock.utils;

import me.chyxelmc.mmoblock.api.integration.MMOCoreIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves internal placeholders ({@code {mmocore_level}}, etc.) in text strings.
 * <p>
 * These placeholders use curly-brace syntax and are resolved directly by the plugin
 * without requiring PlaceholderAPI. They are resolved BEFORE PlaceholderAPI expansion
 * so PlaceholderAPI can still process any remaining {@code %placeholder%} tokens.
 * </p>
 * <p>
 * Supported placeholders:
 * <ul>
 *   <li>{@code {mmocore_level}} - player's MMOCore class level</li>
 *   <li>{@code {mmocore_class}} - player's MMOCore class name</li>
 *   <li>{@code {mmocore_profession}} - player's MMOCore profession/collection skill level</li>
 *   <li>{@code {mmocore_level_<profession>}} - level of a specific profession</li>
 *   <li>{@code {mmocore_mana}} - player's current mana</li>
 *   <li>{@code {mmocore_stamina}} - player's current stamina</li>
 *   <li>{@code {mmocore_stellium}} - player's current stellium</li>
 *   <li>{@code {mmocore_attribute_<attribute_name>}} - player's attribute level by ID</li>
 * </ul>
 * </p>
 */
public final class InternalPlaceholderResolver {

    // Pattern for {mmocore_level_PROFESSION_NAME}
    private static final Pattern MMOCORE_LEVEL_PROFESSION = Pattern.compile("\\{mmocore_level_([a-zA-Z0-9_]+)}", Pattern.CASE_INSENSITIVE);
    // Pattern for {mmocore_attribute_ATTRIBUTE_NAME}
    private static final Pattern MMOCORE_ATTRIBUTE = Pattern.compile("\\{mmocore_attribute_([a-zA-Z0-9_]+)}", Pattern.CASE_INSENSITIVE);
    // Simple placeholders without parameters
    private static final Pattern MMOCORE_SIMPLE = Pattern.compile("\\{mmocore_(level|class|profession|mana|stamina|stellium)}", Pattern.CASE_INSENSITIVE);

    private InternalPlaceholderResolver() {
    }

    /**
     * Resolve all internal placeholders in the given text for the given player.
     *
     * @param player the player to resolve placeholders for
     * @param text   the text containing placeholders
     * @return the text with placeholders resolved, or the original text if no placeholders were found
     */
    public static String resolve(@Nullable final Player player, @Nullable final String text) {
        if (player == null || text == null || text.isEmpty()) {
            return text;
        }
        if (!MMOCoreIntegration.isAvailable()) {
            // MMOCore not installed — replace all MMOCore placeholders with empty string
            // to avoid showing raw placeholder text to the player
            return removeAllMmocorePlaceholders(text);
        }

        String result = text;

        // Resolve simple placeholders
        final Matcher simpleMatcher = MMOCORE_SIMPLE.matcher(result);
        final StringBuffer simpleBuf = new StringBuffer();
        boolean found = false;
        while (simpleMatcher.find()) {
            found = true;
            final String placeholder = simpleMatcher.group(1).toLowerCase(Locale.ROOT);
            final String replacement = resolveSimple(player, placeholder);
            simpleMatcher.appendReplacement(simpleBuf, Matcher.quoteReplacement(replacement));
        }
        if (found) {
            simpleMatcher.appendTail(simpleBuf);
            result = simpleBuf.toString();
        }

        // Resolve {mmocore_level_<profession>}
        final Matcher levelProfMatcher = MMOCORE_LEVEL_PROFESSION.matcher(result);
        final StringBuffer levelProfBuf = new StringBuffer();
        found = false;
        while (levelProfMatcher.find()) {
            found = true;
            final String profession = levelProfMatcher.group(1).toLowerCase(Locale.ROOT);
            final int level = MMOCoreIntegration.getProfessionLevel(player, profession);
            levelProfMatcher.appendReplacement(levelProfBuf, Matcher.quoteReplacement(String.valueOf(level)));
        }
        if (found) {
            levelProfMatcher.appendTail(levelProfBuf);
            result = levelProfBuf.toString();
        }

        // Resolve {mmocore_attribute_<attribute_name>}
        final Matcher attrMatcher = MMOCORE_ATTRIBUTE.matcher(result);
        final StringBuffer attrBuf = new StringBuffer();
        found = false;
        while (attrMatcher.find()) {
            found = true;
            final String attribute = attrMatcher.group(1).toLowerCase(Locale.ROOT);
            final int value = MMOCoreIntegration.getAttribute(player, attribute);
            attrMatcher.appendReplacement(attrBuf, Matcher.quoteReplacement(String.valueOf(value)));
        }
        if (found) {
            attrMatcher.appendTail(attrBuf);
            result = attrBuf.toString();
        }

        return result;
    }

    private static String resolveSimple(final Player player, final String placeholder) {
        return switch (placeholder) {
            case "level" -> String.valueOf(MMOCoreIntegration.getLevel(player));
            case "class" -> MMOCoreIntegration.getClassName(player);
            case "profession" -> {
                // In MMOCore, 'profession' refers to the player's class name.
                // For specific profession/collection skill levels, use {mmocore_level_<profession>}.
                final String className = MMOCoreIntegration.getClassName(player);
                yield className.isEmpty() ? "0" : className;
            }
            case "mana" -> String.valueOf((int) MMOCoreIntegration.getMana(player));
            case "stamina" -> String.valueOf((int) MMOCoreIntegration.getStamina(player));
            case "stellium" -> String.valueOf((int) MMOCoreIntegration.getStellium(player));
            default -> "";
        };
    }

    /**
     * Remove all {mmocore_*} placeholders from the text when MMOCore is not installed.
     */
    private static String removeAllMmocorePlaceholders(final String text) {
        // Remove {mmocore_level_PROFESSION}
        String result = MMOCORE_LEVEL_PROFESSION.matcher(text).replaceAll("");
        // Remove {mmocore_attribute_ATTR}
        result = MMOCORE_ATTRIBUTE.matcher(result).replaceAll("");
        // Remove {mmocore_SIMPLE}
        result = MMOCORE_SIMPLE.matcher(result).replaceAll("");
        return result;
    }
}
