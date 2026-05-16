package me.chyxelmc.mmoblock;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.List;

public final class MMOBlockLoader implements PluginLoader {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String CANONICAL_DATA_DIRECTORY = "MMOBlock";
    private static final List<ExternalLibrary> LIBRARIES = List.of(
            new ExternalLibrary(
                    "net.kyori:adventure-text-minimessage:4.26.1",
                    "net/kyori/adventure-text-minimessage/4.26.1/adventure-text-minimessage-4.26.1.jar",
                    "adventure-text-minimessage-4.26.1.jar",
                    "1d43451e9af473252dc8af3e8084238d5ce68ad43af0e3b7383eb3d4b63fff9f"
            ),
            new ExternalLibrary(
                    "com.h2database:h2:2.2.224",
                    "com/h2database/h2/2.2.224/h2-2.2.224.jar",
                    "h2-2.2.224.jar",
                    "b9d8f19358ada82a4f6eb5b174c6cfe320a375b5a9cb5a4fe456d623e6e55497"
            ),
            new ExternalLibrary(
                    "com.github.ben-manes.caffeine:caffeine:3.2.4",
                    "com/github/ben-manes/caffeine/caffeine/3.2.4/caffeine-3.2.4.jar",
                    "caffeine-3.2.4.jar",
                    "9d9d2cfd681fd9272ded3d27c9930db12f89f732345975aa113ebc223bbf1224"
            ),
            new ExternalLibrary(
                    "com.zaxxer:HikariCP:7.0.2",
                    "com/zaxxer/HikariCP/7.0.2/HikariCP-7.0.2.jar",
                    "HikariCP-7.0.2.jar",
                    "f1e612fa27345be3107a85431e8a8aeb205c15364ab2f2d411e40a9d7bb08095"
            )
    );

    @Override
    public void classloader(final PluginClasspathBuilder builder) {
        final Path dataDirectory = resolveCanonicalDataDirectory(builder.getContext().getDataDirectory());
        final Path libDirectory = dataDirectory.resolve(".caches").resolve("libs");
        final var logger = builder.getContext().getLogger();
        try {
            Files.createDirectories(libDirectory);
            for (final ExternalLibrary library : LIBRARIES) {
                final Path jarPath = libDirectory.resolve(library.mavenPath());
                ensureAvailable(library, jarPath, logger);
                builder.addLibrary(new JarLibrary(jarPath));
                logger.info("MMOBlock library classpath registered: " + library.coordinate() + " -> " + jarPath.getFileName());
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to prepare runtime libraries in " + libDirectory, exception);
        }
    }

    private Path resolveCanonicalDataDirectory(final Path contextDataDirectory) {
        final Path parent = contextDataDirectory.getParent();
        if (parent == null) {
            return contextDataDirectory;
        }
        return parent.resolve(CANONICAL_DATA_DIRECTORY);
    }

    private void ensureAvailable(final ExternalLibrary library, final Path targetPath, final Object logger) throws IOException {
        Files.createDirectories(targetPath.getParent());
        if (Files.exists(targetPath) && Files.size(targetPath) > 0L && verifyChecksum(targetPath, library.sha256())) {
            logInfo(logger, "MMOBlock library cache hit: " + library.coordinate());
            return;
        }

        if (Files.exists(targetPath)) {
            logWarn(logger, "MMOBlock library cache invalid or outdated, redownloading: " + library.coordinate());
            Files.deleteIfExists(targetPath);
        } else {
            logInfo(logger, "MMOBlock library missing, downloading: " + library.coordinate());
        }

        final URI uri = URI.create(MAVEN_CENTRAL + library.mavenPath());
        final Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        try (InputStream inputStream = uri.toURL().openStream()) {
            Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            moveDownloadedFile(tempPath, targetPath);
            if (!verifyChecksum(targetPath, library.sha256())) {
                Files.deleteIfExists(targetPath);
                throw new IOException("Checksum mismatch after download for " + library.coordinate());
            }
            logInfo(logger, "MMOBlock library downloaded and verified: " + library.coordinate());
        } catch (final IOException exception) {
            Files.deleteIfExists(tempPath);
            throw new IOException("Could not download library " + library.coordinate() + " from " + uri, exception);
        }
    }

    private boolean verifyChecksum(final Path file, final String expectedSha256) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            final String actual = HexFormat.of().formatHex(digest.digest());
            return actual.equalsIgnoreCase(expectedSha256);
        } catch (final NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 not supported by JVM", exception);
        }
    }

    private void moveDownloadedFile(final Path tempPath, final Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException exception) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void logInfo(final Object logger, final String message) {
        try {
            logger.getClass().getMethod("info", String.class).invoke(logger, message);
        } catch (final ReflectiveOperationException ignored) {
            System.out.println(message);
        }
    }

    private void logWarn(final Object logger, final String message) {
        try {
            logger.getClass().getMethod("warn", String.class).invoke(logger, message);
        } catch (final ReflectiveOperationException ignored) {
            System.out.println(message);
        }
    }

    private record ExternalLibrary(String coordinate, String mavenPath, String fileName, String sha256) {
    }
}
