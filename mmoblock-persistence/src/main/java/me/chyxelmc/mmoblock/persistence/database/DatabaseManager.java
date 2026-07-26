package me.chyxelmc.mmoblock.persistence.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.plugin.java.JavaPlugin;

import me.chyxelmc.mmoblock.utils.DatabaseUtils;

public final class DatabaseManager {

    public enum Dialect {
        H2,
        MYSQL
    }

    private final JavaPlugin plugin;
    private final DatabaseUtils databaseUtils;
    private Dialect dialect;

    public DatabaseManager(final JavaPlugin plugin, final DatabaseUtils databaseUtils) {
        this.plugin = plugin;
        this.databaseUtils = databaseUtils;
    }

    public void initialize() {
        if (this.databaseUtils.isInitialized()) {
            return;
        }
        final boolean mysqlEnabled = this.plugin.getConfig().getBoolean("databases.mysql.enabled", false);
        if (mysqlEnabled) {
            this.databaseUtils.initializeMySQL(this.plugin);
            this.dialect = Dialect.MYSQL;
        } else {
            this.databaseUtils.initializeH2(this.plugin);
            this.dialect = Dialect.H2;
        }

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS mmoblock_block (
                    unique_id VARCHAR(36) PRIMARY KEY,
                    type VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    origin_x DOUBLE,
                    origin_y DOUBLE,
                    origin_z DOUBLE,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    facing VARCHAR(16) NOT NULL,
                    status VARCHAR(16) NOT NULL
                )
                """);
            migrateBlockColumns(statement);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS mmoblock_respawn (
                    unique_id VARCHAR(36) PRIMARY KEY,
                    last_respawn BIGINT NOT NULL,
                    CONSTRAINT fk_respawn_block FOREIGN KEY (unique_id)
                    REFERENCES mmoblock_block(unique_id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS mmoblock_node (
                    unique_id VARCHAR(36) PRIMARY KEY,
                    node_id VARCHAR(64) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL
                )
                """);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to initialize database", exception);
        }
    }

    private static final java.util.Map<String, String> ALTER_ADD_COLUMN_H2_QUERIES = java.util.Map.of(
        "origin_x", "ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_x DOUBLE",
        "origin_y", "ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_y DOUBLE",
        "origin_z", "ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_z DOUBLE"
    );

    private static final java.util.Map<String, String> ALTER_ADD_COLUMN_MYSQL_QUERIES = java.util.Map.of(
        "origin_x", "ALTER TABLE mmoblock_block ADD COLUMN origin_x DOUBLE",
        "origin_y", "ALTER TABLE mmoblock_block ADD COLUMN origin_y DOUBLE",
        "origin_z", "ALTER TABLE mmoblock_block ADD COLUMN origin_z DOUBLE"
    );

    private void migrateBlockColumns(final Statement statement) throws SQLException {
        for (final java.util.Map.Entry<String, String> entry : ALTER_ADD_COLUMN_H2_QUERIES.entrySet()) {
            final String column = entry.getKey();
            final String exactQuery = this.dialect == Dialect.H2
                ? entry.getValue()
                : ALTER_ADD_COLUMN_MYSQL_QUERIES.get(column);

            if (exactQuery == null) {
                throw new IllegalArgumentException("Rejected unsafe or unknown column name: " + column);
            }

            if (this.dialect == Dialect.H2) {
                statement.execute(exactQuery);
            } else {
                try {
                    statement.execute(exactQuery);
                } catch (final SQLException ignored) {
                    // Column likely already exists; ignore Duplicate column errors
                }
            }
        }
    }

    public Dialect getDialect() {
        return this.dialect;
    }

    public boolean isMySQL() {
        return this.dialect == Dialect.MYSQL;
    }

    public Connection getConnection() throws SQLException {
        return this.databaseUtils.getConnection();
    }

    public void close() {
        this.databaseUtils.close();
    }
}
