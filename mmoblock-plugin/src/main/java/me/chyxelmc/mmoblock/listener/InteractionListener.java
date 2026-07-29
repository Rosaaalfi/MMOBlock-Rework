package me.chyxelmc.mmoblock.listener;

import java.util.UUID;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import net.kyori.adventure.text.Component;

public final class InteractionListener implements Listener {

    private final BlockRuntimeService runtimeService;
    private final NodeRuntimeService nodeRuntimeService;
    private final BlockConfigLoader blockConfigService;
    private final NodeConfigLoader nodeConfigService;
    private final CustomItemUtil customItemUtil;

    public InteractionListener(
            final MMOBlock plugin,
            final BlockRuntimeService runtimeService,
            final NodeRuntimeService nodeRuntimeService,
            final BlockConfigLoader blockConfigService,
            final NodeConfigLoader nodeConfigService
    ) {
        this.runtimeService = runtimeService;
        this.nodeRuntimeService = nodeRuntimeService;
        this.blockConfigService = blockConfigService;
        this.nodeConfigService = nodeConfigService;
        this.customItemUtil = new CustomItemUtil(plugin);
    }

    private static final String CLICK_LEFT = "left_click";
    private static final String CLICK_RIGHT = "right_click";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractBlock(final PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Don't interfere with custom item placement
        if (readCustomItem(event.getPlayer()) != null) {
            return;
        }

        // Distance-tier check: only process clicks within real-block-radius
        final double distSq = event.getPlayer().getLocation().distanceSquared(
                clickedBlock.getLocation().add(0.5, 0.5, 0.5));
        if (distSq > this.blockConfigService.realBlockRadiusSquared()) {
            return;
        }

        final boolean pluginVisualBlock = isPluginVisualBlock(clickedBlock);

        if (!pluginVisualBlock) {
            return;
        }

        final String clickType = event.getAction() == Action.RIGHT_CLICK_BLOCK ? CLICK_RIGHT : CLICK_LEFT;

        // For left-clicks: if the player's tool has a block_break action configured but no
        // left_click/both_click action, do NOT cancel the event. This allows the vanilla
        // block break chain (BlockDamageEvent → BlockBreakEvent) to proceed normally,
        // giving the player the vanilla break animation. BlockLookProtection will handle
        // the break via processBlockBreak when BlockBreakEvent fires.
        if (CLICK_LEFT.equals(clickType) && hasBlockBreakOnly(clickedBlock, event.getPlayer())) {
            return;
        }

        final Component message = this.runtimeService.handleRealBlockClick(
                event.getPlayer(),
                clickType,
                clickedBlock.getWorld(),
                clickedBlock.getX(),
                clickedBlock.getY(),
                clickedBlock.getZ()
        );

        if (message == null) {
            return;
        }

        event.setCancelled(true);
        if (!Component.empty().equals(message)) {
            event.getPlayer().sendMessage(message);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomItemPlace(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
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
        final boolean isLeftClick = event.getAction() == Action.LEFT_CLICK_BLOCK;
        final double x = clicked.getX();
        final double y = clicked.getY() + (isLeftClick ? 0.0D : 1.0D);
        final double z = clicked.getZ();
        final var world = clicked.getWorld();

        if (CustomItemUtil.TYPE_BLOCK.equals(itemData.type())) {
            // Block placement only on right-click (left-click is for removal/break)
            if (isLeftClick) return;
            final var definition = this.blockConfigService.findBlock(itemData.id());
            if (definition == null) {
                return;
            }
            final String facing = yawToFacing(event.getPlayer().getLocation().getYaw());
            this.runtimeService.place(definition.id(), world, x, y, z, facing);
            return;
        }
        if (CustomItemUtil.TYPE_NODE.equals(itemData.type()) && this.nodeRuntimeService != null) {
            // Node placement only on right-click
            if (isLeftClick) return;
            final var definition = this.nodeConfigService.findNode(itemData.id());
            if (definition == null) {
                return;
            }
            this.nodeRuntimeService.placeNode(definition.id(), world, x, y, z, true);
            return;
        }
        if (CustomItemUtil.TYPE_BLOCK_REMOVER.equals(itemData.type())) {
            // Remove block at the clicked position (use clicked.getY() for both clicks)
            final var mmoBlock = this.runtimeService.stateRegistry().blockAt(
                    world.getName(), clicked.getX(), clicked.getY(), clicked.getZ());
            if (mmoBlock != null) {
                this.runtimeService.removeById(mmoBlock.uniqueId());
            }
            return;
        }
        if (CustomItemUtil.TYPE_NODE_REMOVER.equals(itemData.type()) && this.nodeRuntimeService != null) {
            // Remove entire node at the clicked position
            final var mmoBlock = this.runtimeService.stateRegistry().blockAt(
                    world.getName(), clicked.getX(), clicked.getY(), clicked.getZ());
            if (mmoBlock != null) {
                this.nodeRuntimeService.removeNodeByBlockUniqueId(mmoBlock.uniqueId());
            }
        }
    }

    /**
     * Checks whether the player's held item has a {@code block_break} action configured
     * for the clicked block but NO {@code left_click} or {@code both_click} action.
     * When this is the case, the interact event should NOT be cancelled so that the
     * vanilla block break chain (BlockDamageEvent → BlockBreakEvent) can proceed,
     * giving the player the vanilla break animation.
     */
    private boolean hasBlockBreakOnly(final Block clickedBlock, final Player player) {
        final var placed = this.runtimeService.stateRegistry().blockAt(
                clickedBlock.getWorld().getName(),
                clickedBlock.getX(),
                clickedBlock.getY(),
                clickedBlock.getZ()
        );
        if (placed == null) {
            return false;
        }
        final var definition = this.blockConfigService.findBlock(placed.type());
        if (definition == null) {
            return false;
        }
        final ItemStack item = player.getInventory().getItemInMainHand();
        final boolean hasClickAction = this.blockConfigService.resolveToolAction(definition, item, CLICK_LEFT) != null;
        final boolean hasBreakAction = this.blockConfigService.resolveToolAction(definition, item, "block_break") != null;
        return !hasClickAction && hasBreakAction;
    }

    private CustomItemUtil.CustomItemData readCustomItem(final Player player) {
        if (player == null) {
            return null;
        }
        final ItemStack item = player.getInventory().getItemInMainHand();
        return this.customItemUtil.read(item);
    }

    private boolean isPluginVisualBlock(final Block block) {
        final String worldName = block.getWorld().getName();
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        return FakeBlockRegistry.contains(worldName, x, y, z)
                || this.runtimeService.isServerSideInteractionBlock(worldName, x, y, z);
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
