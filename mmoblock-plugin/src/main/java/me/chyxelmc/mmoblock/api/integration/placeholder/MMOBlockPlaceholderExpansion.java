package me.chyxelmc.mmoblock.api.integration.placeholder;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.MMOBlock;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

public final class MMOBlockPlaceholderExpansion extends PlaceholderExpansion {

    private final MMOBlock plugin;

    public MMOBlockPlaceholderExpansion(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "mmoblock";
    }

    @Override
    public String getAuthor() {
        return "Aniko";
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        // If no player or no context is available, allow other expansions to handle
        // this by returning null instead of a default numeric value. Returning "0"
        // earlier caused visible flicker when placeholders were replaced while no
        // per-player context was present.
        if (player == null) return null;
        final UUID playerUniqueId = player.getUniqueId();
        if (playerUniqueId == null) return null;

        final HologramPlaceholderContextStore contextStore = this.plugin.placeholderContextStore();
        if (contextStore == null) return null;

        final HologramPlaceholderContextStore.ContextValues values = contextStore.get(playerUniqueId);

        final String key = params == null ? "" : params.toLowerCase(Locale.ROOT);

        // Simple per-player context-backed placeholders (used when hologram
        // rendering sets a per-player context before calling PlaceholderAPI).
        if (values != null) {
            switch (key) {
                case "progress":
                    return String.valueOf(values.progress());
                case "max_progress":
                    return String.valueOf(values.maxProgress());
                case "respawn_time":
                    // When the block is active respawn time is 0 — present as "Active" per UX request.
                    return values.respawnTimeSeconds() <= 0L ? "Active" : String.valueOf(values.respawnTimeSeconds());
                default:
                    // fallthrough to parameterized handling below
            }
        }

        // Parameterized placeholders: allow callers to request values for an
        // arbitrary block by UUID and/or world coordinates. Supported forms:
        //  - mmoblock_max_progress_<uuid>
        //  - mmoblock_progress_<uuid>_<world>_<x>_<y>_<z>
        //  - mmoblock_respawn_time_<uuid>_<world>_<x>_<y>_<z>
        try {
            // max_progress by uuid
            if (key.startsWith("max_progress_")) {
                final String uuidPart = key.substring("max_progress_".length());
                final java.util.UUID uid = java.util.UUID.fromString(uuidPart);
                final me.chyxelmc.mmoblock.model.PlacedBlockModel block = findPlacedBlockByUuid(uid);
                if (block == null) return null;
                final int max = computeMaxProgressForBlock(block);
                return String.valueOf(max);
            }

            // progress or respawn_time with uuid + world + coords
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(progress|respawn_time)_([0-9a-f\\-]+)_(.+?)_(-?\\d+)_(-?\\d+)_(-?\\d+)$").matcher(key);
            if (m.matches()) {
                final String kind = m.group(1);
                final java.util.UUID uid = java.util.UUID.fromString(m.group(2));
                final String world = m.group(3);
                final double x = Double.parseDouble(m.group(4));
                final double y = Double.parseDouble(m.group(5));
                final double z = Double.parseDouble(m.group(6));

                // Prefer lookup by UUID; fallback to positional lookup.
                me.chyxelmc.mmoblock.model.PlacedBlockModel block = findPlacedBlockByUuid(uid);
                if (block == null) {
                    block = findPlacedBlockByPosition(world, x, y, z);
                }
                if (block == null) return null;

                if ("progress".equals(kind)) {
                    final int p = computeCurrentProgressForBlock(block);
                    return String.valueOf(p);
                } else {
                    final Long respawnAt = block.respawnAt();
                    if (respawnAt == null) return "Active";
                    final long remaining = Math.max(0L, (respawnAt - System.currentTimeMillis()) / 1000L);
                    return String.valueOf(remaining);
                }
            }
        } catch (final Exception ignored) {
            // Fall back to null so PlaceholderAPI can continue gracefully.
            return null;
        }

        return null;
    }

    // Internal helpers -------------------------------------------------

    private me.chyxelmc.mmoblock.model.PlacedBlockModel findPlacedBlockByUuid(final java.util.UUID uid) {
        try {
            final var brs = this.plugin.blockRuntimeService();
            if (brs == null) return null;
            for (final var pb : brs.placedBlocks()) {
                if (pb.uniqueId().equals(uid)) return pb;
            }
        } catch (final Exception e) {
        MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }      return null;
    }

    private me.chyxelmc.mmoblock.model.PlacedBlockModel findPlacedBlockByPosition(final String world, final double x, final double y, final double z) {
        try {
            final var brs = this.plugin.blockRuntimeService();
            if (brs == null) return null;
            return brs.stateRegistry().blockAt(world, x, y, z);
        } catch (final Exception e) {
        MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }      return null;
    }

    private int computeMaxProgressForBlock(final me.chyxelmc.mmoblock.model.PlacedBlockModel block) {
        try {
            final var cfg = this.plugin.blockConfigService();
            if (cfg == null) return 0;
            final var def = cfg.findBlock(block.type());
            if (def == null) return 0;
            final var tools = cfg.tools();
            int max = 1;
            final var groups = def.allowedTools();
            if (groups == null || groups.isEmpty()) return max;
            for (final String group : groups) {
                final var actions = tools.get(group.toLowerCase(java.util.Locale.ROOT));
                if (actions == null) continue;
                for (final var a : actions) {
                    try {
                        final int needed = a.clickNeeded();
                        max = Math.max(max, needed);
                    } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }              }
            }
            return max;
        } catch (final Exception ignored) {
            return 0;
        }
    }

    private int computeCurrentProgressForBlock(final me.chyxelmc.mmoblock.model.PlacedBlockModel block) {
        try {
            final var brs = this.plugin.blockRuntimeService();
            if (brs == null) return 0;
            final var ecs = brs.stateRegistry();
            final var miningComp = ecs.mining(block.uniqueId());
            if (miningComp == null) return 0;
            final var map = miningComp.perPlayerProgress();
            if (map == null || map.isEmpty()) return 0;
            int max = 0;
            for (final Integer v : map.values()) {
                if (v != null) max = Math.max(max, v);
            }
            return max;
        } catch (final Exception ignored) {
            return 0;
        }
    }
}
