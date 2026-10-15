package net.muxigame.musiccompat.mixin;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.gly091020.netMusicListNeoforge.block.EnderMusicPlayerEntity;
import net.muxigame.musiccompat.burn.BurnProtocol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EnderMusicPlayerEntity.class,priority=3000,remap=false)
public abstract class OssEnderMixin {
    @Inject(method="setPlayToClient",at=@At("HEAD"),cancellable=true)
    private void muxiBurn$oss(ItemMusicCD.SongInfo song,CallbackInfo callback){
        if(song==null||!BurnProtocol.media(song.songUrl)){((EnderMusicPlayerEntity)(Object)this).setPlay(false);callback.cancel();}
    }
}
