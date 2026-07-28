package me.chyxelmc.mmoblock.model;

import java.util.List;

import me.chyxelmc.mmoblock.api.model.DropBeam;
import me.chyxelmc.mmoblock.api.model.DropGlow;
import me.chyxelmc.mmoblock.api.model.DropPopup;
import me.chyxelmc.mmoblock.api.model.DropType;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Domain model for a block definition. Merges the former {@code BlockDefinitionImpl}
 * with its supplementary types ({@link ConditionDefinition}, {@link DisplayLine},
 * {@link DropEntry}, {@link ToolAction}) as inner records for a cleaner domain
 * boundary.
 *
 * @param id                    Unique identifier for this block type
 * @param displayName           Human-readable display name
 * @param hitboxWidth           Interaction hitbox width
 * @param hitboxHeight          Interaction hitbox height
 * @param respawnTimeSeconds    Respawn delay after being mined
 * @param randomLocationEnabled Whether to spawn at a random offset
 * @param randomLocationRadius  Radius for random location
 * @param useRealBlockModel     Whether to use in-world block model
 * @param realBlockMaterial     The block material to fake
 * @param soundOnClick          Sound played when clicked
 * @param soundOnDead           Sound played when broken
 * @param soundOnRespawn        Sound played on respawn
 * @param particleBreak         Whether to show break particles
 * @param particleMaterial      Material for break particles
 * @param breakAnimation        Whether to show break animation
 * @param displayHeight         Height offset for hologram display
 * @param allowedTools          Allowed tool group IDs
 * @param displayLines          Hologram display lines (inner type)
 * @param conditions            Condition definitions (inner type)
 * @param displayFacingType     Facing detection type
 * @param displayFacingDistance Facing detection distance
 * @param displayFacingDetectRange Facing detection range
 * @param schematicsEnabled     Whether schematics are enabled
 * @param schematicsNormalFile  Schematic file for normal state
 * @param schematicsDeadFile    Schematic file for dead state
 * @param schematicsPlaceFacing Facing for schematic placement
 * @param schematicsAdjustPosNormal Position adjustment for normal schematic
 * @param schematicsAdjustPosDead    Position adjustment for dead schematic
 * @param bdengineEnabled       Whether BDEngine model is enabled
 * @param bdengineModel         BDEngine model ID
 * @param bdengineSize          BDEngine model scale
 * @param bdengineOnSpawnAnimation BDEngine spawn animation name
 * @param bdengineOnClickAnimation  BDEngine click animation name
 * @param bdengineOnSpawnTimelineLength BDEngine spawn timeline length
 * @param bdengineOnClickTimelineLength BDEngine click timeline length
 * @param bdengineOnSpawnAnimationMode BDEngine spawn animation mode
 * @param bdengineOnClickAnimationMode BDEngine click animation mode
 * @param bdengineCollisionPositions   BDEngine collision positions
 * @param modelEngineEnabled    Whether ModelEngine is enabled
 * @param modelEngineModelId    ModelEngine model ID
 * @param modelEngineModelSize  ModelEngine model scale
 * @param modelEngineOnClickName       ModelEngine click animation name
 * @param modelEngineOnClickLerpIn     ModelEngine click lerp-in
 * @param modelEngineOnClickLerpOut    ModelEngine click lerp-out
 * @param modelEngineOnClickSpeed      ModelEngine click speed
 * @param modelEngineOnSpawnName       ModelEngine spawn animation name
 * @param modelEngineOnSpawnLerpIn     ModelEngine spawn lerp-in
 * @param modelEngineOnSpawnLerpOut    ModelEngine spawn lerp-out
 * @param modelEngineOnSpawnSpeed      ModelEngine spawn speed
 * @param modelEngineCollisionPositions ModelEngine collision positions
 * @param betterModelEnabled    Whether BetterModel is enabled
 * @param betterModelModelId    BetterModel model ID
 * @param betterModelModelSize  BetterModel model scale
 * @param betterModelOnSpawnName BetterModel spawn animation name
 * @param betterModelOnClickName BetterModel click animation name
 * @param betterModelCollisionPositions BetterModel collision positions
 * @param itemName              Custom item name
 * @param itemMaterial          Custom item material
 * @param itemsAdderBlockId     ItemsAdder block ID (if any)
 * @param craftEngineBlockId    CraftEngine block ID (if any)
 */
public record BlockDefinitionModel(
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
    String modelEngineOnSpawnName,
    double modelEngineOnSpawnLerpIn,
    double modelEngineOnSpawnLerpOut,
    double modelEngineOnSpawnSpeed,
    List<String> modelEngineCollisionPositions,
    // betterModel
    boolean betterModelEnabled,
    String betterModelModelId,
    double betterModelModelSize,
    String betterModelOnSpawnName,
    String betterModelOnClickName,
    List<String> betterModelCollisionPositions,
    // item
    String itemName,
    Material itemMaterial,
    // itemsadder
    String itemsAdderBlockId,
    // craftengine
    String craftEngineBlockId
) implements me.chyxelmc.mmoblock.api.model.BlockDefinition {

    // ---- Supplementary inner types merged from former Impl files ----

    /**
     * A single hologram display line with optional click/dead/item/block actions.
     */
    public record DisplayLine(
        int line,
        String text,
        String click,
        String dead,
        String item,
        String block
    ) implements me.chyxelmc.mmoblock.api.model.DisplayLine {
    }

    /**
     * A condition definition for placeholder-based conditional display.
     */
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
    ) implements me.chyxelmc.mmoblock.api.model.ConditionDefinition {
    }

    /**
     * A single drop entry that can be MATERIAL, EXPERIENCE, or COMMAND.
     */
    public record DropEntry(
        DropType type,
        Material material,
        int min,
        int max,
        String command,
        double chance,
        String dropType,
        boolean perPlayer,
        boolean effectExplosion,
        DropGlow effectGlow,
        DropBeam effectBeam,
        String itemsAdderId,
        String craftEngineId,
        String mmoItemsId,
        String experienceSource,
        String mmocoreProfession,
        DropPopup dropPopup,
        // ---- Custom drop handler (third-party addon plugins) ----
        String customHandlerId,
        java.util.Map<String, Object> customData
    ) implements me.chyxelmc.mmoblock.api.model.DropEntry {

        @Override
        public String experienceSource() {
            return this.experienceSource != null ? this.experienceSource : "vanilla";
        }

        @Override
        public String mmocoreProfession() {
            return this.mmocoreProfession != null && !this.mmocoreProfession.isBlank() ? this.mmocoreProfession : "main";
        }

        @Override
        public String customHandlerId() {
            return this.customHandlerId;
        }

        @Override
        public java.util.Map<String, Object> customData() {
            return this.customData != null
                ? java.util.Collections.unmodifiableMap(this.customData)
                : java.util.Collections.emptyMap();
        }
    }

    /**
     * A tool action defining click behaviour for a material or ItemsAdder/CraftEngine item.
     */
    public record ToolAction(
        Material material,
        int clickNeeded,
        int decreaseDurability,
        List<String> allowedDrops,
        String clickType,
        String itemsAdderId,
        String craftEngineId,
        String mmoItemsId
    ) implements me.chyxelmc.mmoblock.api.model.ToolAction {
    }
}
