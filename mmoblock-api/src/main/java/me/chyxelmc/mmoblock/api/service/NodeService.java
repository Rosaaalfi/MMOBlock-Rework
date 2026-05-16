package me.chyxelmc.mmoblock.api.service;

import me.chyxelmc.mmoblock.api.model.NodeDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedNode;
import me.chyxelmc.mmoblock.api.result.NodePlaceResult;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface NodeService {

    NodeDefinition getNodeDefinition(String id);

    Set<String> getNodeIds();

    Collection<? extends PlacedNode> getPlacedNodes();

    NodePlaceResult placeNode(String nodeId, World world, double x, double y, double z);

    boolean removeNode(String nodeId, World world, double x, double y, double z);

    boolean removeNodeByBlockUniqueId(UUID blockUniqueId);

    Collection<? extends PlacedNode> findNodesByType(String nodeId);

    Collection<? extends PlacedNode> findNodesInWorld(String worldName);

    void syncForPlayer(Player player);
}
