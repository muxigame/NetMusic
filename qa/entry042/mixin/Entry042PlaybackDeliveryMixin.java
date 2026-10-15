package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;import net.muxigame.musiccompat.burn.*;import java.util.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlaybackClient.class,remap=false) public abstract class Entry042PlaybackDeliveryMixin {
 @Inject(method="accept",at=@At("HEAD")) private static void delivered(PlaybackNetwork.State packet,CallbackInfo callback){Entry042Scope.require();if(!BurnProtocol.media(packet.reference()))return;Entry042PlaybackDiagnostics.event("actual-playback-state-delivered",Map.of("media",Entry042PlaybackDiagnostics.media(packet.reference()),"position",List.of(packet.pos().getX(),packet.pos().getY(),packet.pos().getZ()),"dimension",packet.dimension(),"seconds",packet.seconds(),"active",packet.active(),"moving",packet.moving(),"prepared",packet.prepared()));}
}
