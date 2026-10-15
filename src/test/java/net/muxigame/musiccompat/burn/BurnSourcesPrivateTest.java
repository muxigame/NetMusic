package net.muxigame.musiccompat.burn;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.sound.sampled.*;

public final class BurnSourcesPrivateTest {
    static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError();}
    record Bytes(HttpRequest request,byte[] body) implements HttpResponse<byte[]>{
        public int statusCode(){return 200;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
        public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
    }
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("music-source-private-tests-");List<Path> cleanup=new ArrayList<>();
        try{
            ByteArrayOutputStream wav=new ByteArrayOutputStream();byte[] pcm=new byte[16000];
            try(var input=new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(8000,16,1,true,false),8000)){AudioSystem.write(input,AudioFileFormat.Type.WAVE,wav);}
            byte[] data=wav.toByteArray();Path original=folder.resolve("original.wav");Files.write(original,data);cleanup.add(original);
            var local=BurnSources.local(original,folder,"Own source","CC0-1.0","Author").acquire(new BurnCoordinator.Cancellation(),p->{});cleanup.add(local.path());
            check(!original.equals(local.path()));check(Arrays.equals(data,Files.readAllBytes(local.path())));check(local.license().equals("CC0-1.0"));
            BurnSources.pcmWav(local.path(),new BurnCoordinator.Cancellation());check(true);
            var link=BurnSources.link(URI.create("https://licensed.example/source.wav"),folder,"Licensed link","CC0-1.0","Author",
                host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})},(request,cancel,progress)->{
                    check(request.headers().firstValue("Cookie").isEmpty());check(request.headers().firstValue("Authorization").isEmpty());return new Bytes(request,data);
                }).acquire(new BurnCoordinator.Cancellation(),p->{});cleanup.add(link.path());
            check(Arrays.equals(data,Files.readAllBytes(link.path())));BurnSources.pcmWav(link.path(),new BurnCoordinator.Cancellation());check(true);
            boolean refused=false;
            try{BurnSources.link(URI.create("https://private.example/source.wav"),folder,"","CC0-1.0","Author",h->new InetAddress[]{InetAddress.getByAddress(new byte[]{127,0,0,1})},(r,c,p)->{throw new AssertionError();}).acquire(new BurnCoordinator.Cancellation(),p->{});}catch(IOException e){refused=true;}check(refused);
            var cancelled=new BurnCoordinator.Cancellation();cancelled.cancel();refused=false;
            try{BurnSources.local(original,folder,"","CC0-1.0","Author").acquire(cancelled,p->{});}catch(IOException e){refused=true;}check(refused);
            for(String platform:List.of("123456","https://music.163.com/song?id=123456","https://MUSIC.163.COM/song?id=123456","https://MUSIC.163.COM./song?id=123456","https://m7.music.126.net/vip.mp3","https://M7.MUSIC.126.NET./vip.mp3")){
                refused=false;
                try{PlatformDownloadSource.create(platform,folder,"Claimed VIP","OWNED-REDISTRIBUTION","Author",(u,t,n,l,c)->{throw new AssertionError("platform fell back to public source");}).acquire(new BurnCoordinator.Cancellation(),p->{});}
                catch(IOException e){refused=e.getMessage().equals("platform_download_permission_unavailable");}check(refused);
            }
            var licensed=PlatformDownloadSource.create("https://licensed.example/source.wav",folder,"Licensed","CC0-1.0","Author",(u,t,n,l,c)->(cancel,progress)->new BurnCoordinator.Audio(original,n,l,c));
            check(licensed.acquire(new BurnCoordinator.Cancellation(),p->{}).path().equals(original));
            System.out.println("{\"burnSourcePathChecks\":"+checks+",\"bothLocalAndLicensedLink\":true,\"networkRequests\":0,\"mcStarted\":false}");
        }finally{
            try(var entries=Files.list(folder)){for(Path p:entries.toList())Files.deleteIfExists(p);}Files.deleteIfExists(folder);
        }
    }
}
