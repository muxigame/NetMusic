package net.muxigame.musiccompat.qa.entry042;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;import com.github.tartaricacid.netmusic.item.ItemMusicCD;import com.github.tartaricacid.netmusic.init.InitBlocks;
import com.github.tartaricacid.netmusic.client.audio.NetMusicSound;import com.gly091020.netMusicListNeoforge.mixin.accessor.TickableSoundGetterMixins;import net.muxigame.musiccompat.burn.BurnProtocol;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.item.ItemStack;import net.minecraft.core.component.DataComponents;import net.minecraft.client.Minecraft;import java.util.*;
public final class Entry042Playback {
 private static BlockPos selected;
 static TileEntityMusicPlayer tile(ServerPlayer player){if(selected==null)throw new IllegalStateException("owned_music_site_required");return (TileEntityMusicPlayer)player.server.overworld().getBlockEntity(selected);}
 static void sites(ServerPlayer player){if(selected==null)selected=player.server.overworld().getSharedSpawnPos().offset(2,0,0);}
 public static int join(ServerPlayer player){Entry042Scope.require();if(!Set.of("10000","10001").contains(player.getGameProfile().getName()))throw new IllegalStateException();var host=player.server.getPlayerList().getPlayerByName("10000");if(host==null)throw new IllegalStateException();player.resetFallDistance();player.teleportTo(player.server.overworld(),host.getX()+1,host.getY(),host.getZ(),player.getYRot(),player.getXRot());player.resetFallDistance();return 1;}
 public static int place(ServerPlayer player,int marker,int site){Entry042Server.require(player);player.closeContainer();selected=player.server.overworld().getSharedSpawnPos().offset(2+site*3,0,0);
  // Fresh vanilla overworld spawn can be below zero and have no listener floor.
  // These nine blocks belong only to the marked QA world; keep both listeners alive.
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)player.server.overworld().setBlock(selected.offset(dx,-1,dz),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
  player.resetFallDistance();player.teleportTo(player.server.overworld(),selected.getX()+.5,selected.getY()+1,selected.getZ()+.5,player.getYRot(),player.getXRot());player.resetFallDistance();
  if(player.server.overworld().getBlockEntity(selected) instanceof TileEntityMusicPlayer old&&!old.getPlayerInv().getStackInSlot(0).isEmpty())throw new IllegalStateException("owned_player_site_occupied");
  int slot=-1;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).getOrDefault(DataComponents.REPAIR_COST,0)==marker){slot=i;break;}if(slot<0)throw new IllegalStateException("actual_marked_cd_required");
  var stack=player.getInventory().getItem(slot);var song=ItemMusicCD.getSongInfo(stack);if(stack.getCount()!=1||song==null||!BurnProtocol.media(song.songUrl))throw new IllegalStateException("actual_burned_owned_cd_required");
  player.server.overworld().setBlock(selected,InitBlocks.MUSIC_PLAYER.get().defaultBlockState(),3);var target=tile(player);if(target==null)throw new IllegalStateException();player.getInventory().setItem(slot,ItemStack.EMPTY);target.getPlayerInv().setStackInSlot(0,stack);target.setPlay(false);target.markDirty();Entry042DiscAudit.audit(player,"actual-cd-moved-to-original-player",true);return 1;
 }
 public static int existing(ServerPlayer player,int marker){Entry042Server.require(player);player.closeContainer();int slot=-1;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).getOrDefault(DataComponents.REPAIR_COST,0)==marker){slot=i;break;}if(slot<0)throw new IllegalStateException();var stack=player.getInventory().getItem(slot);var song=ItemMusicCD.getSongInfo(stack);if(stack.getCount()!=1||song==null||song.readOnly)throw new IllegalStateException("existing_writable_original_cd_required");
  player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,p)->new com.github.tartaricacid.netmusic.inventory.ComputerMenu(id,inventory),net.minecraft.network.chat.Component.literal("Owned original CD rewrite")));player.getInventory().setItem(slot,ItemStack.EMPTY);Entry042Server.input(player.containerMenu).setStackInSlot(0,stack);player.containerMenu.broadcastChanges();Entry042DiscAudit.audit(player,"original-cd-rewrite-menu",true);return 1;
 }
 public static int action(ServerPlayer player,String action){Entry042Server.require(player);sites(player);var target=tile(player);if(target==null)throw new IllegalStateException();
  if(action.equals("start")){var song=ItemMusicCD.getSongInfo(target.getPlayerInv().getStackInSlot(0));if(song==null)throw new IllegalStateException();target.setPlayToClient(song);}
  else {target.setPlay(false);target.setCurrentTime(0);target.markDirty();if(action.equals("return")){var stack=target.getPlayerInv().getStackInSlot(0);target.getPlayerInv().setStackInSlot(0,ItemStack.EMPTY);net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player,stack);}}
  Entry042DiscAudit.audit(player,"original-player-"+action,true);return 1;
 }
 public static int count(ServerPlayer player,int marker){int result=0;var spawn=player.server.overworld().getSharedSpawnPos();for(int site=0;site<2;site++){var be=player.server.overworld().getBlockEntity(spawn.offset(2+site*3,0,0));if(be instanceof TileEntityMusicPlayer music){var stack=music.getPlayerInv().getStackInSlot(0);if(stack.getOrDefault(DataComponents.REPAIR_COST,0)==marker)result+=stack.getCount();}}return result;}
 public static int legacyStart(ServerPlayer player){
  Entry042Server.require(player);sites(player);var target=tile(player);
  if(target==null||target.isPlay())throw new IllegalStateException("stopped_owned_player_required");
  var stack=target.getPlayerInv().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(stack);
  if(stack.getCount()!=1||stack.getOrDefault(DataComponents.REPAIR_COST,0)<=0||song==null||!BurnProtocol.media(song.songUrl)||song.songTime<1||song.songTime>14400)throw new IllegalStateException("actual_owned_burned_disc_required");
  target.setCurrentTime(song.songTime*20+64);target.setPlay(true);target.markDirty();
  var packet=new com.github.tartaricacid.netmusic.network.message.MusicToClientMessage(selected,song.songUrl,song.songUrl,song.songTime,song.songName);
  var recipients=new ArrayList<String>();
  for(String name:List.of("10000","10001")){var peer=player.server.getPlayerList().getPlayerByName(name);
   if(peer!=null&&peer.serverLevel()==player.server.overworld()&&peer.distanceToSqr(selected.getX()+.5,selected.getY()+.5,selected.getZ()+.5)<96*96){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer,packet);recipients.add(name);}}
  Entry042Scope.write("entry042-legacy-playback-dispatch.json",Map.of("runId",System.getProperty("qa.local.runId"),"atUtcMillis",System.currentTimeMillis(),"actualPayload",packet.type().id().toString(),"recipients",recipients,"usedPlaybackStatePayload",false,"actualOwnedDisc",true,"position",List.of(selected.getX(),selected.getY(),selected.getZ())));
  Entry042DiscAudit.audit(player,"original-payload-direct-playback",true);return 1;
 }
 private Entry042Playback(){}
}
