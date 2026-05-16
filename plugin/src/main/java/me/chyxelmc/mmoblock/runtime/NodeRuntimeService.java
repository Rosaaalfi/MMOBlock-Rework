package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.config.NodeConfigService;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.NodeDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.PlacedNode;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.persistence.NodeRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.runtime.ecs.system.LifecycleSystem;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class NodeRuntimeService {

    private static final String DEFAULT_ACTIVE_TEMPLATE = "{block_name} Active";
    private static final String DEFAULT_DEAD_TEMPLATE = "{block_name} {respawn_times}s";
    private static final long HOLOGRAM_UPDATE_INTERVAL_TICKS = 20L;

    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockConfigService blockConfigService;
    private final NodeConfigService nodeConfigService;
    private final BlockRuntimeService blockRuntimeService;
    private final NodeRepository nodeRepository;
    private final DataCache dataCache;
    private final HologramRuntimeService hologramRuntimeService;

    private final Map<UUID, PlacedNode> nodesById = new HashMap<>();
    private final Map<NodeKey, UUID> nodesByKey = new HashMap<>();
    private SchedulerTask updateTask;

    public NodeRuntimeService(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Scheduler scheduler,
            final BlockConfigService blockConfigService,
            final NodeConfigService nodeConfigService,
            final BlockRuntimeService blockRuntimeService,
            final NodeRepository nodeRepository,
            final DataCache dataCache
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.nodeConfigService = nodeConfigService;
        this.blockRuntimeService = blockRuntimeService;
        this.nodeRepository = nodeRepository;
        this.dataCache = dataCache;
        this.hologramRuntimeService = new HologramRuntimeService(plugin, nmsAdapter, scheduler);
        startUpdateTask();
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager) {
        this.hologramRuntimeService.setEntityManager(entityManager);
    }

    public PlaceResult placeNode(final String nodeId, final World world, final double x, final double y, final double z, final boolean persist) {
        final NodeDefinition definition = this.nodeConfigService.findNode(nodeId);
        if (definition == null) {
            return PlaceResult.error("Unknown node id: " + nodeId);
        }
        if (world == null) {
            return PlaceResult.error("World is missing.");
        }
        if (findNodeAt(nodeId, world.getName(), x, y, z) != null) {
            return PlaceResult.error("Node already exists at that position");
        }

        final UUID uniqueId = UUID.randomUUID();
        final PlacedNode node = new PlacedNode(uniqueId, definition.id(), world.getName(), x, y, z);
        this.nodesById.put(uniqueId, node);
        this.nodesByKey.put(new NodeKey(definition.id(), world.getName(), x, y, z), uniqueId);
        if (persist) {
            this.nodeRepository.upsert(node);
        }

        spawnInitialBlocks(node, definition, world);
        updateNodeHologram(node, definition, world);
        return PlaceResult.success(node);
    }

    public boolean removeNode(final String nodeId, final World world, final double x, final double y, final double z) {
        if (world == null) {
            return false;
        }
        final PlacedNode node = findNodeAt(nodeId, world.getName(), x, y, z);
        if (node == null) {
            return false;
        }

        for (final PlacedNode.NodeBlockEntry entry : new ArrayList<>(node.blocks())) {
            this.blockRuntimeService.removeById(entry.blockUniqueId());
        }

        this.hologramRuntimeService.remove(buildHologramAnchor(node));
        this.nodesById.remove(node.uniqueId());
        this.nodesByKey.remove(new NodeKey(node.nodeId(), node.world(), node.x(), node.y(), node.z()));
        this.nodeRepository.delete(node.uniqueId());
        return true;
    }

    public boolean removeNodeByBlockUniqueId(final UUID blockUniqueId) {
        if (blockUniqueId == null) {
            return false;
        }
        for (final PlacedNode node : this.nodesById.values()) {
            for (final PlacedNode.NodeBlockEntry entry : node.blocks()) {
                if (blockUniqueId.equals(entry.blockUniqueId())) {
                    final World world = this.plugin.getServer().getWorld(node.world());
                    if (world == null) {
                        return false;
                    }
                    return removeNode(node.nodeId(), world, node.x(), node.y(), node.z());
                }
            }
        }
        return false;
    }

    public void restoreFromPersistence() {
        for (final PlacedNode node : this.nodeRepository.findAll()) {
            final World world = this.plugin.getServer().getWorld(node.world());
            if (world == null) {
                continue;
            }
            this.nodesById.put(node.uniqueId(), node);
            this.nodesByKey.put(new NodeKey(node.nodeId(), node.world(), node.x(), node.y(), node.z()), node.uniqueId());

            final NodeDefinition definition = this.nodeConfigService.findNode(node.nodeId());
            if (definition == null) {
                continue;
            }
            scheduleRestoreNode(node, definition, world);
        }
    }

    private void scheduleRestoreNode(final PlacedNode node, final NodeDefinition definition, final World world) {
        this.scheduler.runAtLocationLater(new Location(world, node.x(), node.y(), node.z()), () -> {
            if (!this.nodesById.containsKey(node.uniqueId())) {
                return;
            }
            spawnInitialBlocks(node, definition, world);
            updateNodeHologram(node, definition, world);
        }, 20L);
    }

    public void handleChunkLoad(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedNode node : this.nodesById.values()) {
            if (!world.getName().equals(node.world())) {
                continue;
            }
            if (chunkMatches(node, chunkX, chunkZ)) {
                final NodeDefinition definition = this.nodeConfigService.findNode(node.nodeId());
                if (definition != null) {
                    updateNodeHologram(node, definition, world);
                }
            }
        }
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedNode node : this.nodesById.values()) {
            if (!world.getName().equals(node.world())) {
                continue;
            }
            if (chunkMatches(node, chunkX, chunkZ)) {
                this.hologramRuntimeService.remove(buildHologramAnchor(node));
            }
        }
    }

    public void syncForPlayer(final Player player) {
        if (player == null) {
            return;
        }
        final List<PlacedBlock> anchors = new ArrayList<>();
        for (final PlacedNode node : this.nodesById.values()) {
            if (player.getWorld().getName().equals(node.world())) {
                anchors.add(buildHologramAnchor(node));
            }
        }
        this.hologramRuntimeService.syncForPlayer(player, anchors);
    }

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.hologramRuntimeService.handleViewerQuit(playerUniqueId);
    }

    public void shutdown() {
        stopUpdateTask();
        for (final PlacedNode node : this.nodesById.values()) {
            this.hologramRuntimeService.remove(buildHologramAnchor(node));
        }
        this.nodesById.clear();
        this.nodesByKey.clear();
        this.hologramRuntimeService.shutdown();
    }

    public int reloadNodes() {
        final int loaded = this.nodeConfigService.reloadNodes();
        for (final PlacedNode node : this.nodesById.values()) {
            final NodeDefinition definition = this.nodeConfigService.findNode(node.nodeId());
            final World world = this.plugin.getServer().getWorld(node.world());
            if (definition != null && world != null) {
                updateNodeHologram(node, definition, world);
            }
        }
        return loaded;
    }

    public List<String> nodeIds() {
        return new ArrayList<>(this.nodeConfigService.nodeIds());
    }

    public List<PlacedNode> placedNodes() {
        return List.copyOf(this.nodesById.values());
    }

    private void startUpdateTask() {
        stopUpdateTask();
        this.updateTask = this.scheduler.runTimer(
                this::updateAllNodeHolograms,
                HOLOGRAM_UPDATE_INTERVAL_TICKS,
                HOLOGRAM_UPDATE_INTERVAL_TICKS
        );
    }

    private void stopUpdateTask() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
            this.updateTask = null;
        }
    }

    private void updateAllNodeHolograms() {
        for (final PlacedNode node : this.nodesById.values()) {
            final NodeDefinition definition = this.nodeConfigService.findNode(node.nodeId());
            if (definition == null) {
                continue;
            }
            final World world = this.plugin.getServer().getWorld(node.world());
            if (world == null) {
                continue;
            }
            updateNodeHologram(node, definition, world);
        }
    }

    private void updateNodeHologram(final PlacedNode node, final NodeDefinition definition, final World world) {
        if (definition.displayLines() == null || definition.displayLines().isEmpty()) {
            this.hologramRuntimeService.remove(buildHologramAnchor(node));
            return;
        }
        final List<DisplayLine> expanded = expandBlockListLines(node, definition);
        final BlockDefinition hologramDefinition = buildHologramDefinition(definition, expanded);
        this.hologramRuntimeService.showActive(buildHologramAnchor(node), hologramDefinition);
    }

    private List<DisplayLine> expandBlockListLines(final PlacedNode node, final NodeDefinition definition) {
        final List<DisplayLine> sorted = definition.displayLines().stream()
                .sorted(Comparator.comparingInt(DisplayLine::line))
                .toList();

        final List<String> blockListLines = buildBlockListLines(node, definition);
        final List<DisplayLine> expanded = new ArrayList<>();
        int nextLine = 1;

        for (final DisplayLine line : sorted) {
            final String text = line.text();
            if (text != null && text.contains("{block_lists}")) {
                if (!blockListLines.isEmpty()) {
                    for (final String blockLine : blockListLines) {
                        expanded.add(new DisplayLine(
                                nextLine++,
                                text.replace("{block_lists}", blockLine),
                                null,
                                null,
                                null,
                                null
                        ));
                    }
                }
                continue;
            }

            expanded.add(new DisplayLine(
                    nextLine++,
                    line.text(),
                    line.click(),
                    line.dead(),
                    line.item(),
                    line.block()
            ));
        }
        return expanded;
    }

    private List<String> buildBlockListLines(final PlacedNode node, final NodeDefinition definition) {
        final List<String> lines = new ArrayList<>();
        final long now = System.currentTimeMillis();
        final String activeTemplate = safeTemplate(definition.blockListActiveTemplate(), DEFAULT_ACTIVE_TEMPLATE);
        final String deadTemplate = safeTemplate(definition.blockListDeadTemplate(), DEFAULT_DEAD_TEMPLATE);

        final List<PlacedNode.NodeBlockEntry> entries = new ArrayList<>(node.blocks());
        for (final PlacedNode.NodeBlockEntry entry : entries) {
            final PlacedBlock placedBlock = this.blockRuntimeService.findPlacedBlock(entry.blockUniqueId());
            if (placedBlock == null) {
                node.blocks().remove(entry);
                continue;
            }
            final BlockDefinition blockDefinition = this.blockConfigService.findBlock(entry.blockType());
            final String blockName = blockDefinition != null && blockDefinition.displayName() != null
                    ? blockDefinition.displayName()
                    : entry.blockType();
            final boolean active = LifecycleSystem.STATUS_ACTIVE.equalsIgnoreCase(placedBlock.status());
            final String template = active ? activeTemplate : deadTemplate;
            final long remaining = resolveRemainingSeconds(placedBlock, blockDefinition, now);
            lines.add(template
                    .replace("{block_name}", blockName)
                    .replace("{respawn_times}", String.valueOf(remaining))
            );
        }
        return lines;
    }

    private long resolveRemainingSeconds(final PlacedBlock placedBlock, final BlockDefinition blockDefinition, final long now) {
        if (LifecycleSystem.STATUS_ACTIVE.equalsIgnoreCase(placedBlock.status())) {
            return 0L;
        }
        if (placedBlock.respawnAt() != null) {
            return Math.max(0L, (placedBlock.respawnAt() - now) / 1000L);
        }
        if (blockDefinition != null) {
            return Math.max(0L, blockDefinition.respawnTimeSeconds());
        }
        return 0L;
    }

    private BlockDefinition buildHologramDefinition(final NodeDefinition definition, final List<DisplayLine> displayLines) {
        return new BlockDefinition(
                "node-" + definition.id().toLowerCase(Locale.ROOT),
                definition.id(),
                0.25D,
                0.25D,
                0L,
                false,
                0.0D,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                definition.displayHeight(),
                List.of(),
                displayLines,
                List.of(),
                "", 0.0D, 0.0D,
                false, "", "", "north", List.of(), List.of(),
                null,
                null
        );
    }

    private PlacedBlock buildHologramAnchor(final PlacedNode node) {
        return new PlacedBlock(
                node.uniqueId(),
                "node-" + node.nodeId().toLowerCase(Locale.ROOT),
                node.world(),
                node.x(),
                node.y(),
                node.z(),
                node.x(),
                node.y(),
                node.z(),
                "north",
                LifecycleSystem.STATUS_ACTIVE
        );
    }

    private void spawnInitialBlocks(final PlacedNode node, final NodeDefinition definition, final World world) {
        if (definition.listBlocks() == null || definition.listBlocks().isEmpty()) {
            return;
        }
        final int targetCount = Math.max(1, definition.maxBlocks());
        final BlockRuntimeService.RandomLocationContext randomLocationContext = new BlockRuntimeService.RandomLocationContext(
                node.x(),
                node.y(),
                node.z(),
                definition.randomLocationEnabled(),
                definition.randomLocationRadius(),
                definition.randomLocationClosest(),
                definition.randomLocationCenterDistance()
        );
        for (int i = 0; i < targetCount; i++) {
            final String blockId = pickBlockId(definition.listBlocks());
            final BlockRuntimeService.PlaceResult result = this.blockRuntimeService.placeRandomNodeBlock(
                    blockId,
                    world,
                    "north",
                    randomLocationContext
            );
            if (result.success()) {
                node.blocks().add(new PlacedNode.NodeBlockEntry(result.placedBlock().uniqueId(), blockId));
            }
        }
    }

    private String pickBlockId(final List<String> listBlocks) {
        if (listBlocks.size() == 1) {
            return listBlocks.get(0);
        }
        return listBlocks.get(ThreadLocalRandom.current().nextInt(listBlocks.size()));
    }

    private PlacedNode findNodeAt(final String nodeId, final String world, final double x, final double y, final double z) {
        final UUID id = this.nodesByKey.get(new NodeKey(nodeId, world, x, y, z));
        if (id != null) {
            return this.nodesById.get(id);
        }
        return null;
    }

    private boolean chunkMatches(final PlacedNode node, final int chunkX, final int chunkZ) {
        final int nodeChunkX = (int) Math.floor(node.x()) >> 4;
        final int nodeChunkZ = (int) Math.floor(node.z()) >> 4;
        return nodeChunkX == chunkX && nodeChunkZ == chunkZ;
    }

    private String safeTemplate(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record NodeKey(String nodeId, String world, double x, double y, double z) {
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof NodeKey other)) return false;
            return nodeId.equalsIgnoreCase(other.nodeId)
                    && world.equalsIgnoreCase(other.world)
                    && almostEquals(x, other.x)
                    && almostEquals(y, other.y)
                    && almostEquals(z, other.z);
        }

        @Override
        public int hashCode() {
            int result = nodeId.toLowerCase(Locale.ROOT).hashCode();
            result = 31 * result + world.toLowerCase(Locale.ROOT).hashCode();
            result = 31 * result + Double.hashCode(roundCoord(x));
            result = 31 * result + Double.hashCode(roundCoord(y));
            result = 31 * result + Double.hashCode(roundCoord(z));
            return result;
        }

        private double roundCoord(final double value) {
            return Math.round(value * 1000000.0D) / 1000000.0D;
        }

        private boolean almostEquals(final double a, final double b) {
            return Math.abs(a - b) < 0.000001D;
        }
    }

    public record PlaceResult(boolean success, String message, PlacedNode placedNode) {

        public static PlaceResult success(final PlacedNode placedNode) {
            return new PlaceResult(true, "", placedNode);
        }

        public static PlaceResult error(final String message) {
            return new PlaceResult(false, message, null);
        }
    }
}

