package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.ecs.component.HologramDisplayComponent;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles hologram rendering for block entities.
 *
 * <p>This system replaces the hologram rendering responsibility previously
 * handled directly in {@link HologramRuntimeService}. It reads
 * {@link HologramDisplayComponent} data and uses the NMS adapter to
 * upsert/remove packet holograms for nearby players.</p>
 */
public final class HologramRenderSystem {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;

    public HologramRenderSystem(final MMOBlock plugin, final NmsAdapter nmsAdapter, final Scheduler scheduler) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
    }

    /**
     * Spawn or update a hologram at the given location for all nearby players.
     * The hologram is built from the provided {@link HologramDisplayComponent}.
     */
    public void spawnHologram(
            final UUID hologramId,
            final Location location,
            final HologramDisplayComponent displayComponent
    ) {
        final World world = location.getWorld();
        if (world == null) return;

        final var hologramLines = displayComponent.lines().stream()
                .map(line -> NmsAdapter.HologramLine.text(line.text(), 0.0D))
                .toList();

        for (final Player player : world.getPlayers()) {
            try {
                this.nmsAdapter.upsertPacketHologram(player, hologramId, location, hologramLines);
            } catch (final RuntimeException ignored) {
                // expected - player may have left
            }
        }
    }

    /**
     * Remove a hologram for all players.
     */
    public void removeHologram(final UUID hologramId, final World world) {
        if (world == null) return;

        for (final Player player : world.getPlayers()) {
            try {
                this.nmsAdapter.removePacketHologram(player, hologramId);
            } catch (final RuntimeException ignored) {
                // expected - player may have left
            }
        }
    }

    /**
     * Remove a hologram for a specific player.
     */
    public void removeHologramForPlayer(final Player player, final UUID hologramId) {
        if (player == null) return;
        try {
            this.nmsAdapter.removePacketHologram(player, hologramId);
        } catch (final RuntimeException ignored) {
            // expected
        }
    }
}
