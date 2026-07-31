package me.chyxelmc.mmoblock.runtime;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

/**
 * Lightweight registry for positions that currently have a fake-block visual sent to players.
 * Keyed by worldName:x:y:z for O(1) contains checks. Concurrent and safe for async use.
 */
public final class FakeBlockRegistry {

    // Map key -> material name (e.g. "STONE"). Storing material allows NMS handler
    // to re-create the exact fake BlockState when replacing outbound packets.
    private static final Map<String, String> POSITIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> SERVER_MATERIALS = new ConcurrentHashMap<>();

    private FakeBlockRegistry() {}

    private static String key(final String world, final int x, final int y, final int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public static void add(final String world, final int x, final int y, final int z, final String materialName) {
        if (materialName == null) return;
        add(world, x, y, z, materialName, materialName);
    }

    public static void add(
            final String world,
            final int x,
            final int y,
            final int z,
            final String materialName,
            final String serverMaterialName
    ) {
        if (materialName == null) return;
        final String key = key(world, x, y, z);
        POSITIONS.put(key, materialName);
        SERVER_MATERIALS.put(key, serverMaterialName != null ? serverMaterialName : materialName);
    }

    public static void remove(final String world, final int x, final int y, final int z) {
        final String key = key(world, x, y, z);
        POSITIONS.remove(key);
        SERVER_MATERIALS.remove(key);
    }

    public static boolean contains(final String world, final int x, final int y, final int z) {
        return POSITIONS.containsKey(key(world, x, y, z));
    }

    /**
     * Returns the registered material name for a fake-block at the position, or null
     * if none is registered.
     */
    public static String getMaterial(final String world, final int x, final int y, final int z) {
        return POSITIONS.get(key(world, x, y, z));
    }

    public static String getServerMaterial(final String world, final int x, final int y, final int z) {
        final String key = key(world, x, y, z);
        final String serverMaterial = SERVER_MATERIALS.get(key);
        return serverMaterial != null ? serverMaterial : POSITIONS.get(key);
    }

    /**
     * Returns a snapshot of registry keys for the specified world. The returned set
     * is a copy and safe to iterate without concurrent modification concerns.
     */
    public static java.util.Set<String> positionsForWorld(final String world) {
        final String prefix = world + ':';
        final java.util.Set<String> out = new java.util.HashSet<>();
        for (final String k : POSITIONS.keySet()) {
            if (k.startsWith(prefix)) out.add(k);
        }
        return out;
    }

    public static void clear() {
        POSITIONS.clear();
        SERVER_MATERIALS.clear();
    }

    // ============================================================
    // Crash-safe original block data persistence
    //
    // When a block is promoted the original BlockData is stored here
    // and written to a JSON file in the plugin data folder. On normal
    // shutdown (successful demote) the entry is removed. If the
    // server crashes or Folia's RegionShutdownThread prevents demotion,
    // the entry survives so it can be restored on the next startup.
    // ============================================================

    static final Map<String, String> PENDING_RESTORE = new ConcurrentHashMap<>();

    /**
     * Records the original block data for a promoted position so it can be
     * restored if the server shuts down before demotion.
     */
    public static void markForRestore(final String world, final int x, final int y, final int z, final String blockDataAsString) {
        if (blockDataAsString == null) return;
        PENDING_RESTORE.put(key(world, x, y, z), blockDataAsString);
    }

    /**
     * Removes a previously-marked position (called after successful demote).
     */
    public static void clearPendingRestore(final String world, final int x, final int y, final int z) {
        PENDING_RESTORE.remove(key(world, x, y, z));
    }

    /**
     * Returns a snapshot of all positions awaiting restoration.
     */
    public static Map<String, String> pendingRestores() {
        return new java.util.HashMap<>(PENDING_RESTORE);
    }

    /**
     * Clears the pending-restore map (called after successful orphan restoration
     * or when the plugin is fully disabled).
     */
    public static void clearPendingRestores() {
        PENDING_RESTORE.clear();
    }

    private static final Gson GSON = new Gson();
    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Object RESTORE_FILE_LOCK = new Object();

    /**
     * Writes the current pending-restore map to a JSON file so it survives a
     * JVM restart.
     */
    public static void savePendingRestores(final File file) {
        synchronized (RESTORE_FILE_LOCK) {
            if (PENDING_RESTORE.isEmpty()) {
                file.delete();
                return;
            }
            try (final FileWriter w = new FileWriter(file)) {
                GSON.toJson(pendingRestores(), w);
            } catch (final IOException e) {
                // Non-critical; the in-memory entry remains available for retry.
            }
        }
    }

    /**
     * Reads a previously-saved pending-restore file into the in-memory map.
     * Any entries already present in the map are not overwritten.
     */
    public static void loadPendingRestores(final File file) {
        if (!file.exists()) return;
        try (final FileReader r = new FileReader(file)) {
            final Map<String, String> loaded = GSON.fromJson(r, MAP_STRING_STRING_TYPE);
            if (loaded != null) {
                for (final Map.Entry<String, String> e : loaded.entrySet()) {
                    PENDING_RESTORE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Parses a composite key of the form {@code world:x:y:z}.
     */
    private static String[] splitPendingKey(final String key) {
        final int c1 = key.indexOf(':');
        if (c1 < 0) return null;
        final int c2 = key.indexOf(':', c1 + 1);
        if (c2 < 0) return null;
        final int c3 = key.indexOf(':', c2 + 1);
        if (c3 < 0) return null;
        return new String[]{key.substring(0, c1), key.substring(c1 + 1, c2),
                key.substring(c2 + 1, c3), key.substring(c3 + 1)};
    }

    /**
     * Iterates the pending-restore map and attempts to restore each block to
     * its original data. Called once during plugin startup to clean up any
     * blocks that were left in a promoted state after a crash or Folia
     * RegionShutdownThread interruption.
     *
     * <p>Each entry is removed only after its location-scheduled restore succeeds.</p>
     */
    public static void restoreOrphanedBlocks(final File dataFolder, final Scheduler scheduler) {
        final File file = new File(dataFolder, "promoted-blocks.json");
        loadPendingRestores(file);
        if (PENDING_RESTORE.isEmpty()) return;

        for (final Map.Entry<String, String> entry : pendingRestores().entrySet()) {
            final String[] parts = splitPendingKey(entry.getKey());
            if (parts == null) continue;
            try {
                final String worldName = parts[0];
                final int x = Integer.parseInt(parts[1]);
                final int y = Integer.parseInt(parts[2]);
                final int z = Integer.parseInt(parts[3]);
                final String blockDataString = entry.getValue();

                final World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                final BlockData data = Bukkit.createBlockData(blockDataString);
                final Location location = new Location(world, x, y, z);
                scheduler.runAtLocation(location, () -> {
                    try {
                        world.getBlockAt(x, y, z).setBlockData(data, false);
                        PENDING_RESTORE.remove(entry.getKey(), blockDataString);
                        savePendingRestores(file);
                    } catch (final RuntimeException ignored) {
                        // Retain the journal entry so a later startup can retry it.
                    }
                });
            } catch (final Exception ignored) {
                // World not loaded, block data invalid, etc.
                // Skip and retry on next startup.
            }
        }
    }
}
