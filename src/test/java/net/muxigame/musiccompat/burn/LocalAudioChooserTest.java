package net.muxigame.musiccompat.burn;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Injected native-dialog fixtures only: never opens a real window or MC. */
public final class LocalAudioChooserTest {
    private static int checks;
    private static void check(boolean condition) { if(!condition)throw new AssertionError("chooser check "+checks);checks++; }
    private static final class QueueExecutor implements Executor {
        final Queue<Runnable> work=new ArrayDeque<>();
        public void execute(Runnable value){work.add(value);}
        void drain(){while(!work.isEmpty())work.remove().run();}
    }
    public static void main(String[] args)throws Exception {
        check(!LocalAudioChooser.active());
        AtomicInteger audioJobs=new AtomicInteger();
        for(String value:new String[]{null,""," \r\n\t"}) {
            QueueExecutor game=new QueueExecutor(),worker=new QueueExecutor();
            AtomicInteger delivered=new AtomicInteger();
            var chooser=new LocalAudioChooser(game,worker,()->value);
            check(chooser.choose(path->{check(path==null);delivered.incrementAndGet();if(path!=null)audioJobs.incrementAndGet();}));
            check(LocalAudioChooser.active());
            check(delivered.get()==0);worker.drain();check(delivered.get()==0);check(LocalAudioChooser.active());
            game.drain();check(delivered.get()==1);check(!LocalAudioChooser.active());
        }
        check(audioJobs.get()==0);

        QueueExecutor game=new QueueExecutor(),worker=new QueueExecutor();
        AtomicInteger dialogs=new AtomicInteger(),callbacks=new AtomicInteger();
        AtomicReference<String> nativeThread=new AtomicReference<>(),callbackThread=new AtomicReference<>();
        var first=new LocalAudioChooser(game,worker,()->{dialogs.incrementAndGet();nativeThread.set(Thread.currentThread().getName());return "selected audio.m4a";});
        var other=new LocalAudioChooser(game,worker,()->{dialogs.incrementAndGet();return "other.wav";});
        check(first.choose(path->{check(path.equals(Path.of("selected audio.m4a")));callbacks.incrementAndGet();callbackThread.set(Thread.currentThread().getName());}));
        check(!first.choose(path->callbacks.incrementAndGet()));check(!other.choose(path->callbacks.incrementAndGet()));
        Thread background=new Thread(worker::drain,"offline-dialog-worker");background.start();background.join(2000);
        check(!background.isAlive());check(dialogs.get()==1);check(callbacks.get()==0);
        check(!other.choose(path->callbacks.incrementAndGet())); // Main-thread delivery is still pending.
        game.drain();check(callbacks.get()==1);check(nativeThread.get().equals("offline-dialog-worker"));
        check(callbackThread.get().equals(Thread.currentThread().getName()));
        check(other.choose(path->callbacks.incrementAndGet()));worker.drain();game.drain();check(dialogs.get()==2);

        AtomicInteger lateJobs=new AtomicInteger();
        long[] lifecycle={4};boolean[] closed={false},visible={true};
        var late=new LocalAudioChooser(game,worker,()->"late.wav");final long request=lifecycle[0];
        check(late.choose(path->{if(BurnScreenRuntime.selectionCurrent(request,lifecycle[0],closed[0],visible[0])&&path!=null)lateJobs.incrementAndGet();}));
        worker.drain();closed[0]=true;visible[0]=false;lifecycle[0]++;game.drain();check(lateJobs.get()==0);
        // Reopening the same screen object must not revive an old request.
        final long reopenedRequest=lifecycle[0];
        closed[0]=false;visible[0]=true;
        check(late.choose(path->{if(BurnScreenRuntime.selectionCurrent(reopenedRequest,lifecycle[0],closed[0],visible[0])&&path!=null)lateJobs.incrementAndGet();}));
        worker.drain();lifecycle[0]+=2;game.drain();check(lateJobs.get()==0);

        for(LocalAudioChooser.Dialog dialog:new LocalAudioChooser.Dialog[]{()->{throw new IllegalStateException("offline failure");},()->"\u0000invalid"}) {
            var unavailable=new LocalAudioChooser(game,worker,dialog);
            check(unavailable.choose(path->{check(path==null);if(path!=null)audioJobs.incrementAndGet();}));worker.drain();game.drain();
        }
        check(audioJobs.get()==0);
        Executor rejected=command->{throw new RejectedExecutionException();};
        check(!new LocalAudioChooser(game,rejected,()->"never.wav").choose(path->audioJobs.incrementAndGet()));
        check(new LocalAudioChooser(rejected,worker,()->"lost.wav").choose(path->audioJobs.incrementAndGet()));worker.drain();
        check(first.choose(path->{}));worker.drain();game.drain();
        check(first.choose(path->{throw new IllegalStateException("offline callback failure");}));worker.drain();
        try {game.drain();throw new AssertionError();}catch(IllegalStateException expected){checks++;}
        check(first.choose(path->{}));worker.drain();game.drain();
        System.out.println("{\"chooserChecks\":"+checks+",\"realDialogsOpened\":0,\"audioJobsOnCancel\":"+audioJobs.get()+",\"lateAudioJobs\":"+lateJobs.get()+",\"mcStarted\":false}");
    }
}
