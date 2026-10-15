package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;import net.muxigame.musiccompat.burn.*;import java.net.http.*;import java.util.function.LongConsumer;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=PrivatePlayback.class,remap=false) public abstract class Entry042PrivatePlaybackMixin {
 @Inject(method="account",at=@At("HEAD")) private static void accountStart(BurnCoordinator.Cancellation cancel,CallbackInfoReturnable<Object> result){Entry042Scope.require();var mc=net.minecraft.client.Minecraft.getInstance();Entry042PlaybackDiagnostics.account(false,PrivatePlayback.remembered(mc.getConnection())!=null);}
 @Inject(method="account",at=@At("RETURN")) private static void accountDone(BurnCoordinator.Cancellation cancel,CallbackInfoReturnable<Object> result){Entry042Scope.require();var mc=net.minecraft.client.Minecraft.getInstance();Entry042PlaybackDiagnostics.account(true,PrivatePlayback.remembered(mc.getConnection())!=null);}
 @Inject(method="token",at=@At("HEAD"),cancellable=true) private static void token(CallbackInfoReturnable<String> result){Entry042Scope.require();result.setReturnValue("entry042-owned-account-fixture");}
 // Exact candidate10 compiler pin; fail injection if compiler layout changes. No external fallback.
 @Redirect(method="lambda$fetch$10(Ljava/net/http/HttpRequest;)Ljava/net/http/HttpResponse;",at=@At(value="INVOKE",target="Ljava/net/http/HttpClient;send(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;"),require=1,expect=1)
 private static HttpResponse<java.io.InputStream> ownedSend(HttpClient ignored,HttpRequest request,HttpResponse.BodyHandler<java.io.InputStream> handler)throws Exception{return Entry042PlaybackTransport.send(request);}
}
