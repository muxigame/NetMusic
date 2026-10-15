package net.muxigame.musiccompat.qa.entry042.mixin;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.muxigame.musiccompat.qa.entry042.Entry042Lifecycle;
import java.util.concurrent.*;import java.util.function.Consumer;import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=TileEntityMusicPlayer.class,remap=false)
public abstract class Entry042LifecycleCallbackMixin {
 @WrapOperation(method="setPlayToClient",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
 private CompletableFuture<Void> observeCompletion(CompletableFuture<ItemMusicCD.SongInfo> source,Consumer<ItemMusicCD.SongInfo> consumer,Executor executor,Operation<CompletableFuture<Void>> original){
  var completion=original.call(source,consumer,executor);Entry042Lifecycle.consumerFuture((TileEntityMusicPlayer)(Object)this,completion);return completion;
 }
 // Count only accepted resolver callbacks, after the integrated ownership/generation guard.
 // HEAD would now include rejected stale completions; the first mutation is the stable boundary.
 @Inject(method="lambda$setPlayToClient$0",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/netmusic/tileentity/TileEntityMusicPlayer;setCurrentTime(I)V"),require=1,expect=1)
 private void callback(CallbackInfo ci){Entry042Lifecycle.callback((TileEntityMusicPlayer)(Object)this);}
}
