package me.chyxelmc.mmoblock.api.service;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import me.chyxelmc.mmoblock.api.result.PlaceResult;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlockService {

    BlockDefinition getBlockDefinition(String id);

    Set<String> getBlockIds();

    Collection<? extends PlacedBlock> getPlacedBlocks();

    PlacedBlock getPlacedBlock(UUID uniqueId);

    PlaceResult placeBlock(String type, World world, double x, double y, double z, String facing);

    boolean removeBlock(String type, World world, double x, double y, double z);

    boolean removeBlock(UUID uniqueId);

    boolean removeBlockByInteractionEntity(org.bukkit.entity.Entity entity);

    List<? extends PlacedBlock> findBlocksByType(String type);

    List<? extends PlacedBlock> findBlocksInWorld(String worldName);

    void syncFakeBlocksForPlayer(Player player);

    boolean isPlayerLookProtected(Player player);
}
