package me.chyxelmc.mmoblock.api.model;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;

public interface BlockDefinition {
    String id();
    String displayName();
    double hitboxWidth();
    double hitboxHeight();
    long respawnTimeSeconds();
    boolean randomLocationEnabled();
    double randomLocationRadius();
    boolean useRealBlockModel();
    Material realBlockMaterial();
    Sound soundOnClick();
    Sound soundOnDead();
    Sound soundOnRespawn();
    boolean particleBreak();
    Material particleMaterial();
    boolean breakAnimation();
    double displayHeight();
    List<String> allowedTools();
    List<? extends DisplayLine> displayLines();
    List<? extends ConditionDefinition> conditions();
    String displayFacingType();
    double displayFacingDistance();
    double displayFacingDetectRange();
    boolean schematicsEnabled();
    String schematicsNormalFile();
    String schematicsDeadFile();
    String schematicsPlaceFacing();
    List<String> schematicsAdjustPosNormal();
    List<String> schematicsAdjustPosDead();
    boolean bdengineEnabled();
    String bdengineModel();
    double bdengineSize();
    String bdengineOnSpawnAnimation();
    String bdengineOnClickAnimation();
    List<String> bdengineCollisionPositions();
    // modelEngine
    boolean modelEngineEnabled();
    String modelEngineModelId();
    double modelEngineModelSize();
    String modelEngineOnClickName();
    double modelEngineOnClickLerpIn();
    double modelEngineOnClickLerpOut();
    double modelEngineOnClickSpeed();
    String modelEngineOnSpawnName();
    double modelEngineOnSpawnLerpIn();
    double modelEngineOnSpawnLerpOut();
    double modelEngineOnSpawnSpeed();
    List<String> modelEngineCollisionPositions();
    String itemName();
    Material itemMaterial();
}
