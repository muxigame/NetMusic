package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;import net.muxigame.musiccompat.burn.*;import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;import java.net.URI;import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=PrivateLyrics.class,remap=false) public abstract class Entry042LyricPendingMixin {
 @Inject(method="load",at=@At("RETURN")) private static void pending(URI reference,String title,CallbackInfoReturnable<CompletableFuture<LyricRecord>> result){Entry042Scope.require();var future=result.getReturnValue();if(future!=null)Entry042PlaybackDiagnostics.lyric(reference,future);}
}
