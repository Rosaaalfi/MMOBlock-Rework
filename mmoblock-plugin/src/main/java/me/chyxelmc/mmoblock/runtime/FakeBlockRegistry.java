package me.chyxelmc.mmoblock.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
}
