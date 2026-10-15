package net.muxigame.musiccompat.burn;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** One native chooser per client JVM. Only the injected game executor delivers
 * selections; dismissing the native dialog never becomes Path.of("").
 */
public final class LocalAudioChooser {
    @FunctionalInterface interface Dialog { String open() throws Exception; }
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private final Executor gameThread, worker;
    private final Dialog dialog;

    public LocalAudioChooser(Executor gameThread) {
        this(gameThread, command -> {
            Thread thread = new Thread(command, "Muxi music local chooser");
            thread.setDaemon(true);
            thread.start();
        }, LocalAudioChooser::nativeDialog);
    }
    LocalAudioChooser(Executor gameThread, Executor worker, Dialog dialog) {
        this.gameThread = gameThread;
        this.worker = worker;
        this.dialog = dialog;
    }
    /** Used only by the client's automatic focus-loss pause field-read mixin. */
    public static boolean active() { return OPEN.get(); }
    /** false means another chooser/callback is still pending; no new dialog. */
    public boolean choose(Consumer<Path> selected) {
        if (!OPEN.compareAndSet(false, true)) return false;
        try {
            worker.execute(() -> {
                Path choice = null;
                try {
                    String value = dialog.open();
                    if (value != null && !value.isBlank()) choice = Path.of(value);
                } catch (Exception | LinkageError unavailable) {
                    // Native dialog failures do not create audio jobs or expose paths.
                } catch (Error fatal) {
                    OPEN.set(false);
                    throw fatal;
                }
                Path delivered = choice;
                try {
                    gameThread.execute(() -> {
                        try { selected.accept(delivered); }
                        finally { OPEN.set(false); }
                    });
                } catch (RuntimeException | Error rejected) {
                    OPEN.set(false);
                }
            });
        } catch (RuntimeException | Error rejected) {
            OPEN.set(false);
            return false;
        }
        return true;
    }
    private static String nativeDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filters = stack.mallocPointer(6);
            for (String pattern : List.of("*.wav", "*.mp3", "*.ogg", "*.flac", "*.aac", "*.m4a"))
                filters.put(stack.UTF8(pattern));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog("选择本机音频", "", filters,
                    "WAV / MP3 / OGG / FLAC / AAC / M4A", false);
        }
    }
}
