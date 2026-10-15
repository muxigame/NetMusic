package net.muxigame.musiccompat.burn;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the real queued future boundary used by the resolver, without Minecraft or network. */
public final class PlaybackLifecycleTest {
 private static int checks,callbacks;
 private static void check(boolean valid){checks++;if(!valid)throw new AssertionError("lifecycle_check_"+checks);}
 private static final class Queue implements Executor {
  final ArrayDeque<Runnable> work=new ArrayDeque<>();
  public void execute(Runnable value){work.addLast(value);}
  void drain(){while(!work.isEmpty())work.removeFirst().run();}
 }
 private static final class Scene {
  Object connection=new Object(),level=new Object(),owner=new Object(),disc=new Object();
  String reference="private:one";boolean removed,present=true,sameDisc=true,playing=true;
  PlaybackLifecycle.Binding bind(){return new PlaybackLifecycle.Binding(connection,level,owner,disc,reference);}
  boolean current(PlaybackLifecycle.Binding b){return b.current(connection,level,owner,reference,sameDisc,removed,playing);}
  PlaybackLifecycle.Generation.Ticket begin(PlaybackLifecycle.Generation g){return g.begin(level,owner,disc,reference);}
  boolean current(PlaybackLifecycle.Generation g,PlaybackLifecycle.Generation.Ticket t){return g.current(t,level,owner,disc,reference,removed,present,sameDisc);}
 }
 private static void bindingCases(){
  for(int fault=0;fault<7;fault++){
   var s=new Scene();var b=s.bind();check(s.current(b));
   var oldConnection=s.connection;var oldLevel=s.level;var oldOwner=s.owner;var oldReference=s.reference;
   switch(fault){case 0->s.connection=new Object();case 1->s.level=new Object();case 2->s.owner=new Object();case 3->s.reference="private:two";case 4->s.sameDisc=false;case 5->s.removed=true;case 6->s.playing=false;}
   check(!s.current(b));
   s.connection=oldConnection;s.level=oldLevel;s.owner=oldOwner;s.reference=oldReference;s.sameDisc=true;s.removed=false;s.playing=true;
   check(!s.current(b)); // A later matching URL or replacement never revives this sound.
  }
  var s=new Scene();var b=s.bind();b.retire();check(!s.current(b));
 }
 private static void queuedCallbacks(){
  for(int fault=0;fault<10;fault++){
   var s=new Scene();var g=new PlaybackLifecycle.Generation();var ticket=s.begin(g);
   var queue=new Queue();var writes=new AtomicInteger();var source=new CompletableFuture<String>();
   var completion=source.thenAcceptAsync(value->{callbacks++;if(s.current(g,ticket))writes.incrementAndGet();},queue);
   source.complete("resolved");check(!completion.isDone());check(writes.get()==0);
   switch(fault){
    case 1->s.removed=true;case 2->s.owner=new Object();case 3->s.level=new Object();case 4->s.disc=new Object();
    case 5->s.reference="private:two";case 6->s.sameDisc=false;case 7->s.present=false;
    case 8->s.begin(g); // Same disc, same reference, newer request must supersede the queued callback.
    case 9->g.invalidate(); // Stop/reinsert before callback, even if the exact same disc object returns.
   }
   queue.drain();completion.join();check(writes.get()==(fault==0?1:0));
  }
  var s=new Scene();var g=new PlaybackLifecycle.Generation();var old=s.begin(g);var newer=s.begin(g);
  check(!s.current(g,old));check(s.current(g,newer));
 }
 private static void cancellation(){
  var s=new Scene();var state=new PrivateSoundState(0,false,100);state.lifetime=s.bind();state.sample(5,false);
  check(state.ready&&state.lyricTick()==100);state.cancel();check(state.cancelled);check(!s.current(state.lifetime));
  state.sample(25,false);check(state.seconds==5&&state.lyricTick()==100);
  var pending=new CompletableFuture<String>();check(pending.cancel(false));check(pending.isCancelled());check(!pending.complete("late lyric"));
 }
 private static void legacySoundPaths(){
  var gate=new PlaybackLifecycle.StartGate(100);
  check(gate.observe(true,false,100)==PlaybackLifecycle.StartGate.Decision.WAIT);
  check(gate.observe(true,false,2_000_000_100L)==PlaybackLifecycle.StartGate.Decision.WAIT);
  check(gate.observe(true,true,2_000_000_101L)==PlaybackLifecycle.StartGate.Decision.RUN);
  check(gate.observe(true,false,2_000_000_102L)==PlaybackLifecycle.StartGate.Decision.STOP);
  check(gate.observe(true,true,2_000_000_103L)==PlaybackLifecycle.StartGate.Decision.STOP);
  var timedOut=new PlaybackLifecycle.StartGate(100);
  check(timedOut.observe(true,false,3_000_000_100L)==PlaybackLifecycle.StartGate.Decision.STOP);
  check(timedOut.observe(true,true,3_000_000_101L)==PlaybackLifecycle.StartGate.Decision.STOP);
  var changed=new PlaybackLifecycle.StartGate(100);
  check(changed.observe(false,false,101)==PlaybackLifecycle.StartGate.Decision.STOP);
  check(changed.observe(true,true,102)==PlaybackLifecycle.StartGate.Decision.STOP);
  String media="https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/ea8bbe98-43f0-462c-bd51-fec644838448.mp3";
  var automatic=PlaybackLifecycle.initialPrivateState(true,media,60);
  check(automatic!=null&&!automatic.cancelled&&automatic.target==0&&automatic.duration==60);
  check(PlaybackLifecycle.initialPrivateState(true,"https://example.com/original.mp3",60)==null);
  check(PlaybackLifecycle.initialPrivateState(false,media,60)==null);
  check(PlaybackLifecycle.initialPrivateState(true,media,0).cancelled);
  check(PlaybackLifecycle.initialPrivateState(true,media,14401).cancelled);
  // Both the canonical and the original/nonprivate standard path must pass the
  // same gate before upstream tick can emit NOTE or write the old lyric record.
  for(String reference:new String[]{media,"https://example.com/original.mp3"}){
   var scene=new Scene();scene.reference=reference;var binding=scene.bind();
   var notes=new AtomicInteger();var stops=new AtomicInteger();
   Runnable tick=()->PlaybackLifecycle.tick(true,()->scene.current(binding),stops::incrementAndGet,notes::incrementAndGet);
   tick.run();check(notes.get()==1&&stops.get()==0);
   scene.sameDisc=false; // Disc extracted while stale isPlay=true remains client-side.
   for(int i=0;i<40;i++)tick.run();check(notes.get()==1&&stops.get()==40);
   scene.sameDisc=true;tick.run();check(notes.get()==1); // reinserting cannot revive old sound
  }
  var original=new AtomicInteger();
  PlaybackLifecycle.tick(false,()->{throw new AssertionError("unrelated subclass inspected");},()->{throw new AssertionError("unrelated subclass stopped");},original::incrementAndGet);
  check(original.get()==1);
 }
 public static void main(String[] args){bindingCases();queuedCallbacks();cancellation();legacySoundPaths();System.out.println("{\"checks\":"+checks+",\"queuedCallbacks\":"+callbacks+",\"minecraftStarted\":false,\"networkRequests\":0}");}
}
