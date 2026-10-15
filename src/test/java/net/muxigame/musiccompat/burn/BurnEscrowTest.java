package net.muxigame.musiccompat.burn;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Real ItemStack, handler, component codecs and the production escrow implementation. */
public final class BurnEscrowTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("escrow invariant "+checks);checks++;}
    public static void main(String[] args){
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var registry=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for(int marker=1;marker<=60;marker++){
            CompoundTag data=new CompoundTag();data.putInt("ownedDiscMarker",marker);
            ItemStack original=new ItemStack(Items.PAPER);original.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            var input=new ItemStackHandler(1);input.setStackInSlot(0,original);
            BurnEscrow escrow=BurnEscrow.reserve(input);
            check(input.getStackInSlot(0).isEmpty()); // Original menu removed cannot return the escrow.
            original.setCount(0);check(escrow.original().getCount()==1); // Old aliases are detached.
            ItemStack copy=escrow.original();copy.setCount(0);check(escrow.original().getCount()==1);
            CompoundTag persistent=new CompoundTag();persistent.put("item",escrow.original().save(registry));
            ItemStack recovered=ItemStack.parseOptional(registry,persistent.getCompound("item"));
            check(recovered.getCount()==1&&recovered.get(DataComponents.CUSTOM_DATA).copyTag().getInt("ownedDiscMarker")==marker);
            ItemStack delivered=escrow.finish(recovered);check(delivered.getCount()==1&&escrow.settled());
            check(delivered.get(DataComponents.CUSTOM_DATA).copyTag().getInt("ownedDiscMarker")==marker);
            for(int late=0;late<8;late++)check(escrow.finish(recovered).isEmpty());
            check(input.getStackInSlot(0).isEmpty());
        }
        try{new BurnEscrow(ItemStack.EMPTY);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try{new BurnEscrow(new ItemStack(Items.PAPER,2));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        System.out.println("{\"actualEscrowAndCodecChecks\":"+checks+",\"nativeMenuTested\":false,\"cloudCalls\":0}");
    }
}
