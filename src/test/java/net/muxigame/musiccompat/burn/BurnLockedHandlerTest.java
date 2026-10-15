package net.muxigame.musiccompat.burn;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.concurrent.atomic.*;

/** Real vanilla ItemStacks and original handler; injected boolean lock, no server or Mixin claims. */
public final class BurnLockedHandlerTest {
 private static int checks;
 private static void check(boolean value){if(!value)throw new AssertionError("handler invariant "+checks);checks++;}
 public static void main(String[] args){
  net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  AtomicBoolean lock=new AtomicBoolean();AtomicInteger changes=new AtomicInteger();
  ItemStackHandler original=new ItemStackHandler(1){
   @Override public int getSlotLimit(int slot){return 1;}
   @Override public boolean isItemValid(int slot,ItemStack value){return value.is(Items.PAPER);}
   @Override protected void onContentsChanged(int slot){changes.incrementAndGet();}
  };
  var handler=new BurnLockedItemHandler(original,lock::get);
  check(handler.getSlots()==1&&handler.getSlotLimit(0)==1);
  // A third-party replacement's validator, stack limits and callbacks must remain delegated.
  check(handler.isItemValid(0,new ItemStack(Items.PAPER)));
  check(handler.insertItem(0,new ItemStack(Items.PAPER),true).isEmpty()&&changes.get()==0);
  check(original.getStackInSlot(0).isEmpty());
  check(!handler.isItemValid(0,new ItemStack(Items.STONE)));
  check(handler.insertItem(0,new ItemStack(Items.STONE),false).getCount()==1&&original.getStackInSlot(0).isEmpty());
  check(handler.insertItem(0,new ItemStack(Items.PAPER),false).isEmpty());
  check(changes.get()==1&&original.getStackInSlot(0).getCount()==1);
  lock.set(true);int before=changes.get();
  for(boolean simulate:new boolean[]{true,false}){
   check(handler.extractItem(0,1,simulate).isEmpty());
   check(handler.insertItem(0,new ItemStack(Items.PAPER),simulate).getCount()==1);
   check(original.getStackInSlot(0).getCount()==1&&changes.get()==before);
  }
  check(!handler.isItemValid(0,new ItemStack(Items.PAPER)));
  handler.setStackInSlot(0,ItemStack.EMPTY);check(original.getStackInSlot(0).getCount()==1);
  handler.setSize(0);check(original.getSlots()==1&&original.getStackInSlot(0).getCount()==1);
  handler.deserializeNBT(null,new CompoundTag());check(original.getStackInSlot(0).getCount()==1);
  ItemStack snapshot=handler.getStackInSlot(0);snapshot.setCount(0);check(original.getStackInSlot(0).getCount()==1);
  snapshot=handler.getStackInSlot(0);snapshot.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));check(!original.getStackInSlot(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
  check(changes.get()==before);lock.set(false);
  check(handler.extractItem(0,1,true).getCount()==1&&original.getStackInSlot(0).getCount()==1);
  check(handler.extractItem(0,1,false).getCount()==1&&original.getStackInSlot(0).isEmpty());
  check(changes.get()==before+1);
  handler.setStackInSlot(0,new ItemStack(Items.PAPER));check(original.getStackInSlot(0).getCount()==1&&changes.get()==before+2);
  check(handler.getStackInSlot(0)==original.getStackInSlot(0));
  handler.setSize(2);check(original.getSlots()==2);
  // The fork installs handlers in real constructors, without loading a Mixin.
  var inventory=new net.minecraft.world.entity.player.Inventory(null);
  var directMenus=java.util.List.of(
      new com.github.tartaricacid.netmusic.inventory.CDBurnerMenu(1,inventory),
      new com.github.tartaricacid.netmusic.inventory.ComputerMenu(2,inventory));
  for(var menu:directMenus){
   ItemStackHandler input=menu instanceof com.github.tartaricacid.netmusic.inventory.CDBurnerMenu burner
       ?burner.getInput():((com.github.tartaricacid.netmusic.inventory.ComputerMenu)menu).getInput();
   var output=((net.muxigame.musiccompat.burn.BurnMenuAccess)menu).muxiBurn$output();
   check(input instanceof BurnLockedItemHandler&&output instanceof BurnLockedItemHandler);
   check(((net.neoforged.neoforge.items.SlotItemHandler)menu.slots.get(0)).getItemHandler()==input);
   check(((net.neoforged.neoforge.items.SlotItemHandler)menu.slots.get(1)).getItemHandler()==output);
   input.setStackInSlot(0,new ItemStack(Items.PAPER));
   check(menu.slots.getFirst().getItem()==input.getStackInSlot(0));
   var unverified=new com.github.tartaricacid.netmusic.item.ItemMusicCD.SongInfo("fixture","unverified",1,false);
   if(menu instanceof com.github.tartaricacid.netmusic.inventory.CDBurnerMenu burner)burner.setSongInfo(unverified);
   else ((com.github.tartaricacid.netmusic.inventory.ComputerMenu)menu).setSongInfo(unverified);
   check(input.getStackInSlot(0).getCount()==1&&output.getStackInSlot(0).isEmpty());
   BurnServer.removed(menu);check(input.getStackInSlot(0).isEmpty()&&output.getStackInSlot(0).isEmpty());
  }
  System.out.println("{\"slotMutationChecks\":"+checks+",\"nativeMixinTested\":false,\"actualMcServerStarted\":false,\"cloudCalls\":0}");
 }
}
