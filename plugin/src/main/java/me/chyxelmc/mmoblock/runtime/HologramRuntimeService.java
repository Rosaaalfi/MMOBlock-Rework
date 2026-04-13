package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.utils.TextColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class HologramRuntimeService {

    private static final double PACKET_SYNC_RADIUS_SQUARED = 128.0D * 128.0D;

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final HologramBackend backend;

    public HologramRuntimeService(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.backend = chooseBackend();
        this.plugin.getLogger().info("Hologram backend: " + this.backend.name());
    }

    public void showActive(final PlacedBlock block, final BlockDefinition definition) {
        render(block, definition, RenderState.ACTIVE, "", "");
    }

    public void showProgress(final PlacedBlock block, final BlockDefinition definition, final String progressBar) {
        render(block, definition, RenderState.PROGRESS, progressBar, "");
    }

    public void showDead(final PlacedBlock block, final BlockDefinition definition, final long seconds) {
        if (block.respawnAt() == null) {
            block.setRespawnAt(System.currentTimeMillis() + seconds * 1000L);
        }
        render(block, definition, RenderState.DEAD, "", "");
    }

    public void updateDeadRespawnTime(final PlacedBlock block, final BlockDefinition definition) {
        if (block.respawnAt() == null) {
            remove(block);
            return;
        }
        final long remaining = Math.max(0L, (block.respawnAt() - System.currentTimeMillis()) / 1000L);
        render(block, definition, RenderState.DEAD, "", String.valueOf(remaining));
    }

    public void remove(final PlacedBlock block) {
        this.backend.remove(block);
    }

    public void clearAll() {
        this.backend.clearAll();
    }

    public void shutdown() {
        this.backend.shutdown();
    }

    public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
        this.backend.syncForPlayer(player, blocks);
    }

    public void handleViewerQuit(final UUID playerUniqueId) {
        this.backend.handleViewerQuit(playerUniqueId);
    }

    public boolean hasNearbyPlayers(final PlacedBlock block, final double radius) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return false;
        }
        final Location center = new Location(world, block.originX() + 0.5D, block.originY() + 0.5D, block.originZ() + 0.5D);
        return !world.getNearbyPlayers(center, radius).isEmpty();
    }

    private void render(
        final PlacedBlock block,
        final BlockDefinition definition,
        final RenderState state,
        final String progressBar,
        final String respawnTime
    ) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null || definition.displayLines().isEmpty()) {
            remove(block);
            return;
        }

        final List<DisplayLine> sorted = definition.displayLines().stream()
            .sorted(Comparator.comparingInt(DisplayLine::line))
            .toList();

        final List<RenderedLine> renderedLines = new ArrayList<>();
        for (final DisplayLine line : sorted) {
            final RenderedLine rendered = resolveLine(line, state, progressBar, respawnTime);
            if (rendered != null) {
                renderedLines.add(rendered);
            }
        }

        if (renderedLines.isEmpty()) {
            remove(block);
            return;
        }

        final Location location = resolveBaseLocation(block, definition, state, world);
        this.backend.upsert(block, location, renderedLines);
     }

    private Location resolveBaseLocation(final PlacedBlock block, final BlockDefinition definition, final RenderState state, final World world) {
        final boolean dead = state == RenderState.DEAD;
        final double baseX = dead ? block.originX() : block.x();
        final double baseY = dead ? block.originY() : block.y();
        final double baseZ = dead ? block.originZ() : block.z();
        return new Location(world, baseX + 0.5D, baseY + definition.displayHeight(), baseZ + 0.5D);
    }

    private RenderedLine resolveLine(final DisplayLine line, final RenderState state, final String progressBar, final String respawnTime) {
        if (line.item() != null && !line.item().isBlank()) {
            final Material material = parseDisplayMaterial(line.item(), false);
            return material == null ? null : RenderedLine.item(material);
        }
        if (line.block() != null && !line.block().isBlank()) {
            final Material material = parseDisplayMaterial(line.block(), true);
            return material == null ? null : RenderedLine.block(material);
        }

        final String value = switch (state) {
            case ACTIVE -> line.text();
            case PROGRESS -> line.click() != null ? line.click() : line.text();
            case DEAD -> line.dead() != null ? line.dead() : line.text();
        };
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (state) {
            case PROGRESS -> RenderedLine.text(value.replace("{progress_bar}", progressBar));
            case DEAD -> RenderedLine.text(value.replace("{respawn_time}", respawnTime));
            default -> RenderedLine.text(value);
        };
    }

    private Material parseDisplayMaterial(final String raw, final boolean requireBlock) {
        final String normalized = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        final Material material = Material.matchMaterial(normalized, false);
        if (material == null) {
            return null;
        }
        if (requireBlock && !material.isBlock()) {
            return null;
        }
        return material;
    }

    private enum RenderState {
        ACTIVE,
        PROGRESS,
        DEAD
    }

    private HologramBackend chooseBackend() {
        if (!this.nmsAdapter.supportsPacketHolograms()) {
            throw new IllegalStateException("Current NMS adapter does not support packet holograms");
        }
        return new PacketNmsBackend(this.plugin, this.nmsAdapter);
    }

    private interface HologramBackend {

        String name();

        void upsert(PlacedBlock block, Location baseLocation, List<RenderedLine> lines);

        void remove(PlacedBlock block);

        void clearAll();

        default void shutdown() {
            clearAll();
        }

        default void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            // Optional hook for backends that need packet-driven per-player synchronization.
        }

        default void handleViewerQuit(final UUID playerUniqueId) {
            // Optional hook for backends that keep per-viewer runtime state.
        }
    }


    private static final class PacketNmsBackend implements HologramBackend {

        private final MMOBlock plugin;
        private final NmsAdapter nmsAdapter;
        private final Map<UUID, PacketSession> sessions = new ConcurrentHashMap<>();
        private final Map<SyncKey, SyncAction> pendingSync = new ConcurrentHashMap<>();
        private final AtomicLong sessionRevision = new AtomicLong();
        private final BukkitTask flushTask;

        private PacketNmsBackend(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
            this.plugin = plugin;
            this.nmsAdapter = nmsAdapter;
            this.flushTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::flushPendingSync, 1L, 1L);
        }

        @Override
        public String name() {
            return "packet-nms";
        }

        @Override
        public void upsert(final PlacedBlock block, final Location baseLocation, final List<RenderedLine> lines) {
            final World world = baseLocation.getWorld();
            if (world == null) {
                return;
            }

            final long revision = this.sessionRevision.incrementAndGet();
            final PacketSession session = new PacketSession(
                block.world(),
                baseLocation.clone(),
                new ArrayList<>(lines),
                new HashSet<>(),
                List.of(),
                revision
            );
            this.sessions.put(block.uniqueId(), session);

            final PacketLayout layout = currentLayout();
            CompletableFuture
                .supplyAsync(() -> toPacketLines(session.lines(), layout))
                .thenAccept(packetLines -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                    final PacketSession current = this.sessions.get(block.uniqueId());
                    if (current == null || current.revision() != revision) {
                        return;
                    }
                    current.setPacketLines(packetLines);
                    for (final Player viewer : world.getPlayers()) {
                        enqueueSync(viewer.getUniqueId(), block.uniqueId(), SyncAction.UPSERT);
                    }
                }));
        }

        @Override
        public void remove(final PlacedBlock block) {
            final PacketSession session = this.sessions.remove(block.uniqueId());
            if (session == null) {
                return;
            }
            final World world = this.plugin.getServer().getWorld(session.worldName());
            if (world == null) {
                return;
            }
            for (final Player viewer : world.getPlayers()) {
                enqueueSync(viewer.getUniqueId(), block.uniqueId(), SyncAction.REMOVE);
            }
        }

        @Override
        public void clearAll() {
            final List<UUID> ids = new ArrayList<>(this.sessions.keySet());
            for (final UUID id : ids) {
                final PacketSession session = this.sessions.remove(id);
                if (session == null) {
                    continue;
                }
                final World world = this.plugin.getServer().getWorld(session.worldName());
                if (world == null) {
                    continue;
                }
                for (final Player viewer : world.getPlayers()) {
                    enqueueSync(viewer.getUniqueId(), id, SyncAction.REMOVE);
                }
            }
        }

        @Override
        public void shutdown() {
            clearAll();
            this.pendingSync.clear();
            this.flushTask.cancel();
        }

        @Override
        public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            for (final PlacedBlock block : blocks) {
                enqueueSync(player.getUniqueId(), block.uniqueId(), SyncAction.UPSERT);
            }
        }

        @Override
        public void handleViewerQuit(final UUID playerUniqueId) {
            for (final PacketSession session : this.sessions.values()) {
                session.viewers().remove(playerUniqueId);
            }
            this.pendingSync.keySet().removeIf(key -> key.playerUniqueId().equals(playerUniqueId));
        }

        private void syncViewer(final UUID hologramUniqueId, final Player viewer) {
            final PacketSession session = this.sessions.get(hologramUniqueId);
            if (session == null) {
                this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
                return;
            }

            if (session.packetLines().isEmpty()) {
                enqueueSync(viewer.getUniqueId(), hologramUniqueId, SyncAction.UPSERT);
                return;
            }

            final World world = this.plugin.getServer().getWorld(session.worldName());
            if (world == null) {
                this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
                return;
            }

            final boolean shouldShow = viewer.getWorld().equals(world)
                && viewer.getLocation().distanceSquared(session.baseLocation()) <= PACKET_SYNC_RADIUS_SQUARED;

            if (!shouldShow) {
                this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
                session.viewers().remove(viewer.getUniqueId());
                return;
            }

            this.nmsAdapter.upsertPacketHologram(viewer, hologramUniqueId, session.baseLocation(), session.packetLines());
            session.viewers().add(viewer.getUniqueId());
        }

        private void enqueueSync(final UUID playerUniqueId, final UUID hologramUniqueId, final SyncAction action) {
            final SyncKey key = new SyncKey(playerUniqueId, hologramUniqueId);
            this.pendingSync.compute(key, (ignored, previous) -> {
                if (action == SyncAction.REMOVE) {
                    return SyncAction.REMOVE;
                }
                if (previous == SyncAction.REMOVE) {
                    return SyncAction.REMOVE;
                }
                return SyncAction.UPSERT;
            });
        }

        private void flushPendingSync() {
            final int budget = Math.max(16, this.plugin.getConfig().getInt("hologram.packet.maxUpdatesPerTick", 200));
            int processed = 0;
            for (final Map.Entry<SyncKey, SyncAction> entry : this.pendingSync.entrySet()) {
                if (processed >= budget) {
                    break;
                }
                if (!this.pendingSync.remove(entry.getKey(), entry.getValue())) {
                    continue;
                }

                final Player player = this.plugin.getServer().getPlayer(entry.getKey().playerUniqueId());
                if (player != null) {
                    if (entry.getValue() == SyncAction.REMOVE) {
                        this.nmsAdapter.removePacketHologram(player, entry.getKey().hologramUniqueId());
                    } else {
                        syncViewer(entry.getKey().hologramUniqueId(), player);
                    }
                }
                processed++;
            }
        }

        private PacketLayout currentLayout() {
            return new PacketLayout(
                this.plugin.getConfig().getDouble("hologram.packet.spacing.text", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.item", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.block", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.text", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.item", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.block", 0.0D)
            );
        }

        private List<NmsAdapter.HologramLine> toPacketLines(final List<RenderedLine> lines, final PacketLayout layout) {
            final List<NmsAdapter.HologramLine> packetLines = new ArrayList<>(lines.size());
            double cumulativeOffset = 0.0D;
            for (final RenderedLine line : lines) {
                final double offsetY = cumulativeOffset + layout.offset(line.type());
                switch (line.type()) {
                    case TEXT -> packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(line.text()), offsetY));
                    case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), offsetY));
                    case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), offsetY));
                }
                cumulativeOffset += layout.spacing(line.type());
            }
            return packetLines;
        }
    }

    private static final class PacketSession {

        private final String worldName;
        private final Location baseLocation;
        private final List<RenderedLine> lines;
        private final Set<UUID> viewers;
        private final long revision;
        private volatile List<NmsAdapter.HologramLine> packetLines;

        private PacketSession(
            final String worldName,
            final Location baseLocation,
            final List<RenderedLine> lines,
            final Set<UUID> viewers,
            final List<NmsAdapter.HologramLine> packetLines,
            final long revision
        ) {
            this.worldName = worldName;
            this.baseLocation = baseLocation;
            this.lines = lines;
            this.viewers = viewers;
            this.packetLines = packetLines;
            this.revision = revision;
        }

        private String worldName() {
            return this.worldName;
        }

        private Location baseLocation() {
            return this.baseLocation;
        }

        private List<RenderedLine> lines() {
            return this.lines;
        }

        private Set<UUID> viewers() {
            return this.viewers;
        }

        private List<NmsAdapter.HologramLine> packetLines() {
            return this.packetLines;
        }

        private void setPacketLines(final List<NmsAdapter.HologramLine> packetLines) {
            this.packetLines = packetLines;
        }

        private long revision() {
            return this.revision;
        }
    }

    private record SyncKey(UUID playerUniqueId, UUID hologramUniqueId) {
    }

    private enum SyncAction {
        UPSERT,
        REMOVE
    }

    private record PacketLayout(
        double textSpacing,
        double itemSpacing,
        double blockSpacing,
        double textOffset,
        double itemOffset,
        double blockOffset
    ) {

        private double spacing(final RenderedLine.Type type) {
            return switch (type) {
                case TEXT -> this.textSpacing;
                case ITEM -> this.itemSpacing;
                case BLOCK -> this.blockSpacing;
            };
        }

        private double offset(final RenderedLine.Type type) {
            return switch (type) {
                case TEXT -> this.textOffset;
                case ITEM -> this.itemOffset;
                case BLOCK -> this.blockOffset;
            };
        }
    }

    private record RenderedLine(Type type, String text, Material material) {

        static RenderedLine text(final String text) {
            return new RenderedLine(Type.TEXT, text, null);
        }

        static RenderedLine item(final Material material) {
            return new RenderedLine(Type.ITEM, null, material);
        }

        static RenderedLine block(final Material material) {
            return new RenderedLine(Type.BLOCK, null, material);
        }

        enum Type {
            TEXT,
            ITEM,
            BLOCK
        }
    }
}

