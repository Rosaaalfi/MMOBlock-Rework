package me.chyxelmc.mmoblock.nms.v1_21_8;
import java.util.List;
import me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftContainer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
public final class GuiInventoryAccessImpl implements GuiInventoryAccess {
 @Override public boolean openCustomInventory(Player player,Inventory inventory,String title){ServerPlayer h=((CraftPlayer)player).getHandle();if(h.connection==null)return false;MenuType<?>t=CraftContainer.getNotchInventoryType(inventory);AbstractContainerMenu m=new CraftContainer(inventory,h,h.nextContainerCounter());m=CraftEventFactory.callInventoryOpenEvent(h,m);if(m==null)return false;m.checkReachable=false;h.connection.send(new ClientboundOpenScreenPacket(m.containerId,t,Component.literal(title)));h.containerMenu=m;h.initMenu(m);return true;}
 @Override public boolean updateTitle(Player player,String title){ServerPlayer h=((CraftPlayer)player).getHandle();AbstractContainerMenu m=h.containerMenu;if(h.connection==null||m==null)return false;h.connection.send(new ClientboundBundlePacket(List.of(new ClientboundOpenScreenPacket(m.containerId,m.getType(),Component.literal(title)),new ClientboundContainerSetContentPacket(m.containerId,m.incrementStateId(),m.getItems(),m.getCarried()))));return true;}
 @Override public void sendContent(Player player){ServerPlayer h=((CraftPlayer)player).getHandle();AbstractContainerMenu m=h.containerMenu;if(h.connection!=null&&m!=null)h.connection.send(new ClientboundContainerSetContentPacket(m.containerId,m.incrementStateId(),m.getItems(),m.getCarried()));}
 @Override public void sendSlot(Player player,int slot,ItemStack item){ServerPlayer h=((CraftPlayer)player).getHandle();AbstractContainerMenu m=h.containerMenu;if(h.connection!=null&&m!=null)h.connection.send(new ClientboundContainerSetSlotPacket(m.containerId,m.incrementStateId(),slot,CraftItemStack.asNMSCopy(item)));}
 @Override public int activeContainerId(Player player){AbstractContainerMenu m=((CraftPlayer)player).getHandle().containerMenu;return m==null?-1:m.containerId;}
 @Override public boolean isViewing(Player player,Inventory inventory){return player.getOpenInventory().getTopInventory()==inventory;}
 @Override public Inventory topInventory(InventoryView view){return view.getTopInventory();}
 @Override public ItemStack itemFromView(InventoryView view,int slot){return view.getItem(slot);}
}
