package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.config.NodeConfigService;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InteractionListener implements Listener {

    private static final String CLICK_LEFT = "left_click";
    private static final String CLICK_RIGHT = "right_click";
    private static final long LEGACY_CLICK_DEBOUNCE_MS = 350L;

    private final BlockRuntimeService runtimeService;
    private final NodeRuntimeService nodeRuntimeService;
    private final BlockConfigService blockConfigService;
    private final NodeConfigService nodeConfigService;
    private final CustomItemUtil customItemUtil;
    private final java.util.Map<java.util.UUID, Long> legacyRightClickDebounce = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> legacyLeftClickDebounce = new java.util.concurrent.ConcurrentHashMap<>();

    public InteractionListener(
            final MMOBlock plugin,
            final BlockRuntimeService runtimeService,
            final NodeRuntimeService nodeRuntimeService,
            final BlockConfigService blockConfigService,
            final NodeConfigService nodeConfigService
    ) {
        this.runtimeService = runtimeService;
        this.nodeRuntimeService = nodeRuntimeService;
        this.blockConfigService = blockConfigService;
        this.nodeConfigService = nodeConfigService;
        this.customItemUtil = new CustomItemUtil(plugin);
    }

    private boolean isLegacyClickDebounced(final org.bukkit.entity.Player player,
                                           final java.util.Map<java.util.UUID, Long> debounceMap) {
        final long now = System.currentTimeMillis();
        final Long prev = debounceMap.get(player.getUniqueId());
        if (prev != null && (now - prev) < LEGACY_CLICK_DEBOUNCE_MS) return true;
        debounceMap.put(player.getUniqueId(), now);
        return false;
    }


    private boolean isEmptyMessage(final Component message) {
        return Component.empty().equals(message);
    }

    @EventHandler
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        final CustomItemUtil.CustomItemData itemData = readCustomItem(event.getPlayer());
        if (itemData != null) {
            if (CustomItemUtil.TYPE_BLOCK_REMOVER.equals(itemData.type())) {
                if (this.runtimeService.removeByInteractionEntity(event.getRightClicked())) {
                    event.setCancelled(true);
                    this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
                }
                return;
            }
            if (CustomItemUtil.TYPE_NODE_REMOVER.equals(itemData.type()) && this.nodeRuntimeService != null) {
                final java.util.UUID blockId = this.runtimeService.resolveBlockUniqueId(event.getRightClicked());
                if (blockId != null && this.nodeRuntimeService.removeNodeByBlockUniqueId(blockId)) {
                    event.setCancelled(true);
                    this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
                }
                return;
            }
        }
        final Component message = this.runtimeService.handleInteraction(event.getRightClicked(), event.getPlayer(), CLICK_RIGHT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        if (!isEmptyMessage(message)) {
            event.getPlayer().sendMessage(message);
        }
        // Ensure fake-block visuals remain for the clicking player (do not vanish on interaction)
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        final CustomItemUtil.CustomItemData itemData = readCustomItem(player);
        if (itemData != null) {
            if (CustomItemUtil.TYPE_BLOCK_REMOVER.equals(itemData.type())) {
                if (this.runtimeService.removeByInteractionEntity(event.getEntity())) {
                    event.setCancelled(true);
                    this.runtimeService.syncFakeBlocksForPlayer(player);
                }
                return;
            }
            if (CustomItemUtil.TYPE_NODE_REMOVER.equals(itemData.type()) && this.nodeRuntimeService != null) {
                final java.util.UUID blockId = this.runtimeService.resolveBlockUniqueId(event.getEntity());
                if (blockId != null && this.nodeRuntimeService.removeNodeByBlockUniqueId(blockId)) {
                    event.setCancelled(true);
                    this.runtimeService.syncFakeBlocksForPlayer(player);
                }
                return;
            }
        }

        final Component message = this.runtimeService.handleInteraction(event.getEntity(), player, CLICK_LEFT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        if (!isEmptyMessage(message)) {
            player.sendMessage(message);
        }
        // Re-send fake-blocks to the player so the visual does not disappear on click
        this.runtimeService.syncFakeBlocksForPlayer(player);
    }

    @EventHandler
    public void onLegacyRightClick(final PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (isLegacyClickDebounced(event.getPlayer(), this.legacyRightClickDebounce)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isCustomItem(event.getPlayer())) {
            return;
        }
        final Component message = this.runtimeService.handleLegacyFallbackInteraction(event.getPlayer(), CLICK_RIGHT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        if (!isEmptyMessage(message)) {
            event.getPlayer().sendMessage(message);
        }
        // Legacy click also should keep fake blocks visible
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onLegacyLeftClick(final PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (isLegacyClickDebounced(event.getPlayer(), this.legacyLeftClickDebounce)) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (isCustomItem(event.getPlayer())) {
            return;
        }
        final Component message = this.runtimeService.handleLegacyFallbackInteraction(event.getPlayer(), CLICK_LEFT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        if (!isEmptyMessage(message)) {
            event.getPlayer().sendMessage(message);
        }
        // Legacy click also should keep fake blocks visible
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onCustomItemPlace(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final CustomItemUtil.CustomItemData itemData = readCustomItem(event.getPlayer());
        if (itemData == null) {
            return;
        }
        event.setCancelled(true);
        final var clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        final double x = clicked.getX();
        final double y = clicked.getY() + 1.0D;
        final double z = clicked.getZ();
        final var world = clicked.getWorld();

        if (CustomItemUtil.TYPE_BLOCK.equals(itemData.type())) {
            final var definition = this.blockConfigService.findBlock(itemData.id());
            if (definition == null) {
                return;
            }
            final String facing = yawToFacing(event.getPlayer().getLocation().getYaw());
            this.runtimeService.place(definition.id(), world, x, y, z, facing);
            return;
        }
        if (CustomItemUtil.TYPE_NODE.equals(itemData.type()) && this.nodeRuntimeService != null) {
            final var definition = this.nodeConfigService.findNode(itemData.id());
            if (definition == null) {
                return;
            }
            this.nodeRuntimeService.placeNode(definition.id(), world, x, y, z, true);
        }
    }

    private CustomItemUtil.CustomItemData readCustomItem(final Player player) {
        if (player == null) {
            return null;
        }
        final ItemStack item = player.getInventory().getItemInMainHand();
        return this.customItemUtil.read(item);
    }

    private boolean isCustomItem(final Player player) {
        return readCustomItem(player) != null;
    }

    private String yawToFacing(final float yaw) {
        final float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 45.0F && normalized < 135.0F) {
            return "west";
        }
        if (normalized >= 135.0F && normalized < 225.0F) {
            return "north";
        }
        if (normalized >= 225.0F && normalized < 315.0F) {
            return "east";
        }
        return "south";
    }
}
