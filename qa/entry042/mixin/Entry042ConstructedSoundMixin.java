package net.muxigame.musiccompat.qa.entry042.mixin;
import com.github.tartaricacid.netmusic.client.audio.MusicPlayManager;
import com.github.tartaricacid.netmusic.client.audio.NetMusicSound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.muxigame.musiccompat.qa.entry042.Entry042PlaybackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
/** Observe the completed factory result at its caller, not between constructor injections. */
@Mixin(value=MusicPlayManager.class,remap=false)
public abstract class Entry042ConstructedSoundMixin {
 @WrapOperation(method="lambda$playMusic$0",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"),require=1,expect=1)
 private static void beforePlay(SoundManager manager,SoundInstance sound,Operation<Void> original){
  if(sound instanceof NetMusicSound music)Entry042PlaybackClient.constructed(music);
  original.call(manager,sound);
 }
}
