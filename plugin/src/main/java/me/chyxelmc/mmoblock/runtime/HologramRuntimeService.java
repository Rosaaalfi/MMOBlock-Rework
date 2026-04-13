package me.chyxelmc.mmoblock.runtime;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.utils.TextColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
        this.backend.syncForPlayer(player, blocks);
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

        final Location location = new Location(world, block.x() + 0.5D, block.y() + definition.displayHeight(), block.z() + 0.5D);
        this.backend.upsert(block, location, renderedLines);
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
        if (this.nmsAdapter.supportsPacketHolograms()) {
            return new PacketNmsBackend(this.plugin, this.nmsAdapter);
        }

        final String configured = this.plugin.getConfig().getString("hologramLib", "DecentHolograms");
        if (configured.equalsIgnoreCase("decentholograms")) {
            final Plugin decent = this.plugin.getServer().getPluginManager().getPlugin("DecentHolograms");
            if (decent != null && decent.isEnabled()) {
                final DecentHologramBackend decentBackend = DecentHologramBackend.tryCreate(this.plugin);
                if (decentBackend != null) {
                    return decentBackend;
                }
            }
        }

        return new DisplayEntityBackend(this.plugin);
    }

    private interface HologramBackend {

        String name();

        void upsert(PlacedBlock block, Location baseLocation, List<RenderedLine> lines);

        void remove(PlacedBlock block);

        void clearAll();

        default void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            // Optional hook for backends that need packet-driven per-player synchronization.
        }
    }

    private static final class DisplayEntityBackend implements HologramBackend {

        private final MMOBlock plugin;
        private final NamespacedKey uniqueIdKey;
        private final Map<UUID, List<UUID>> sessions = new ConcurrentHashMap<>();

        private DisplayEntityBackend(final MMOBlock plugin) {
            this.plugin = plugin;
            this.uniqueIdKey = new NamespacedKey(plugin, "hologram_unique_id");
        }

        @Override
        public String name() {
            return "display-entity-vanilla";
        }

        @Override
        public void upsert(final PlacedBlock block, final Location baseLocation, final List<RenderedLine> lines) {
            if (baseLocation.getWorld() == null) {
                return;
            }
            final World world = baseLocation.getWorld();
            final List<UUID> entityIds = this.sessions.computeIfAbsent(block.uniqueId(), ignored -> new ArrayList<>());

            if (entityIds.isEmpty()) {
                removeTaggedArmorStands(world, baseLocation, block.uniqueId());
            }

            // Remove extra entities when line count shrinks.
            while (entityIds.size() > lines.size()) {
                final UUID removed = entityIds.removeLast();
                final Entity entity = world.getEntity(removed);
                if (entity != null) {
                    entity.remove();
                }
            }

            // Create missing entities.
            while (entityIds.size() < lines.size()) {
                final Entity entity = spawnEntityForLine(world, baseLocation, lines.get(entityIds.size()), block.uniqueId());
                entityIds.add(entity.getUniqueId());
            }

            for (int i = 0; i < lines.size(); i++) {
                final UUID entityId = entityIds.get(i);
                Entity entity = world.getEntity(entityId);
                final RenderedLine line = lines.get(i);
                if (entity == null || !matchesType(entity, line.type())) {
                    if (entity != null) {
                        entity.remove();
                    }
                    entity = spawnEntityForLine(world, baseLocation, line, block.uniqueId());
                    entityIds.set(i, entity.getUniqueId());
                }
                final double y = baseLocation.getY() - (i * 0.25D);
                entity.teleport(new Location(world, baseLocation.getX(), y, baseLocation.getZ()));
                applyLine(entity, line);
            }

            // Push visibility immediately so nearby players do not need a move event first.
            syncWorldPlayers(world, block, entityIds);
        }

        private void removeTaggedArmorStands(final World world, final Location baseLocation, final UUID blockUniqueId) {
            for (final Entity entity : world.getNearbyEntities(baseLocation, 1.5D, 2.5D, 1.5D, candidate -> candidate instanceof Display)) {
                final String raw = entity.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
                if (raw == null || !raw.equals(blockUniqueId.toString())) {
                    continue;
                }
                entity.remove();
            }
        }

        private Entity spawnEntityForLine(final World world, final Location location, final RenderedLine line, final UUID blockUniqueId) {
            final EntityType type = switch (line.type()) {
                case ITEM -> EntityType.ITEM_DISPLAY;
                case BLOCK -> EntityType.BLOCK_DISPLAY;
                default -> EntityType.TEXT_DISPLAY;
            };
            final Entity entity = world.spawnEntity(location, type);
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(this.uniqueIdKey, PersistentDataType.STRING, blockUniqueId.toString());
            applyDisplayDefaults(entity);
            applyLine(entity, line);
            return entity;
        }

        private boolean matchesType(final Entity entity, final RenderedLine.Type type) {
            return switch (type) {
                case ITEM -> entity instanceof ItemDisplay;
                case BLOCK -> entity instanceof BlockDisplay;
                default -> entity instanceof TextDisplay;
            };
        }

        private void applyDisplayDefaults(final Entity entity) {
            if (!(entity instanceof Display display)) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(1);
        }

        private void applyLine(final Entity entity, final RenderedLine line) {
            switch (line.type()) {
                case TEXT -> {
                    if (entity instanceof TextDisplay textDisplay) {
                        textDisplay.setBillboard(Display.Billboard.HORIZONTAL);
                        textDisplay.setSeeThrough(true);
                        textDisplay.setDefaultBackground(false);
                        textDisplay.text(TextColorUtil.toComponent(line.text()));
                    }
                }
                case ITEM -> {
                    if (entity instanceof ItemDisplay itemDisplay) {
                        itemDisplay.setBillboard(Display.Billboard.CENTER);
                        itemDisplay.setItemStack(new ItemStack(line.material()));
                    }
                }
                case BLOCK -> {
                    if (entity instanceof BlockDisplay blockDisplay) {
                        blockDisplay.setBlock(line.material().createBlockData());
                    }
                }
            }
        }

        @Override
        public void remove(final PlacedBlock block) {
            final List<UUID> entities = this.sessions.remove(block.uniqueId());
            if (entities == null) {
                return;
            }
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world == null) {
                return;
            }
            for (final UUID uuid : entities) {
                final Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        }

        @Override
        public void clearAll() {
            final List<List<UUID>> entries = new ArrayList<>(this.sessions.values());
            this.sessions.clear();
            for (final List<UUID> entityIds : entries) {
                for (final World world : this.plugin.getServer().getWorlds()) {
                    for (final UUID entityId : entityIds) {
                        final Entity entity = world.getEntity(entityId);
                        if (entity != null) {
                            entity.remove();
                        }
                    }
                }
            }
        }

        @Override
        public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            final World playerWorld = player.getWorld();
            for (final PlacedBlock block : blocks) {
                final List<UUID> entityIds = this.sessions.get(block.uniqueId());
                if (entityIds == null || entityIds.isEmpty()) {
                    continue;
                }

                final World blockWorld = this.plugin.getServer().getWorld(block.world());
                if (blockWorld == null) {
                    continue;
                }

                final Location anchor = new Location(blockWorld, block.x() + 0.5D, block.y(), block.z() + 0.5D);
                final boolean shouldShow = blockWorld.equals(playerWorld)
                    && player.getLocation().distanceSquared(anchor) <= PACKET_SYNC_RADIUS_SQUARED;

                for (final UUID entityId : entityIds) {
                    final Entity entity = blockWorld.getEntity(entityId);
                    if (entity == null) {
                        continue;
                    }
                    if (shouldShow) {
                        player.showEntity(this.plugin, entity);
                    } else {
                        player.hideEntity(this.plugin, entity);
                    }
                }
            }
        }

        private void syncWorldPlayers(final World world, final PlacedBlock block, final List<UUID> entityIds) {
            final Location anchor = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
            for (final Player viewer : world.getPlayers()) {
                final boolean shouldShow = viewer.getLocation().distanceSquared(anchor) <= PACKET_SYNC_RADIUS_SQUARED;
                for (final UUID entityId : entityIds) {
                    final Entity entity = world.getEntity(entityId);
                    if (entity == null) {
                        continue;
                    }
                    if (shouldShow) {
                        viewer.showEntity(this.plugin, entity);
                    } else {
                        viewer.hideEntity(this.plugin, entity);
                    }
                }
            }
        }
    }

    private static final class DecentHologramBackend implements HologramBackend {

        private final MMOBlock plugin;
        private final Map<UUID, String> sessions = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> lineCounts = new ConcurrentHashMap<>();

        private DecentHologramBackend(final MMOBlock plugin) {
            this.plugin = plugin;
        }

        public static DecentHologramBackend tryCreate(final MMOBlock plugin) {
            try {
                // Verify classes are linked and available before backend activation.
                DHAPI.getHologram("mmoblock_bootstrap_check");
                return new DecentHologramBackend(plugin);
            } catch (final NoClassDefFoundError exception) {
                plugin.getLogger().warning("DecentHolograms found but API classes unavailable, using fallback backend.");
                return null;
            }
        }

        @Override
        public String name() {
            return "decentholograms";
        }

        @Override
        public void upsert(final PlacedBlock block, final Location baseLocation, final List<RenderedLine> lines) {
            try {
                final String hologramName = this.sessions.computeIfAbsent(block.uniqueId(), id -> "mmoblock_" + id.toString().replace('-', '_'));
                Hologram hologram = DHAPI.getHologram(hologramName);
                final int currentSize = this.lineCounts.getOrDefault(block.uniqueId(), 0);
                if (hologram != null && currentSize == 0) {
                    DHAPI.removeHologram(hologramName);
                    hologram = null;
                }
                if (hologram == null) {
                    hologram = DHAPI.createHologram(hologramName, baseLocation);
                }

                for (int i = currentSize; i < lines.size(); i++) {
                    DHAPI.addHologramLine(hologram, "");
                }
                for (int i = 0; i < lines.size(); i++) {
                    final RenderedLine line = lines.get(i);
                    switch (line.type()) {
                        case TEXT -> DHAPI.setHologramLine(hologram, i, TextColorUtil.toDecentHologramsText(line.text()));
                        case ITEM, BLOCK -> DHAPI.setHologramLine(hologram, i, new ItemStack(line.material()));
                    }
                }
                for (int i = currentSize - 1; i >= lines.size(); i--) {
                    DHAPI.removeHologramLine(hologram, i);
                }
                hologram.setLocation(baseLocation);
                this.lineCounts.put(block.uniqueId(), lines.size());
            } catch (final RuntimeException exception) {
                this.plugin.getLogger().warning("Failed to update DecentHolograms lines: " + exception.getMessage());
            }
        }

        @Override
        public void remove(final PlacedBlock block) {
            final String name = this.sessions.remove(block.uniqueId());
            this.lineCounts.remove(block.uniqueId());
            if (name == null) {
                return;
            }
            try {
                DHAPI.removeHologram(name);
            } catch (final RuntimeException exception) {
                this.plugin.getLogger().warning("Failed to remove DecentHolograms hologram: " + exception.getMessage());
            }
        }

        @Override
        public void clearAll() {
            final List<UUID> ids = new ArrayList<>(this.sessions.keySet());
            for (final UUID id : ids) {
                final String name = this.sessions.remove(id);
                this.lineCounts.remove(id);
                if (name == null) {
                    continue;
                }
                try {
                    DHAPI.removeHologram(name);
                } catch (final RuntimeException ignored) {
                }
            }
        }
    }

    private static final class PacketNmsBackend implements HologramBackend {

        private final MMOBlock plugin;
        private final NmsAdapter nmsAdapter;
        private final Map<UUID, PacketSession> sessions = new ConcurrentHashMap<>();

        private PacketNmsBackend(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
            this.plugin = plugin;
            this.nmsAdapter = nmsAdapter;
        }

        @Override
        public String name() {
            return "packet-nms";
        }

        @Override
        public void upsert(final PlacedBlock block, final Location baseLocation, final List<RenderedLine> lines) {
            this.sessions.put(block.uniqueId(), new PacketSession(block.world(), baseLocation.clone(), new ArrayList<>(lines), new HashSet<>()));
            final World world = baseLocation.getWorld();
            if (world == null) {
                return;
            }
            for (final Player viewer : world.getPlayers()) {
                syncViewer(block.uniqueId(), viewer);
            }
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
                this.nmsAdapter.removePacketHologram(viewer, block.uniqueId());
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
                    this.nmsAdapter.removePacketHologram(viewer, id);
                }
            }
        }

        @Override
        public void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            for (final PlacedBlock block : blocks) {
                syncViewer(block.uniqueId(), player);
            }
        }

        private void syncViewer(final UUID hologramUniqueId, final Player viewer) {
            final PacketSession session = this.sessions.get(hologramUniqueId);
            if (session == null) {
                this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
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

            this.nmsAdapter.upsertPacketHologram(viewer, hologramUniqueId, session.baseLocation(), toPacketLines(session.lines()));
            session.viewers().add(viewer.getUniqueId());
        }

        private List<NmsAdapter.HologramLine> toPacketLines(final List<RenderedLine> lines) {
            final List<NmsAdapter.HologramLine> packetLines = new ArrayList<>(lines.size());
            double cumulativeOffset = 0.0D;
            for (final RenderedLine line : lines) {
                final double offsetY = cumulativeOffset + offsetFor(line.type());
                switch (line.type()) {
                    case TEXT -> packetLines.add(NmsAdapter.HologramLine.text(TextColorUtil.toLegacySection(line.text()), offsetY));
                    case ITEM -> packetLines.add(NmsAdapter.HologramLine.item(line.material(), offsetY));
                    case BLOCK -> packetLines.add(NmsAdapter.HologramLine.block(line.material(), offsetY));
                }
                cumulativeOffset += spacingFor(line.type());
            }
            return packetLines;
        }

        private double spacingFor(final RenderedLine.Type type) {
            return this.plugin.getConfig().getDouble("hologram.packet.spacing." + type.name().toLowerCase(), 0.25D);
        }

        private double offsetFor(final RenderedLine.Type type) {
            return this.plugin.getConfig().getDouble("hologram.packet.offset." + type.name().toLowerCase(), 0.0D);
        }
    }

    private record PacketSession(String worldName, Location baseLocation, List<RenderedLine> lines, Set<UUID> viewers) {
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

