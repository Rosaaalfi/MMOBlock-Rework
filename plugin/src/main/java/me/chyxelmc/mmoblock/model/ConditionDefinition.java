package me.chyxelmc.mmoblock.model;

public record ConditionDefinition(
        int id,
        String type,
        String value,
        String operator,
        String compareTo,
        String placeholderTextRequire,
        String placeholderTextNotMet,
        String sendTitle,
        String sendSubtitle
) {
}

