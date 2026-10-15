package net.muxigame.musiccompat.burn;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import java.net.URI;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Lyrics are independent of audio. A cold verified account has its own bounded budget. */
public final class PrivateLyrics {
 private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"Muxi original lyrics");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public static CompletableFuture<LyricRecord> load(URI reference,String title){
  var result=new CompletableFuture<LyricRecord>();var cancel=new BurnCoordinator.Cancellation();var worker=new AtomicReference<Future<?>>();var finished=new AtomicBoolean();
  result.whenComplete((value,error)->{if(!finished.get()){cancel.cancel();var task=worker.get();if(task!=null)task.cancel(true);}});
  try{worker.set(IO.submit(()->{
   if(result.isDone())return;
   try{var mc=net.minecraft.client.Minecraft.getInstance();Object connection=mc.getConnection();var trace=MusicPlaybackMetrics.begin(reference,connection);
    try(var scope=trace.bind()){
     var origin=PrivatePlayback.lyricOrigin(reference,cancel);cancel.check();
     if(result.isDone()||connection==null||mc.getConnection()!=connection){finished.set(true);result.complete(null);return;}
     LyricRecord value;try(var phase=trace.phase("lyrics_platform")){value=PlatformLyrics.fetch(origin.origin(),origin.title());cancel.check();phase.success();}
     if(!result.isDone()&&mc.getConnection()==connection){finished.set(true);result.complete(value);}
    }
   }catch(Exception unavailable){finished.set(true);result.complete(null);}
  }));if(result.isDone()&&!finished.get())worker.get().cancel(true);}catch(RejectedExecutionException busy){finished.set(true);result.complete(null);}
  return result.completeOnTimeout(null,PlaybackBudgets.LYRICS_SECONDS,TimeUnit.SECONDS);
 }
 private PrivateLyrics(){}
}
