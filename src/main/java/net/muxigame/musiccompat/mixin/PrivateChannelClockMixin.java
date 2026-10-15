package net.muxigame.musiccompat.mixin;
import com.mojang.blaze3d.audio.Channel;import net.minecraft.client.sounds.AudioStream;import net.muxigame.musiccompat.burn.PrivatePcmStream;import org.lwjgl.openal.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Counts exactly the PCM chunks the original channel unqueues; it never deletes or unqueues buffers. */
@Mixin(value=Channel.class,remap=false)
public abstract class PrivateChannelClockMixin {
 @Shadow @Final private int source;@Unique private PrivatePcmStream muxiPlayback$stream;
 @Inject(method="attachBufferStream",at=@At("RETURN"))private void muxiPlayback$attach(AudioStream stream,CallbackInfo c){muxiPlayback$stream=stream instanceof PrivatePcmStream privateStream?privateStream:null;muxiPlayback$sample();}
 @ModifyExpressionValue(method="removeProcessedBuffers",at=@At(value="INVOKE",target="Lorg/lwjgl/openal/AL10;alGetSourcei(II)I"))private int muxiPlayback$processed(int actual){if(muxiPlayback$stream!=null)muxiPlayback$stream.processed(actual);return actual;}
 @Inject(method="updateStream",at=@At("RETURN"))private void muxiPlayback$update(CallbackInfo c){muxiPlayback$sample();}
 @Inject(method="play",at=@At("RETURN"))private void muxiPlayback$play(CallbackInfo c){if(muxiPlayback$stream!=null&&muxiPlayback$stream.state.followPause&&muxiPlayback$stream.state.timelinePaused)AL10.alSourcePause(source);muxiPlayback$sample();}
 @Unique private void muxiPlayback$sample(){if(muxiPlayback$stream==null)return;var state=muxiPlayback$stream.state;int status=AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE);if(state.followPause&&!state.timelinePaused&&!state.localPaused&&status==AL10.AL_PAUSED){AL10.alSourcePlay(source);state.followPause=false;status=AL10.AL_PLAYING;}if(status==AL10.AL_PLAYING||status==AL10.AL_PAUSED)muxiPlayback$stream.state.sample(muxiPlayback$stream.position(AL10.alGetSourcef(source,AL11.AL_SEC_OFFSET)),status==AL10.AL_PAUSED);}
}
