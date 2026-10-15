package net.muxigame.musiccompat.qa.entry042.mixin;
import com.mojang.blaze3d.audio.Channel;import net.muxigame.musiccompat.qa.entry042.*;import org.lwjgl.openal.AL10;import org.lwjgl.openal.AL11;
import net.muxigame.musiccompat.burn.PrivatePcmStream;import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
/** Read on the original OpenAL channel thread; the original delete runs exactly once. */
@Mixin(value=Channel.class,remap=false) public abstract class Entry042AudioClockMixin implements Entry042AudioClock {
 @Shadow @Final private int source;@Unique private double entry042$processed;@Unique private PrivatePcmStream entry042$privateStream;@Unique private String entry042$streamType="";
 @Inject(method="attachBufferStream",at=@At("RETURN"))private void entry042$base(AudioStream stream,org.spongepowered.asm.mixin.injection.callback.CallbackInfo c){entry042$processed=0;entry042$streamType=stream.getClass().getName();entry042$privateStream=stream instanceof PrivatePcmStream p?p:null;}
 @Redirect(method="removeProcessedBuffers",at=@At(value="INVOKE",target="Lorg/lwjgl/openal/AL10;alDeleteBuffers([I)V"))
 private void entry042$observe(int[] buffers){
  try{for(int id:buffers){int size=AL10.alGetBufferi(id,AL10.AL_SIZE),bits=AL10.alGetBufferi(id,AL10.AL_BITS),channels=AL10.alGetBufferi(id,AL10.AL_CHANNELS),frequency=AL10.alGetBufferi(id,AL10.AL_FREQUENCY);if(bits>0&&channels>0&&frequency>0)entry042$processed+=(double)size/(bits/8.0*channels*frequency);}}
  finally{AL10.alDeleteBuffers(buffers);}
 }
 public String entry042StreamType(){return entry042$streamType;}
 public double entry042ElapsedSeconds(){return (entry042$privateStream==null?0:entry042$privateStream.base)+entry042$processed+AL10.alGetSourcef(source,AL11.AL_SEC_OFFSET);}
}
