package net.muxigame.musiccompat.burn;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import javax.sound.sampled.*;

/** Private snapshots: never uploads the original file path or writes user files. */
public final class BurnSources {
    private BurnSources() {}
    public static BurnCoordinator.Source local(Path original, Path temp, String title, String license, String credit) {
        return (cancel, progress) -> {
            Path path = original.toAbsolutePath().normalize();
            if (path.toString().startsWith("\\\\") || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("not_local_audio");
            if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
                int type = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(path.getRoot().toString());
                if (type != 2 && type != 3 && type != 5 && type != 6) throw new IOException("not_local_audio");
            }
            long size = Files.size(path);
            try (InputStream in = Files.newInputStream(path)) {
                return snapshot(in, size, temp, title, license, credit, cancel, progress);
            }
        };
    }
    public static BurnCoordinator.Source localAutomatic(Path original,Path temp){
        String filename=original.getFileName()==null?"未命名音频":original.getFileName().toString().replaceFirst("\\.[^.]+$","");
        return (cancel,progress)->{var audio=local(original,temp,filename,"OWNED-REDISTRIBUTION","未知作者").acquire(cancel,progress);var info=SongMetadata.local(audio.path(),filename);return new BurnCoordinator.Audio(audio.path(),info.title(),audio.license(),info.artist());};
    }
    public static BurnCoordinator.Source link(URI uri, Path temp, String title, String license, String credit) {
        return link(uri,temp,title,license,credit,InetAddress::getAllByName,(request,cancel,progress)->
            BurnProtocol.media(request.uri().toString())?PrivatePlayback.fetch(request,cancel,progress):BurnHttp.send(request,(int)BurnCoordinator.MAX_BYTES,cancel,progress));
    }
    interface AddressResolver {InetAddress[] resolve(String host)throws Exception;}
    interface Fetcher {HttpResponse<byte[]> fetch(HttpRequest request,BurnCoordinator.Cancellation cancel,java.util.function.LongConsumer progress)throws Exception;}
    static BurnCoordinator.Source link(URI uri, Path temp, String title, String license, String credit,AddressResolver addresses,Fetcher fetch) {
        return (cancel, progress) -> {
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443)
                throw new IOException("invalid_audio_link");
            for (InetAddress address : addresses.resolve(uri.getHost()))
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc)
                    throw new IOException("private_address_unavailable");
            // Public direct-download URLs only. No account Cookie/Auth, DRM extraction or redirects.
            HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).GET().build();
            HttpResponse<byte[]> response=fetch.fetch(request,cancel,
                n->progress.accept(new BurnCoordinator.Progress(null,BurnCoordinator.Stage.ACQUIRING,n,-1,"")));
            if(response.statusCode()!=200)throw new IOException("download_unavailable");
            try(InputStream in=new ByteArrayInputStream(response.body())){
                return snapshot(in,response.body().length,temp,title,license,credit,cancel,progress);
            }
        };
    }
    private static BurnCoordinator.Audio snapshot(InputStream in, long expected, Path temp, String title,
            String license, String credit, BurnCoordinator.Cancellation cancel,
            java.util.function.Consumer<BurnCoordinator.Progress> progress) throws Exception {
        if (expected > BurnCoordinator.MAX_BYTES || expected == 0) throw new IOException("invalid_audio_size");
        Files.createDirectories(temp);
        Path copy = Files.createTempFile(temp, "muxi-burn-", ".audio");
        try (OutputStream out = Files.newOutputStream(copy)) {
            cancel.bind(in);
            try {
                byte[] buffer = new byte[65536]; int read; long total = 0;
                while ((read = in.read(buffer)) != -1) {
                    cancel.check(); total += read;
                    if (total > BurnCoordinator.MAX_BYTES) throw new IOException("invalid_audio_size");
                    out.write(buffer, 0, read);
                    progress.accept(new BurnCoordinator.Progress(null, BurnCoordinator.Stage.ACQUIRING, total, expected, ""));
                }
                if (total == 0 || expected >= 0 && total != expected) throw new IOException("truncated_audio");
            } finally { cancel.unbind(in); }
            return new BurnCoordinator.Audio(copy, title, license, credit);
        } catch (Exception error) { Files.deleteIfExists(copy); throw error; }
    }
    public static void pcmWav(Path path, BurnCoordinator.Cancellation cancel) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat format = stream.getFormat();
            if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    && !format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)) throw new IOException("unsupported_audio");
            if (format.getChannels() < 1 || format.getChannels() > 2
                    || format.getSampleSizeInBits() != 8 && format.getSampleSizeInBits() != 16
                    || format.getSampleRate() < 8000 || format.getSampleRate() > 96000
                    || stream.getFrameLength() < 1 || stream.getFrameLength() > format.getSampleRate() * 600)
                throw new IOException("unsupported_audio");
            cancel.bind(stream);
            try {
                byte[] bytes = new byte[65536]; long frames = 0; int read;
                while ((read = stream.read(bytes)) != -1) { cancel.check(); frames += read; }
                if (frames != stream.getFrameLength() * format.getFrameSize()) throw new IOException("truncated_audio");
            } finally { cancel.unbind(stream); }
        }
    }
    /** Header check only; the backend fully decodes and validates actual content. */
    public static void supportedHeader(Path path,BurnCoordinator.Cancellation cancel)throws Exception{
        cancel.check();byte[] header;
        try(InputStream stream=Files.newInputStream(path)){header=stream.readNBytes(256);}
        String first=new String(header,java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean wav=first.startsWith("RIFF")&&first.length()>=12&&first.substring(8,12).equals("WAVE");
        boolean ogg=first.startsWith("OggS")&&first.contains("\u0001vorbis");
        boolean mpeg=header.length>2&&(header[0]&255)==255&&(header[1]&224)==224;
        boolean mp4=header.length>=12&&first.substring(4,8).equals("ftyp");
        if(!wav&&!ogg&&!mp4&&!first.startsWith("fLaC")&&!first.startsWith("ID3")&&!mpeg)
            throw new IOException("unsupported_audio");
    }
}
