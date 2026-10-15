package net.muxigame.musiccompat.mixin;

import com.github.tartaricacid.netmusic.client.audio.NetMusicSound;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;

/** Select our integrated decoder before Playlist's getStream HEAD replacement. */
@Mixin(value=SoundEngine.class,remap=false)
public abstract class PrivateSoundEngineStreamMixin {
    @WrapOperation(method="play",at=@At(value="INVOKE",target="Lnet/minecraft/client/resources/sounds/SoundInstance;getStream(Lnet/minecraft/client/sounds/SoundBufferLibrary;Lnet/minecraft/client/resources/sounds/Sound;Z)Ljava/util/concurrent/CompletableFuture;"),require=1,expect=1)
    private CompletableFuture<AudioStream> privateStream(SoundInstance instance, SoundBufferLibrary library, Sound sound, boolean looping, Operation<CompletableFuture<AudioStream>> original) {
        if(instance.getClass()==NetMusicSound.class && ((NetMusicSound)instance).muxiPlayback$state()!=null)
            return ((NetMusicSound)instance).muxiPlayback$privateStream();
        return original.call(instance,library,sound,looping);
    }
}
