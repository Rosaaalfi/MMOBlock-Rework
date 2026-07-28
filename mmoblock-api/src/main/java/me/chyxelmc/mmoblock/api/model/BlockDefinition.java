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

    // ---- Third-party block integration IDs ----

    /**
     * ItemsAdder namespaced block ID for {@code modelType.block}, e.g. {@code "itemsadder:example_block"}.
     * When non-null, the block model is placed as a real ItemsAdder custom block instead of
     * sending a fake block packet with {@link #realBlockMaterial()}.
     */
    default String itemsAdderBlockId() {
        return null;
    }

    /**
     * CraftEngine namespaced block ID for {@code modelType.block}, e.g. {@code "craftengine:custom_block"}.
     * When non-null, the block model is placed as a real CraftEngine custom block instead of
     * sending a fake block packet with {@link #realBlockMaterial()}.
     */
    default String craftEngineBlockId() {
        return null;
    }

    // ---- Dead-state block model ----

    /**
     * The type of dead block (vanilla, craftengine, itemsadder).
     * When non-null, the block will change to its dead-state model after being mined
     * instead of disappearing entirely.
     */
    default String deadBlockType() {
        return null;
    }

    /**
     * Vanilla material for the dead-state block model.
     */
    default Material realDeadBlockMaterial() {
        return null;
    }

    /**
     * ItemsAdder block ID for the dead-state block model.
     */
    default String itemsAdderDeadBlockId() {
        return null;
    }

    /**
     * CraftEngine block ID for the dead-state block model.
     */
    default String craftEngineDeadBlockId() {
        return null;
    }
}
