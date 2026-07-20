package me.chyxelmc.mmoblock.model;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;

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
    // bdengine
    boolean bdengineEnabled,
    String bdengineModel,
    double bdengineSize,
    String bdengineOnSpawnAnimation,
    String bdengineOnClickAnimation,
    double bdengineOnSpawnTimelineLength,
    double bdengineOnClickTimelineLength,
    String bdengineOnSpawnAnimationMode,
    String bdengineOnClickAnimationMode,
    List<String> bdengineCollisionPositions,
    // modelEngine
    boolean modelEngineEnabled,
    String modelEngineModelId,
    double modelEngineModelSize,
    String modelEngineOnClickName,
    double modelEngineOnClickLerpIn,
    double modelEngineOnClickLerpOut,
    double modelEngineOnClickSpeed,
    String modelEngineOnDeadName,
    double modelEngineOnDeadLerpIn,
    double modelEngineOnDeadLerpOut,
    double modelEngineOnDeadSpeed,
    List<String> modelEngineCollisionPositions,
    // item
    String itemName,
    Material itemMaterial
) implements me.chyxelmc.mmoblock.api.model.BlockDefinition {
}
