package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;

public record BlockDefinition(
    String id,
    double hitboxWidth,
    double hitboxHeight,
    long respawnTimeSeconds,
    boolean randomLocationEnabled,
    double randomLocationRadius,
    boolean useRealBlockModel,
    Material realBlockMaterial,
    Sound soundOnClick,
    Sound soundOnDead,
    Sound soundOnRespawn,
    boolean particleBreak,
    boolean breakAnimation,
    double displayHeight,
    List<String> allowedTools,
    List<DisplayLine> displayLines
) {
}

