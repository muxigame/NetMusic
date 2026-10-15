package net.muxigame.musiccompat.burn;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One real server-owned item, detached from every mutable menu reference. */
public final class BurnEscrow {
    private final ItemStack original;
    private boolean settled;
    public BurnEscrow(ItemStack item) {
        if(item.isEmpty()||item.getCount()!=1)throw new IllegalArgumentException("one_disc_required");
        original=item.copy();
    }
    public static BurnEscrow reserve(ItemStackHandler handler) {
        BurnEscrow result=new BurnEscrow(handler.getStackInSlot(0));
        handler.setStackInSlot(0,ItemStack.EMPTY);
        if(!handler.getStackInSlot(0).isEmpty())throw new IllegalStateException("disc_reservation_failed");
        return result;
    }
    public ItemStack original(){return original.copy();}
    public synchronized boolean settled(){return settled;}
    /** A second callback, cancel or logout cannot obtain another item. */
    public synchronized ItemStack finish(ItemStack result){
        if(settled)return ItemStack.EMPTY;
        if(result.isEmpty()||result.getCount()!=1)throw new IllegalArgumentException("one_disc_required");
        ItemStack owned=result.copy();settled=true;return owned;
    }
}
