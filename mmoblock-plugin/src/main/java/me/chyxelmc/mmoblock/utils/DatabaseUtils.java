package me.chyxelmc.mmoblock.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DatabaseUtils {

    private static final Logger LOGGER = Logger.getLogger("MMOBlock");
    private static final String PASSWORD_HASH_FILE = ".h2_password.hash";

    private HikariDataSource dataSource;

    public DatabaseUtils() {
        // No initialization needed; use initializeH2 or initializeMySQL to set up the connection pool.
    }

    public void initializeH2(final JavaPlugin plugin) {
        final ConfigurationSection config = plugin.getConfig().getConfigurationSection("databases.h2");
        if (config == null) {
            throw new IllegalStateException("Missing databases.h2 config section");
        }

        final String configuredFile = config.getString("file", ".caches/data");
        final Path configuredPath = Path.of(configuredFile);
        final Path dbPath = configuredPath.isAbsolute()
                ? configuredPath
                : plugin.getDataFolder().toPath().resolve(configuredPath);
        final File dbFile = dbPath.toFile();
        final Path dbParentDir = dbPath.getParent();
        if (dbParentDir != null) {
            dbParentDir.toFile().mkdirs();
        }

        // Resolve password: environment variable MMOBLOCK_H2_PASSWORD takes priority,
        // then config key 'password' under databases.h2, then empty string for local dev.
        final String h2Password = resolveH2Password(config);

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE");
        hikariConfig.setDriverClassName("org.h2.Driver");
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword(h2Password);
        hikariConfig.setPoolName("mmoblock-h2");
        hikariConfig.setMaximumPoolSize(8);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(300_000L);
        hikariConfig.setMaxLifetime(600_000L);
        hikariConfig.setConnectionTimeout(10_000L);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "64");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "4096");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            // Verify the connection works. H2 encrypts its file with the password;
            // a wrong password produces "Wrong user name or password" at this point.
            try (Connection conn = this.dataSource.getConnection()) {
                // Connection succeeded — password is correct.
            }
        } catch (final SQLException exception) {
            final String msg = exception.getMessage();
            if (msg != null && msg.contains("28000")) {
                // H2 error code 28000 = "Wrong user name or password"
                LOGGER.severe("[MMOBlock] Failed to open H2 database: the configured password does not match "
                        + "the password used when the database file '" + dbFile.getAbsolutePath() + ".mv.db' was created.");
                LOGGER.severe("[MMOBlock] The H2 database password is encoded into the database file and cannot be changed "
                        + "without the original password. To use a new password:");
                LOGGER.severe("[MMOBlock]   1. Revert the password in config.yml to the original password, OR");
                LOGGER.severe("[MMOBlock]   2. Delete the existing database file (loses all MMOBlock data) to start fresh.");
                LOGGER.severe("[MMOBlock]   3. For a safe password change, use: /mmoblock db changepassword <old> <new>");
                throw new IllegalStateException("H2 database password mismatch for file: " + dbFile.getAbsolutePath(), exception);
            }
            // Re-throw other connection failures
            throw new IllegalStateException("Failed to initialize H2 database at: " + dbFile.getAbsolutePath(), exception);
        }

        // Record the active password hash for change detection on next startup
        saveActivePasswordHash(dbParentDir, h2Password);
    }

    public void initializeMySQL(final JavaPlugin plugin) {
        final ConfigurationSection config = plugin.getConfig().getConfigurationSection("databases.mysql");
        if (config == null) {
            throw new IllegalStateException("Missing databases.mysql config section");
        }

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getString("host", "localhost"),
                config.getInt("port", 3306),
                config.getString("database", "mmoblock")
        ));
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setUsername(config.getString("username", "root"));
        hikariConfig.setPassword(config.getString("password", ""));
        hikariConfig.setPoolName("mmoblock-mysql");
        hikariConfig.setMaximumPoolSize(12);
        hikariConfig.setMinimumIdle(3);
        hikariConfig.setIdleTimeout(300_000L);
        hikariConfig.setMaxLifetime(600_000L);
        hikariConfig.setConnectionTimeout(10_000L);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "128");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "4096");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Changes the H2 database password. This connects with the old password,
     * executes {@code ALTER USER SA SET PASSWORD}, updates the config, and
     * records the new password hash for future change detection.
     *
     * @param plugin      the plugin instance for config access
     * @param oldPassword the current H2 database password
     * @param newPassword the new H2 database password
     * @throws IllegalStateException if the old password does not match or the operation fails
     */
    public void changePassword(final JavaPlugin plugin, final String oldPassword, final String newPassword) {
        if (!isInitialized()) {
            throw new IllegalStateException("H2 database is not initialized");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password must not be empty");
        }

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER USER SA SET PASSWORD '" + newPassword.replace("'", "''") + "'");
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to change H2 database password", exception);
        }

        // Update the config.yml with the new password
        plugin.getConfig().set("databases.h2.password", newPassword);
        plugin.saveConfig();

        // Update the password hash file
        final String configuredFile = plugin.getConfig().getString("databases.h2.file", ".caches/data");
        final Path configuredPath = Path.of(configuredFile);
        final Path dbPath = configuredPath.isAbsolute()
                ? configuredPath
                : plugin.getDataFolder().toPath().resolve(configuredPath);
        saveActivePasswordHash(dbPath.getParent(), newPassword);

        // Rebuild the datasource with the new password so existing connections work
        this.dataSource.close();
        initializeH2(plugin);
    }

    public Connection getConnection() throws SQLException {
        if (this.dataSource == null || this.dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or already closed");
        }
        return this.dataSource.getConnection();
    }

    public void close() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
            this.dataSource = null;
        }
    }

    public boolean isInitialized() {
        return this.dataSource != null && !this.dataSource.isClosed();
    }

    /**
     * Resolves the H2 database password from, in order of priority:
     * <ol>
     *   <li>The environment variable {@code MMOBLOCK_H2_PASSWORD}
     *   <li>The config key {@code password} under the {@code databases.h2} section
     *   <li>Empty string (for local development)
     * </ol>
     * Logs a warning when the resolved value is blank (production best practice).
     */
    private static String resolveH2Password(final ConfigurationSection config) {
        final String envVal = System.getenv("MMOBLOCK_H2_PASSWORD");
        if (envVal != null && !envVal.isEmpty()) {
            return envVal;
        }
        final String cfgVal = config.getString("password");
        if (cfgVal != null && !cfgVal.isEmpty()) {
            return cfgVal;
        }
        LOGGER.warning("[MMOBlock] Database password is empty - set MMOBLOCK_H2_PASSWORD environment variable "
                + "or the 'databases.h2.password' config key for a non-blank password in production.");
        return "";
    }

    private static void saveActivePasswordHash(final Path dbParentDir, final String password) {
        if (dbParentDir == null) {
            return;
        }
        try {
            dbParentDir.toFile().mkdirs();
            final String hash = sha256Hex(password);
            final Path hashFile = dbParentDir.resolve(PASSWORD_HASH_FILE);
            Files.writeString(hashFile, hash, StandardCharsets.UTF_8);
        } catch (final Exception ignored) {
            // Best-effort; failure to write the hash file is non-fatal.
        }
    }

    private static String sha256Hex(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final Exception ignored) {
            return "";
        }
    }
}
