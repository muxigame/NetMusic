package net.muxigame.musiccompat.burn;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;import com.github.tartaricacid.netmusic.item.ItemMusicCD;import com.github.tartaricacid.netmusic.network.message.MusicToClientMessage;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.*;import net.neoforged.neoforge.network.PacketDistributor;import net.neoforged.neoforge.event.level.ChunkWatchEvent;import net.neoforged.neoforge.event.tick.ServerTickEvent;import net.neoforged.neoforge.event.server.ServerStoppedEvent;import java.util.*;
/** No world scan or chunk loads. Retain only active, already loaded original private players. */
public final class PlaybackServer {
 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
 private record Notice(Object connection,String reason){}private static final Map<UUID,Notice> notices=new LinkedHashMap<>();
 private static boolean capable(ServerPlayer p){try{return PlaybackDelivery.status(p.connection,p.connection.isAcceptingMessages())==PlaybackDelivery.Result.READY;}catch(RuntimeException error){return false;}}
 private static void unavailable(ServerPlayer p,Key k,Active v,String reason){
  var notice=new Notice(p.connection,reason);if(notice.equals(notices.get(p.getUUID())))return;
  if(notices.size()>=4096)notices.remove(notices.keySet().iterator().next());notices.put(p.getUUID(),notice);
  String capability;try{capability="protocol="+p.connection.protocol()+",connected="+p.connection.getConnection().isConnected()+",accepting="+p.connection.isAcceptingMessages()+",state="+p.connection.hasChannel(PlaybackNetwork.State.TYPE)+",report="+p.connection.hasChannel(PlaybackNetwork.Report.TYPE);}catch(RuntimeException error){capability="inspection_failed_"+error.getClass().getSimpleName();}
  LOG.warn("Private music delivery unavailable: player={} epoch={} dimension={} position={} reason={} capability={} media={}",p.getUUID(),v.clock.epoch,k.level.dimension().location(),k.pos,reason,capability,v.reference);
  if(p.connection.isAcceptingMessages())try{p.sendSystemMessage(net.minecraft.network.chat.Component.literal("音乐未能同步播放：音乐连接未就绪，请更新整合包后重新连接。"));}catch(RuntimeException error){LOG.warn("Private music notice unavailable: player={} reason={}",p.getUUID(),error.getClass().getSimpleName());}
 }

 private record Key(ServerLevel level,BlockPos pos){}private record Active(PlaybackTimeline clock,String reference,String title){}
 private static final Map<Key,Active> active=new LinkedHashMap<>();private record Listener(UUID player,Key key){}private static final Map<Listener,Long> reports=new HashMap<>();
 public static boolean start(net.minecraft.world.level.Level level,BlockPos pos,MusicToClientMessage message){if(!(level instanceof ServerLevel server)||!BurnProtocol.media(message.url()))return false;
  if(message.timeSecond()<1||message.timeSecond()>14400)return true;
  var key=new Key(server,pos.immutable());if(active.size()>=512&&!active.containsKey(key))return true;
  var value=new Active(new PlaybackTimeline(message.timeSecond(),server.getGameTime(),0),message.url(),message.songName().substring(0,Math.min(256,message.songName().length())));active.put(key,value);
  for(var player:server.players())if(near(player,key)){if(capable(player))value.clock.elect(player.getUUID());send(player,key,value,true);}return true;
 }
 private static boolean near(ServerPlayer p,Key k){return p.serverLevel()==k.level&&p.distanceToSqr(k.pos.getX()+.5,k.pos.getY()+.5,k.pos.getZ()+.5)<96*96;}
 private static boolean valid(Key key,Active value){if(!key.level.hasChunkAt(key.pos))return false;var be=key.level.getBlockEntity(key.pos);if(!(be instanceof TileEntityMusicPlayer tile)||!tile.isPlay())return false;var song=ItemMusicCD.getSongInfo(tile.getPlayerInv().getStackInSlot(0));return song!=null&&value.reference.equals(song.songUrl);}
 private static void send(ServerPlayer p,Key k,Active v,boolean playing){var point=v.clock.snapshot(k.level.getGameTime());
  var result=PlaybackDelivery.send(p.connection,p.connection.isAcceptingMessages(),()->PacketDistributor.sendToPlayer(p,new PlaybackNetwork.State(k.level.dimension().location().toString(),k.pos,v.clock.epoch,v.reference,v.title,v.clock.duration,v.clock.prepared()?point.seconds():v.clock.initial(),v.clock.prepared()&&point.moving(),v.clock.prepared(),playing)),error->unavailable(p,k,v,"send_rejected_"+error.getClass().getSimpleName()));
  if(result==PlaybackDelivery.Result.SENT)notices.remove(p.getUUID());
  if(result==PlaybackDelivery.Result.UNSUPPORTED||result==PlaybackDelivery.Result.NOT_PLAY)unavailable(p,k,v,result.name().toLowerCase(java.util.Locale.ROOT));
 }
 public static void sent(ChunkWatchEvent.Sent event){for(var be:event.getChunk().getBlockEntities().values())if(be instanceof TileEntityMusicPlayer tile&&tile.isPlay()){
  var song=ItemMusicCD.getSongInfo(tile.getPlayerInv().getStackInSlot(0));if(song==null||!BurnProtocol.media(song.songUrl)||song.songTime<1||song.songTime>14400)continue;
  var key=new Key(event.getLevel(),tile.getBlockPos().immutable());if(!near(event.getPlayer(),key))continue;var v=active.get(key);
  if(v==null){if(active.size()>=512)continue;double elapsed=Math.max(0,Math.min(song.songTime,(song.songTime*20+64-tile.getCurrentTime())/20.0));v=new Active(new PlaybackTimeline(song.songTime,event.getLevel().getGameTime(),elapsed),song.songUrl,song.songName.substring(0,Math.min(256,song.songName.length())));active.put(key,v);}
  if(valid(key,v)){if(capable(event.getPlayer()))v.clock.elect(event.getPlayer().getUUID());send(event.getPlayer(),key,v,true);}
 }}
 public static void report(ServerPlayer player,PlaybackNetwork.Report p){var key=new Key(player.serverLevel(),p.pos());var value=active.get(key);long now=player.serverLevel().getGameTime();var listener=new Listener(player.getUUID(),key);long prior=reports.getOrDefault(listener,Long.MIN_VALUE/2);if(now-prior<5||value==null||!near(player,key)||!capable(player)||!valid(key,value))return;if(reports.size()>=4096&&!reports.containsKey(listener))return;reports.put(listener,now);if(value.clock.report(player.getUUID(),p.epoch(),p.seconds(),p.moving(),now))for(var other:player.serverLevel().players())if(other!=player&&near(other,key))send(other,key,value,true);}
 public static void tick(ServerTickEvent.Post event){var iterator=active.entrySet().iterator();while(iterator.hasNext()){var e=iterator.next();var leader=e.getValue().clock.leader();var player=leader==null?null:e.getKey().level.getServer().getPlayerList().getPlayer(leader);if(player==null||!near(player,e.getKey())||!capable(player)){e.getValue().clock.release(leader);for(var other:e.getKey().level.players())if(near(other,e.getKey())&&capable(other)){e.getValue().clock.elect(other.getUUID());break;}}if(!valid(e.getKey(),e.getValue())){for(var p:e.getKey().level.players())if(near(p,e.getKey()))send(p,e.getKey(),e.getValue(),false);iterator.remove();}}notices.entrySet().removeIf(n->{var p=event.getServer().getPlayerList().getPlayer(n.getKey());return p==null||p.connection!=n.getValue().connection;});reports.keySet().removeIf(k->!active.containsKey(k.key)||k.key.level.getServer().getPlayerList().getPlayer(k.player)==null);}
 public static void stopped(ServerStoppedEvent event){active.clear();reports.clear();notices.clear();}
 private PlaybackServer(){}
}
