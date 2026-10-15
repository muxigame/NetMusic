package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;
import net.muxigame.musiccompat.burn.ExistingPlatformClientSource;import java.net.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=ExistingPlatformClientSource.class,remap=false) public abstract class Entry042DnsMixin {
 @Redirect(method="clientTransport",at=@At(value="INVOKE",target="Ljava/net/InetAddress;getAllByName(Ljava/lang/String;)[Ljava/net/InetAddress;")) private static InetAddress[] local(String host)throws Exception{Entry042Scope.require();if(!java.util.Set.of("u.y.qq.com","api.bilibili.com","entry042.qqmusic.qq.com","entry042.bilivideo.com").contains(host))throw new IllegalStateException("entry042_dns_refused");return new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})};}
}
