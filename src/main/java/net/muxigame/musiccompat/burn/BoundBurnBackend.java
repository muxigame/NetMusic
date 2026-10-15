package net.muxigame.musiccompat.burn;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

/** Every ticket owns its backend and server context, including delayed aborts. */
public final class BoundBurnBackend implements BurnCoordinator.Backend {
    public record Binding(BurnCoordinator.Backend backend,BurnCoordinator.Ticket ticket,Runnable cancelContext) {}
    public interface Authorizer {
        Binding authorize(BurnCoordinator.Checked audio,UUID request,BurnCoordinator.Cancellation cancel)throws Exception;
    }
    private final Authorizer authorizer;
    public BoundBurnBackend(Authorizer authorizer){this.authorizer=authorizer;}
    public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked audio,UUID request,BurnCoordinator.Cancellation cancel)throws Exception{
        Binding binding=authorizer.authorize(audio,request,cancel);
        if(binding==null||binding.backend()==null||binding.ticket()==null||binding.cancelContext()==null)throw new IOException("invalid_upload_binding");
        return new BurnCoordinator.Ticket(binding.ticket().jobId(),binding);
    }
    private static Binding binding(BurnCoordinator.Ticket ticket)throws IOException{
        if(!(ticket.transport() instanceof Binding value)||!ticket.jobId().equals(value.ticket().jobId()))throw new IOException("invalid_upload_binding");
        return value;
    }
    public void upload(BurnCoordinator.Ticket ticket,BurnCoordinator.Checked audio,BurnCoordinator.Cancellation cancel,Consumer<BurnCoordinator.Progress> progress)throws Exception{
        Binding value=binding(ticket);value.backend().upload(value.ticket(),audio,cancel,progress);
    }
    public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket ticket,BurnCoordinator.Cancellation cancel)throws Exception{
        Binding value=binding(ticket);return value.backend().verify(value.ticket(),cancel);
    }
    public void cancel(BurnCoordinator.Ticket ticket)throws Exception{
        Binding value=binding(ticket);
        try{value.cancelContext().run();}finally{value.backend().cancel(value.ticket());}
    }
}
