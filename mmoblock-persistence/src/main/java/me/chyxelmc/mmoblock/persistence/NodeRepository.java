package me.chyxelmc.mmoblock.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.chyxelmc.mmoblock.model.PlacedNodeModel;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.persistence.database.DatabaseManager;

public final class NodeRepository {

    private static final String ALL_NODES_CACHE_KEY = "_all_nodes";
    private static final long ALL_NODES_TTL_MS = 30_000L;

    private final DatabaseManager databaseManager;
    private final DataCache dataCache;

    public NodeRepository(final DatabaseManager databaseManager, final DataCache dataCache) {
        this.databaseManager = databaseManager;
        this.dataCache = dataCache;
    }

    public void upsert(final PlacedNodeModel node) {
        final boolean mysql = this.databaseManager.isMySQL();
        final String sql = mysql
            ? """
                INSERT INTO mmoblock_node (unique_id, node_id, world, x, y, z)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    node_id = VALUES(node_id),
                    world = VALUES(world),
                    x = VALUES(x),
                    y = VALUES(y),
                    z = VALUES(z)
                """
            : """
                MERGE INTO mmoblock_node (unique_id, node_id, world, x, y, z)
                KEY(unique_id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, node.uniqueId().toString());
            statement.setString(2, node.nodeId());
            statement.setString(3, node.world());
            statement.setDouble(4, node.x());
            statement.setDouble(5, node.y());
            statement.setDouble(6, node.z());
            statement.executeUpdate();
            this.dataCache.markDbTimestamp(ALL_NODES_CACHE_KEY);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to save node " + node.uniqueId(), exception);
        }
    }

    public void delete(final UUID uniqueId) {
        final String sql = "DELETE FROM mmoblock_node WHERE unique_id = ?";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
            this.dataCache.markDbTimestamp(ALL_NODES_CACHE_KEY);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to delete node " + uniqueId, exception);
        }
    }

    public List<PlacedNodeModel> findAll() {
        if (!isAllNodesStale()) {
            final List<PlacedNodeModel> cached = this.dataCache.getAllNodes();
            if (cached != null) {
                return cached;
            }
        }
        final List<PlacedNodeModel> nodes = new ArrayList<>();
        final String sql = "SELECT unique_id, node_id, world, x, y, z FROM mmoblock_node";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                nodes.add(new PlacedNodeModel(
                        UUID.fromString(resultSet.getString("unique_id")),
                        resultSet.getString("node_id"),
                        resultSet.getString("world"),
                        resultSet.getDouble("x"),
                        resultSet.getDouble("y"),
                        resultSet.getDouble("z")
                ));
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load nodes", exception);
        }
        this.dataCache.cacheNodes(nodes);
        return nodes;
    }

    private boolean isAllNodesStale() {
        return System.currentTimeMillis() - this.dataCache.getDbTimestamp(ALL_NODES_CACHE_KEY) > ALL_NODES_TTL_MS;
    }
}
