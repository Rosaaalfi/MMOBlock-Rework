package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.ConditionDefinition;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.utils.ConditionEvaluator;
import me.chyxelmc.mmoblock.utils.HologramAnimationUtil;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.util.Vector;

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
        // logging removed
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void showActive(final PlacedBlock block, final BlockDefinition definition) {
        render(block, definition, RenderState.ACTIVE, "", "", 0, 0, 0L);
    }

    public void showProgress(
            final PlacedBlock block,
            final BlockDefinition definition,
            final String progressBar,
            final int progress,
            final int maxProgress
    ) {
        render(block, definition, RenderState.PROGRESS, progressBar, "", progress, maxProgress, 0L);
    }

    public void showDead(final PlacedBlock block, final BlockDefinition definition, final long seconds) {
        if (block.respawnAt() == null) {
            block.setRespawnAt(System.currentTimeMillis() + seconds * 1000L);
        }
        render(block, definition, RenderState.DEAD, "", String.valueOf(seconds), 0, 0, seconds);
    }

    public void updateDeadRespawnTime(final PlacedBlock block, final BlockDefinition definition) {
        if (block.respawnAt() == null) {
            remove(block);
            return;
        }
        final long remaining = Math.max(0L, (block.respawnAt() - System.currentTimeMillis()) / 1000L);
        render(block, definition, RenderState.DEAD, "", String.valueOf(remaining), 0, 0, remaining);
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
            }
        }
        this.backend.remove(block);
    }

    public void clearAll() {
        if (this.entityManager != null) {
            for (final java.util.Map.Entry<java.util.UUID, java.util.UUID> e : this.hologramEntities.entrySet()) {
                try {
                    final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent comp =
                            this.entityManager.getComponent(e.getValue(), me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                    if (comp != null) {
                        removeEcsPacketHologramForViewers(comp);
                        comp.markRemoved();
                    } else {
                        this.entityManager.removeEntity(e.getValue());
                    }
                } catch (final Throwable ignored) {
                }
            }
            this.hologramEntities.clear();
        }
        this.backend.clearAll();
    }

    public void shutdown() {
        if (this.entityManager != null) {
            // mark all ECS holograms removed
            for (final java.util.Map.Entry<java.util.UUID, java.util.UUID> e : this.hologramEntities.entrySet()) {
                try {
                    final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent comp =
                            this.entityManager.getComponent(e.getValue(), me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                    if (comp != null) {
                        removeEcsPacketHologramForViewers(comp);
                        comp.markRemoved();
                    }
                    this.entityManager.removeEntity(e.getValue());
                } catch (final Throwable ignored) {
                }
            }
            this.hologramEntities.clear();
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
        }
        this.backend.syncForPlayer(player, blocks);
    }

    public void handleViewerQuit(final UUID playerUniqueId) {
        this.backend.handleViewerQuit(playerUniqueId);
    }

    private void removeEcsPacketHologramForViewers(
            final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent component
    ) {
        if (component == null || component.baseLocation() == null || component.baseLocation().getWorld() == null) {
            return;
        }
        for (final Player player : component.baseLocation().getWorld().getPlayers()) {
            try {
                this.nmsAdapter.removePacketHologram(player, component.hologramUniqueId());
            } catch (final Throwable ignored) {
            }
        }
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
            final String respawnTime,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
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
            final RenderedLine rendered = resolveLine(line, state, progressBar, respawnTime, progress, maxProgress, respawnTimeSeconds);
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
        final boolean animated = hasAnimatedText(renderedLines);
        final boolean containsPlaceholderApiTokens = hasPlaceholderApiTokens(renderedLines);
        final PlaceholderValues placeholderValues = new PlaceholderValues(progress, maxProgress, respawnTimeSeconds, state.name());

        // If ECS integration available, create/update a HologramComponent entity.
        // Animated holograms are handled by packet backend because they need periodic
        // text recomposition.
        if (this.entityManager != null && !animated && !containsPlaceholderApiTokens) {
            this.backend.remove(block);
            final java.util.List<NmsAdapter.HologramLine> packetLines = toPacketLines(renderedLines, currentAnimationStep());
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
                    // logging removed
                    this.backend.upsert(block, location, renderedLines, placeholderValues, definition);
                }
            } else {
                final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent component =
                        this.entityManager.getComponent(ecsId, me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
                if (component != null && !component.removed()) {
                    component.update(location, packetLines);
                } else {
                    this.hologramEntities.remove(hologramUniqueId, ecsId);
                    try {
                        final java.util.UUID created = me.chyxelmc.mmoblock.nmsloader.utils.NmsEcsUtils.createHologramEntity(
                                this.entityManager,
                                hologramUniqueId,
                                location,
                                packetLines
                        );
                        this.hologramEntities.put(hologramUniqueId, created);
                    } catch (final Throwable t) {
                        // logging removed
                        this.backend.upsert(block, location, renderedLines, placeholderValues, definition);
                    }
                }
            }
            return;
        }

        if (this.entityManager != null) {
            removeEcsHologramEntity(block.uniqueId());
        }

        this.backend.upsert(block, location, renderedLines, placeholderValues, definition);
    }

    private java.util.List<NmsAdapter.HologramLine> toPacketLines(final java.util.List<RenderedLine> lines, final long animationStep) {
        final java.util.List<NmsAdapter.HologramLine> packetLines = new java.util.ArrayList<>(lines.size());
        for (final RenderedLine line : lines) {
            switch (line.type()) {
                case TEXT -> {
                    final String resolved = HologramAnimationUtil.resolveAnimations(line.text(), animationStep);
                    // Keep legacy §-serialized text here for maximum client compatibility (legacy armor stand nameplates
                    // expect legacy § codes). The NMS adapter will handle MiniMessage for modern text-displays, but
                    // armor-stand fallbacks require legacy format — so serialize to legacy section here.
                    packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(resolved), line.offsetY()));
                }
                case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), line.offsetY()));
                case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), line.offsetY()));
            }
        }
        return packetLines;
    }

    private static boolean hasAnimatedText(final List<RenderedLine> lines) {
        for (final RenderedLine line : lines) {
            if (line.type() == RenderedLine.Type.TEXT && HologramAnimationUtil.containsAnimationTag(line.text())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPlaceholderApiTokens(final List<RenderedLine> lines) {
        for (final RenderedLine line : lines) {
            if (line.type() != RenderedLine.Type.TEXT || line.text() == null) {
                continue;
            }
            final String text = line.text();
            final int first = text.indexOf('%');
            if (first >= 0) {
                final int second = text.indexOf('%', first + 1);
                if (second > first + 1) {
                    return true;
                }
            }
            if (text.contains("{condition_")) {
                return true;
            }
        }
        return false;
    }

    private void removeEcsHologramEntity(final UUID hologramUniqueId) {
        final UUID ecsId = this.hologramEntities.remove(hologramUniqueId);
        if (ecsId == null || this.entityManager == null) {
            return;
        }
        final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent component =
                this.entityManager.getComponent(ecsId, me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
        if (component != null) {
            component.markRemoved();
            return;
        }
        try {
            this.entityManager.removeEntity(ecsId);
        } catch (final Throwable ignored) {
        }
    }

    private static long currentAnimationStep() {
        return HologramAnimationUtil.currentSystemStep();
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

    private RenderedLine resolveLine(
            final DisplayLine line,
            final RenderState state,
            final String progressBar,
            final String respawnTime,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
    ) {
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

        final String renderedText = switch (state) {
            case PROGRESS -> effectiveValue.replace("{progress_bar}", progressBar);
            case DEAD -> effectiveValue.replace("{respawn_time}", respawnTime);
            default -> effectiveValue;
        };

        return RenderedLine.text(
                renderedText
                        .replace("%mmoblock_progress%", String.valueOf(progress))
                        .replace("%mmoblock_max_progress%", String.valueOf(maxProgress))
                        .replace("%mmoblock_respawn_time%", String.valueOf(respawnTimeSeconds))
        );
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

        void upsert(
                PlacedBlock block,
                Location baseLocation,
                List<RenderedLine> lines,
                PlaceholderValues placeholderValues,
                BlockDefinition definition
        );

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
        private final AtomicLong animationTick = new AtomicLong();
        // Tick counter for throttling proximity rescans
        private final AtomicLong proximityTick = new AtomicLong();
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
        public void upsert(
                final PlacedBlock block,
                final Location baseLocation,
                final List<RenderedLine> lines,
                final PlaceholderValues placeholderValues,
                final BlockDefinition definition
        ) {
            final World world = baseLocation.getWorld();
            if (world == null) {
                return;
            }

            final long revision = this.sessionRevision.incrementAndGet();
            final boolean animated = hasAnimatedText(lines);
            final boolean dynamicPlaceholders = HologramRuntimeService.hasPlaceholderApiTokens(lines);
            final PacketSession session = new PacketSession(
                    block.world(),
                    baseLocation.clone(),
                    new ArrayList<>(lines),
                    new HashSet<>(),
                    List.of(),
                    revision,
                    animated,
                    dynamicPlaceholders,
                    placeholderValues,
                    definition
            );

            // Clear old caches when creating new session
            final PacketSession oldSession = this.sessions.get(block.uniqueId());
            if (oldSession != null) {
                oldSession.lastSentState().clear();
                oldSession.lastSentResolvedLines().clear();
            }

            this.sessions.put(block.uniqueId(), session);

            if (animated) {
                session.setPacketLines(toPacketLines(session.lines(), currentAnimationStep()));
                for (final Player viewer : world.getPlayers()) {
                    enqueueSync(viewer.getUniqueId(), block.uniqueId(), SyncAction.UPSERT);
                }
                return;
            }

            // For non-animated, compute packet lines asynchronously
            CompletableFuture
                    .supplyAsync(() -> toPacketLines(session.lines(), currentAnimationStep()))
                    .thenAccept(packetLines -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                        final PacketSession current = this.sessions.get(block.uniqueId());
                        if (current == null || current.revision() != revision) {
                            return;
                        }
                        current.setPacketLines(packetLines);

                        // Only enqueue if there are current viewers - minimizes redundant packets
                        if (!current.viewers().isEmpty()) {
                            for (final UUID viewerId : new java.util.HashSet<>(current.viewers())) {
                                enqueueSync(viewerId, block.uniqueId(), SyncAction.UPSERT);
                            }
                        }
                    }));
        }

        @Override
        public void remove(final PlacedBlock block) {
            final PacketSession session = this.sessions.remove(block.uniqueId());
            if (session == null) {
                return;
            }
            // Clear caches when removing hologram
            session.lastSentState().clear();
            session.lastSentResolvedLines().clear();
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
                // Clear all caches
                session.lastSentState().clear();
                session.lastSentResolvedLines().clear();
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
            flushPendingSync();
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
                session.lastSentState().remove(playerUniqueId);
                session.lastSentResolvedLines().remove(playerUniqueId);
            }
            this.pendingSync.keySet().removeIf(key -> key.playerUniqueId().equals(playerUniqueId));
        }

        private void syncViewer(final UUID hologramUniqueId, final Player viewer) {
            final PacketSession session = this.sessions.get(hologramUniqueId);
            if (session == null) {
                this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
                return;
            }

            final List<NmsAdapter.HologramLine> linesToSend;
            final long currentAnimationStep;

            if (session.animated()) {
                currentAnimationStep = currentAnimationStep();
                linesToSend = session.packetLinesForStep(currentAnimationStep, current -> toPacketLines(session.lines(), current));
            } else if (session.packetLines().isEmpty()) {
                // If packetLines are empty for a non-animated session, start a single
                // asynchronous resolution compute to populate packetLines. Prevent
                // duplicate resolution tasks using an AtomicBoolean on the session.
                if (session.startResolving()) {
                    // Perform the potentially expensive toPacketLines() off the main thread.
                    CompletableFuture
                            .supplyAsync(() -> toPacketLines(session.lines(), currentAnimationStep()))
                            .whenComplete((packetLines, throwable) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                                try {
                                    // If session was removed or changed revision meanwhile, ignore
                                    final PacketSession current = this.sessions.get(hologramUniqueId);
                                    if (current == null || current.revision() != session.revision()) {
                                        return;
                                    }
                                    if (throwable == null && packetLines != null && !packetLines.isEmpty()) {
                                        current.setPacketLines(packetLines);
                                        // Re-enqueue UPSERT for all online players in the same world so
                                        // they receive the freshly computed packet lines.
                                        final World world = this.plugin.getServer().getWorld(current.worldName());
                                        if (world != null) {
                                            for (final Player p : world.getPlayers()) {
                                                enqueueSync(p.getUniqueId(), hologramUniqueId, SyncAction.UPSERT);
                                            }
                                        }
                                    }
                                } finally {
                                    // allow subsequent resolves if needed
                                    final PacketSession current = this.sessions.get(hologramUniqueId);
                                    if (current != null) current.finishResolving();
                                }
                            }));
                }
                // If resolution already in progress (or just started), do not proceed with sync now.
                return;
            } else {
                currentAnimationStep = -1L;
                linesToSend = session.packetLines();
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
                session.lastSentState().remove(viewer.getUniqueId());
                return;
            }

            // Only resolve placeholders for animated OR holograms with placeholder API tokens
            boolean hasPlaceholderTokens = hasPlaceholderApiTokens(linesToSend);
            final List<NmsAdapter.HologramLine> resolvedLines;

            if (session.animated() || hasPlaceholderTokens) {
                // For animated or placeholder-bearing holograms, always resolve to get fresh values
                resolvedLines = resolveViewerPlaceholders(
                        viewer,
                        linesToSend,
                        session.placeholderValues(),
                        session.baseLocation(),
                        hologramUniqueId,
                        session.definition(),
                        currentAnimationStep
                );
            } else {
                // For static item/block holograms, no need to resolve - just use raw lines
                resolvedLines = linesToSend;
            }

            // Check if we already sent this state - skip redundant sends to prevent flicker
            final CachedViewerState lastState = session.lastSentState().get(viewer.getUniqueId());
            final CachedViewerState currentState = new CachedViewerState(currentAnimationStep, session.placeholderValues(), linesToSend);

            if (lastState != null && lastState.equals(currentState) && linesAreEqual(session.lastSentResolvedLines().get(viewer.getUniqueId()), resolvedLines)) {
                // Nothing changed, don't send update
                session.viewers().add(viewer.getUniqueId());
                return;
            }

            // Something changed, send update and record state
            this.nmsAdapter.upsertPacketHologram(
                    viewer,
                    hologramUniqueId,
                    session.baseLocation(),
                    resolvedLines
            );
            session.lastSentState().put(viewer.getUniqueId(), currentState);
            session.cacheResolvedLines(viewer.getUniqueId(), resolvedLines);
            session.viewers().add(viewer.getUniqueId());
        }

        private static boolean linesAreEqual(final List<NmsAdapter.HologramLine> a, final List<NmsAdapter.HologramLine> b) {
            if (a == null || b == null) return a == b;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                final NmsAdapter.HologramLine la = a.get(i);
                final NmsAdapter.HologramLine lb = b.get(i);
                if (la.type() != lb.type()) return false;
                if (!java.util.Objects.equals(la.offsetY(), lb.offsetY())) return false;
                if (la.type() == NmsAdapter.HologramLineType.TEXT) {
                    if (!java.util.Objects.equals(la.text(), lb.text())) return false;
                } else if (la.type() == NmsAdapter.HologramLineType.ITEM || la.type() == NmsAdapter.HologramLineType.BLOCK) {
                    if (!java.util.Objects.equals(la.material(), lb.material())) return false;
                }
            }
            return true;
        }

        private static boolean hasPlaceholderApiTokens(final List<NmsAdapter.HologramLine> lines) {
            for (final NmsAdapter.HologramLine line : lines) {
                if (line.type() != NmsAdapter.HologramLineType.TEXT || line.text() == null) continue;
                final String text = line.text();
                if (text.contains("%mmoblock_") || text.contains("%") || text.contains("{condition_")) {
                    return true;
                }
            }
            return false;
        }

        private List<NmsAdapter.HologramLine> resolveViewerPlaceholders(
                final Player viewer,
                final List<NmsAdapter.HologramLine> source,
                final PlaceholderValues placeholderValues,
                final Location baseLocation,
                final UUID hologramUniqueId,
                final BlockDefinition definition,
                final long animationStep
        ) {
            if (source.isEmpty()) {
                return source;
            }
            boolean changed = false;
            final List<NmsAdapter.HologramLine> resolved = new ArrayList<>(source.size());

            // Precompile patterns for entity-specific placeholders.
            final Pattern pProgressSpecific = Pattern.compile("%mmoblock_progress_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");
            final Pattern pMaxSpecific = Pattern.compile("%mmoblock_max_progress_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");
            final Pattern pRespawnSpecific = Pattern.compile("%mmoblock_respawn_time_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");

            final boolean viewerLookingAt = playerIsLookingAt(viewer, baseLocation);
            final long step = animationStep >= 0 ? animationStep : currentAnimationStep();

            for (final NmsAdapter.HologramLine line : source) {
                if (line.type() != NmsAdapter.HologramLineType.TEXT || line.text() == null || line.text().isEmpty()) {
                    resolved.add(line);
                    continue;
                }
                String text = line.text();

                // 1) Handle entity-specific placeholders like %mmoblock_progress_<uuid>_<world>_<x>_<y>_<z>%
                text = replaceEntitySpecificPlaceholder(text, pProgressSpecific, hologramUniqueId, baseLocation, viewerLookingAt,
                        String.valueOf(placeholderValues.progress()));
                text = replaceEntitySpecificPlaceholder(text, pMaxSpecific, hologramUniqueId, baseLocation, viewerLookingAt,
                        String.valueOf(placeholderValues.maxProgress()));
                // respawn: if state is ACTIVE -> show "Active" regardless of raytrace; else provide seconds when looking
                final String respawnReplacement = "ACTIVE".equals(placeholderValues.stateName()) ? "Active" : String.valueOf(placeholderValues.respawnTimeSeconds());
                text = replaceEntitySpecificPlaceholder(text, pRespawnSpecific, hologramUniqueId, baseLocation, viewerLookingAt,
                        respawnReplacement);

                // 2) General placeholders without explicit entity id
                if (viewerLookingAt) {
                    // When the player is looking at this hologram, provide current values for the common tokens
                    text = text.replace("%mmoblock_progress%", String.valueOf(placeholderValues.progress()));
                    text = text.replace("%mmoblock_max_progress%", String.valueOf(placeholderValues.maxProgress()));
                    if ("ACTIVE".equals(placeholderValues.stateName())) {
                        text = text.replace("%mmoblock_respawn_time%", "Active");
                    } else {
                        text = text.replace("%mmoblock_respawn_time%", String.valueOf(placeholderValues.respawnTimeSeconds()));
                    }
                } else {
                    // If not looking: still render max progress as a stable value to avoid flicker
                    text = text.replace("%mmoblock_max_progress%", String.valueOf(placeholderValues.maxProgress()));
                    // For respawn time, if state is ACTIVE, parse to "Active"
                    if ("ACTIVE".equals(placeholderValues.stateName())) {
                        text = text.replace("%mmoblock_respawn_time%", "Active");
                    }
                }

                // 2.5) Condition placeholders
                text = replaceConditionPlaceholders(viewer, definition, text, step);

                // 3) Hand off to plugin placeholder API for other placeholders and final sanitization
                final String replaced = this.plugin.applyHologramPlaceholderApi(
                        viewer,
                        text,
                        placeholderValues.progress(),
                        placeholderValues.maxProgress(),
                        placeholderValues.respawnTimeSeconds()
                );
                if (!replaced.equals(line.text())) {
                    changed = true;
                }
                resolved.add(NmsAdapter.HologramLine.text(replaced, line.offsetY()));
            }
            return changed ? resolved : source;
        }

        private String replaceConditionPlaceholders(
                final Player viewer,
                final BlockDefinition definition,
                final String input,
                final long animationStep
        ) {
            if (definition == null || definition.conditions() == null || definition.conditions().isEmpty()) {
                return input;
            }
            final Matcher matcher = CONDITION_PATTERN.matcher(input);
            final StringBuffer sb = new StringBuffer();
            boolean any = false;
            while (matcher.find()) {
                any = true;
                final int id;
                try {
                    id = Integer.parseInt(matcher.group(1));
                } catch (final NumberFormatException exception) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                    continue;
                }
                final ConditionDefinition condition = findCondition(definition.conditions(), id);
                if (condition == null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                    continue;
                }
                final boolean met = ConditionEvaluator.isMet(this.plugin, viewer, condition);
                String replacement = ConditionEvaluator.resolvePlaceholderText(this.plugin, viewer, condition, met);
                if (replacement == null) {
                    replacement = "";
                }
                final String animated = HologramAnimationUtil.resolveAnimations(replacement, animationStep);
                final String replacementLegacy = TextColorUtil.toLegacySection(animated);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacementLegacy));
            }
            if (!any) {
                return input;
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        private ConditionDefinition findCondition(final List<ConditionDefinition> conditions, final int id) {
            for (final ConditionDefinition condition : conditions) {
                if (condition != null && condition.id() == id) {
                    return condition;
                }
            }
            return null;
        }

        private static String replaceEntitySpecificPlaceholder(
                String input,
                final Pattern p,
                final UUID hologramUniqueId,
                final Location baseLocation,
                final boolean viewerLookingAt,
                final String replacementWhenMatched
        ) {
            final Matcher m = p.matcher(input);
            final StringBuffer sb = new StringBuffer();
            boolean any = false;
            while (m.find()) {
                any = true;
                final String id = m.group(1);
                final String world = m.group(2);
                final String sx = m.group(3);
                final String sy = m.group(4);
                final String sz = m.group(5);

                boolean match = false;
                try {
                    if (id != null && id.equalsIgnoreCase(hologramUniqueId.toString())) {
                        match = true;
                        if (world != null && sx != null && sy != null && sz != null) {
                            final String baseWorld = baseLocation.getWorld() == null ? "" : baseLocation.getWorld().getName();
                            final int bx = baseLocation.getBlockX();
                            final int by = baseLocation.getBlockY();
                            final int bz = baseLocation.getBlockZ();
                            if (!world.equals(baseWorld) || bx != Integer.parseInt(sx) || by != Integer.parseInt(sy) || bz != Integer.parseInt(sz)) {
                                match = false;
                            }
                        }
                    }
                } catch (final Throwable ignored) {
                    match = false;
                }

                if (match && viewerLookingAt) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(replacementWhenMatched));
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            if (!any) return input;
            m.appendTail(sb);
            return sb.toString();
        }

        private static boolean playerIsLookingAt(final Player player, final Location target) {
            if (player == null || target == null) return false;
            try {
                final Vector eye = player.getEyeLocation().toVector();
                final Vector dir = player.getEyeLocation().getDirection().normalize();
                final Vector toTarget = target.toVector().subtract(eye);
                final double projection = toTarget.dot(dir);
                if (projection < 0) return false;
                final double maxDistance = 20.0D;
                if (projection > maxDistance) return false;
                final Vector closest = eye.clone().add(dir.multiply(projection));
                final double distanceSq = closest.distanceSquared(target.toVector());
                final double threshold = 1.5D;
                return distanceSq <= (threshold * threshold);
            } catch (final Throwable ignored) {
                return false;
            }
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
            enqueueAnimatedUpdates();
            // Periodic proximity re-scan: runs every "hologram.packet.proximityScanIntervalTicks" (default 20)
            proximityRescanIfDue();
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

        private void enqueueAnimatedUpdates() {
            final int interval = Math.max(1, this.plugin.getConfig().getInt("hologram.packet.animationUpdateIntervalTicks", 1));
            final long tick = this.animationTick.incrementAndGet();
            if (tick % interval != 0L) {
                return;
            }

            for (final Map.Entry<UUID, PacketSession> entry : this.sessions.entrySet()) {
                final PacketSession session = entry.getValue();
                if ((!session.animated() && !session.dynamicPlaceholders()) || session.viewers().isEmpty()) {
                    continue;
                }
                for (final UUID viewerId : session.viewers()) {
                    enqueueSync(viewerId, entry.getKey(), SyncAction.UPSERT);
                }
            }
        }

        /**
         * Perform a throttled proximity rescan across sessions. This checks each session's world
         * and enqueues UPSERT for online players in range who are not yet recorded as viewers.
         * Runs on main server thread as it accesses Bukkit APIs.
         */
        private void proximityRescanIfDue() {
            final int interval = Math.max(1, this.plugin.getConfig().getInt("hologram.packet.proximityScanIntervalTicks", 20));
            final long tick = this.proximityTick.incrementAndGet();
            if (tick % interval != 0L) return;

            for (final Map.Entry<UUID, PacketSession> entry : this.sessions.entrySet()) {
                final UUID hologramId = entry.getKey();
                final PacketSession session = entry.getValue();

                // Skip proximity rescan for animated holograms - they're constantly updated anyway
                if (session.animated()) continue;

                final World world = this.plugin.getServer().getWorld(session.worldName());
                if (world == null) continue;

                for (final Player player : world.getPlayers()) {
                    try {
                        // If player is in range and not in viewers set, enqueue an UPSERT to (re)send hologram
                        if (!session.viewers().contains(player.getUniqueId())
                                && player.getLocation().distanceSquared(session.baseLocation()) <= PACKET_SYNC_RADIUS_SQUARED) {
                            enqueueSync(player.getUniqueId(), hologramId, SyncAction.UPSERT);
                        }
                    } catch (final Throwable ignored) {
                    }
                }
            }
        }

        private List<NmsAdapter.HologramLine> toPacketLines(final List<RenderedLine> lines, final long animationStep) {
            final List<NmsAdapter.HologramLine> packetLines = new ArrayList<>(lines.size());
            for (final RenderedLine line : lines) {
                switch (line.type()) {
                    case TEXT -> {
                        final String resolved = HologramAnimationUtil.resolveAnimations(line.text(), animationStep);
                        packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(resolved), line.offsetY()));
                    }
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
        private final boolean animated;
        private final boolean dynamicPlaceholders;
        private final PlaceholderValues placeholderValues;
        private final BlockDefinition definition;
        private volatile long packetLinesStep;
        private volatile List<NmsAdapter.HologramLine> packetLines;
        // Prevent duplicate background resolution tasks for packetLines
        private final AtomicBoolean resolving = new AtomicBoolean(false);
        // Cache last sent state to avoid redundant updates when values don't change
        private final Map<UUID, CachedViewerState> lastSentState = new ConcurrentHashMap<>();

        private PacketSession(
                final String worldName,
                final Location baseLocation,
                final List<RenderedLine> lines,
                final Set<UUID> viewers,
                final List<NmsAdapter.HologramLine> packetLines,
                final long revision,
                final boolean animated,
                final boolean dynamicPlaceholders,
                final PlaceholderValues placeholderValues,
                final BlockDefinition definition
        ) {
            this.worldName = worldName;
            this.baseLocation = baseLocation;
            this.lines = lines;
            this.viewers = viewers;
            this.packetLines = packetLines;
            this.revision = revision;
            this.animated = animated;
            this.dynamicPlaceholders = dynamicPlaceholders;
            this.placeholderValues = placeholderValues;
            this.definition = definition;
            this.packetLinesStep = animated ? Long.MIN_VALUE : Long.MAX_VALUE;
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

        /**
         * Attempt to mark this session as resolving. Returns true if this call set the flag
         * (i.e. no other resolver was already active).
         */
        private boolean startResolving() {
            return this.resolving.compareAndSet(false, true);
        }

        /**
         * Mark resolving as finished.
         */
        private void finishResolving() {
            this.resolving.set(false);
        }

        private synchronized List<NmsAdapter.HologramLine> packetLinesForStep(
                final long step,
                final java.util.function.LongFunction<List<NmsAdapter.HologramLine>> compute
        ) {
            if (!this.animated) {
                return this.packetLines;
            }
            if (this.packetLinesStep == step && this.packetLines != null && !this.packetLines.isEmpty()) {
                return this.packetLines;
            }
            final List<NmsAdapter.HologramLine> computed = compute.apply(step);
            this.packetLines = computed;
            this.packetLinesStep = step;
            return computed;
        }

        private long revision() {
            return this.revision;
        }

        private boolean animated() {
            return this.animated;
        }

        private boolean dynamicPlaceholders() {
            return this.dynamicPlaceholders;
        }

        private PlaceholderValues placeholderValues() {
            return this.placeholderValues;
        }

        private BlockDefinition definition() {
            return this.definition;
        }

        private Map<UUID, CachedViewerState> lastSentState() {
            return this.lastSentState;
        }

        private final Map<UUID, List<NmsAdapter.HologramLine>> lastSentResolvedLines = new ConcurrentHashMap<>();

        private Map<UUID, List<NmsAdapter.HologramLine>> lastSentResolvedLines() {
            return this.lastSentResolvedLines;
        }

        private void cacheResolvedLines(final UUID viewerId, final List<NmsAdapter.HologramLine> lines) {
            this.lastSentResolvedLines.put(viewerId, lines);
        }
    }

    private record SyncKey(UUID playerUniqueId, UUID hologramUniqueId) {
    }

    private enum SyncAction {
        UPSERT,
        REMOVE
    }

    private record PlaceholderValues(int progress, int maxProgress, long respawnTimeSeconds, String stateName) {
    }

    private record CachedViewerState(long animationStep, PlaceholderValues placeholderValues, List<NmsAdapter.HologramLine> baseLines) {
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

    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\{condition_(\\d+)}");
}
