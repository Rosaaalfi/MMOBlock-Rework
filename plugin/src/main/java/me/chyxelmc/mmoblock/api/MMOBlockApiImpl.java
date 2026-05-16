package me.chyxelmc.mmoblock.api;

import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.config.NodeConfigService;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.NodeDefinition;
import me.chyxelmc.mmoblock.api.result.NodePlaceResult;
import me.chyxelmc.mmoblock.api.result.PlaceResult;
import me.chyxelmc.mmoblock.api.service.BlockService;
import me.chyxelmc.mmoblock.api.service.NodeService;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MMOBlockApiImpl implements BlockService, NodeService, me.chyxelmc.mmoblock.api.MMOBlockApi {

    private final BlockRuntimeService blockRuntimeService;
    private final BlockConfigService blockConfigService;
    private final NodeRuntimeService nodeRuntimeService;
    private final NodeConfigService nodeConfigService;

    public MMOBlockApiImpl(
            final BlockRuntimeService blockRuntimeService,
            final BlockConfigService blockConfigService,
            final NodeRuntimeService nodeRuntimeService,
            final NodeConfigService nodeConfigService
    ) {
        this.blockRuntimeService = blockRuntimeService;
        this.blockConfigService = blockConfigService;
        this.nodeRuntimeService = nodeRuntimeService;
        this.nodeConfigService = nodeConfigService;
    }

    // ================ MMOBlockApi ================

    @Override
    public BlockService getBlockService() {
        return this;
    }

    @Override
    public NodeService getNodeService() {
        return this.nodeRuntimeService != null ? this : null;
    }

    // ================ BlockService ================

    @Override
    public BlockDefinition getBlockDefinition(final String id) {
        final me.chyxelmc.mmoblock.model.BlockDefinition def = this.blockConfigService.findBlock(id);
        return def;
    }

    @Override
    public Set<String> getBlockIds() {
        return this.blockConfigService.blockIds();
    }

    @Override
    public Collection<? extends me.chyxelmc.mmoblock.api.model.PlacedBlock> getPlacedBlocks() {
        return this.blockRuntimeService.placedBlocks();
    }

    @Override
    public me.chyxelmc.mmoblock.api.model.PlacedBlock getPlacedBlock(final UUID uniqueId) {
        return this.blockRuntimeService.findPlacedBlock(uniqueId);
    }

    @Override
    public PlaceResult placeBlock(final String type, final World world, final double x, final double y, final double z, final String facing) {
        final BlockRuntimeService.PlaceResult result = this.blockRuntimeService.place(type, world, x, y, z, facing);
        if (result.success()) {
            return PlaceResult.success(result.placedBlock());
        }
        return PlaceResult.error(result.message());
    }

    @Override
    public boolean removeBlock(final String type, final World world, final double x, final double y, final double z) {
        return this.blockRuntimeService.remove(type, world, x, y, z);
    }

    @Override
    public boolean removeBlock(final UUID uniqueId) {
        return this.blockRuntimeService.removeById(uniqueId);
    }

    @Override
    public boolean removeBlockByInteractionEntity(final org.bukkit.entity.Entity entity) {
        return this.blockRuntimeService.removeByInteractionEntity(entity);
    }

    @Override
    public List<? extends me.chyxelmc.mmoblock.api.model.PlacedBlock> findBlocksByType(final String type) {
        return this.blockRuntimeService.placedBlocks().stream()
                .filter(b -> b.type().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends me.chyxelmc.mmoblock.api.model.PlacedBlock> findBlocksInWorld(final String worldName) {
        return this.blockRuntimeService.placedBlocks().stream()
                .filter(b -> b.world().equals(worldName))
                .collect(Collectors.toList());
    }

    @Override
    public void syncFakeBlocksForPlayer(final Player player) {
        this.blockRuntimeService.syncFakeBlocksForPlayer(player);
    }

    @Override
    public boolean isPlayerLookProtected(final Player player) {
        return this.blockRuntimeService.isPlayerLookProtected(player);
    }

    // ================ NodeService ================

    @Override
    public NodeDefinition getNodeDefinition(final String id) {
        final me.chyxelmc.mmoblock.model.NodeDefinition def = this.nodeConfigService.findNode(id);
        return def;
    }

    @Override
    public Set<String> getNodeIds() {
        return this.nodeConfigService.nodeIds();
    }

    @Override
    public Collection<? extends me.chyxelmc.mmoblock.api.model.PlacedNode> getPlacedNodes() {
        if (this.nodeRuntimeService == null) return List.of();
        return this.nodeRuntimeService.placedNodes();
    }

    @Override
    public NodePlaceResult placeNode(final String nodeId, final World world, final double x, final double y, final double z) {
        if (this.nodeRuntimeService == null) {
            return NodePlaceResult.error("Node runtime not available");
        }
        final NodeRuntimeService.PlaceResult result = this.nodeRuntimeService.placeNode(nodeId, world, x, y, z, true);
        if (result.success()) {
            return NodePlaceResult.success(result.placedNode());
        }
        return NodePlaceResult.error(result.message());
    }

    @Override
    public boolean removeNode(final String nodeId, final World world, final double x, final double y, final double z) {
        if (this.nodeRuntimeService == null) return false;
        return this.nodeRuntimeService.removeNode(nodeId, world, x, y, z);
    }

    @Override
    public boolean removeNodeByBlockUniqueId(final UUID blockUniqueId) {
        if (this.nodeRuntimeService == null) return false;
        return this.nodeRuntimeService.removeNodeByBlockUniqueId(blockUniqueId);
    }

    @Override
    public Collection<? extends me.chyxelmc.mmoblock.api.model.PlacedNode> findNodesByType(final String nodeId) {
        if (this.nodeRuntimeService == null) return List.of();
        return this.nodeRuntimeService.placedNodes().stream()
                .filter(n -> n.nodeId().equalsIgnoreCase(nodeId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends me.chyxelmc.mmoblock.api.model.PlacedNode> findNodesInWorld(final String worldName) {
        if (this.nodeRuntimeService == null) return List.of();
        return this.nodeRuntimeService.placedNodes().stream()
                .filter(n -> n.world().equals(worldName))
                .collect(Collectors.toList());
    }

    @Override
    public void syncForPlayer(final Player player) {
        if (this.nodeRuntimeService != null) {
            this.nodeRuntimeService.syncForPlayer(player);
        }
    }
}
