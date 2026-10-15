package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;
import net.muxigame.musiccompat.burn.*;import java.net.http.*;import java.util.function.LongConsumer;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BurnHttp.class,remap=false) public abstract class Entry042NetworkMixin {
 @Inject(method="send",at=@At("HEAD"),cancellable=true) private static void local(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel,LongConsumer progress,CallbackInfoReturnable<HttpResponse<byte[]>> result)throws Exception{result.setReturnValue(Entry042Fixture.send(request,limit,cancel,progress));}
}
