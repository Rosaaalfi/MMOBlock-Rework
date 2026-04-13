package me.chyxelmc.mmoblock.persistence;

import me.chyxelmc.mmoblock.model.PlacedBlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BlockRepository {

    private final DatabaseManager databaseManager;

    public BlockRepository(final DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void upsert(final PlacedBlock block) {
        final String sql = """
            MERGE INTO mmoblock_block (unique_id, type, world, x, y, z, facing, status)
            KEY(unique_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, block.uniqueId().toString());
            statement.setString(2, block.type());
            statement.setString(3, block.world());
            statement.setDouble(4, block.x());
            statement.setDouble(5, block.y());
            statement.setDouble(6, block.z());
            statement.setString(7, block.facing());
            statement.setString(8, block.status());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to save placed block " + block.uniqueId(), exception);
        }
    }

    public void delete(final UUID uniqueId) {
        final String sql = "DELETE FROM mmoblock_block WHERE unique_id = ?";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to delete placed block " + uniqueId, exception);
        }
    }

    public List<PlacedBlock> findAll() {
        final List<PlacedBlock> blocks = new ArrayList<>();
        final String sql = "SELECT unique_id, type, world, x, y, z, facing, status FROM mmoblock_block";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                blocks.add(new PlacedBlock(
                    UUID.fromString(resultSet.getString("unique_id")),
                    resultSet.getString("type"),
                    resultSet.getString("world"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getDouble("z"),
                    resultSet.getString("facing"),
                    resultSet.getString("status")
                ));
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load placed blocks", exception);
        }
        return blocks;
    }
}

