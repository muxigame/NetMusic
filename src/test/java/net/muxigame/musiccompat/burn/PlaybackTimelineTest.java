package net.muxigame.musiccompat.burn;
import java.util.*;import java.io.*;import java.nio.*;import javax.sound.sampled.*;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
public final class PlaybackTimelineTest {
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("timeline check "+checks);}static void close(double a,double b){check(Math.abs(a-b)<.001);}
 static LyricRecord lyric(){var a=new Int2ObjectAVLTreeMap<String>();var b=new Int2ObjectAVLTreeMap<String>();for(int t:new int[]{0,100,300,600,900}){a.put(t,"Owned "+t);b.put(t,"Owned translation "+t);}return new LyricRecord(a,b);}
 static AudioInputStream pcm(byte[] b){return new AudioInputStream(new ByteArrayInputStream(b),new AudioFormat(8000,16,2,true,false),b.length/4);}
 public static void main(String[] args)throws Exception{
  var host=UUID.randomUUID();var guest=UUID.randomUUID();var clock=new PlaybackTimeline(60,1000,0);clock.elect(host);clock.elect(guest);
  check(!clock.prepared());check(!clock.report(guest,clock.epoch,1,true,1020));check(!clock.report(host,UUID.randomUUID().toString(),1,true,1020));
  check(clock.report(host,clock.epoch,.85,true,1036));check(clock.prepared());close(clock.snapshot(1046).seconds(),1.35);
  // Native r5: audio .85 froze while old lyric tick 36->136. Drive actual lyric map with the new clock.
  var state=new PrivateSoundState(0,false,60);var record=lyric();check(state.lyricTick()==0);state.sample(.85,false);record.updateCurrentLine(state.lyricTick());int pausedTick=state.lyricTick();
  check(clock.report(host,clock.epoch,.85,false,1046));for(int i=0;i<100;i++){state.sample(.85,true);record.updateCurrentLine(state.lyricTick());check(state.lyricTick()==pausedTick);check(record.getLyrics().firstIntKey()==0);check(record.getTransLyrics().firstIntKey()==0);}
  close(clock.snapshot(1146).seconds(),.85);state.sample(1.1,false);check(clock.report(host,clock.epoch,1.1,true,1151));close(clock.snapshot(1171).seconds(),2.1);
  check(!clock.report(host,clock.epoch,.1,true,1172));check(!clock.report(host,clock.epoch,Double.NaN,true,1172));check(!clock.report(host,clock.epoch,61,true,1172));
  // Guest reconnect obtains the host's pause-adjusted point. Existing host state is never mutated.
  double before=state.seconds;var point=clock.snapshot(1201);var rejoined=new PrivateSoundState(point.seconds(),false,60);close(point.seconds(),3.6);close(state.seconds,before);check(rejoined!=state);
  var next=new PlaybackTimeline(60,1201,0);next.elect(host);check(!next.epoch.equals(clock.epoch));check(!next.report(host,clock.epoch,4,true,1202));
  state.cancelled=true;state.sample(40,false);close(state.seconds,before);
  // Actual PCM discard/read/queue accounting, not a mock seek implementation.
  byte[] bytes=new byte[8000*4*4];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i%251);
  var pcmState=new PrivateSoundState(1.23456,false,60);try(var stream=new PrivatePcmStream(pcm(bytes),pcmState)){
   long frames=(long)(1.23456*8000);close(stream.base,frames/8000.0);var data=stream.read(103);check(data.isDirect());check(data.remaining()==100);for(int i=0;i<100;i++)check(data.get()==bytes[(int)frames*4+i]);
   var second=stream.read(4000);check(second.remaining()==4000);stream.processed(1);close(stream.position(.125),stream.base+100/32000.0+.125);stream.processed(1);close(stream.position(0),stream.base+4100/32000.0);
   while(stream.read(4096)!=null){}check(stream.read(4096)==null);
  }
  boolean failed=false;try(var stream=new PrivatePcmStream(pcm(bytes),new PrivateSoundState(5,false,60))){}catch(EOFException e){failed=true;}check(failed);
  var cancelled=new PrivateSoundState(1,false,60);cancelled.cancelled=true;failed=false;try(var stream=new PrivatePcmStream(pcm(bytes),cancelled)){}catch(IOException e){failed=true;}check(failed);
  try(var stream=new PrivatePcmStream(pcm(bytes),new PrivateSoundState(0,false,60))){failed=false;try{stream.read(1);}catch(IOException e){failed=true;}check(failed);stream.close();failed=false;try{stream.read(100);}catch(IOException e){failed=true;}check(failed);}
  var restored=new PlaybackTimeline(60,1000,30);restored.elect(host);close(restored.snapshot(1100).seconds(),35);check(restored.report(host,restored.epoch,35,false,1100));close(restored.snapshot(2000).seconds(),35);
  // Cold follower receives the same epoch's real leader point before its first queued PCM.
  var cold=new PrivateSoundState(0,false,60);try(var stream=new PrivatePcmStream(pcm(bytes),cold)){
   cold.updateTarget(2.0001,false,true);var first=stream.read(100);close(stream.base,2);check(cold.seekCommitted);for(int i=0;i<100;i++)check(first.get()==bytes[64000+i]);
   cold.updateTarget(3,false,true);close(cold.seekTarget(),2.0001); // committed audio cannot be silently rewound/retargeted
  }
  var pausedJoin=new PrivateSoundState(2,false,60);pausedJoin.followPause=true;pausedJoin.timelinePaused=true;pausedJoin.updateTarget(2,false,true);check(pausedJoin.timelinePaused);pausedJoin.updateTarget(2,true,true);check(!pausedJoin.timelinePaused);
  var election=new PlaybackTimeline(60,1000,0);election.elect(host);check(election.report(host,election.epoch,2,true,1040));election.release(guest);check(host.equals(election.leader()));election.release(host);check(election.leader()==null);election.elect(guest);check(guest.equals(election.leader()));check(election.report(guest,election.epoch,2.5,true,1050));close(election.snapshot(1070).seconds(),3.5);check(!election.report(guest,election.epoch,10,true,1071));
  // Exercise the actual registered payload codecs, including prepared versus paused.
  // A silent, still-connected elected listener cannot strand late joiners forever.
  var stalled=new PlaybackTimeline(60,1000,0);stalled.elect(host);
  check(!stalled.report(guest,stalled.epoch,4.9,true,1099));
  check(!stalled.report(guest,stalled.epoch,Double.NaN,true,1100));check(host.equals(stalled.leader()));
  check(!stalled.report(guest,UUID.randomUUID().toString(),5,true,1100));check(host.equals(stalled.leader()));
  check(stalled.report(guest,stalled.epoch,5,true,1100));check(guest.equals(stalled.leader()));
  check(!stalled.report(host,stalled.epoch,5.2,true,1104));close(stalled.snapshot(1120).seconds(),6);
  check(stalled.report(guest,stalled.epoch,6,false,1120));
  check(!stalled.report(host,stalled.epoch,6,false,1219));
  check(!stalled.report(host,stalled.epoch,5,false,1220));check(guest.equals(stalled.leader()));
  check(stalled.report(host,stalled.epoch,6,false,1220));check(host.equals(stalled.leader()));
  close(stalled.snapshot(1240).seconds(),6);
  check(!stalled.report(guest,stalled.epoch,6,false,1210));check(host.equals(stalled.leader()));
  check(!stalled.report(null,stalled.epoch,6,false,1320));check(host.equals(stalled.leader()));
  var ref="https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/ea8bbe98-43f0-462c-bd51-fec644838448.mp3";
  var pos=new net.minecraft.core.BlockPos(2,-60,0);
  var packet=new PlaybackNetwork.State("minecraft:overworld",pos,clock.epoch,ref,"Owned",60,42.51,false,true,true);
  var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{PlaybackNetwork.State.CODEC.encode(wire,packet);var decoded=PlaybackNetwork.State.CODEC.decode(wire);check(packet.equals(decoded));check(decoded.prepared()&&!decoded.moving()&&decoded.active());check(wire.readableBytes()==0);}finally{wire.release();}
  wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{var report=new PlaybackNetwork.Report(pos,clock.epoch,42.51,true);PlaybackNetwork.Report.CODEC.encode(wire,report);check(report.equals(PlaybackNetwork.Report.CODEC.decode(wire)));check(wire.readableBytes()==0);}finally{wire.release();}
  var translated=lyric();translated.updateCurrentLine(861);check(translated.getLyrics().firstIntKey()==600);check(translated.getTransLyrics().firstIntKey()==600);
  System.out.println("{\"timelineAndDecodedPcmChecks\":"+checks+",\"nativePauseTraceReplayed\":true,\"realLyricRecord\":true,\"realPcmStream\":true,\"actualPayloadCodec\":true,\"mcStarted\":false,\"networkRequests\":0}");
 }
}
