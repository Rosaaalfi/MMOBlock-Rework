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
    // Optional ECS integration: if present we create HologramComponent entities
    private me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager;
    private final java.util.Map<java.util.UUID, java.util.UUID> hologramEntities = new java.util.concurrent.ConcurrentHashMap<>();

    public HologramRuntimeService(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.backend = chooseBackend();
        this.plugin.getLogger().info("Hologram backend: " + this.backend.name());
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager) {
        this.entityManager = entityManager;
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
        if (this.entityManager != null) {
            final java.util.UUID ecsId = this.hologramEntities.remove(block.uniqueId());
            if (ecsId != null) {
                final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent comp =
                        this.entityManager.getComponent(ecsId, me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                if (comp != null) {
                    try {
                        comp.markRemoved();
                    } catch (final Throwable ignored) {
                    }
                } else {
                    try {
                        this.entityManager.removeEntity(ecsId);
                    } catch (final Throwable ignored) {
                    }
                }
                return;
            }
        }
        this.backend.remove(block);
    }

    public void clearAll() {
        this.backend.clearAll();
    }

    public void shutdown() {
        if (this.entityManager != null) {
            // mark all ECS holograms removed
            for (final java.util.Map.Entry<java.util.UUID, java.util.UUID> e : this.hologramEntities.entrySet()) {
                try {
                    final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent comp =
                            this.entityManager.getComponent(e.getValue(), me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                    if (comp != null) comp.markRemoved();
                    this.entityManager.removeEntity(e.getValue());
                } catch (final Throwable ignored) {
                }
            }
            this.hologramEntities.clear();
            return;
        }
        this.backend.shutdown();
    }

    public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
        if (this.entityManager != null) {
            for (final PlacedBlock block : blocks) {
                final java.util.UUID ecsId = this.hologramEntities.get(block.uniqueId());
                if (ecsId == null) continue;
                final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent comp =
                        this.entityManager.getComponent(ecsId, me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                if (comp == null) continue;
                try {
                    // immediate upsert for this player
                    this.nmsAdapter.upsertPacketHologram(player, comp.hologramUniqueId(), comp.baseLocation(), comp.lines());
                } catch (final Throwable ignored) {
                }
            }
            return;
        }
        this.backend.syncForPlayer(player, blocks);
    }

    public void handleViewerQuit(final UUID playerUniqueId) {
        if (this.entityManager != null) {
            // nothing specific to do for ECS backend; the NMS adapter still maintains per-player caches
            return;
        }
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

        final PacketLayout layout = packetLayoutFromConfig();
        final List<RenderedLine> renderedLines = new ArrayList<>();
        double cumulativeOffset = 0.0D;
        for (final DisplayLine line : sorted) {
            final RenderedLine.Type slotType = resolveSlotType(line);
            final RenderedLine rendered = resolveLine(line, state, progressBar, respawnTime);
            if (rendered != null) {
                final double offsetY = cumulativeOffset + layout.offset(rendered.type());
                renderedLines.add(rendered.withOffsetY(offsetY));
            }
            // Keep vertical slot spacing even when the line is hidden.
            cumulativeOffset += layout.spacing(slotType);
        }

        if (renderedLines.isEmpty()) {
            remove(block);
            return;
        }

        final Location location = resolveBaseLocation(block, definition, state, world);
        // If ECS integration available, create/update a HologramComponent entity
        if (this.entityManager != null) {
            final java.util.List<NmsAdapter.HologramLine> packetLines = toPacketLines(renderedLines);
            final java.util.UUID hologramUniqueId = block.uniqueId();
            final java.util.UUID ecsId = this.hologramEntities.get(hologramUniqueId);
            if (ecsId == null) {
                try {
                    final java.util.UUID created = me.chyxelmc.mmoblock.nmsloader.utils.NmsEcsUtils.createHologramEntity(
                            this.entityManager,
                            hologramUniqueId,
                            location,
                            packetLines
                    );
                    this.hologramEntities.put(hologramUniqueId, created);
                } catch (final Throwable t) {
                    // Fallback to backend if ECS creation fails
                    this.plugin.getLogger().warning("Failed to create hologram ECS entity: " + t.getMessage());
                    this.backend.upsert(block, location, renderedLines);
                }
            } else {
                // Replace existing ECS component by removing and recreating
                try {
                    this.entityManager.removeEntity(ecsId);
                    final java.util.UUID created = me.chyxelmc.mmoblock.nmsloader.utils.NmsEcsUtils.createHologramEntity(
                            this.entityManager,
                            hologramUniqueId,
                            location,
                            packetLines
                    );
                    this.hologramEntities.put(hologramUniqueId, created);
                } catch (final Throwable t) {
                    this.plugin.getLogger().warning("Failed to update hologram ECS entity: " + t.getMessage());
                    this.backend.upsert(block, location, renderedLines);
                }
            }
            return;
        }

        this.backend.upsert(block, location, renderedLines);
    }

    private java.util.List<NmsAdapter.HologramLine> toPacketLines(final java.util.List<RenderedLine> lines) {
        final java.util.List<NmsAdapter.HologramLine> packetLines = new java.util.ArrayList<>(lines.size());
        for (final RenderedLine line : lines) {
            switch (line.type()) {
                case TEXT -> packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(line.text()), line.offsetY()));
                case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), line.offsetY()));
                case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), line.offsetY()));
            }
        }
        return packetLines;
    }

    private PacketLayout packetLayoutFromConfig() {
        return new PacketLayout(
                this.plugin.getConfig().getDouble("hologram.packet.spacing.text", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.item", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.block", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.text", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.item", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.block", 0.0D)
        );
    }

    private Location resolveBaseLocation(final PlacedBlock block, final BlockDefinition definition, final RenderState state, final World world) {
        final boolean dead = state == RenderState.DEAD;
        final double baseX = dead ? block.originX() : block.x();
        final double baseY = dead ? block.originY() : block.y();
        final double baseZ = dead ? block.originZ() : block.z();
        return new Location(world, baseX + 0.5D, baseY + definition.displayHeight(), baseZ + 0.5D);
    }

    private RenderedLine resolveLine(final DisplayLine line, final RenderState state, final String progressBar, final String respawnTime) {
        String effectiveValue = null;

        switch (state) {
            case ACTIVE:
                effectiveValue = line.text();
                break;
            case PROGRESS:
                // Prioritize click field for PROGRESS state
                effectiveValue = line.click();
                if (effectiveValue == null || effectiveValue.isBlank()) {
                    // Fallback to text if click is not defined
                    effectiveValue = line.text();
                }
                break;
            case DEAD:
                effectiveValue = line.dead();
                if (effectiveValue == null || effectiveValue.isBlank()) {
                    // Fallback to text if dead is not defined
                    effectiveValue = line.text();
                }
                break;
        }

        // If the effective value for this state is a "hide" indicator, treat the line as absent.
        if (isHideValue(effectiveValue)) {
            return null;
        }

        // If it's not a hide value, then check for item/block displays.
        // These should only be rendered if the effectiveValue didn't indicate hiding.
        if (line.item() != null && !line.item().isBlank()) {
            final Material material = parseDisplayMaterial(line.item(), false);
            return material == null ? null : RenderedLine.item(material);
        }
        if (line.block() != null && !line.block().isBlank()) {
            final Material material = parseDisplayMaterial(line.block(), true);
            return material == null ? null : RenderedLine.block(material);
        }

        // Otherwise, treat as a text line.
        if (effectiveValue == null || effectiveValue.isBlank()) {
            return null;
        }

        return switch (state) {
            case PROGRESS -> RenderedLine.text(effectiveValue.replace("{progress_bar}", progressBar));
            case DEAD -> RenderedLine.text(effectiveValue.replace("{respawn_time}", respawnTime));
            default -> RenderedLine.text(effectiveValue);
        };
    }

    private static boolean isHideValue(final String value) {
        if (value == null) return false;
        final String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        // Menggunakan startsWith agar "hide #comment" tetap terdeteksi sebagai hide
        return normalized.startsWith("hide") || normalized.equals("true") || normalized.equals("none");
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

    private RenderedLine.Type resolveSlotType(final DisplayLine line) {
        if (line.item() != null && !line.item().isBlank()) {
            return RenderedLine.Type.ITEM;
        }
        if (line.block() != null && !line.block().isBlank()) {
            return RenderedLine.Type.BLOCK;
        }
        return RenderedLine.Type.TEXT;
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

            CompletableFuture
                    .supplyAsync(() -> toPacketLines(session.lines()))
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

        private List<NmsAdapter.HologramLine> toPacketLines(final List<RenderedLine> lines) {
            final List<NmsAdapter.HologramLine> packetLines = new ArrayList<>(lines.size());
            for (final RenderedLine line : lines) {
                switch (line.type()) {
                    case TEXT -> packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(line.text()), line.offsetY()));
                    case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), line.offsetY()));
                    case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), line.offsetY()));
                }
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

    private record RenderedLine(Type type, String text, Material material, double offsetY) {

        static RenderedLine text(final String text) {
            return new RenderedLine(Type.TEXT, text, null, 0.0D);
        }

        static RenderedLine item(final Material material) {
            return new RenderedLine(Type.ITEM, null, material, 0.0D);
        }

        static RenderedLine block(final Material material) {
            return new RenderedLine(Type.BLOCK, null, material, 0.0D);
        }

        RenderedLine withOffsetY(final double offsetY) {
            return new RenderedLine(this.type, this.text, this.material, offsetY);
        }

        enum Type {
            TEXT,
            ITEM,
            BLOCK
        }
    }
}
