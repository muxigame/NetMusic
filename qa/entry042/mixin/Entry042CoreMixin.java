package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;
import com.google.gson.JsonObject;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;import net.muxigame.musiccompat.burn.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BurnClientSession.class,remap=false) public abstract class Entry042CoreMixin {
 @Inject(method="account",at=@At("HEAD"),cancellable=true) private void account(@Coerce Object task,BurnCoordinator.Cancellation cancel,CallbackInfoReturnable<JsonObject> result)throws Exception{Entry042Scope.require();cancel.check();var mc=Minecraft.getInstance();var field=task.getClass().getDeclaredField("connection");field.setAccessible(true);if(mc.player==null||mc.getConnection()!=field.get(task)||!mc.player.getGameProfile().getName().equals(Long.toString(Entry042Scope.uid())))throw new IllegalStateException("owned_original_connection_required");result.setReturnValue(Entry042Fixture.account());}
 @Inject(method="link",at=@At("HEAD"),cancellable=true) private void direct(AbstractContainerScreen<?> screen,String value,CallbackInfoReturnable<BurnCoordinator.Source> result){Entry042Scope.require();if(value.equals(Entry042DirectSource.URL))result.setReturnValue(Entry042DirectSource.create());}
 @Inject(method="freshToken",at=@At("HEAD"),cancellable=true) private static void token(CallbackInfoReturnable<String> result){Entry042Scope.require();result.setReturnValue("entry042-owned-account-fixture");}
}
