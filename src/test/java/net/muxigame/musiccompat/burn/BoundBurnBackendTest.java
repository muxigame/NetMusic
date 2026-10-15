package net.muxigame.musiccompat.burn;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Offline regression of old-abort/new-retry ownership, no MC or provider. */
public final class BoundBurnBackendTest {
    private static void check(boolean value){if(!value)throw new AssertionError("attempt ownership failed");}
    public static void main(String[] args)throws Exception{
        List<String> events=new ArrayList<>();AtomicReference<String> context=new AtomicReference<>();
        BoundBurnBackend backend=new BoundBurnBackend((audio,id,cancel)->{
            String request=id.toString();context.set(request);
            BurnCoordinator.Backend owner=new BurnCoordinator.Backend(){
                public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked a,UUID r,BurnCoordinator.Cancellation c){throw new AssertionError();}
                public void upload(BurnCoordinator.Ticket t,BurnCoordinator.Checked a,BurnCoordinator.Cancellation c,Consumer<BurnCoordinator.Progress> p){events.add("upload:"+request);check(t.transport().equals(request));}
                public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket t,BurnCoordinator.Cancellation c){events.add("verify:"+request);return new BurnCoordinator.Receipt(t.jobId(),"receipt","stable","track",1);}
                public void cancel(BurnCoordinator.Ticket t){events.add("cancel:"+request);check(t.transport().equals(request));}
            };
            return new BoundBurnBackend.Binding(owner,new BurnCoordinator.Ticket(request,request),()->context.compareAndSet(request,null));
        });
        BurnCoordinator.Cancellation cancel=new BurnCoordinator.Cancellation();
        var first=backend.authorize(null,UUID.randomUUID(),cancel);var second=backend.authorize(null,UUID.randomUUID(),cancel);
        backend.cancel(first);check(context.get().equals(second.jobId()));
        backend.upload(second,null,cancel,p->{});check(events.get(1).equals("upload:"+second.jobId()));
        check(backend.verify(second,cancel).jobId().equals(second.jobId()));
        backend.cancel(second);check(context.get()==null);
        try{backend.cancel(new BurnCoordinator.Ticket(first.jobId(),second.transport()));throw new AssertionError();}catch(IOException expected){}
        try{backend.verify(new BurnCoordinator.Ticket(first.jobId(),null),cancel);throw new AssertionError();}catch(IOException expected){}
        check(events.equals(List.of("cancel:"+first.jobId(),"upload:"+second.jobId(),"verify:"+second.jobId(),"cancel:"+second.jobId())));
        System.out.println("{\"attemptBindingCases\":6,\"offlineOnly\":true,\"nativeMcTested\":false}");
    }
}
