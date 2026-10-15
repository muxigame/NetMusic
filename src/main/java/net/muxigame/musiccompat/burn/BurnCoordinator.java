package net.muxigame.musiccompat.burn;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Pure client job state machine; all blocking work belongs to its worker. */
public final class BurnCoordinator implements AutoCloseable {
    public static final long MAX_BYTES = 32L * 1024 * 1024;
    public enum Stage { PREPARING, ACQUIRING, CHECKING, AUTHORIZING, UPLOADING, VERIFYING, COMMITTING,
                        DONE, FAILED, CANCELLED }
    public record Progress(UUID generation, Stage stage, long bytes, long total, String code,boolean discReturned) {
        public Progress(UUID generation,Stage stage,long bytes,long total,String code){this(generation,stage,bytes,total,code,false);}
    }
    public record Audio(Path path, String title, String license, String credit, LyricOrigin lyricOrigin) {
        public Audio(Path path,String title,String license,String credit){this(path,title,license,credit,LyricOrigin.NONE);}
        public Audio {java.util.Objects.requireNonNull(lyricOrigin);}
    }
    public record Checked(Audio audio, long bytes, String sha256) {}
    public record Ticket(String jobId, Object transport) {}
    public record Receipt(String jobId, String receiptId, String url, String title, int seconds, String proof) {
        public Receipt(String jobId,String receiptId,String url,String title,int seconds){this(jobId,receiptId,url,title,seconds,"");}
    }
    public interface Source { Audio acquire(Cancellation cancel, Consumer<Progress> progress) throws Exception; }
    public interface Probe { void check(Path path, Cancellation cancel) throws Exception; }
    public interface Backend {
        Ticket authorize(Checked audio, UUID request, Cancellation cancel) throws Exception;
        void upload(Ticket ticket, Checked audio, Cancellation cancel, Consumer<Progress> progress) throws Exception;
        Receipt verify(Ticket ticket, Cancellation cancel) throws Exception;
        void cancel(Ticket ticket) throws Exception;
    }
    public interface Commit {
        /** UI/game-thread reservation is sent before a subsequent close-menu packet. */
        default void begin(UUID request) throws Exception {}
        /** Waits for the server-owned escrow before source/account network work. */
        default void prepare(UUID request,Cancellation cancel) throws Exception {}
        /** Called on the game thread: checks the original task, escrow nonce and connection. */
        boolean stillCurrent();
        /** Sends only a one-time receipt ID to the server, never platform credentials. */
        void send(Receipt receipt) throws Exception;
    }
    public static final class Cancellation {
        private final AtomicBoolean value = new AtomicBoolean();
        private volatile Closeable active;
        public void check() throws InterruptedIOException {
            if (value.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("cancelled");
        }
        public synchronized void bind(Closeable resource) throws IOException {
            check(); active = resource;
        }
        public synchronized void unbind(Closeable resource) { if (active == resource) active = null; }
        public void cancel() {
            Closeable resource;
            // Serialize with bind: cancellation must either close its resource or
            // make bind reject it. Do not hold the monitor during external I/O.
            synchronized (this) { value.set(true); resource = active; active = null; }
            if (resource != null) try { resource.close(); } catch (IOException ignored) {}
        }
        public boolean cancelled() { return value.get(); }
    }

    private final Executor gameThread;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Muxi music burn"); thread.setDaemon(true); return thread;
    });
    private final Backend backend;
    private final Probe probe;
    private final Consumer<Progress> listener;
    private volatile Job current;
    private volatile boolean closed;

    private static final class Job {
        final UUID id = UUID.randomUUID();
        final Cancellation cancel = new Cancellation();
        final AtomicBoolean terminal = new AtomicBoolean();
        final AtomicBoolean aborted = new AtomicBoolean();
        volatile Ticket ticket;
        volatile Future<?> future;
        volatile boolean awaitingConfirmation;
        volatile boolean committed;
        volatile boolean userCancelled;
        volatile boolean refunded;
        volatile String failureCode="server_commit_failed";
        volatile int serverStatus;
    }

    public BurnCoordinator(Executor gameThread, Backend backend, Probe probe, Consumer<Progress> listener) {
        this.gameThread = gameThread; this.backend = backend; this.probe = probe; this.listener = listener;
    }

    public synchronized UUID start(Source source, Commit commit) {
        if (closed) throw new IllegalStateException("coordinator_closed");
        if (current != null && !current.terminal.get()) throw new IllegalStateException("burn_already_running");
        Job job = new Job(); current = job;
        try {commit.begin(job.id);job.future = worker.submit(() -> run(job, source, commit));}
        catch(Exception failed){fail(job,failed);}
        return job.id;
    }

    private void emit(Job job, Stage stage, long bytes, long total, String code) {
        gameThread.execute(() -> {
            if (current == job && (job.serverStatus!=2 || stage==Stage.DONE)
                    && (job.serverStatus!=3 || stage!=Stage.DONE) && (!job.terminal.get() || stage==Stage.DONE || stage==Stage.FAILED || stage==Stage.CANCELLED)
                    && (!job.cancel.cancelled() || stage==Stage.FAILED || stage==Stage.CANCELLED || job.committed&&stage==Stage.DONE))
                listener.accept(new Progress(job.id, stage, bytes, total, code,job.refunded));
        });
    }
    private void stage(Job job, Stage stage) throws IOException {
        job.cancel.check(); emit(job, stage, 0, 0, "");
    }
    private void run(Job job, Source source, Commit commit) {
        Audio audio = null;
        try {
            stage(job, Stage.PREPARING);
            commit.prepare(job.id,job.cancel);
            stage(job, Stage.ACQUIRING);
            audio = source.acquire(job.cancel, p -> emit(job, Stage.ACQUIRING, p.bytes(), p.total(), ""));
            stage(job, Stage.CHECKING);
            if (!audio.license().matches("CC0-1\\.0|CC-BY-4\\.0|OWNED-REDISTRIBUTION") || audio.credit().isBlank())
                throw new IOException("redistribution_permission_required");
            long size = Files.size(audio.path());
            if (size <= 0 || size > MAX_BYTES) throw new IOException("invalid_audio_size");
            probe.check(audio.path(), job.cancel);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(audio.path())) {
                job.cancel.bind(in);
                try {
                    byte[] buffer = new byte[65536]; int read; long actual = 0;
                    while ((read = in.read(buffer)) != -1) {
                        job.cancel.check(); actual += read;
                        if (actual > MAX_BYTES) throw new IOException("invalid_audio_size");
                        digest.update(buffer, 0, read);
                    }
                    if (actual != size) throw new IOException("audio_changed");
                } finally { job.cancel.unbind(in); }
            }
            Checked checked = new Checked(audio, size, HexFormat.of().formatHex(digest.digest()));
            stage(job, Stage.AUTHORIZING); job.ticket = backend.authorize(checked, job.id, job.cancel);
            stage(job, Stage.UPLOADING);
            backend.upload(job.ticket, checked, job.cancel, p -> emit(job, Stage.UPLOADING, p.bytes(), p.total(), ""));
            stage(job, Stage.VERIFYING); Receipt receipt = backend.verify(job.ticket, job.cancel);
            if (!receipt.jobId().equals(job.ticket.jobId())) throw new IOException("invalid_receipt");
            stage(job, Stage.COMMITTING);
            gameThread.execute(() -> {
                if (current != job || job.cancel.cancelled() || !commit.stillCurrent()) {
                    cancel(job); return;
                }
                try {
                    job.awaitingConfirmation=true;
                    commit.send(receipt);
                    // DONE is sent only after an explicit server confirmation.
                    CompletableFuture.runAsync(() -> gameThread.execute(() -> {
                        if(current==job && job.awaitingConfirmation && !job.terminal.get())
                            fail(job,new IOException("server_commit_timeout"));
                    }),CompletableFuture.delayedExecutor(25,TimeUnit.SECONDS));
                } catch (Exception error) { fail(job,error); }
            });
        } catch (Exception error) {
            if (job.cancel.cancelled() || error instanceof InterruptedIOException || error instanceof InterruptedException)
                cancel(job);
            else fail(job,error);
        } finally {
            if (audio != null) try { Files.deleteIfExists(audio.path()); } catch (IOException ignored) {}
        }
    }

    private void fail(Job job,Exception error) {
        String message=error.getMessage();
        String code=java.util.Set.of("unsupported_audio","truncated_audio","audio_changed","invalid_audio_size",
            "download_unavailable","account_authorization_required","music_service_unavailable","upload_ticket_expired",
            "oss_upload_failed","invalid_receipt","redistribution_permission_required","music_upload_quota",
            "music_daily_upload_limit","music_intent_rate_limit","music_oss_read_deadline","music_oss_transport_unavailable","music_oss_response_limit","music_oss_payload_refused","music_oss_origin_refused","music_oss_write_guard","storage_operation_failed","music_sts_exchange_unavailable","music_workload_identity_unavailable","music_identity_too_short","music_operation_limit","music_payload_limit","music_read_limit","request_id_reused","job_not_pending","job_cancelled_or_expired","audio_digest_mismatch","unsupported_or_truncated_audio","music_upload_unconfigured","live_account_connection_required","server_commit_timeout",
            "platform_download_permission_unavailable","platform_client_session_unavailable","platform_login_required","platform_login_expired","platform_account_changed",
            "platform_capability_unavailable","platform_permission_denied","platform_trial_only","platform_drm_unsupported","platform_format_unavailable",
            "platform_download_denied","platform_download_capability_unavailable","platform_media_expired","platform_media_unavailable","platform_api_unavailable",
            "platform_source_invalid","invalid_audio_link","not_local_audio","platform_redirect_refused","platform_response_invalid","platform_media_url_refused","platform_song_id_required",
            "server_commit_failed","burn_context_required","audio_validation_busy","audio_validation_timeout","invalid_metadata_size","core_account_api_update_required")
            .contains(message==null?"":message)?message:"burn_failed_retry_available";
        job.failureCode=code;
        if (job.terminal.compareAndSet(false, true)) {
            job.cancel.cancel();if(job.future!=null)job.future.cancel(true);
            emit(job, Stage.FAILED, 0, 0, code);
        }
        abort(job);
    }
    private void abort(Job job) {
        if(job.committed)return;
        Ticket ticket = job.ticket;
        if (ticket != null && !closed && job.aborted.compareAndSet(false,true)) try {
            worker.submit(() -> { try { backend.cancel(ticket); } catch (Exception ignored) {} });
        } catch (RejectedExecutionException ignored) {}
    }
    public boolean running(){Job job=current;return job!=null&&!job.terminal.get();}
    /** Local failure/cancellation is not evidence that the server returned the CD. */
    public boolean needsServerSettlement(){Job job=current;return job!=null&&job.serverStatus==0&&!job.committed;}
    public synchronized void cancel() { if (current != null) cancel(current); }
    private void cancel(Job job) {
        job.cancel.cancel();
        if (job.future != null) job.future.cancel(true);
        if (job.terminal.compareAndSet(false, true)){job.userCancelled=true;emit(job, Stage.CANCELLED, 0, 0, "cancelled");}
        abort(job);
    }
    public void confirmed(UUID generation, boolean success) {
        Job job = current;
        if(!success){rejected(generation);return;}
        if (job != null && job.awaitingConfirmation && job.id.equals(generation) && !job.cancel.cancelled()
                && job.terminal.compareAndSet(false, true)){job.committed=true;emit(job, Stage.DONE, 0, 0, "");}
    }
    /** An authenticated task/nonce response settles cancel-versus-commit races. */
    public void serverSettled(UUID generation,boolean success){
        Job job=current;
        if(job==null||!job.id.equals(generation)||job.serverStatus!=0||job.committed)return;
        if(success){
            if(!job.awaitingConfirmation)return;
            job.serverStatus=2;job.committed=true;job.terminal.set(true);emit(job,Stage.DONE,0,0,"");
        }else{
            job.serverStatus=3;job.refunded=true;
            if(job.userCancelled)emit(job,Stage.CANCELLED,0,0,"cancelled");
            else if(job.terminal.get())emit(job,Stage.FAILED,0,0,job.failureCode);
            else rejected(generation);
        }
    }
    public void rejected(UUID generation){Job job=current;if(job!=null&&!job.terminal.get()&&job.id.equals(generation))fail(job,new IOException("server_commit_failed"));}
    @Override public synchronized void close() {
        if (closed) return;
        cancel(); closed = true; worker.shutdown();
    }
}
