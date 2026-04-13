package me.chyxelmc.mmoblock.lib;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime library cache for external dependencies.
 * Downloads libraries to lib/ folder so Paper's classpath loader can consume them at startup.
 */
public final class LibraryLoader {

    private static final String LIB_DIRECTORY = "lib";
    private final Path libDir;
    private final Logger logger;

    public LibraryLoader(final Path pluginDataFolder, final Logger logger) {
        this.libDir = pluginDataFolder.resolve(LIB_DIRECTORY);
        this.logger = logger;
        try {
            Files.createDirectories(this.libDir);
        } catch (final IOException ex) {
            logger.log(Level.SEVERE, "Could not create lib directory", ex);
        }
    }

    /**
     * Load a library from Maven Central or download it.
     *
     * @param groupId      Maven group ID (e.g., "net.kyori")
     * @param artifactId   Maven artifact ID (e.g., "adventure-text-minimessage")
     * @param version      Library version (e.g., "4.26.1")
     * @return true if loaded successfully, false otherwise
     */
    public boolean loadLibrary(final String groupId, final String artifactId, final String version) {
        final String fileName = artifactId + "-" + version + ".jar";
        final Path libPath = this.libDir.resolve(fileName);

        if (!Files.exists(libPath)) {
            final String downloadUrl = toMavenCentralUrl(groupId, artifactId, version);
            this.logger.info("[MMOBlock] Downloading " + fileName + "...");
            if (!downloadFile(downloadUrl, libPath)) {
                this.logger.log(Level.SEVERE, "Failed to download " + fileName);
                return false;
            }
            this.logger.info("[MMOBlock] Downloaded " + fileName);
        }

        this.logger.info("[MMOBlock] Library available in cache: " + libPath.getFileName());
        return true;
    }

    /**
     * Load the H2 database driver.
     *
     * @return true if loaded successfully
     */
    public boolean loadH2Driver() {
        return loadLibrary("com.h2database", "h2", "2.2.224");
    }

    /**
     * Load the Adventure MiniMessage library.
     *
     * @return true if loaded successfully
     */
    public boolean loadAdventureMiniMessage() {
        return loadLibrary("net.kyori", "adventure-text-minimessage", "4.26.1");
    }

    private String toMavenCentralUrl(final String groupId, final String artifactId, final String version) {
        final String path = groupId.replace(".", "/");
        return String.format(
            "https://repo.maven.apache.org/maven2/%s/%s/%s/%s-%s.jar",
            path, artifactId, version, artifactId, version
        );
    }

    private boolean downloadFile(final String urlStr, final Path destination) {
        try (final InputStream input = URI.create(urlStr).toURL().openStream()) {
            Files.copy(input, destination);
            return true;
        } catch (final IOException ex) {
            this.logger.log(Level.WARNING, "Could not download " + urlStr, ex);
            return false;
        }
    }
}

