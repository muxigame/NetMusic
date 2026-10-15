package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.burn.PlaybackNetwork;import net.muxigame.musiccompat.qa.entry042.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** QA only: a real optional-registration omission, never a hasChannel mock. */
@Mixin(value=PlaybackNetwork.class,remap=false)public abstract class Entry042PlaybackNetworkMixin {
 @Inject(method="payloads",at=@At("HEAD"),cancellable=true)private static void omitted(RegisterPayloadHandlersEvent event,CallbackInfo ci){
  if(Boolean.getBoolean("muxi.music.entry042.unsupportedGuest")&&"guest".equals(System.getProperty("qa.local.role"))){Entry042Scope.require();Entry042Fixture.event("owned-private-payload-registration-omitted",java.util.Map.of("qaOnly",true,"actualNegotiationRequired",true));ci.cancel();}
 }
}
