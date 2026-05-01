package me.chyxelmc.mmoblock.utils;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.ConditionDefinition;
import org.bukkit.entity.Player;

public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean isMet(final MMOBlock plugin, final Player player, final ConditionDefinition condition) {
        final String left = resolvePlaceholder(plugin, player, condition.value());
        final String right = resolvePlaceholder(plugin, player, condition.compareTo());
        return compare(left, right, condition.operator());
    }

    public static String resolvePlaceholderText(
            final MMOBlock plugin,
            final Player player,
            final ConditionDefinition condition,
            final boolean met
    ) {
        final String raw = met ? condition.placeholderTextRequire() : condition.placeholderTextNotMet();
        return resolvePlaceholder(plugin, player, raw);
    }

    public static String resolvePlaceholder(final MMOBlock plugin, final Player player, final String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return plugin.applyHologramPlaceholderApi(player, input, 0, 0, 0L);
    }

    private static boolean compare(final String left, final String right, final String operator) {
        final String op = operator == null ? "==" : operator.trim();
        if (op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=")) {
            final Double l = parseNumber(left);
            final Double r = parseNumber(right);
            if (l == null || r == null) {
                return false;
            }
            return switch (op) {
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default -> false;
            };
        }

        final Double lNum = parseNumber(left);
        final Double rNum = parseNumber(right);
        final boolean numericEqual = lNum != null && rNum != null && Double.compare(lNum, rNum) == 0;
        final boolean stringEqual = left != null && right != null && left.equalsIgnoreCase(right);
        final boolean equals = numericEqual || stringEqual;
        return op.equals("!=") ? !equals : equals;
    }

    private static Double parseNumber(final String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (final NumberFormatException exception) {
            return null;
        }
    }
}

