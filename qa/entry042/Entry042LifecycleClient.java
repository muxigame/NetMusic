package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import java.nio.file.*;import java.util.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.particle.*;import net.minecraft.client.sounds.*;
import net.minecraft.core.BlockPos;import net.minecraft.core.particles.*;
import com.github.tartaricacid.netmusic.client.audio.NetMusicSound;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;
import com.gly091020.netMusicListNeoforge.mixin.accessor.TickableSoundGetterMixins;
import net.muxigame.musiccompat.burn.*;import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** Retains the real removed sound and original BE after SoundEngine drops its list entry. */
public final class Entry042LifecycleClient {
 private static NetMusicSound retained;private static TileEntityMusicPlayer owner;private static BlockPos pos;
 private static Object armedConnection;private static net.minecraft.client.multiplayer.ClientLevel armedLevel;
 private static ChannelAccess.ChannelHandle handle;private static long ticks,emissions,lastEmissionTick,invalidTick=-1;
 private static final Set<Particle> ownedNotes=Collections.newSetFromMap(new IdentityHashMap<>());private static String noteTrackingError="";private static long taggedNotes;
 private static int sourceState;private static long sourceAt;private static final List<Map<String,Object>> results=new ArrayList<>();
 private long id=-1;private int writeTicks;
 public Entry042LifecycleClient(){Entry042Scope.require();NeoForge.EVENT_BUS.addListener(this::tick);}
 private static Set<Particle> particles()throws Exception{var mc=Minecraft.getInstance();var values=Collections.newSetFromMap(new IdentityHashMap<Particle,Boolean>());mc.particleEngine.iterateParticles(values::add);@SuppressWarnings("unchecked") var pending=(Queue<Particle>)Entry042Client.field(mc.particleEngine,"particlesToAdd");values.addAll(pending);return values;}
 public static Set<Particle> beforeNote(NetMusicSound sound,ParticleOptions particle){
  if(sound!=retained||particle.getType()!=ParticleTypes.NOTE)return null;Entry042Scope.require();emissions++;lastEmissionTick=ticks;
  try{return particles();}catch(Exception error){noteTrackingError=error.getClass().getSimpleName();return null;}
 }
 public static void afterNote(Set<Particle> before){if(before==null)return;try{for(var particle:particles())if(particle.getClass()==NoteParticle.class&&!before.contains(particle)){ownedNotes.add(particle);taggedNotes++;}}catch(Exception error){noteTrackingError=error.getClass().getSimpleName();}}
 private static void arm()throws Exception{var mc=Minecraft.getInstance();var engine=((TickableSoundGetterMixins.SoundManagerMixin)mc.getSoundManager()).getSoundEngine();retained=null;
  for(var sound:((TickableSoundGetterMixins.SoundEngineMixin)engine).getTickableSoundInstances())if(sound.getClass()==NetMusicSound.class&&((PrivateSoundAccess)sound).muxiPlayback$state()!=null&&!sound.isStopped()){if(retained!=null)throw new IllegalStateException("one_owned_private_sound_required");retained=(NetMusicSound)sound;}
  if(retained==null)throw new IllegalStateException("ready_owned_private_sound_required");pos=(BlockPos)Entry042Client.field(retained,"pos");var be=mc.level.getBlockEntity(pos);if(be==null||be.getClass()!=TileEntityMusicPlayer.class)throw new IllegalStateException("original_owned_be_required");owner=(TileEntityMusicPlayer)be;
  @SuppressWarnings("unchecked") var channels=(Map<Object,ChannelAccess.ChannelHandle>)Entry042Client.field(engine,"instanceToChannel");handle=channels.get(retained);if(handle==null)throw new IllegalStateException("actual_owned_channel_required");armedConnection=mc.getConnection();armedLevel=mc.level;emissions=lastEmissionTick=0;invalidTick=-1;sourceState=0;sourceAt=0;ownedNotes.clear();noteTrackingError="";taggedNotes=0;
 }
 private static Map<String,Object> sample()throws Exception{var mc=Minecraft.getInstance();var row=new LinkedHashMap<String,Object>();row.put("clientTick",ticks);row.put("atUtcMillis",System.currentTimeMillis());row.put("armed",retained!=null);
  if(retained==null)return row;var access=(PrivateSoundAccess)retained;var state=access.muxiPlayback$state();boolean loaded=mc.level!=null&&mc.level.hasChunkAt(pos);var current=loaded?mc.level.getBlockEntity(pos):null;
  boolean alive=!state.cancelled&&state.lifetime!=null&&mc.getConnection()==armedConnection&&mc.level==armedLevel&&current==owner&&!owner.isRemoved()&&owner.isPlay()&&net.minecraft.world.item.ItemStack.matches((net.minecraft.world.item.ItemStack)state.lifetime.disc(),owner.getPlayerInv().getStackInSlot(0));if(!alive&&invalidTick<0)invalidTick=ticks;
  if(handle!=null&&!handle.isStopped())handle.execute(channel->{try{int source=(int)Entry042Client.field(channel,"source");sourceState=org.lwjgl.openal.AL10.alGetSourcei(source,org.lwjgl.openal.AL10.AL_SOURCE_STATE);sourceAt=System.currentTimeMillis();}catch(Exception ignored){}});
  row.put("soundIdentity",System.identityHashCode(retained));row.put("ownerIdentity",System.identityHashCode(owner));row.put("sameOwner",current==owner);row.put("chunkLoaded",loaded);row.put("soundStopped",retained.isStopped());row.put("engineActive",mc.getSoundManager().isActive(retained));row.put("bindingAlive",alive);row.put("cancelled",state.cancelled);row.put("pcmReady",state.ready);row.put("audioSeconds",state.seconds);row.put("soundLyricsPresent",Entry042Client.field(retained,"lyricRecord")!=null);row.put("ownerLyricsPresent",owner.lyricRecord!=null);row.put("channelReleased",handle!=null&&handle.isStopped());row.put("lastSourceState",sourceState);row.put("lastSourceAtUtcMillis",sourceAt);
  row.put("noteEmissions",emissions);row.put("lastEmissionTick",lastEmissionTick);row.put("invalidTick",invalidTick);
  int notes=0,maxLifetime=0;for(var particle:ownedNotes)if(particle.isAlive()){notes++;maxLifetime=Math.max(maxLifetime,particle.getLifetime());}
  ownedNotes.removeIf(particle->!particle.isAlive());row.put("actualOwnedAliveNotes",notes);row.put("taggedActualNotes",taggedNotes);row.put("noteTrackingErrorType",noteTrackingError);row.put("maxNoteLifetime",maxLifetime);return row;
 }
 private void command()throws Exception{var file=Entry042Scope.root().resolve("coordinator/entry042-lifecycle-command-"+Entry042Scope.role()+".json");if(!Files.exists(file))return;var command=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(!System.getProperty("qa.local.runId").equals(command.get("runId").getAsString()))throw new IllegalStateException("owned_run_required");long next=command.get("id").getAsLong();if(next<=id)return;id=next;String action=command.get("cmd").getAsString();var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("cmd",action);row.put("atUtcMillis",System.currentTimeMillis());
  try{if(action.equals("arm"))arm();else if(action.equals("probe")){}else{if(!Set.of("capture","hold","remove","replace","release","restore","leave","return").contains(action))throw new IllegalArgumentException("owned_lifecycle_command_required");var mc=Minecraft.getInstance();if(mc.getConnection()==null||mc.player==null)throw new IllegalStateException("owned_connection_required");mc.player.connection.sendCommand("entry042-lifecycle "+action+" "+id);}row.put("ok",true);}catch(Exception error){row.put("ok",false);row.put("errorType",error.getClass().getSimpleName());}
  results.add(row);if(results.size()>64)results.remove(0);Entry042Scope.write("entry042-lifecycle-results-"+Entry042Scope.role()+".json",Map.of("runId",System.getProperty("qa.local.runId"),"results",List.copyOf(results)));
 }
 public void tick(ClientTickEvent.Post event){ticks++;if(++writeTicks%2!=0)return;try{Entry042Scope.require();command();Entry042Scope.write("entry042-lifecycle-observed-"+Entry042Scope.role()+".json",Map.of("runId",System.getProperty("qa.local.runId"),"sample",sample(),"physicalAudibilityVerified",false,"newParticlesSuppressedByQa",false));}catch(Entry042Scope.ReceiptWriteFailure retry){}catch(Exception error){Entry042Scope.write("entry042-lifecycle-observed-"+Entry042Scope.role()+".json",Map.of("runId",System.getProperty("qa.local.runId"),"observationErrorType",error.getClass().getSimpleName()));}}
}
