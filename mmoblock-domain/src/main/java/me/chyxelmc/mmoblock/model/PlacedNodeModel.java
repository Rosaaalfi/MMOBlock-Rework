package me.chyxelmc.mmoblock.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;

/**
 * Domain model for a placed node (a collection of blocks) in the world.
 *
 * <p>Renamed from {@code PlacedNodeImpl} to align with the domain model naming
 * convention and to distinguish it from implementation-level classes.</p>
 */
public final class PlacedNodeModel implements me.chyxelmc.mmoblock.api.model.PlacedNode {

    private final UUID uniqueId;
    private final String nodeId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final List<NodeBlockEntryModel> blocks = new ArrayList<>();

    public PlacedNodeModel(
            final UUID uniqueId,
            final String nodeId,
            final String world,
            final double x,
            final double y,
            final double z
    ) {
        this.uniqueId = uniqueId;
        this.nodeId = nodeId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public String nodeId() {
        return nodeId;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public List<NodeBlockEntryModel> blocks() {
        return this.blocks;
    }

    /**
     * A single block entry within a node, linking to a block unique ID and type.
     */
    public record NodeBlockEntryModel(UUID blockUniqueId, String blockType) implements me.chyxelmc.mmoblock.api.model.PlacedNode.NodeBlockEntry {
    }

    /**
     * Configuration definition for a node type. Merged from the former {@code NodeDefinitionImpl}.
     */
    public record NodeDefinition(
        String id,
        List<String> listBlocks,
        int maxBlocks,
        boolean randomLocationEnabled,
        double randomLocationRadius,
        boolean randomLocationClosest,
        double randomLocationCenterDistance,
        String blockListActiveTemplate,
        String blockListDeadTemplate,
        double displayHeight,
        double detectRange,
        List<? extends me.chyxelmc.mmoblock.api.model.DisplayLine> displayLines,
        String itemName,
        Material itemMaterial
    ) implements me.chyxelmc.mmoblock.api.model.NodeDefinition {
    }
}
