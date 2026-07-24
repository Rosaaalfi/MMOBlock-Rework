package me.chyxelmc.mmoblock.ecs.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.DropEntry;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.ToolAction;
import me.chyxelmc.mmoblock.utils.TextColor;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

/**
 * Handles drop resolution and dispatch for mined blocks.
 *
 * <p>Renamed from {@code DropSystem} to better reflect that this system
 * spawns drops (as opposed to managing drop configuration).</p>
 */
public final class DropSpawnSystem implements Listener {

    private static final long RAINBOW_INTERVAL_TICKS = 8L;
    private static final long BEAM_INTERVAL_TICKS = 3L;
    private static final String REDSTONE_PARTICLE = "REDSTONE";

    private final MMOBlock plugin;
    private final BlockConfigLoader blockConfigService;
    private final Scheduler scheduler;
    private final NmsAdapter nmsAdapter;
    private final NamespacedKey noMergeKey;

    public DropSpawnSystem(final MMOBlock plugin, final BlockConfigLoader blockConfigService, final Scheduler scheduler, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
        this.scheduler = scheduler;
        this.nmsAdapter = nmsAdapter;
        this.noMergeKey = new NamespacedKey(plugin, "drop_no_merge");
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void executeDrops(final PlacedBlockModel block, final ToolAction action, final Player player) {
        for (final String dropId : action.allowedDrops()) {
            for (final DropEntry entry : this.blockConfigService.findDrops(dropId)) {
                if (ThreadLocalRandom.current().nextDouble() > entry.chance()) {
                    continue;
                }
                final int amount = randomRange(entry.min(), entry.max());
                final int finalAmount = amount;
                final String expSource = entry.experienceSource();
                switch (entry.type()) {
                    case MATERIAL -> dropMaterial(block, player, entry, finalAmount);
                    case EXPERIENCE -> {
                        if ("mmocore".equalsIgnoreCase(expSource)) {
                            final String profession = entry.mmocoreProfession();
                            if (profession != null && !"main".equalsIgnoreCase(profession)) {
                                me.chyxelmc.mmoblock.api.integration.MMOCoreIntegration.giveProfessionExperience(player, profession, finalAmount);
                            } else {
                                me.chyxelmc.mmoblock.api.integration.MMOCoreIntegration.giveExperience(player, finalAmount);
                            }
                        } else {
                            player.giveExp(finalAmount);
                        }
                        spawnDropPopup(block, player, entry, finalAmount);
                    }
                    case COMMAND -> {
                        if (entry.command() != null && !entry.command().isBlank()) {
                            final String resolved = entry.command().replace("%player%", player.getName());
                            this.scheduler.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
                        }
                        spawnDropPopup(block, player, entry, 1);
                    }
                }
            }
        }
    }

    private void dropMaterial(final PlacedBlockModel block, final Player player, final DropEntry entry, final int amount) {
        if (amount <= 0) {
            return;
        }
        final ItemStack stack;
        if (entry.mmoItemsId() != null) {
            stack = me.chyxelmc.mmoblock.api.integration.MMOItemsIntegration.getItemStack(entry.mmoItemsId(), amount);
            if (stack == null) {
                return;
            }
        } else if (entry.craftEngineId() != null) {
            stack = me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.getItemStack(entry.craftEngineId(), amount);
            if (stack == null) {
                return;
            }
        } else if (entry.itemsAdderId() != null) {
            stack = me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.getItemStack(entry.itemsAdderId(), amount);
            if (stack == null) {
                return;
            }
        } else if (entry.material() != null) {
            stack = new ItemStack(entry.material(), amount);
        } else {
            return;
        }
        spawnDropPopup(block, player, entry, amount, stack);
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

        for (int i = 0; i < amount; i++) {
            final ItemStack unitStack = stack.clone();
            unitStack.setAmount(1);
            final Item item = world.dropItem(location, unitStack);
            if (item == null) {
                continue;
            }
            item.getPersistentDataContainer().set(this.noMergeKey, PersistentDataType.BYTE, (byte) 1);

            if (entry.perPlayer()) {
                item.setOwner(player.getUniqueId());
                try {
                    item.setVisibleByDefault(false);
                    player.showEntity(this.plugin, item);
                } catch (final NoSuchMethodError | Exception ignored) {
                }
            }

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

    private Particle resolveBeamParticle(final String particleName) {
        final String requested = particleName == null || particleName.isBlank()
                ? "DUST"
                : particleName.toUpperCase(Locale.ROOT);

        final List<String> candidates = new ArrayList<>();
        candidates.add(requested);
        if (REDSTONE_PARTICLE.equals(requested)) {
            candidates.add("DUST");
        } else if ("DUST".equals(requested)) {
            candidates.add(REDSTONE_PARTICLE);
        }

        for (final String candidate : candidates) {
            try {
                return Particle.valueOf(candidate);
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.plugin.getLogger().warning("Unknown beam particle '" + particleName + "', skipping beam effect.");
        return null;
    }

    private boolean isDustParticle(final Particle particle) {
        final String name = particle.name();
        return "DUST".equals(name) || REDSTONE_PARTICLE.equals(name);
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
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
                "dark_purple", "gold", "gray", "dark_gray", "blue",
                "green", "aqua", "red", "light_purple", "yellow", "white"
        ));
        Collections.shuffle(colors, ThreadLocalRandom.current());
        return colors;
    }

    private void spawnDropPopup(final PlacedBlockModel block, final Player player, final DropEntry entry, final int amount) {
        spawnDropPopup(block, player, entry, amount, null);
    }

    private void spawnDropPopup(final PlacedBlockModel block, final Player player, final DropEntry entry, final int amount, final ItemStack itemStack) {
        if (entry.dropPopup() == null || !entry.dropPopup().enabled()) {
            return;
        }

        String rawText = entry.dropPopup().text();

        // Resolve placeholders based on drop type
        switch (entry.type()) {
            case MATERIAL -> {
                rawText = rawText.replace("{item_amount}", String.valueOf(amount));
                if (itemStack != null && itemStack.hasItemMeta()) {
                    final var meta = itemStack.getItemMeta();
                    if (meta.hasDisplayName()) {
                        rawText = rawText.replace("{item_name}", meta.getDisplayName());
                    } else {
                        rawText = rawText.replace("{item_name}", formatMaterialName(itemStack.getType()));
                    }
                } else if (itemStack != null) {
                    rawText = rawText.replace("{item_name}", formatMaterialName(itemStack.getType()));
                }
            }
            case EXPERIENCE -> {
                rawText = rawText.replace("{exp_amount}", String.valueOf(amount));
                rawText = rawText.replace("{item_amount}", String.valueOf(amount));
                final String expSource = entry.experienceSource();
                if ("mmocore".equalsIgnoreCase(expSource)) {
                    rawText = rawText.replace("{mmocore_exp_amount}", String.valueOf(amount));
                    rawText = rawText.replace("{vanilla_exp_amount}", "0");
                } else {
                    rawText = rawText.replace("{mmocore_exp_amount}", "0");
                    rawText = rawText.replace("{vanilla_exp_amount}", String.valueOf(amount));
                }
            }
            case COMMAND -> {
                rawText = rawText.replace("{item_amount}", String.valueOf(amount));
            }
        }

        // Resolve PlaceholderAPI placeholders
        final String resolvedText = this.plugin.applyHologramPlaceholderApi(player, rawText, 0, 0, 0L);
        final String legacyText = TextColor.toLegacySection(resolvedText);

        // Position hologram in front of player's camera (relative to head/eye position)
        // with a small random offset so it doesn't appear at the same spot every time
        final org.bukkit.Location eyeLoc = player.getEyeLocation();
        final org.bukkit.util.Vector direction = eyeLoc.getDirection().normalize();
        final org.bukkit.Location loc = eyeLoc.clone().add(direction.multiply(1.8D));
        loc.add(
                ThreadLocalRandom.current().nextDouble() * 1.0D - 0.5D,
                ThreadLocalRandom.current().nextDouble() * 0.8D - 0.1D,
                ThreadLocalRandom.current().nextDouble() * 1.0D - 0.5D
        );

        // Use a marker entity with Paper per-player visibility API so the popup
        // is ONLY visible to the player who received the drop.
        // The entity is invisible by default, then explicitly shown to the target player.
        final World world = loc.getWorld();
        if (world == null) return;

        // Spawn first, then configure — avoids NoSuchMethodError on Paper 1.21.11+
        // where World.spawn(Location, Class, Consumer) with org.bukkit.util.Consumer was removed.
        final org.bukkit.entity.ArmorStand stand = world.spawn(loc, org.bukkit.entity.ArmorStand.class);
        if (stand == null) return;
        stand.setMarker(true);
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setGravity(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(legacyText);
        // Paper API: invisible to all players by default
        try {
            stand.setVisibleByDefault(false);
        } catch (final NoSuchMethodError ignored) {
            // Spigot fallback — entity is visible but positioned at player's face
        }

        // Only show this entity to the player who received the drop
        try {
            player.showEntity(this.plugin, stand);
        } catch (final NoSuchMethodError ignored) {
            // Spigot fallback — entity is visible but positioned at player's face
        }

        // Remove the popup after 2 seconds (40 ticks)
        this.scheduler.runForEntityLater(stand, stand::remove, null, 40L);
    }

    /**
     * Formats a Bukkit Material enum name into a human-readable name.
     * e.g. {@code IRON_INGOT} becomes {@code Iron Ingot}.
     */
    private static String formatMaterialName(final Material material) {
        if (material == null) return "Item";
        final String[] words = material.name().toLowerCase().split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String word : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private int randomRange(final int min, final int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
