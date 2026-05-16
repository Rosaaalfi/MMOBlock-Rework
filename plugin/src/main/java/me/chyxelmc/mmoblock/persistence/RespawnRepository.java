package me.chyxelmc.mmoblock.persistence;

import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.persistence.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RespawnRepository {

    private final DatabaseManager databaseManager;
    private final DataCache dataCache;

    public RespawnRepository(final DatabaseManager databaseManager, final DataCache dataCache) {
        this.databaseManager = databaseManager;
        this.dataCache = dataCache;
    }

    public void upsert(final UUID uniqueId, final long lastRespawn) {
        final String sql = """
            MERGE INTO mmoblock_respawn (unique_id, last_respawn)
            KEY(unique_id) VALUES (?, ?)
            """;
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.setLong(2, lastRespawn);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to upsert respawn row for " + uniqueId, exception);
        }
    }

    public Long findById(final UUID uniqueId) {
        final Long cached = this.dataCache.getRespawn(uniqueId);
        if (cached != null) {
            return cached;
        }
        final String sql = "SELECT last_respawn FROM mmoblock_respawn WHERE unique_id = ?";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                final long fromDb = resultSet.getLong("last_respawn");
                this.dataCache.cacheRespawn(uniqueId, fromDb);
                return fromDb;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to find respawn row for " + uniqueId, exception);
        }
    }

    public Map<UUID, Long> findAll() {
        final Map<UUID, Long> result = new HashMap<>();
        final String sql = "SELECT unique_id, last_respawn FROM mmoblock_respawn";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.put(UUID.fromString(resultSet.getString("unique_id")), resultSet.getLong("last_respawn"));
            }
            return result;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load respawn rows", exception);
        }
    }

    public void delete(final UUID uniqueId) {
        final String sql = "DELETE FROM mmoblock_respawn WHERE unique_id = ?";
        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to delete respawn row for " + uniqueId, exception);
        }
    }
}

