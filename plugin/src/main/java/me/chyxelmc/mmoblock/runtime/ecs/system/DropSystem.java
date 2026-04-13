package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.model.DropEntry;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles drop resolution and dispatch for mined blocks.
 */
public final class DropSystem {

    private final MMOBlock plugin;
    private final BlockConfigService blockConfigService;

    public DropSystem(final MMOBlock plugin, final BlockConfigService blockConfigService) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
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
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), entry.command().replace("%player%", player.getName()));
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
        if ("front_ground".equals(dropType)) {
            location = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.25D));
        } else {
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world == null) {
                return;
            }
            location = new Location(world, block.x(), block.y(), block.z());
        }
        if (location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location, stack);
        }
    }

    private int randomRange(final int min, final int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}

