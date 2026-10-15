package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;import com.github.img.netmusicbetterlogin.api.BetterNetWorker;import java.util.*;import java.net.URI;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Prevent original login UI QR checks from making real network/account requests. */
@Mixin(value=BetterNetWorker.class,remap=false)
public abstract class Entry042LoginNetworkMixin {
 @Inject(method="get",at=@At("HEAD"),cancellable=true) private static void fixture(String url,Map<String,String> headers,CallbackInfoReturnable<BetterNetWorker.HttpResponse> ci){
  Entry042Scope.require();URI uri=URI.create(url);if(!"music.163.com".equals(uri.getHost())||!"https".equals(uri.getScheme()))throw new IllegalStateException("owned_login_http_refused");
  String body;if(uri.getPath().equals("/api/login/qrcode/unikey"))body="{\"code\":200,\"unikey\":\"owned_fixture_key\"}";else if(uri.getPath().equals("/api/login/qrcode/client/login"))body="{\"code\":801,\"message\":\"Owned fixture waiting\"}";else throw new IllegalStateException("owned_login_http_refused");
  Entry042Fixture.event("original-login-http-fixture",Map.of("actualNetworkCalls",0,"actualPlatformLoginVerified",false));ci.setReturnValue(new BetterNetWorker.HttpResponse(body,Map.of()));
 }
}
