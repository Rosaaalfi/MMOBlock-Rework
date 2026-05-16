package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;

import java.util.List;

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
        List<DisplayLine> displayLines,
        String itemName,
        Material itemMaterial
) implements me.chyxelmc.mmoblock.api.model.NodeDefinition {
}
