package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.burn.*;import net.muxigame.musiccompat.qa.entry042.Entry042BurnReplies;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BurnClientSession.class,remap=false)public abstract class Entry042BurnReplyMixin {
 @Inject(method="receive",at=@At("RETURN"))private void received(BurnNetwork.Result result,CallbackInfo ci){Entry042BurnReplies.received(this,result);}
}
