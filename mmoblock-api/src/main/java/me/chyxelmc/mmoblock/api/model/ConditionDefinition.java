package me.chyxelmc.mmoblock.api.model;

public interface ConditionDefinition {
    int id();
    String type();
    String value();
    String operator();
    String compareTo();
    String placeholderTextRequire();
    String placeholderTextNotMet();
    String sendTitle();
    String sendSubtitle();
}
