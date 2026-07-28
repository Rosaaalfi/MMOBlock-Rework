package me.chyxelmc.mmoblock.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Polymart / Voxel.shop purchase verification for MMOBlock.
 *
 * <p>This class uses {@code %%__POLYMART__%%} and {@code %%__LICENSE__%%}
 * placeholders that Polymart's resource injector replaces at download time.
 * When the plugin is built with {@code -PnoTargetedStore} (or for non-Voxel
 * distribution), this class is excluded from the final JAR entirely.</p>
 *
 * <h3>Behaviors</h3>
 * <ul>
 *   <li><b>Valid purchase</b> — log a welcome message and continue startup.</li>
 *   <li><b>No license / never purchased</b> — disable the plugin silently.</li>
 *   <li><b>Leaker (previously valid, now revoked)</b> — log an embarrassing
 *       message and disable the plugin.</li>
 *   <li><b>Not a Polymart build</b> (e.g. dev build) — skip verification
 *       and continue.</li>
 * </ul>
 */
public final class TargetedStore {

    private static final String VERIFY_URL = "https://api.voxel.shop/v1/verifyPurchase";
    private static final int RESOURCE_ID = 6390; // MMOBlock resource ID on Voxel.shop
    private static final String VALIDATION_FILE = "targeted_store.dat";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final Gson GSON = new Gson();

    /**
     * Polymart placeholder — replaced with "true" when the JAR is downloaded
     * from Polymart / Voxel.shop. Uses {@code contains("%%__")} to detect
     * whether injection occurred (see static initializer below).
     */
    private static final String POLYMART_DETECT = "%%__POLYMART__%%";
    private static final String LICENSE = "%%__LICENSE__%%";
    private static final String USER = "%%__USER__%%";

    /**
     * {@code true} if the JAR was downloaded from Polymart / Voxel.shop
     * (the {@code %%__POLYMART__%%} placeholder was replaced).
     */
    public static final boolean IS_POLYMART_BUILD = !POLYMART_DETECT.contains("%%__");

    private TargetedStore() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Result of a purchase verification check.
     */
    public enum VerifyResult {
        /** Purchase is valid — continue startup. */
        VALID,
        /** No valid purchase found — disable silently. */
        INVALID,
        /** Purchase was revoked / suspended — leaker detected, show embarrassing message. */
        LEAKER
    }

    /**
     * Run the purchase verification and return the result.
     *
     * @param plugin the plugin instance
     * @return the verification result (never null)
     */
    public static VerifyResult verify(final JavaPlugin plugin) {
        // Not a Polymart build → skip
        if (!IS_POLYMART_BUILD) {
            return VerifyResult.VALID;
        }

        // Check if license placeholder was replaced
        if (LICENSE.contains("%%__")) {
            // License placeholder not replaced → not a valid Polymart download
            return VerifyResult.INVALID;
        }

        try {
            final boolean apiSuccess = callVerifyApi();

            if (apiSuccess) {
                // Save validation state for leaker detection
                saveValidationState(plugin);
                return VerifyResult.VALID;
            }

            // API returned failure — check if this was previously valid
            if (hadPreviousValidation(plugin)) {
                // Previously valid, now revoked → leaker
                return VerifyResult.LEAKER;
            }

            return VerifyResult.INVALID;

        } catch (final Exception e) {
            // Network error, timeout, etc. → be permissive, allow startup
            // If we have previous validation, assume still valid
            if (hadPreviousValidation(plugin)) {
                return VerifyResult.VALID;
            }
            return VerifyResult.INVALID;
        }
    }

    /**
     * Get the buyer display name from Polymart placeholders.
     * Returns {@code "Unknown"} if the placeholders were not replaced.
     */
    public static String getBuyerName() {
        if (USER.contains("%%__")) {
            return "Unknown";
        }
        return USER;
    }

    /**
     * Show the welcome / embarrassing messages based on the result.
     */
    public static void handleResult(final JavaPlugin plugin, final VerifyResult result) {
        switch (result) {
            case VALID -> {
                if (IS_POLYMART_BUILD) {
                    plugin.getLogger().info("[Chyxel] Purchase verified! Thank you " + getBuyerName() + " for supporting MMOBlock.");
                }
            }
            case LEAKER -> {
                final String name = getBuyerName();
                plugin.getLogger().severe("============================================================");
                plugin.getLogger().severe("[Chyxel] LICENSE REVOKED - LEAKER DETECTED!");
                plugin.getLogger().severe("");
                plugin.getLogger().severe("Hello " + name + ", it looks like you shared this plugin");
                plugin.getLogger().severe("illegally. Your license has been revoked by the system.");
                plugin.getLogger().severe("");
                plugin.getLogger().severe("Sharing paid plugins is illegal and violates copyright.");
                plugin.getLogger().severe("");
                plugin.getLogger().severe("Please purchase this plugin at https://voxel.shop/product/6390/");
                plugin.getLogger().severe("============================================================");
            }
            case INVALID -> {
                // Silent disable — no message
            }
        }
    }

    // -------------------------------------------------------------
    // API call
    // -------------------------------------------------------------

    private static boolean callVerifyApi() throws IOException {
        final String encodedLicense = URLEncoder.encode(LICENSE, StandardCharsets.UTF_8);
        final URI uri = URI.create(VERIFY_URL + "?license=" + encodedLicense + "&resource_id=" + RESOURCE_ID);

        final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "MMOBlock-TargetedStore");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);

        final int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            return false;
        }

        final JsonObject root;
        try (final java.io.InputStreamReader reader = new java.io.InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }

        if (root == null) {
            return false;
        }

        final JsonObject response = root.getAsJsonObject("response");
        if (response == null) {
            return false;
        }

        if (response.has("success")) {
            final var successValue = response.get("success");
            if (successValue != null && successValue.isJsonPrimitive()) {
                final var prim = successValue.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    return prim.getAsBoolean();
                }
                if (prim.isString()) {
                    return Boolean.parseBoolean(prim.getAsString());
                }
            }
        }

        return false;
    }

    // -------------------------------------------------------------
    // Local validation state (for leaker detection)
    // -------------------------------------------------------------

    private static File validationFile(final Plugin plugin) {
        return new File(plugin.getDataFolder(), VALIDATION_FILE);
    }

    private static void saveValidationState(final Plugin plugin) {
        final File file = validationFile(plugin);
        file.getParentFile().mkdirs();
        try (final DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeUTF(USER);
            out.writeLong(System.currentTimeMillis());
        } catch (final IOException ignored) {
            // Best-effort save
        }
    }

    private static boolean hadPreviousValidation(final Plugin plugin) {
        final File file = validationFile(plugin);
        if (!file.isFile()) {
            return false;
        }
        try (final DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            final String savedUser = in.readUTF();
            // If the saved user matches the current user, it's a previous validation
            return savedUser.equals(USER) || savedUser.equals(getBuyerName());
        } catch (final IOException ignored) {
            return false;
        }
    }
}
