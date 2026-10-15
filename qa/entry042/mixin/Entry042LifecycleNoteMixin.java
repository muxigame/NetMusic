package net.muxigame.musiccompat.qa.entry042.mixin;
import com.github.tartaricacid.netmusic.client.audio.NetMusicSound;
import net.minecraft.world.level.Level;import net.minecraft.core.particles.ParticleOptions;
import net.muxigame.musiccompat.qa.entry042.Entry042LifecycleClient;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(value=NetMusicSound.class,remap=false)
public abstract class Entry042LifecycleNoteMixin {
 @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
 private void notes(Level level,ParticleOptions particle,double x,double y,double z,double dx,double dy,double dz,Operation<Void> original){var before=Entry042LifecycleClient.beforeNote((NetMusicSound)(Object)this,particle);try{original.call(level,particle,x,y,z,dx,dy,dz);}finally{Entry042LifecycleClient.afterNote(before);}}
}
