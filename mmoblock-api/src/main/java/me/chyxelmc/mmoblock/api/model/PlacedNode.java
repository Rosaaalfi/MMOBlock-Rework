package me.chyxelmc.mmoblock.api.model;

import java.util.List;
import java.util.UUID;

public interface PlacedNode {
    UUID uniqueId();
    String nodeId();
    String world();
    double x();
    double y();
    double z();
    List<? extends NodeBlockEntry> blocks();

    interface NodeBlockEntry {
        UUID blockUniqueId();
        String blockType();
    }
}
