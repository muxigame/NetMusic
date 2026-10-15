package net.muxigame.musiccompat.qa.entry042.mixin;
import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.muxigame.musiccompat.qa.entry042.Entry042Lifecycle;
import org.spongepowered.asm.mixin.Mixin;import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;import java.util.concurrent.CompletableFuture;
@Mixin(value=MusicPlayResolverManager.class,priority=5000,remap=false)
public abstract class Entry042LifecycleResolverMixin {
 @WrapMethod(method="resolve")
 private static CompletableFuture<ItemMusicCD.SongInfo> hold(ItemMusicCD.SongInfo song,Operation<CompletableFuture<ItemMusicCD.SongInfo>> original){return Entry042Lifecycle.gate(song,original.call(song));}
}
