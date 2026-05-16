package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;

public record BlockDefinition(
    String id,
    String displayName,
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
    Material particleMaterial,
    boolean breakAnimation,
    double displayHeight,
    List<String> allowedTools,
    List<DisplayLine> displayLines,
    List<ConditionDefinition> conditions,
    // displayFacing
    String displayFacingType,
    double displayFacingDistance,
    double displayFacingDetectRange,
    // schematics
    boolean schematicsEnabled,
    String schematicsNormalFile,
    String schematicsDeadFile,
    String schematicsPlaceFacing,
    List<String> schematicsAdjustPosNormal,
    List<String> schematicsAdjustPosDead,
    // item
    String itemName,
    Material itemMaterial
) implements me.chyxelmc.mmoblock.api.model.BlockDefinition {
}
