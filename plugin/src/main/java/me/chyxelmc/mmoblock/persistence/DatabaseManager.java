package me.chyxelmc.mmoblock.persistence;

import me.chyxelmc.mmoblock.MMOBlock;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final MMOBlock plugin;
    private String jdbcUrl;

    public DatabaseManager(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            Class.forName("org.h2.Driver");
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("H2 driver is missing from plugin runtime", exception);
        }

        final String configuredFile = this.plugin.getConfig().getString("databases.h2.file", "data.db");
        final File dbFile = new File(this.plugin.getDataFolder(), configuredFile);
        this.jdbcUrl = "jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE";

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
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to initialize H2 database", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(this.jdbcUrl, "sa", "");
    }

    public void close() {
        // H2 file-mode is connection based; no global handle to close.
    }
}

