package net.muxigame.musiccompat.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The normal per-player save, without saving unrelated players or changing access rules. */
@Mixin(PlayerList.class)
public interface BurnPlayerListAccess {
    @Invoker("save") void muxiBurn$save(ServerPlayer player);
}
