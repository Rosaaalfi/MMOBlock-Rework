package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;

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
    String itemName();
    Material itemMaterial();
}
