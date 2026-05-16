package me.chyxelmc.mmoblock.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlacedNode implements me.chyxelmc.mmoblock.api.model.PlacedNode {

    private final UUID uniqueId;
    private final String nodeId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final List<NodeBlockEntry> blocks = new ArrayList<>();

    public PlacedNode(
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

    public List<NodeBlockEntry> blocks() {
        return this.blocks;
    }

    public record NodeBlockEntry(UUID blockUniqueId, String blockType) implements me.chyxelmc.mmoblock.api.model.PlacedNode.NodeBlockEntry {
    }
}

