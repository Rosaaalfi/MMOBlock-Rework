package me.chyxelmc.mmoblock.nms.mojang.v1_19_4;

import java.util.List;

import me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftContainer;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class GuiInventoryAccessImpl implements GuiInventoryAccess {
    @Override
    public boolean openCustomInventory(final Player player, final Inventory inventory, final String title) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        if (handle.connection == null) return false;
        final MenuType<?> type = CraftContainer.getNotchInventoryType(inventory);
        AbstractContainerMenu menu = new CraftContainer(inventory, handle, handle.nextContainerCounter());
        menu = CraftEventFactory.callInventoryOpenEvent(handle, menu);
        if (menu == null) return false;
        menu.checkReachable = false;
        handle.connection.send(new ClientboundOpenScreenPacket(menu.containerId, type, Component.literal(title)));
        handle.containerMenu = menu;
        handle.initMenu(menu);
        return true;
    }

    @Override
    public boolean updateTitle(final Player player, final String title) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        final AbstractContainerMenu menu = handle.containerMenu;
        if (handle.connection == null || menu == null) return false;
        final var open = new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), Component.literal(title));
        final var content = new ClientboundContainerSetContentPacket(menu.containerId, menu.incrementStateId(), menu.getItems(), menu.getCarried());
        handle.connection.send(new ClientboundBundlePacket(List.of(open, content)));
        return true;
    }

    @Override public void sendContent(final Player player) { final ServerPlayer handle = ((CraftPlayer) player).getHandle(); final AbstractContainerMenu menu = handle.containerMenu; if (handle.connection != null && menu != null) handle.connection.send(new ClientboundContainerSetContentPacket(menu.containerId, menu.incrementStateId(), menu.getItems(), menu.getCarried())); }
    @Override public void sendSlot(final Player player, final int rawSlot, final ItemStack item) { final ServerPlayer handle = ((CraftPlayer) player).getHandle(); final AbstractContainerMenu menu = handle.containerMenu; if (handle.connection != null && menu != null) handle.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), rawSlot, CraftItemStack.asNMSCopy(item))); }
    @Override public int activeContainerId(final Player player) { final AbstractContainerMenu menu = ((CraftPlayer) player).getHandle().containerMenu; return menu == null ? -1 : menu.containerId; }
    @Override public boolean isViewing(final Player player, final Inventory inventory) { return player.getOpenInventory().getTopInventory() == inventory; }
    @Override public Inventory topInventory(final InventoryView view) { return view.getTopInventory(); }
    @Override public ItemStack itemFromView(final InventoryView view, final int rawSlot) { return view.getItem(rawSlot); }
}
