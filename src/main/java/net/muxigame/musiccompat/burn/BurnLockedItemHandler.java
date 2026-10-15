package net.muxigame.musiccompat.burn;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Wraps only the two burn menus' original handlers, including their validation/callbacks. */
public final class BurnLockedItemHandler extends ItemStackHandler {
    private final ItemStackHandler original;
    private final java.util.function.BooleanSupplier guard;
    public BurnLockedItemHandler(ItemStackHandler original){super(0);this.original=original;this.guard=()->BurnServer.locked(this);}
    BurnLockedItemHandler(ItemStackHandler original,java.util.function.BooleanSupplier guard){super(0);this.original=original;this.guard=guard;}
    private boolean locked(){return guard.getAsBoolean();}
    @Override public int getSlots(){return original.getSlots();}
    @Override public int getSlotLimit(int slot){return original.getSlotLimit(slot);}
    @Override public ItemStack getStackInSlot(int slot){var value=original.getStackInSlot(slot);return locked()?value.copy():value;}
    @Override public boolean isItemValid(int slot,ItemStack value){return !locked()&&original.isItemValid(slot,value);}
    @Override public ItemStack insertItem(int slot,ItemStack value,boolean simulate){return locked()?value:original.insertItem(slot,value,simulate);}
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return locked()?ItemStack.EMPTY:original.extractItem(slot,amount,simulate);}
    @Override public void setStackInSlot(int slot,ItemStack value){if(!locked())original.setStackInSlot(slot,value);}
    @Override public void setSize(int size){if(original==null)super.setSize(size);else if(!locked())original.setSize(size);}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider){return original.serializeNBT(provider);}
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag value){if(!locked())original.deserializeNBT(provider,value);}
}
