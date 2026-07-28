package me.chyxelmc.mmoblock.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Asynchronous update checker that queries the MMOBlock repository index
 * for the latest available version.
 * <p>
 * Fetches {@code https://repo.chyxelmc.me/repository/index.json}, parses the
 * artifact list to find {@code mmoblock} (or {@code mmoblock-api}), and compares
 * the latest remote version with the currently running plugin version.
 * </p>
 * <p>
 * Runs on a daemon thread so it never blocks plugin startup. Results are logged
 * via {@link MMOBlockLogger} at INFO level if an update is available, or DEBUG
 * if the user is already on the latest version.
 * </p>
 */
public final class UpdateChecker {

    private static final String REPO_URL = "https://repo.chyxelmc.me/repository/index.json";
    private static final String USER_AGENT = "MMOBlock-UpdateChecker";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private UpdateChecker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Start an asynchronous update check.
     * <p>
     * Safe to call from any thread. The check runs on a daemon thread and will
     * never throw — errors are silently logged as debug messages.
     * </p>
     *
     * @param currentVersion the currently installed plugin version (e.g. {@code "26.7.25"})
     */
    public static void checkAsync(final String currentVersion) {
        final Thread checker = new Thread(() -> check(currentVersion), "mmoblock-update-checker");
        checker.setDaemon(true);
        checker.start();
    }

    /**
     * Perform a synchronous update check. This blocks the calling thread
     * for up to ~10 seconds.
     *
     * @param currentVersion the currently installed plugin version
     */
    public static void check(final String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return;
        }

        try {
            final URI uri = new URI(REPO_URL);
            final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);

            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                MMOBlockLogger.debug("[UpdateChecker] Server returned HTTP " + responseCode);
                return;
            }

            final Gson gson = new Gson();
            final JsonObject root;
            try (final InputStreamReader reader = new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8)) {
                root = gson.fromJson(reader, JsonObject.class);
            }

            if (root == null) {
                MMOBlockLogger.debug("[UpdateChecker] Failed to parse repository index.");
                return;
            }

            final JsonArray artifacts = root.getAsJsonArray("artifacts");
            if (artifacts == null || artifacts.isEmpty()) {
                MMOBlockLogger.debug("[UpdateChecker] No artifacts found in repository index.");
                return;
            }

            // Search for the MMOBlock artifact (try both "mmoblock" and "mmoblock-api")
            String latestVersion = null;
            for (final JsonElement element : artifacts) {
                final JsonObject artifact = element.getAsJsonObject();
                final String artifactId = artifact.get("artifactId").getAsString();
                if ("mmoblock".equalsIgnoreCase(artifactId)
                        || "mmoblock-api".equalsIgnoreCase(artifactId)
                        || "MMOBlock".equalsIgnoreCase(artifactId)) {
                    final JsonElement latest = artifact.get("latestVersion");
                    if (latest != null && !latest.isJsonNull()) {
                        latestVersion = latest.getAsString();
                    }
                    break;
                }
            }

            if (latestVersion == null || latestVersion.isBlank()) {
                MMOBlockLogger.debug("[UpdateChecker] Could not determine latest version from repository.");
                return;
            }

            final int comparison = compareVersions(currentVersion, latestVersion);
            if (comparison < 0) {
                MMOBlockLogger.info("update.new_version", "A new version is available: " + latestVersion
                        + " (current: " + currentVersion + "). Download at https://voxel.shop/product/6390/",
                        java.util.Map.of("{latest}", latestVersion, "{current}", currentVersion,
                                "{url}", "https://voxel.shop/product/6390/"));
            } else if (comparison == 0) {
                MMOBlockLogger.info("update.latest", "You are running the latest version (" + currentVersion + ").",
                        java.util.Map.of("{version}", currentVersion));
            } else {
                MMOBlockLogger.info("update.newer", "You are running a newer version than the repository ("
                        + currentVersion + " > " + latestVersion + ").",
                        java.util.Map.of("{current}", currentVersion, "{latest}", latestVersion));
            }

        } catch (final Exception ex) {
            MMOBlockLogger.debug("[UpdateChecker] Failed to check for updates: " + ex.getMessage());
        }
    }

    /**
     * Compare two version strings in semver-like format (e.g. {@code "26.7.25"}).
     *
     * @return negative if {@code current < latest}, 0 if equal, positive if {@code current > latest}
     */
    private static int compareVersions(final String current, final String latest) {
        final String[] currentParts = current.split("\\.");
        final String[] latestParts = latest.split("\\.");
        final int maxLength = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < maxLength; i++) {
            final int currentNum = i < currentParts.length ? parseIntSafely(currentParts[i]) : 0;
            final int latestNum = i < latestParts.length ? parseIntSafely(latestParts[i]) : 0;
            if (currentNum != latestNum) {
                return Integer.compare(currentNum, latestNum);
            }
        }
        return 0;
    }

    private static int parseIntSafely(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }
}
