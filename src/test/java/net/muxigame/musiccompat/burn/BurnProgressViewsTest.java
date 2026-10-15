package net.muxigame.musiccompat.burn;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.nio.file.*;

/** Actual production presentation subscriptions, including replay after a detached view. */
public final class BurnProgressViewsTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("view invariant "+checks);checks++;}
    private static void backgroundCompletion(BurnCoordinator.Stage closeAt)throws Exception{
        var queue=new ConcurrentLinkedQueue<Runnable>();
        var shown=new AtomicReference<Object>();var page=new Object();shown.set(page);
        var views=new BurnProgressViews<Object>(v->shown.get()==v);
        var closed=new AtomicBoolean();var sent=new AtomicBoolean();var late=new AtomicInteger();
        views.attach(page,p->{
            if(closed.get())late.incrementAndGet();
            if(p.stage()==closeAt){closed.set(true);shown.set(null);views.detach(page);}
        });
        var backend=new BurnCoordinator.Backend(){
            public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked a,UUID id,BurnCoordinator.Cancellation c){return new BurnCoordinator.Ticket(id.toString(),null);}
            public void upload(BurnCoordinator.Ticket t,BurnCoordinator.Checked a,BurnCoordinator.Cancellation c,java.util.function.Consumer<BurnCoordinator.Progress> p){}
            public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket t,BurnCoordinator.Cancellation c){return new BurnCoordinator.Receipt(t.jobId(),"receipt","fixture","title",1,"proof");}
            public void cancel(BurnCoordinator.Ticket t){throw new AssertionError("closing a view cancelled its task");}
        };
        try(var job=new BurnCoordinator(queue::add,backend,(p,c)->{},views::publish)){
            UUID id=job.start((c,p)->{Path file=Files.createTempFile("music-close-phase-",".wav");Files.write(file,new byte[]{1,2,3});return new BurnCoordinator.Audio(file,"title","OWNED-REDISTRIBUTION","artist");},new BurnCoordinator.Commit(){
                public boolean stillCurrent(){return true;}
                public void send(BurnCoordinator.Receipt receipt){sent.set(true);}
            });
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!sent.get()){
                Runnable next;while((next=queue.poll())!=null)next.run();
                if(System.nanoTime()>end)throw new AssertionError("close phase timeout: "+closeAt);
                Thread.sleep(1);
            }
            check(closed.get());check(job.running());check(late.get()==0);
            job.serverSettled(id,true);Runnable next;while((next=queue.poll())!=null)next.run();
            check(!job.running());check(late.get()==0);
            var reopened=new Object();var replay=new ArrayList<BurnCoordinator.Progress>();shown.set(reopened);views.attach(reopened,replay::add);
            check(replay.size()==1&&replay.getFirst().stage()==BurnCoordinator.Stage.DONE&&replay.getFirst().generation().equals(id));
        }
    }
    public static void main(String[] args)throws Exception{
        AtomicReference<Object> visible=new AtomicReference<>();
        var views=new BurnProgressViews<Object>(v->visible.get()==v);
        var first=new Object();var reopened=new Object();
        List<BurnCoordinator.Progress> oldRows=new ArrayList<>(),newRows=new ArrayList<>();
        UUID request=UUID.randomUUID();
        visible.set(first);views.attach(first,oldRows::add);
        for(int phase=0;phase<3;phase++){
            var pending=new BurnCoordinator.Progress(request,BurnCoordinator.Stage.UPLOADING,phase,3,"");
            views.publish(pending);check(oldRows.getLast()==pending);
            views.detach(first);visible.set(null);
            var background=new BurnCoordinator.Progress(request,BurnCoordinator.Stage.VERIFYING,0,0,"");
            int oldCount=oldRows.size();views.publish(background);check(oldRows.size()==oldCount);
            visible.set(reopened);views.attach(reopened,newRows::add);check(newRows.getLast()==background);
            var done=new BurnCoordinator.Progress(request,BurnCoordinator.Stage.DONE,0,0,"");
            views.publish(done);check(newRows.getLast()==done&&oldRows.size()==oldCount);
            views.detach(reopened);visible.set(first);views.attach(first,oldRows::add);check(oldRows.getLast()==done);
        }
        views.reset();int before=newRows.size();visible.set(reopened);views.attach(reopened,newRows::add);check(newRows.size()==before);
        var next=new BurnCoordinator.Progress(UUID.randomUUID(),BurnCoordinator.Stage.PREPARING,0,0,"");views.publish(next);
        check(newRows.getLast()==next&&newRows.getLast().generation()!=request);
        views.clear();before=newRows.size();views.publish(next);check(newRows.size()==before);
        for(var phase:List.of(BurnCoordinator.Stage.PREPARING,BurnCoordinator.Stage.ACQUIRING,BurnCoordinator.Stage.CHECKING,BurnCoordinator.Stage.AUTHORIZING,BurnCoordinator.Stage.UPLOADING,BurnCoordinator.Stage.VERIFYING,BurnCoordinator.Stage.COMMITTING))backgroundCompletion(phase);
        System.out.println("{\"productionViewDetachReplayChecks\":"+checks+",\"actualWorkerClosePhases\":7,\"nativeGuiTested\":false}");
    }
}
