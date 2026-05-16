package me.chyxelmc.mmoblock.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseUtils {

    private HikariDataSource dataSource;

    public DatabaseUtils() {
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

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE");
        hikariConfig.setDriverClassName("org.h2.Driver");
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        hikariConfig.setPoolName("mmoblock-h2");
        hikariConfig.setMaximumPoolSize(8);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(300_000L);
        hikariConfig.setMaxLifetime(600_000L);
        hikariConfig.setConnectionTimeout(10_000L);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "64");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "4096");

        this.dataSource = new HikariDataSource(hikariConfig);
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
}
