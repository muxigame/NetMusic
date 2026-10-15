package net.muxigame.musiccompat.burn;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.*;

/** Executable worker/state tests: reserve first, fail closed, no late DONE or cross-generation abort. */
public final class BurnCoordinatorTransactionTest {
 private static int checks;
 private static void check(boolean value){if(!value)throw new AssertionError("transaction invariant "+checks);checks++;}
 private static final class Harness implements AutoCloseable {
  final Queue<Runnable> game=new ConcurrentLinkedQueue<>();final List<BurnCoordinator.Progress> progress=new ArrayList<>();
  final List<String> events=new CopyOnWriteArrayList<>();final AtomicInteger aborts=new AtomicInteger();
  final BurnCoordinator job;
  Harness(){job=new BurnCoordinator(game::add,new BurnCoordinator.Backend(){
   public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked a,UUID request,BurnCoordinator.Cancellation c){events.add("authorize");return new BurnCoordinator.Ticket(request.toString(),null);}
   public void upload(BurnCoordinator.Ticket t,BurnCoordinator.Checked a,BurnCoordinator.Cancellation c,Consumer<BurnCoordinator.Progress> p){events.add("upload");}
   public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket t,BurnCoordinator.Cancellation c){events.add("verify");return new BurnCoordinator.Receipt(t.jobId(),"receipt","fixture","title",1,"proof");}
   public void cancel(BurnCoordinator.Ticket t){aborts.incrementAndGet();}
  },(p,c)->{},progress::add);}
  void drain(){Runnable action;while((action=game.poll())!=null)action.run();}
  void until(BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(true){drain();if(ready.getAsBoolean())return;if(System.nanoTime()>end)throw new AssertionError("worker_test_timeout");Thread.sleep(5);}}
  boolean has(BurnCoordinator.Stage stage){return progress.stream().anyMatch(p->p.stage()==stage);}
  BurnCoordinator.Source source(){return (cancel,update)->{events.add("acquire");Path p=Files.createTempFile("owned-burn-transaction-", ".wav");Files.write(p,new byte[]{1,2,3});return new BurnCoordinator.Audio(p,"title","OWNED-REDISTRIBUTION","artist");};}
  BurnCoordinator.Commit commit(){return new BurnCoordinator.Commit(){
   public void prepare(UUID request,BurnCoordinator.Cancellation cancel){events.add("reserve");}
   public boolean stillCurrent(){return true;}
   public void send(BurnCoordinator.Receipt receipt){events.add("send");}
  };}
  public void close(){job.close();drain();}
 }
 public static void main(String[] args)throws Exception{
  try(var h=new Harness()){
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));
   check(h.events.equals(List.of("reserve","acquire","authorize","upload","verify","send")));
   check(!h.has(BurnCoordinator.Stage.DONE)&&h.job.running());
   h.job.confirmed(UUID.randomUUID(),true);h.drain();check(!h.has(BurnCoordinator.Stage.DONE));
   h.job.confirmed(id,true);h.drain();check(h.has(BurnCoordinator.Stage.DONE)&&!h.job.running());
   h.job.rejected(id);h.drain();check(!h.has(BurnCoordinator.Stage.FAILED));
  }
  try(var h=new Harness()){
   h.job.start(h.source(),new BurnCoordinator.Commit(){
    public void prepare(UUID id,BurnCoordinator.Cancellation cancel)throws IOException{throw new IOException("burn_context_required");}
    public boolean stillCurrent(){return true;}public void send(BurnCoordinator.Receipt r){throw new AssertionError();}
   });h.until(()->h.has(BurnCoordinator.Stage.FAILED));
   check(h.events.isEmpty());check(!h.job.running());check(h.progress.getLast().code().equals("burn_context_required"));check(!h.has(BurnCoordinator.Stage.ACQUIRING));
  }
  try(var h=new Harness()){
   var entered=new CountDownLatch(1);var blocked=new CountDownLatch(1);var closed=new AtomicBoolean();
   UUID id=h.job.start((cancel,p)->{cancel.bind(()->{closed.set(true);blocked.countDown();});entered.countDown();blocked.await();cancel.check();throw new AssertionError("cancel must stop source");},h.commit());
   h.until(()->entered.getCount()==0);h.job.rejected(id);h.until(()->h.has(BurnCoordinator.Stage.FAILED));
   check(closed.get());check(h.events.equals(List.of("reserve")));check(h.progress.getLast().code().equals("server_commit_failed"));check(!h.job.running());
   check(h.progress.stream().filter(p->p.stage()==BurnCoordinator.Stage.FAILED).count()==1&&!h.has(BurnCoordinator.Stage.CANCELLED));
   UUID retry=h.job.start(h.source(),h.commit());h.job.rejected(id);h.until(()->h.events.contains("send"));
   check(h.job.running());h.job.confirmed(retry,true);h.drain();check(h.has(BurnCoordinator.Stage.DONE));
  }
  try(var h=new Harness()){
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));
   h.job.confirmed(id,false);h.job.rejected(id);h.until(()->h.has(BurnCoordinator.Stage.FAILED)&&h.aborts.get()==1);
   check(h.progress.getLast().code().equals("server_commit_failed"));check(h.aborts.get()==1);check(!h.has(BurnCoordinator.Stage.DONE));
  }
  try(var h=new Harness()){
   var entered=new CountDownLatch(1);UUID id=h.job.start((cancel,p)->{entered.countDown();new CountDownLatch(1).await();throw new AssertionError();},h.commit());
   h.until(()->entered.getCount()==0);h.job.cancel();h.until(()->h.has(BurnCoordinator.Stage.CANCELLED));
   h.job.confirmed(id,true);h.drain();check(!h.has(BurnCoordinator.Stage.DONE));check(!h.job.running());check(!h.events.contains("authorize"));
  }
  try(var h=new Harness()){
   Thread caller=Thread.currentThread();var reserved=new AtomicBoolean();
   UUID id=h.job.start(h.source(),new BurnCoordinator.Commit(){
    public void begin(UUID request){check(Thread.currentThread()==caller);reserved.set(true);h.events.add("begin");}
    public void prepare(UUID request,BurnCoordinator.Cancellation cancel){check(reserved.get());h.events.add("reserve");}
    public boolean stillCurrent(){return true;}public void send(BurnCoordinator.Receipt r){h.events.add("send");}
   });check(reserved.get());h.until(()->h.events.contains("send"));
   check(h.events.equals(List.of("begin","reserve","acquire","authorize","upload","verify","send")));
   h.job.serverSettled(UUID.randomUUID(),true);h.drain();check(h.job.running());
   h.job.cancel();h.drain();check(h.has(BurnCoordinator.Stage.CANCELLED));
   h.job.serverSettled(id,true);h.drain();check(h.has(BurnCoordinator.Stage.DONE)&&!h.job.running());
   h.job.serverSettled(id,false);h.drain();check(!h.has(BurnCoordinator.Stage.FAILED));
  }
  try(var h=new Harness()){
   h.job.start(h.source(),new BurnCoordinator.Commit(){
    public void begin(UUID request)throws IOException{throw new IOException("burn_context_required");}
    public boolean stillCurrent(){return true;}public void send(BurnCoordinator.Receipt r){throw new AssertionError();}
   });h.until(()->h.has(BurnCoordinator.Stage.FAILED));check(h.events.isEmpty());check(!h.job.running());
  }
  try(var h=new Harness()){
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var viewAttached=new AtomicBoolean(true);
   UUID id=h.job.start((cancel,p)->{entered.countDown();release.await();cancel.check();return h.source().acquire(cancel,p);},h.commit());
   h.until(()->entered.getCount()==0);viewAttached.set(false);check(h.job.running());release.countDown();
   h.until(()->h.events.contains("send"));check(!viewAttached.get()&&h.job.running());
   h.job.serverSettled(id,true);h.drain();check(h.has(BurnCoordinator.Stage.DONE));check(h.aborts.get()==0);
  }
  try(var h=new Harness()){
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));h.job.cancel();h.drain();
   check(!h.progress.getLast().discReturned());h.job.serverSettled(id,false);h.drain();
   check(h.progress.getLast().stage()==BurnCoordinator.Stage.CANCELLED&&h.progress.getLast().discReturned());
   h.job.serverSettled(id,true);h.drain();check(!h.has(BurnCoordinator.Stage.DONE));
  }
  try(var h=new Harness()){
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));h.job.rejected(id);h.drain();
   check(!h.progress.getLast().discReturned());h.job.serverSettled(id,false);h.drain();
   check(h.progress.getLast().stage()==BurnCoordinator.Stage.FAILED&&h.progress.getLast().discReturned());
   check(h.progress.getLast().code().equals("server_commit_failed"));
  }
  // A failed commit can race the server's successful write. Losing its first
  // reply must leave read-only settlement queries enabled, without reupload.
  try(var h=new Harness()){
   check(!h.job.needsServerSettlement());
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));
   h.job.rejected(id);h.drain();check(h.has(BurnCoordinator.Stage.FAILED));
   check(h.job.needsServerSettlement());
   h.job.serverSettled(UUID.randomUUID(),true);h.drain();check(h.job.needsServerSettlement());
   h.job.serverSettled(id,true);h.drain();
   check(h.progress.getLast().stage()==BurnCoordinator.Stage.DONE);
   check(!h.job.needsServerSettlement());
   check(h.events.stream().filter("send"::equals).count()==1);
  }
  try(var h=new Harness()){
   UUID id=h.job.start(h.source(),h.commit());h.until(()->h.events.contains("send"));
   h.job.rejected(id);h.drain();check(h.job.needsServerSettlement());
   h.job.serverSettled(id,false);h.drain();
   check(h.progress.getLast().discReturned());check(!h.job.needsServerSettlement());
  }
  // Repeat cancellation cannot close an already captured resource twice.
  var cancellation=new BurnCoordinator.Cancellation();var closedResource=new AtomicInteger();
  cancellation.bind(closedResource::incrementAndGet);cancellation.cancel();cancellation.cancel();
  check(closedResource.get()==1);
  try{cancellation.bind(()->{});throw new AssertionError("bind after cancel");}
  catch(InterruptedIOException expected){check(true);}
  System.out.println("{\"workerTransactionChecks\":"+checks+",\"cloudCalls\":0,\"mcStarted\":false,\"serverLifecycleTested\":false}");
 }
}
