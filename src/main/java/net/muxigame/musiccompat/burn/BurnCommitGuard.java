package net.muxigame.musiccompat.burn;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Server thread integration point. A receipt cannot write a replaced CD/menu. */
public final class BurnCommitGuard {
    public interface Menu {
        Object identity();
        boolean connected();
        boolean sameInput(Object snapshot);
        void write(BurnCoordinator.Receipt receipt);
    }
    public interface Verifier {
        CompletableFuture<BurnCoordinator.Receipt> consume(long verifiedUid, String gameSession,
                String jobId, String receiptId);
    }
    private final Executor serverThread;
    private final Verifier verifier;
    private final Supplier<Menu> currentMenu;
    public BurnCommitGuard(Executor serverThread, Verifier verifier, Supplier<Menu> currentMenu) {
        this.serverThread=serverThread;this.verifier=verifier;this.currentMenu=currentMenu;
    }
    public CompletableFuture<Boolean> commit(long uid, String session, Object menuId, Object cdSnapshot,
            String jobId, String receiptId) {
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        serverThread.execute(() -> {
            Menu menu=currentMenu.get();
            if (!current(menu,menuId,cdSnapshot)) {result.complete(false);return;}
            // Only backend-verified fields become SongInfo. Client URLs/titles are ignored.
            try {
                verifier.consume(uid,session,jobId,receiptId).whenComplete((receipt,error) -> serverThread.execute(() -> {
                    Menu now=currentMenu.get();
                    if(error!=null || receipt==null || !jobId.equals(receipt.jobId()) || !current(now,menuId,cdSnapshot)) {
                        result.complete(false);return;
                    }
                    try {now.write(receipt);result.complete(true);}catch(Exception failure){result.complete(false);}
                }));
            } catch(Exception error){result.complete(false);}
        });
        return result;
    }
    private boolean current(Menu menu,Object id,Object input) {
        return menu!=null && menu.connected() && menu.identity()==id && menu.sameInput(input);
    }
}
