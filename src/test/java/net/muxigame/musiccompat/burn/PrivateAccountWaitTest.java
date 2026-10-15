package net.muxigame.musiccompat.burn;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.io.*;
public final class PrivateAccountWaitTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("account wait "+checks);}
 static long deadline(){return System.nanoTime()+2_000_000_000L;}
 public static void main(String[] args)throws Exception{
  var cached=new AtomicReference<String>();var scheduler=Executors.newSingleThreadScheduledExecutor();
  try{
   // Real empty completion must not beat a delayed verified same-connection account.
   scheduler.schedule(()->cached.set("verified"),75,TimeUnit.MILLISECONDS);
   String result=PrivateAccountWait.await(cached::get,CompletableFuture.completedFuture(""),body->{throw new AssertionError();},()->true,new BurnCoordinator.Cancellation(),deadline());check(result.equals("verified"));
   cached.set(null);check(PrivateAccountWait.await(cached::get,CompletableFuture.completedFuture("approved"),body->body,()->true,new BurnCoordinator.Cancellation(),deadline()).equals("approved"));
   cached.set("verified");check(PrivateAccountWait.await(cached::get,new CompletableFuture<>(),body->body,()->true,new BurnCoordinator.Cancellation(),deadline()).equals("verified"));
   boolean refused=false;try{PrivateAccountWait.await(cached::get,new CompletableFuture<>(),body->body,()->false,new BurnCoordinator.Cancellation(),deadline());}catch(IOException error){refused=error.getMessage().equals("live_music_connection_required");}check(refused);
   var cancel=new BurnCoordinator.Cancellation();cancel.cancel();refused=false;try{PrivateAccountWait.await(cached::get,new CompletableFuture<>(),body->body,()->true,cancel,deadline());}catch(InterruptedIOException error){refused=true;}check(refused);
   cached.set(null);refused=false;try{PrivateAccountWait.await(cached::get,CompletableFuture.completedFuture(""),body->body,()->true,new BurnCoordinator.Cancellation(),System.nanoTime()+60_000_000L);}catch(IOException error){refused=error.getMessage().equals("music_account_timeout");}check(refused);
   var flights=new PrivateAccountWait.Flights();var connection=new Object();var calls=new AtomicInteger();var pool=Executors.newFixedThreadPool(4);try{
    var list=new java.util.ArrayList<Future<CompletableFuture<String>>>();for(int i=0;i<20;i++)list.add(pool.submit(()->flights.get(connection,future->calls.incrementAndGet())));
    var first=list.get(0).get();for(var request:list)check(request.get()==first);check(calls.get()==1);
    var next=flights.get(new Object(),future->calls.incrementAndGet());check(next!=first&&calls.get()==2);first.complete("old");check(!next.isDone());
   }finally{pool.shutdownNow();}
   scheduler.schedule(()->cached.set("late-valid"),75,TimeUnit.MILLISECONDS);var failure=new CompletableFuture<String>();failure.completeExceptionally(new IOException("provider-private-secret"));check(PrivateAccountWait.await(cached::get,failure,body->body,()->true,new BurnCoordinator.Cancellation(),deadline()).equals("late-valid"));
  }finally{scheduler.shutdownNow();}
  System.out.println("{\"accountWaitChecks\":"+checks+",\"emptyCallbackBeforeVerifiedAccount\":true,\"coalescedRequests\":true,\"mcStarted\":false}");
 }
}
