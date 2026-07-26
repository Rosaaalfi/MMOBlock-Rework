package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.block.MiningProgressTracker;
import me.chyxelmc.mmoblock.runtime.block.RespawnScheduler;
import me.chyxelmc.mmoblock.runtime.block.DropService;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.block.ReconcileCoordinator;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.block.BlockChunkLifecycleOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockEventDispatcher;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockLookProtection;
import me.chyxelmc.mmoblock.runtime.block.BlockManagementService;
import me.chyxelmc.mmoblock.runtime.block.BlockMiningOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockMiningProgressReset;
import me.chyxelmc.mmoblock.runtime.block.BlockPlacementService;
import me.chyxelmc.mmoblock.runtime.block.BlockQueryService;
import me.chyxelmc.mmoblock.runtime.block.BlockRandomLocationResolver;
import me.chyxelmc.mmoblock.runtime.block.BlockRespawnOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.RandomLocationContext;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.interaction.LegacyInteractionRaytrace;
import me.chyxelmc.mmoblock.runtime.visual.BdEngineService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.SchematicService;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockServiceFactory {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockConfigLoader blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final DataCache dataCache;

    // ECS state - created first
    private final BlockStateRegistry stateRegistry = new BlockStateRegistry();

    // ECS systems
    private MiningProgressTracker miningSystem;
    private RespawnScheduler respawnSystem;
    private BlockVisualSyncService visualSyncSystem;
    private BlockLifecycleState lifecycleSystem;
    private DropService dropSystem;
    private ReconcileCoordinator reconcileSystem;

    // Runtime services
    private HologramRuntimeService hologramRuntimeService;
    private SchematicService schematicService;
    private BdEngineService bdEngineService;
    private BlockModelApplier modelApplier;
    private BlockInteractionOrchestrator interactionOrchestrator;
    private BlockRandomLocationResolver randomLocationResolver;
    private BlockEventDispatcher eventDispatcher;
    private BlockLookProtection lookProtection;
    private LegacyInteractionRaytrace legacyInteractionRaytrace;

    // Orchestrators
    private BlockLifecycleOrchestrator lifecycleOrchestrator;
    private BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator;
    private BlockMiningProgressReset miningProgressReset;

    // Shared state containers
    private final Set<UUID> transientBlocks = new HashSet<>();
    private final Set<UUID> suppressDeadHologram = new HashSet<>();
    private final Map<UUID, RandomLocationContext> nodeRandomLocationContexts = new HashMap<>();
    private final NamespacedKey uniqueIdKey;

    // Forward references for circular deps (orchestrators → placementService → orchestrators)
    private final AtomicReference<BlockPlacementService> placementServiceRef = new AtomicReference<>();

    // The 3 produced services
    private BlockPlacementService placementService;
    private BlockQueryService queryService;
    private BlockManagementService managementService;

    private static final Particle BREAK_PARTICLE;

    static {
        Particle particle;
        try {
            particle = Particle.valueOf("BLOCK_CRUMBLE"); // 1.21.2+
        } catch (final IllegalArgumentException ex) {
            try {
                particle = Particle.valueOf("BLOCK");      // 1.20.5–1.21.1
            } catch (final IllegalArgumentException ex2) {
                particle = Particle.valueOf("BLOCK_DUST"); // 1.19.4–1.20.4
            }
        }
        BREAK_PARTICLE = particle;
    }

    public BlockServiceFactory(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Scheduler scheduler,
            final BlockConfigLoader blockConfigService,
            final PersistenceReadSystem persistenceReadSystem,
            final PersistenceSystem persistenceSystem,
            final DataCache dataCache
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.dataCache = dataCache;
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");

        createEcsSystems();
        createRuntimeServices();
        createOrchestrators();
        createServices();
    }

    // ============================================================
    // Construction phases
    // ============================================================

    private void createEcsSystems() {
        this.miningSystem = new MiningProgressTracker(this.stateRegistry);
        this.respawnSystem = new RespawnScheduler(this.plugin, this.scheduler, this.stateRegistry);
        this.visualSyncSystem = new BlockVisualSyncService(this.plugin, this.nmsAdapter);
        this.lifecycleSystem = new BlockLifecycleState();
        this.reconcileSystem = new ReconcileCoordinator();
    }

    private void createRuntimeServices() {
        this.hologramRuntimeService = new HologramRuntimeService(this.plugin, this.nmsAdapter, this.scheduler);
        this.schematicService = new SchematicService(this.plugin, this.nmsAdapter);
        this.bdEngineService = new BdEngineService(this.plugin, this.nmsAdapter);
        this.modelApplier = new BlockModelApplier(this.plugin, this.nmsAdapter, this.schematicService, this.bdEngineService);
        this.dropSystem = new DropService(this.plugin, this.blockConfigService, this.scheduler, this.nmsAdapter);
        this.randomLocationResolver = new BlockRandomLocationResolver(this.stateRegistry);
        this.lookProtection = new BlockLookProtection(this.stateRegistry);
        this.eventDispatcher = new BlockEventDispatcher(this.plugin);
        this.legacyInteractionRaytrace = new LegacyInteractionRaytrace(
                this.stateRegistry,
                this.blockConfigService,
                this.lifecycleSystem
        );
        this.interactionOrchestrator = new BlockInteractionOrchestrator(
                this.plugin,
                this.nmsAdapter,
                this.visualSyncSystem,
                this.modelApplier,
                this.uniqueIdKey
        );
    }

    private void createOrchestrators() {
        // BlockLifecycleOrchestrator — no circular deps; can be created directly
        this.lifecycleOrchestrator = new BlockLifecycleOrchestrator(
                this.plugin,
                this.blockConfigService,
                this.persistenceSystem,
                this.stateRegistry,
                this.respawnSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.interactionOrchestrator,
                this.eventDispatcher,
                this.transientBlocks,
                this.suppressDeadHologram
        );

        // BlockChunkLifecycleOrchestrator — depends on respawnOrchestrator, not created yet.
        // We'll create it later after respawnOrchestrator exists.
        // For now, skip it.

        // miningOrchestrator — uses method refs that require placementService.
        // Through AtomicReference, we pass lambdas that will resolve once placementService is set.
        // But we need miningOrchestrator for respawnOrchestrator... circular again.
        // 
        // Solution: Create miningOrchestrator and respawnOrchestrator in sequence.
        // miningOrchestrator uses AtomicReference for transient/suppress/respawn callbacks.
        // respawnOrchestrator takes miningOrchestrator as a direct ref.
        // After both are created + placementService, the AtomicReference resolves.

        this.miningProgressReset = new BlockMiningProgressReset(
                this.plugin,
                this.scheduler,
                this.blockConfigService,
                this.stateRegistry,
                this.miningSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService
        );
    }

    private void createServices() {
        // Step 1: Create miningOrchestrator with forward refs through AtomicReference
        final BlockMiningOrchestrator miningOrchestrator = new BlockMiningOrchestrator(
                this.plugin,
                this.blockConfigService,
                this.persistenceSystem,
                this.miningSystem,
                this.dropSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.interactionOrchestrator,
                this.eventDispatcher,
                uniqueId -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    return ps != null && ps.isTransient(uniqueId);
                },
                uniqueId -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    return ps != null && ps.shouldSuppressDeadHologram(uniqueId);
                },
                (block, world, delayMillis) -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    if (ps != null) {
                        ps.scheduleRespawn(block, world, delayMillis);
                    }
                },
                BREAK_PARTICLE
        );

        // Step 2: Create respawnOrchestrator with forward refs to placementService
        final BlockRespawnOrchestrator respawnOrchestrator = new BlockRespawnOrchestrator(
                this.blockConfigService,
                this.persistenceSystem,
                this.stateRegistry,
                this.respawnSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.randomLocationResolver,
                this.interactionOrchestrator,
                this.eventDispatcher,
                miningOrchestrator,
                this.nodeRandomLocationContexts,
                uniqueId -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    return ps != null && ps.isTransient(uniqueId);
                },
                uniqueId -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    return ps != null && ps.shouldSuppressDeadHologram(uniqueId);
                },
                block -> {
                    final BlockPlacementService ps = this.placementServiceRef.get();
                    if (ps != null) {
                        ps.cleanupMissingDefinition(block);
                    }
                }
        );

        // Step 3: Create chunkLifecycleOrchestrator (now has final respawnOrchestrator)
        this.chunkLifecycleOrchestrator = new BlockChunkLifecycleOrchestrator(
                this.plugin,
                this.scheduler,
                this.blockConfigService,
                this.persistenceReadSystem,
                this.persistenceSystem,
                this.stateRegistry,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.schematicService,
                this.bdEngineService,
                this.interactionOrchestrator,
                respawnOrchestrator
        );

        // Step 4: Create placementService — now all orchestrators are ready
        this.placementService = new BlockPlacementService(
                this.plugin,
                this.blockConfigService,
                this.stateRegistry,
                this.persistenceSystem,
                this.respawnSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.interactionOrchestrator,
                this.eventDispatcher,
                this.randomLocationResolver,
                this.chunkLifecycleOrchestrator,
                this.lifecycleOrchestrator,
                respawnOrchestrator,
                miningOrchestrator,
                this.schematicService,
                this.bdEngineService,
                this.transientBlocks,
                this.suppressDeadHologram,
                this.nodeRandomLocationContexts
        );

        // Step 5: Resolve the AtomicReference so forward refs now work
        this.placementServiceRef.set(this.placementService);

        // Step 6: Create queryService
        this.queryService = new BlockQueryService(
                this.stateRegistry,
                this.dataCache,
                this.blockConfigService,
                this.uniqueIdKey,
                this.lookProtection
        );

        // Step 7: Create managementService
        this.managementService = new BlockManagementService(
                this.plugin,
                this.nmsAdapter,
                this.scheduler,
                this.blockConfigService,
                this.stateRegistry,
                this.placementService,
                this.queryService,
                this.hologramRuntimeService,
                this.schematicService,
                this.bdEngineService,
                this.modelApplier,
                this.visualSyncSystem,
                this.interactionOrchestrator,
                miningOrchestrator,
                this.chunkLifecycleOrchestrator,
                respawnOrchestrator,
                this.lookProtection,
                this.miningProgressReset,
                this.legacyInteractionRaytrace,
                this.lifecycleSystem,
                this.persistenceReadSystem,
                this.persistenceSystem,
                this.reconcileSystem
        );
    }

    // ============================================================
    // Service accessors
    // ============================================================

    public BlockPlacementService getPlacementService() {
        return this.placementService;
    }

    public BlockQueryService getQueryService() {
        return this.queryService;
    }

    public BlockManagementService getManagementService() {
        return this.managementService;
    }

    public BlockStateRegistry getEcsState() {
        return this.stateRegistry;
    }

    public BlockMiningProgressReset getMiningProgressReset() {
        return this.miningProgressReset;
    }

    public BlockLookProtection getLookProtection() {
        return this.lookProtection;
    }

    public NamespacedKey getUniqueIdKey() {
        return this.uniqueIdKey;
    }
}
