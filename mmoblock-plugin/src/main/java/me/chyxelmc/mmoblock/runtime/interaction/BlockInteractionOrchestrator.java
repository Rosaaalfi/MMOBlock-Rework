package me.chyxelmc.mmoblock.runtime.interaction;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.ecs.component.InteractionComponent;
import me.chyxelmc.mmoblock.ecs.component.PositionComponent;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

public final class BlockInteractionOrchestrator {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockVisualSyncService visualSyncSystem;
    private final BlockModelApplier modelApplier;
    private final NamespacedKey uniqueIdKey;
    private EntityManager entityManager;

    public BlockInteractionOrchestrator(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final BlockVisualSyncService visualSyncSystem,
            final BlockModelApplier modelApplier,
            final NamespacedKey uniqueIdKey
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = plugin.scheduler();
        this.visualSyncSystem = visualSyncSystem;
        this.modelApplier = modelApplier;
        this.uniqueIdKey = uniqueIdKey;
    }

    public void setEntityManager(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean spawn(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        final Location location = new Location(world, placedBlock.x() + 0.5D, placedBlock.y(), placedBlock.z() + 0.5D);

        // Folia thread safety: entity operations must run on the owning region's tick thread
        if (!isOwnedByCurrentRegion(location)) {
            this.scheduler.runAtLocation(location, () -> spawn(placedBlock, definition, world));
            return true; // optimistic — actual failure will be logged by the scheduled call
        }

        try {
            if (placedBlock.interactionEntityId() != null) {
                this.nmsAdapter.removeInteraction(world, placedBlock.interactionEntityId());
                placedBlock.setInteractionEntityId(null);
            }
            removeDuplicateTaggedInteractions(world, location, placedBlock.uniqueId());

            UUID ecsEntityId = null;
            if (this.entityManager != null) {
                ecsEntityId = submitInteractionEntity(placedBlock, definition, location);
                if (isEcsManagedSpawn()) {
                    applyDeferredSpawnVisuals(placedBlock, definition, world);
                    return true;
                }
            }

            final NmsAdapter.SpawnResult spawnResult = this.nmsAdapter.spawnInteraction(
                    world,
                    location,
                    (float) definition.hitboxWidth(),
                    (float) definition.hitboxHeight(),
                    this.uniqueIdKey,
                    placedBlock.uniqueId()
            );
            if (!spawnResult.success() || spawnResult.interactionUniqueId() == null) {
                logSpawnFailure(placedBlock, world, spawnResult);
                removeSubmittedEntity(ecsEntityId);
                return false;
            }

            placedBlock.setInteractionEntityId(spawnResult.interactionUniqueId());
            updateSpawnedInteraction(ecsEntityId, spawnResult.interactionUniqueId());
            applySpawnedModels(placedBlock, definition, world);
            return true;
        } catch (final RuntimeException exception) {
            return false;
        }
    }

    public void despawn(final PlacedBlockModel block) {
        if (block.interactionEntityId() == null) {
            return;
        }
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }

        // Folia thread safety: entity operations must run on the owning region's tick thread
        final Location location = new Location(world, block.x(), block.y(), block.z());
        if (!isOwnedByCurrentRegion(location)) {
            this.scheduler.runAtLocation(location, () -> despawn(block));
            return;
        }

        final NmsAdapter.RemoveResult removeResult = this.nmsAdapter.removeInteraction(world, block.interactionEntityId());
        if (!removeResult.success()) {
            final Entity entity = world.getEntity(block.interactionEntityId());
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            return;
        }

        removeInteractionEntity(block.uniqueId());
        block.setInteractionEntityId(null);
    }

    public void applyModelsAfterEcsSpawn(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final World world
    ) {
        this.visualSyncSystem.applyRealBlockModel(block, definition, world);
        this.modelApplier.applySchematicModel(block, definition, world, false);
        this.modelApplier.applyBdEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineCollision(block, definition, world);
        this.modelApplier.applyBetterModelModel(block, definition, world);
        this.modelApplier.applyBetterModelCollision(block, definition, world);
    }

    private UUID submitInteractionEntity(
            final PlacedBlockModel placedBlock,
            final BlockDefinitionModel definition,
            final Location location
    ) {
        final UUID ecsEntityId = UUID.randomUUID();
        this.entityManager.submit(entityManager -> {
            entityManager.addEntity(ecsEntityId);
            entityManager.addComponent(ecsEntityId, new PositionComponent(location));
            entityManager.addComponent(ecsEntityId, new InteractionComponent(
                    (float) definition.hitboxWidth(),
                    (float) definition.hitboxHeight(),
                    this.uniqueIdKey,
                    placedBlock.uniqueId()
            ));
        });
        return ecsEntityId;
    }

    private boolean isEcsManagedSpawn() {
        return this.plugin.getConfig().getBoolean("ecs.spawn-managed", false);
    }

    private void applyDeferredSpawnVisuals(
            final PlacedBlockModel placedBlock,
            final BlockDefinitionModel definition,
            final World world
    ) {
        try {
            this.visualSyncSystem.applyRealBlockModel(placedBlock, definition, world);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }      this.modelApplier.applySchematicModel(placedBlock, definition, world, false);
        this.modelApplier.applyBdEngineModel(placedBlock, definition, world);
    }

    private void removeSubmittedEntity(final UUID ecsEntityId) {
        if (ecsEntityId != null && this.entityManager != null) {
            this.entityManager.submit(entityManager -> entityManager.removeEntity(ecsEntityId));
        }
    }

    private void updateSpawnedInteraction(final UUID ecsEntityId, final UUID interactionUniqueId) {
        if (this.entityManager == null || ecsEntityId == null) {
            return;
        }
        this.entityManager.submit(entityManager -> {
            final InteractionComponent component = entityManager.getComponent(ecsEntityId, InteractionComponent.class);
            if (component != null) {
                component.setSpawnedInteraction(interactionUniqueId);
            }
        });
    }

    private void applySpawnedModels(
            final PlacedBlockModel placedBlock,
            final BlockDefinitionModel definition,
            final World world
    ) {
        this.visualSyncSystem.applyRealBlockModel(placedBlock, definition, world);
        this.modelApplier.applySchematicModel(placedBlock, definition, world, false);
        this.modelApplier.applyBdEngineModel(placedBlock, definition, world);
        this.modelApplier.applyModelEngineModel(placedBlock, definition, world);
        this.modelApplier.applyModelEngineCollision(placedBlock, definition, world);
        this.modelApplier.applyBetterModelModel(placedBlock, definition, world);
        this.modelApplier.applyBetterModelCollision(placedBlock, definition, world);
    }

    private void removeInteractionEntity(final UUID blockUniqueId) {
        if (this.entityManager == null) {
            return;
        }
        this.entityManager.submit(entityManager -> {
            for (final UUID ecsId : entityManager.allEntities()) {
                final InteractionComponent component = entityManager.getComponent(ecsId, InteractionComponent.class);
                if (component == null || component.blockUniqueId() == null || !component.blockUniqueId().equals(blockUniqueId)) {
                    continue;
                }
                entityManager.removeEntity(ecsId);
                break;
            }
        });
    }

    private void removeDuplicateTaggedInteractions(final World world, final Location location, final UUID blockUniqueId) {
        for (final Entity entity : world.getNearbyEntities(location, 1.5D, 2.0D, 1.5D, candidate -> candidate instanceof Interaction)) {
            final Interaction interaction = (Interaction) entity;
            final String raw = interaction.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
            if (raw == null || !raw.equals(blockUniqueId.toString())) {
                continue;
            }
            interaction.remove();
        }
    }

    private void logSpawnFailure(
            final PlacedBlockModel placedBlock,
            final World world,
            final NmsAdapter.SpawnResult spawnResult
    ) {
        MMOBlockLogger.warning(
                "Failed to spawn interaction for block " + placedBlock.uniqueId()
                        + " at " + world.getName() + " "
                        + placedBlock.x() + "," + placedBlock.y() + "," + placedBlock.z()
                        + " reason=" + spawnResult.reason()
        );
    }

    /**
     * Checks whether the current thread owns the region that contains the given location.
     * On Paper (non-Folia) this always returns {@code true}.
     */
    private static boolean isOwnedByCurrentRegion(final Location location) {
        if (location == null || location.getWorld() == null) return false;
        try {
            final java.lang.reflect.Method method = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return Boolean.TRUE.equals(method.invoke(null, location));
        } catch (final NoSuchMethodException ignored) {
            return true; // Paper — single region
        } catch (final Exception e) {
            MMOBlockLogger.debug("isOwnedByCurrentRegion failed at " + location + ": " + e.getMessage());
            return false;
        }
    }
}
