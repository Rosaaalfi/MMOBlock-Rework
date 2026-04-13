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

        default void syncForPlayer(final Player player, final Collection<PlacedBlock> blocks) {
            // Optional hook for backends that need packet-driven per-player synchronization.
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

