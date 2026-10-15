package net.muxigame.musiccompat.qa.entry042;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.init.InitBlocks;
import net.muxigame.musiccompat.burn.BurnProtocol;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;import net.minecraft.world.level.block.Blocks;
import net.minecraft.commands.Commands;import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.mojang.brigadier.arguments.*;import java.util.*;import java.util.concurrent.*;
/** Only marked original CDs and the two existing owned sites. Never creates or copies a disc. */
public final class Entry042Lifecycle {
 private static TileEntityMusicPlayer original;private static BlockPos pos;private static String reference;
 private static boolean removed,armed;private static Gate gate;private static CompletableFuture<?> oldConsumer;private static int ticks,oldCallbacks,newCallbacks,dispatches;
 private static final List<Map<String,Object>> commands=new ArrayList<>();
 private static final class Gate {
  final CompletableFuture<ItemMusicCD.SongInfo> source,result=new CompletableFuture<>();boolean released;
  Gate(CompletableFuture<ItemMusicCD.SongInfo> source){this.source=source;}
  void release(){if(released)return;released=true;source.whenComplete((song,error)->{if(error!=null)result.completeExceptionally(error);else result.complete(song);});}
 }
 public static CompletableFuture<ItemMusicCD.SongInfo> gate(ItemMusicCD.SongInfo song,CompletableFuture<ItemMusicCD.SongInfo> source){
  if(!armed||!Entry042Scope.role().equals("server")||song==null||!Objects.equals(reference,song.songUrl))return source;
  Entry042Scope.require();if(gate!=null)throw new IllegalStateException("single_owned_resolver_gate_required");
  armed=false;gate=new Gate(source);return gate.result;
 }
 public static void consumerFuture(TileEntityMusicPlayer tile,CompletableFuture<?> future){if(tile==original&&gate!=null&&!gate.released&&Entry042Scope.role().equals("server")){Entry042Scope.require();oldConsumer=future;}}
 public static void callback(TileEntityMusicPlayer tile){if(original==null||!Entry042Scope.role().equals("server"))return;Entry042Scope.require();if(tile==original)oldCallbacks++;else if(tile.getBlockPos().equals(pos))newCallbacks++;}
 public static void dispatch(net.minecraft.world.level.Level level,BlockPos position){if(original==null||!Entry042Scope.role().equals("server")||!position.equals(pos)||level!=original.getLevel())return;Entry042Scope.require();dispatches++;}
 public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("entry042-lifecycle")
  .requires(s->s.getEntity() instanceof ServerPlayer p&&Set.of("10000","10001").contains(p.getGameProfile().getName()))
  .then(Commands.argument("action",StringArgumentType.word()).then(Commands.argument("id",IntegerArgumentType.integer(0)).executes(c->action(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"action"),IntegerArgumentType.getInteger(c,"id"))))));}
 private static void owned(ServerPlayer p){Entry042Scope.require();if(!Set.of("10000","10001").contains(p.getGameProfile().getName())||!p.server.getWorldData().getLevelName().equals("qa-world"))throw new IllegalStateException("owned_player_world_required");}
 private static TileEntityMusicPlayer current(ServerPlayer p){var be=pos==null?null:p.server.overworld().getBlockEntity(pos);return be!=null&&be.getClass()==TileEntityMusicPlayer.class?(TileEntityMusicPlayer)be:null;}
 private static void invariant(boolean value){if(!value)throw new IllegalStateException("owned_lifecycle_invariant_failed");}
 private static void capture(ServerPlayer p){
  invariant(gate==null||gate.released);original=null;var spawn=p.server.overworld().getSharedSpawnPos();
  for(int site=0;site<2;site++){var be=p.server.overworld().getBlockEntity(spawn.offset(2+site*3,0,0));if(be!=null&&be.getClass()==TileEntityMusicPlayer.class){var t=(TileEntityMusicPlayer)be;var stack=t.getPlayerInv().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(stack);if(stack.getCount()==1&&stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0)>0&&song!=null&&BurnProtocol.media(song.songUrl)){invariant(original==null);original=t;pos=t.getBlockPos().immutable();reference=song.songUrl;}}}
  invariant(original!=null);gate=null;oldConsumer=null;armed=false;removed=false;oldCallbacks=newCallbacks=dispatches=0;
 }
 private static void remove(ServerPlayer p){invariant(original!=null&&!removed&&current(p)==original);var level=p.server.overworld();
  // Remove the actual BE first so onRemove cannot spawn a second/drop copy of the held original CD.
  level.removeBlockEntity(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);removed=true;invariant(original.isRemoved()&&current(p)==null);
 }
 private static void restore(ServerPlayer p){
  if(original==null)return;armed=false;if(gate!=null)gate.release();var t=current(p);ItemStack stack=ItemStack.EMPTY;
  if(t!=null){t.setPlay(false);t.setCurrentTime(0);stack=t.getPlayerInv().getStackInSlot(0);t.getPlayerInv().setStackInSlot(0,ItemStack.EMPTY);t.markDirty();}
  if(t!=original&&!original.getPlayerInv().getStackInSlot(0).isEmpty()){invariant(stack.isEmpty());stack=original.getPlayerInv().getStackInSlot(0);original.getPlayerInv().setStackInSlot(0,ItemStack.EMPTY);}
  if(!stack.isEmpty()){invariant(stack.getCount()==1);net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(p,stack);}
  p.server.overworld().removeBlockEntity(pos);p.server.overworld().setBlock(pos,Blocks.AIR.defaultBlockState(),3);removed=true;
  Entry042DiscAudit.audit(p,"lifecycle-original-disc-returned",true);
 }
 private static void teleport(ServerPlayer p,boolean away){invariant(original!=null);var destination=away?pos.offset(4096,0,4096):pos;var level=p.server.overworld();
  if(away)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(destination.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
  p.resetFallDistance();p.teleportTo(level,destination.getX()+.5,destination.getY()+1,destination.getZ()+.5,p.getYRot(),p.getXRot());p.resetFallDistance();
 }
 private static int action(ServerPlayer p,String action,int id){owned(p);if(!Set.of("leave","return","probe").contains(action))Entry042Server.require(p);
  switch(action){
   case "capture"->capture(p);
   case "hold"->{invariant(original!=null&&!original.isPlay()&&current(p)==original&&gate==null);armed=true;original.setPlayToClient(ItemMusicCD.getSongInfo(original.getPlayerInv().getStackInSlot(0)));invariant(gate!=null&&!gate.result.isDone());}
   case "remove"->remove(p);
   case "replace"->{invariant(removed&&original!=null&&current(p)==null);var disc=original.getPlayerInv().getStackInSlot(0);invariant(disc.getCount()==1);original.getPlayerInv().setStackInSlot(0,ItemStack.EMPTY);p.server.overworld().setBlock(pos,InitBlocks.MUSIC_PLAYER.get().defaultBlockState(),3);var replacement=current(p);invariant(replacement!=null&&replacement!=original);replacement.getPlayerInv().setStackInSlot(0,disc);replacement.setPlay(false);replacement.markDirty();replacement.setPlayToClient(ItemMusicCD.getSongInfo(disc));}
   case "release"->{invariant(gate!=null&&removed);gate.release();}
   case "restore"->restore(p);
   case "leave"->teleport(p,true);
   case "return"->teleport(p,false);
   case "probe"->{}
   default->throw new IllegalArgumentException("owned_lifecycle_action_required");
  }
  var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("action",action);row.put("uid",p.getGameProfile().getName());row.put("atUtcMillis",System.currentTimeMillis());row.put("facts",facts(p));if(commands.size()>=64)commands.remove(0);commands.add(row);write(p);return 1;
 }
 private static Map<String,Object> facts(ServerPlayer p){var row=new LinkedHashMap<String,Object>();row.put("captured",original!=null);row.put("oldCallbacks",oldCallbacks);row.put("newCallbacks",newCallbacks);row.put("dispatches",dispatches);row.put("removedByHelper",removed);
  if(original!=null){row.put("oldIdentity",System.identityHashCode(original));row.put("oldRemoved",original.isRemoved());row.put("oldPlaying",original.isPlay());row.put("oldTime",original.getCurrentTime());var t=current(p);row.put("currentPresent",t!=null);row.put("currentIsOriginal",t==original);row.put("currentIdentity",t==null?0:System.identityHashCode(t));row.put("currentPlaying",t!=null&&t.isPlay());row.put("currentTime",t==null?0:t.getCurrentTime());row.put("position",List.of(pos.getX(),pos.getY(),pos.getZ()));}
  row.put("oldConsumerFuturePresent",oldConsumer!=null);if(oldConsumer!=null){row.put("oldConsumerFutureDone",oldConsumer.isDone());row.put("oldConsumerFutureExceptional",oldConsumer.isCompletedExceptionally());}row.put("gatePresent",gate!=null);if(gate!=null){row.put("sourceDone",gate.source.isDone());row.put("sourceExceptional",gate.source.isCompletedExceptionally());row.put("sourceIdentity",System.identityHashCode(gate.source));row.put("returnedGateIdentity",System.identityHashCode(gate.result));row.put("released",gate.released);row.put("heldFutureDone",gate.result.isDone());}return row;
 }
 private static void write(ServerPlayer p){Entry042Scope.write("entry042-lifecycle-server.json",Map.of("runId",System.getProperty("qa.local.runId"),"atUtcMillis",System.currentTimeMillis(),"facts",facts(p),"commands",List.copyOf(commands),"actualOriginalResolverFuture",true,"newDiscsCreated",0,"realCloudCalls",0));}
 public static void tick(ServerTickEvent.Post event){if(!Entry042Scope.role().equals("server")||original==null||++ticks%2!=0)return;var p=event.getServer().getPlayerList().getPlayerByName("10000");if(p!=null)write(p);}
 private Entry042Lifecycle(){}
}
