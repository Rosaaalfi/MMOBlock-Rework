package me.chyxelmc.mmoblock.runtime.ecs.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.model.DropEntry;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

/**
 * Handles drop resolution and dispatch for mined blocks.
 */
public final class DropSystem implements Listener {

    private static final long RAINBOW_INTERVAL_TICKS = 8L;
    private static final long BEAM_INTERVAL_TICKS = 3L;

    private final MMOBlock plugin;
    private final BlockConfigService blockConfigService;
    private final Scheduler scheduler;
    private final NmsAdapter nmsAdapter;
    private final NamespacedKey noMergeKey;

    public DropSystem(final MMOBlock plugin, final BlockConfigService blockConfigService, final Scheduler scheduler, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
        this.scheduler = scheduler;
        this.nmsAdapter = nmsAdapter;
        this.noMergeKey = new NamespacedKey(plugin, "drop_no_merge");
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void executeDrops(final PlacedBlock block, final ToolAction action, final Player player) {
        for (final String dropId : action.allowedDrops()) {
            for (final DropEntry entry : this.blockConfigService.findDrops(dropId)) {
                if (ThreadLocalRandom.current().nextDouble() > entry.chance()) {
                    continue;
                }
                final int amount = randomRange(entry.min(), entry.max());
                switch (entry.type()) {
                    case MATERIAL -> dropMaterial(block, player, entry, amount);
                    case EXPERIENCE -> player.giveExp(amount);
                    case COMMAND -> {
                        if (entry.command() != null && !entry.command().isBlank()) {
                            final String resolved = entry.command().replace("%player%", player.getName());
                            // Bukkit.dispatchCommand must run on the global tick thread;
                            // in Folia the caller may be on a region thread so we
                            // schedule via the global-region-aware scheduler.
                            this.scheduler.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
                        }
                    }
                }
            }
        }
    }

    private void dropMaterial(final PlacedBlock block, final Player player, final DropEntry entry, final int amount) {
        if (entry.material() == null || amount <= 0) {
            return;
        }
        final ItemStack stack = new ItemStack(entry.material(), amount);
        final String dropType = entry.dropType().toLowerCase();
        if ("inventory".equals(dropType)) {
            final Map<Integer, ItemStack> remainder = player.getInventory().addItem(stack);
            remainder.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            return;
        }

        final Location location;
        if ("front_ground".equals(dropType) || "frontground".equals(dropType)) {
            location = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.25D));
        } else {
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world == null) {
                return;
            }
            location = new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D);
        }

        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Drop each unit as its own entity so every item flies individually
        for (int i = 0; i < amount; i++) {
            final Item item = world.dropItem(location, new ItemStack(entry.material(), 1));
            if (item == null) {
                continue;
            }
            item.getPersistentDataContainer().set(this.noMergeKey, PersistentDataType.BYTE, (byte) 1);

            // Per-player visibility via Paper API
            if (entry.perPlayer()) {
                item.setOwner(player.getUniqueId());
                try {
                    // Paper API: make entity invisible to all by default, then show to the breaker
                    item.setVisibleByDefault(false);
                    player.showEntity(this.plugin, item);
                } catch (final NoSuchMethodError | Exception ignored) {
                    // Paper API not available — item is visible to everyone, pickup is still owner-only
                }
            }

            // Explosion velocity effect via Bukkit API — random direction per item
            if (entry.effectExplosion()) {
                final boolean frontGround = "front_ground".equals(dropType) || "frontground".equals(dropType);
                final double strength;
                final double upward;
                if (frontGround) {
                    strength = ThreadLocalRandom.current().nextDouble() * 0.3D + 0.1D;
                    upward = ThreadLocalRandom.current().nextDouble() * 0.3D + 0.55D;
                } else {
                    strength = ThreadLocalRandom.current().nextDouble() * 0.4D + 0.15D;
                    upward = ThreadLocalRandom.current().nextDouble() * 0.35D + 0.60D;
                }
                final double angle = ThreadLocalRandom.current().nextDouble() * 2.0D * Math.PI;
                final Vector velocity = new Vector(
                        Math.cos(angle) * strength,
                        upward,
                        Math.sin(angle) * strength
                );
                item.setVelocity(velocity);
            }

            // Colored glow effect — per-player or broadcast
            if (entry.effectGlow() != null) {
                final String colorName = entry.effectGlow().color();
                final Player glowTarget = entry.perPlayer() ? player : null;
                if ("rainbow".equalsIgnoreCase(colorName)) {
                    final List<String> colors = randomizedRainbowColors();
                    applyEntityGlow(item, glowTarget, colors.get(0));
                    scheduleRainbowGlow(item, glowTarget, 1, colors);
                } else {
                    applyEntityGlow(item, glowTarget, colorName);
                }
            }

            // Colored particle beam effect — per-player or broadcast
            if (entry.effectBeam() != null) {
                final String colorName = entry.effectBeam().color();
                final String particleName = entry.effectBeam().particle();
                final Player beamTarget = entry.perPlayer() ? player : null;
                if ("rainbow".equalsIgnoreCase(colorName)) {
                    final List<String> colors = randomizedRainbowColors();
                    applyItemBeam(item, beamTarget, colors.get(0), particleName);
                    scheduleRainbowBeam(item, beamTarget, 1, colors, particleName);
                } else {
                    applyItemBeam(item, beamTarget, colorName, particleName);
                    scheduleContinuousBeam(item, beamTarget, colorName, particleName);
                }
            }
        }
    }

    private void applyEntityGlow(final Item item, final Player player, final String colorName) {
        this.scheduler.runForEntity(item, () -> {
            if (player != null) {
                this.nmsAdapter.applyEntityGlow(player, item, colorName);
            } else {
                for (final Player online : Bukkit.getOnlinePlayers()) {
                    this.nmsAdapter.applyEntityGlow(online, item, colorName);
                }
            }
        }, null);
    }

    /**
     * Schedules a rainbow color cycle for a glowing item entity.
     * Cycles through every glow color in a shuffled order until the item is removed.
     */
    private void scheduleRainbowGlow(final Item item, final Player player, final int index, final List<String> colors) {
        final List<String> cycle = colors == null || colors.isEmpty() || index >= colors.size()
                ? randomizedRainbowColors()
                : colors;
        final int colorIndex = index >= cycle.size() ? 0 : index;
        final String color = cycle.get(colorIndex);
        final int nextIndex = colorIndex + 1;
        this.scheduler.runForEntityLater(item, () -> {
            if (!item.isValid() || (player != null && !player.isOnline())) {
                return;
            }
            if (player != null) {
                this.nmsAdapter.applyEntityGlow(player, item, color);
            } else {
                for (final Player online : Bukkit.getOnlinePlayers()) {
                    this.nmsAdapter.applyEntityGlow(online, item, color);
                }
            }
            scheduleRainbowGlow(item, player, nextIndex, cycle);
        }, null, RAINBOW_INTERVAL_TICKS);
    }

    private void applyItemBeam(final Item item, final Player player, final String colorName, final String particleName) {
        this.scheduler.runForEntity(item, () -> {
            if (!item.isValid() || (player != null && !player.isOnline())) {
                return;
            }
            spawnItemBeam(player, item.getLocation(), colorName, particleName);
        }, null);
    }

    /**
     * Schedules a repeating rainbow particle beam that follows the item entity.
     * Cycles through shuffled glow colors every interval tick until the item is removed.
     */
    private void scheduleRainbowBeam(final Item item, final Player player, final int index, final List<String> colors, final String particleName) {
        final List<String> cycle = colors == null || colors.isEmpty() || index >= colors.size()
                ? randomizedRainbowColors()
                : colors;
        final int colorIndex = index >= cycle.size() ? 0 : index;
        final String color = cycle.get(colorIndex);
        final int nextIndex = colorIndex + 1;
        this.scheduler.runForEntityLater(item, () -> {
            if (!item.isValid() || (player != null && !player.isOnline())) {
                return;
            }
            spawnItemBeam(player, item.getLocation(), color, particleName);
            scheduleRainbowBeam(item, player, nextIndex, cycle, particleName);
        }, null, BEAM_INTERVAL_TICKS);
    }

    /**
     * Schedules a repeating particle beam that follows the item entity until
     * the item is picked up or removed.
     */
    private void scheduleContinuousBeam(final Item item, final Player player, final String colorName, final String particleName) {
        this.scheduler.runForEntityLater(item, () -> {
            if (!item.isValid() || (player != null && !player.isOnline())) {
                return;
            }
            spawnItemBeam(player, item.getLocation(), colorName, particleName);
            scheduleContinuousBeam(item, player, colorName, particleName);
        }, null, BEAM_INTERVAL_TICKS);
    }

    private void spawnItemBeam(final Player player, final Location location, final String colorName, final String particleName) {
        final Particle particle = resolveBeamParticle(particleName);
        if (particle == null) {
            return;
        }
        try {
            final Color color = resolveBeamColor(colorName);
            final Location loc = location.clone();
            final double startY = loc.getY() + 0.7D;
            final double endY = startY + 0.7D;
            final boolean broadcast = player == null;
            for (double y = startY; y <= endY; y += 0.25D) {
                loc.setY(y);
                if (isDustParticle(particle)) {
                    final Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
                    if (broadcast) {
                        location.getWorld().spawnParticle(particle, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D, dustOptions);
                    } else {
                        player.spawnParticle(particle, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D, dustOptions);
                    }
                } else {
                    if (broadcast) {
                        location.getWorld().spawnParticle(particle, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    } else {
                        player.spawnParticle(particle, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                }
            }
        } catch (final Exception e) {
            this.plugin.getLogger().warning("Failed to spawn drop beam particle '" + particle.name() + "': " + e.getMessage());
        }
    }

    /**
     * Resolves the particle to use for the beam effect, falling back between the
     * legacy "REDSTONE" and the current "DUST" enum name (renamed in newer
     * Paper/Spigot API versions) so a stale config value doesn't silently disable
     * the effect.
     */
    private Particle resolveBeamParticle(final String particleName) {
        final String requested = particleName == null || particleName.isBlank()
                ? "DUST"
                : particleName.toUpperCase(Locale.ROOT);

        final List<String> candidates = new ArrayList<>();
        candidates.add(requested);
        // "REDSTONE" and "DUST" refer to the same particle across API versions
        // (renamed in 1.20.5+). The config always supplies an explicit default
        // of "REDSTONE", so we must alias both directions, not just when the
        // configured value is blank.
        if ("REDSTONE".equals(requested)) {
            candidates.add("DUST");
        } else if ("DUST".equals(requested)) {
            candidates.add("REDSTONE");
        }

        for (final String candidate : candidates) {
            try {
                return Particle.valueOf(candidate);
            } catch (final IllegalArgumentException ignored) {
                // try next candidate
            }
        }
        this.plugin.getLogger().warning("Unknown beam particle '" + particleName + "', skipping beam effect.");
        return null;
    }

    private boolean isDustParticle(final Particle particle) {
        final String name = particle.name();
        return "DUST".equals(name) || "REDSTONE".equals(name);
    }

    private static Color resolveBeamColor(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Color.WHITE;
        }
        final String normalized = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "black" -> Color.BLACK;
            case "navy", "dark_blue" -> Color.fromRGB(0x0000AA);
            case "dark_green" -> Color.fromRGB(0x00AA00);
            case "teal", "dark_aqua", "dark_cyan" -> Color.fromRGB(0x00AAAA);
            case "maroon", "dark_red" -> Color.fromRGB(0xAA0000);
            case "purple", "dark_purple" -> Color.fromRGB(0xAA00AA);
            case "orange", "gold" -> Color.fromRGB(0xFFAA00);
            case "silver", "gray", "grey" -> Color.fromRGB(0xAAAAAA);
            case "dark_gray", "dark_grey" -> Color.fromRGB(0x555555);
            case "blue" -> Color.fromRGB(0x5555FF);
            case "lime", "green" -> Color.fromRGB(0x55FF55);
            case "cyan", "aqua" -> Color.fromRGB(0x55FFFF);
            case "red" -> Color.fromRGB(0xFF5555);
            case "pink", "fuchsia", "magenta", "light_purple" -> Color.fromRGB(0xFF55FF);
            case "yellow", "olive" -> Color.fromRGB(0xFFFF55);
            case "white" -> Color.WHITE;
            default -> Color.WHITE;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(final ItemMergeEvent event) {
        if (isNoMergeDrop(event.getEntity()) || isNoMergeDrop(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    private boolean isNoMergeDrop(final Item item) {
        return item != null && item.getPersistentDataContainer().has(this.noMergeKey, PersistentDataType.BYTE);
    }

    private List<String> randomizedRainbowColors() {
        final List<String> colors = new ArrayList<>(List.of(
                "black",
                "dark_blue",
                "dark_green",
                "dark_aqua",
                "dark_red",
                "dark_purple",
                "gold",
                "gray",
                "dark_gray",
                "blue",
                "green",
                "aqua",
                "red",
                "light_purple",
                "yellow",
                "white"
        ));
        Collections.shuffle(colors, ThreadLocalRandom.current());
        return colors;
    }

    private int randomRange(final int min, final int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}