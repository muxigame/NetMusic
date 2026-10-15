package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.burn.*;import net.muxigame.musiccompat.qa.entry042.*;import java.util.*;import java.util.function.Consumer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=PlaybackDelivery.class,remap=false)public abstract class Entry042DeliveryMixin {
 @Inject(method="send",at=@At("RETURN"))private static void delivered(ICommonPacketListener listener,boolean accepting,Runnable operation,Consumer<RuntimeException> failure,CallbackInfoReturnable<PlaybackDelivery.Result> ci){
  if(!"server".equals(System.getProperty("qa.local.role"))||!(listener instanceof ServerGamePacketListenerImpl game))return;Entry042Scope.require();
  Entry042Fixture.event("actual-private-delivery-result",Map.of("player",game.player.getGameProfile().getName(),"result",ci.getReturnValue().name(),"protocol",String.valueOf(listener.protocol()),"connected",listener.getConnection().isConnected(),"accepting",accepting,"stateChannel",listener.hasChannel(PlaybackNetwork.State.TYPE),"reportChannel",listener.hasChannel(PlaybackNetwork.Report.TYPE),"dimension",game.player.level().dimension().location().toString()));
 }
}
