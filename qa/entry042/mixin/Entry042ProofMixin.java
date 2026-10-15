package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;
import com.google.gson.JsonObject;import net.muxigame.musiccompat.burn.BurnProofVerifier;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BurnProofVerifier.class,remap=false) public abstract class Entry042ProofMixin {
 @Inject(method="consume",at=@At("HEAD"),cancellable=true) private void fixture(String proof,long uid,String nonce,CallbackInfoReturnable<JsonObject> result)throws Exception{result.setReturnValue(Entry042Fixture.consume(proof,uid,nonce));}
}
