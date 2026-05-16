package me.chyxelmc.mmoblock.persistence.database;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.utils.DatabaseUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final MMOBlock plugin;
    private final DatabaseUtils databaseUtils;

    public DatabaseManager(final MMOBlock plugin, final DatabaseUtils databaseUtils) {
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
        } else {
            this.databaseUtils.initializeH2(this.plugin);
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
            statement.execute("ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_x DOUBLE");
            statement.execute("ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_y DOUBLE");
            statement.execute("ALTER TABLE mmoblock_block ADD COLUMN IF NOT EXISTS origin_z DOUBLE");
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

    public Connection getConnection() throws SQLException {
        return this.databaseUtils.getConnection();
    }

    public void close() {
        this.databaseUtils.close();
    }
}
