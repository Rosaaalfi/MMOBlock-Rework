package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;

import me.chyxelmc.mmoblock.api.model.DropBeam;
import me.chyxelmc.mmoblock.api.model.DropGlow;
import me.chyxelmc.mmoblock.api.model.DropType;

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
    String itemsAdderId
) implements me.chyxelmc.mmoblock.api.model.DropEntry {
}

