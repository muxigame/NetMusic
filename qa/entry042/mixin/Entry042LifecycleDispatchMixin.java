package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.burn.PlaybackServer;import net.muxigame.musiccompat.qa.entry042.Entry042Lifecycle;
import com.github.tartaricacid.netmusic.network.message.MusicToClientMessage;
import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=PlaybackServer.class,remap=false)
public abstract class Entry042LifecycleDispatchMixin {
 @Inject(method="start",at=@At("HEAD"))
 private static void dispatch(Level level,BlockPos pos,MusicToClientMessage message,CallbackInfoReturnable<Boolean> ci){Entry042Lifecycle.dispatch(level,pos);}
}
