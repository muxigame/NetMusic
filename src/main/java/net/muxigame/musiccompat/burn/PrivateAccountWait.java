package net.muxigame.musiccompat.burn;
import java.io.IOException;import java.util.concurrent.*;import java.util.function.*;
/** An empty passport callback may precede a verified same-connection account from another caller. */
public final class PrivateAccountWait {
 @FunctionalInterface public interface Decoder<T>{T decode(String body)throws Exception;}
 public static <T>T await(Supplier<T> cache,CompletableFuture<String> reply,Decoder<T> decode,BooleanSupplier live,BurnCoordinator.Cancellation cancel,long deadline)throws Exception{
  boolean consumed=false;
  for(;;){
   cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");
   T known=cache.get();if(known!=null)return known;
   if(System.nanoTime()>=deadline)throw new IOException("music_account_timeout");
   if(!consumed&&reply.isDone()){
    consumed=true;String body=null;try{body=reply.getNow(null);}catch(CompletionException|CancellationException unavailable){}
    if(body!=null&&!body.isBlank())return decode.decode(body);
   }
   if(!consumed)try{reply.get(50,TimeUnit.MILLISECONDS);}catch(TimeoutException|ExecutionException|CancellationException pending){}
   else Thread.sleep(50);
  }
 }
 public static final class Flights {
  private Object connection;private CompletableFuture<String> reply;private long until;
  public synchronized CompletableFuture<String> get(Object key,Consumer<CompletableFuture<String>> request){
   if(key==null)throw new IllegalArgumentException("connection_required");long now=System.nanoTime();
   if(connection==key&&reply!=null&&now<until)return reply;
   connection=key;reply=new CompletableFuture<>();until=now+PlaybackBudgets.ACCOUNT_NANOS;request.accept(reply);return reply;
  }
 }
 private PrivateAccountWait(){}
}
