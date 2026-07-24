package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

import java.util.List;

public interface NodeDefinition {
    String id();
    List<String> listBlocks();
    int maxBlocks();
    boolean randomLocationEnabled();
    double randomLocationRadius();
    boolean randomLocationClosest();
    double randomLocationCenterDistance();
    String blockListActiveTemplate();
    String blockListDeadTemplate();
    double displayHeight();
    double detectRange();
    List<? extends DisplayLine> displayLines();
    String itemName();
    Material itemMaterial();
}
