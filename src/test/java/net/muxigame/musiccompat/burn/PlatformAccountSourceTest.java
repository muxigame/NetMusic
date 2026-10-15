package net.muxigame.musiccompat.burn;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/** Real loopback simulated platform, synthetic account/rights only. No live VIP. */
public final class PlatformAccountSourceTest {
    static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("platform fixture failed");}
    static final long SONG=123456;
    static final AtomicReference<String> cookie=new AtomicReference<>("OFFLINE_ACCOUNT_A");
    static JsonObject raw;static int apiStatus=200,mediaStatus=200,apiCalls,mediaCalls;
    static boolean switchApi,switchMedia,renew,encrypted;static byte[] audio;static boolean apiCookie,mediaCookie;
    static void reset(){
        apiCalls=mediaCalls=0;apiStatus=mediaStatus=200;switchApi=switchMedia=renew=encrypted=apiCookie=mediaCookie=false;cookie.set("OFFLINE_ACCOUNT_A");
        raw=JsonParser.parseString("{\"code\":200,\"data\":[{\"id\":123456,\"code\":200,\"url\":\"https://m7.music.126.net/temporary.wav\",\"type\":\"wav\",\"expi\":60,\"freeTrialInfo\":null}],\"OFFLINE_ONLY_downloadAllowed\":true}").getAsJsonObject();
    }
    static JsonObject item(){return raw.getAsJsonArray("data").get(0).getAsJsonObject();}
    static PlatformAccountSource.DownloadAuthority authority= (reply,id)->reply.get("OFFLINE_ONLY_downloadAllowed").getAsBoolean()?PlatformAccountSource.DownloadCapability.ALLOWED:PlatformAccountSource.DownloadCapability.DENIED;
    static BurnCoordinator.Source source(Path folder,PlatformAccountSource.Transport transport,PlatformAccountSource.DownloadAuthority rights){
        PlatformAccountSource.Sessions sessions=()->{
            String snapshot=cookie.get();return new PlatformAccountSource.Session(snapshot,"higher",()->snapshot.equals(cookie.get()));
        };
        return rights==null?PlatformAccountSource.create(SONG,folder,"Synthetic owned fixture","OWNED-REDISTRIBUTION","Fixture owner",sessions,transport):PlatformAccountSource.create(SONG,folder,"Synthetic owned fixture","OWNED-REDISTRIBUTION","Fixture owner",sessions,transport,rights);
    }
    static void denied(Path folder,PlatformAccountSource.Transport transport,String code)throws Exception{
        try{source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});throw new AssertionError("denial accepted");}
        catch(IOException expected){check(expected.getMessage().equals(code));check(!expected.toString().contains("OFFLINE_ACCOUNT"));}
    }
    static void simulatedBurn(Path folder,PlatformAccountSource.Transport transport)throws Exception{
        Queue<Runnable> game=new ConcurrentLinkedQueue<>();List<BurnCoordinator.Progress> progress=new CopyOnWriteArrayList<>();AtomicReference<String> reference=new AtomicReference<>();
        BurnCoordinator.Backend upload=new BurnCoordinator.Backend(){
            public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked audio,UUID id,BurnCoordinator.Cancellation cancel){check(audio.audio().license().equals("OWNED-REDISTRIBUTION"));return new BurnCoordinator.Ticket(id.toString(),null);}
            public void upload(BurnCoordinator.Ticket ticket,BurnCoordinator.Checked checked,BurnCoordinator.Cancellation cancel,java.util.function.Consumer<BurnCoordinator.Progress> p)throws Exception{check(Arrays.equals(Files.readAllBytes(checked.audio().path()),audio));}
            public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket ticket,BurnCoordinator.Cancellation cancel){return new BurnCoordinator.Receipt(ticket.jobId(),UUID.randomUUID().toString(),"https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10001/"+ticket.jobId()+".mp3","Offline only",1,"OFFLINE_COMMIT_FIXTURE");}
            public void cancel(BurnCoordinator.Ticket ticket){}
        };
        try(var coordinator=new BurnCoordinator(game::add,upload,BurnSources::supportedHeader,progress::add)){
            UUID id=coordinator.start(source(folder,transport,null),new BurnCoordinator.Commit(){public boolean stillCurrent(){return true;}public void send(BurnCoordinator.Receipt receipt){reference.set(receipt.url());}});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(reference.get()==null&&System.nanoTime()<deadline){Runnable r;while((r=game.poll())!=null)r.run();Thread.sleep(5);}
            check(reference.get()!=null&&!reference.get().contains("?")&&!reference.get().contains("OFFLINE_ACCOUNT"));
            check(progress.stream().noneMatch(p->p.stage()==BurnCoordinator.Stage.DONE));coordinator.confirmed(id,true);
            Runnable r;while((r=game.poll())!=null)r.run();check(progress.stream().anyMatch(p->p.stage()==BurnCoordinator.Stage.DONE));
        }
    }
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("music-platform-fixture-");var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var pcm=new AudioInputStream(new ByteArrayInputStream(new byte[16000]),new AudioFormat(8000,16,1,true,false),8000)){AudioSystem.write(pcm,AudioFileFormat.Type.WAVE,bytes);}audio=bytes.toByteArray();
        server.createContext("/api",exchange->{
            apiCalls++;apiCookie="OFFLINE_ACCOUNT_A".equals(exchange.getRequestHeaders().getFirst("Cookie"));
            byte[] body=raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(switchApi)cookie.set("OFFLINE_ACCOUNT_B");
            exchange.getResponseHeaders().set("Location","/media");exchange.sendResponseHeaders(apiStatus,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.createContext("/media",exchange->{
            mediaCalls++;mediaCookie|=exchange.getRequestHeaders().containsKey("Cookie")||exchange.getRequestHeaders().containsKey("Authorization");
            if(switchMedia)cookie.set("OFFLINE_ACCOUNT_B");int status=renew&&mediaCalls==1?403:mediaStatus;
            byte[] body=encrypted?new byte[]{0,3,9,17,1}:audio;exchange.getResponseHeaders().set("Location","/api");exchange.sendResponseHeaders(status,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });server.start();
        var http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        PlatformAccountSource.Transport transport=(request,limit,cancel)->{
            cancel.check();boolean api=request.uri().getHost().equals("music.163.com");
            check(api?request.uri().getRawPath().equals("/api/song/enhance/player/url/v1"):request.uri().getHost().equals("m7.music.126.net"));
            check(request.headers().firstValue("Authorization").isEmpty());
            if(!api){check(request.headers().firstValue("Cookie").isEmpty());check(request.uri().getScheme().equals("https"));if(request.uri().getRawPath().contains("%"))check(request.uri().getRawPath().equals("/a%2Fb.wav")&&request.uri().getRawQuery().equals("q=%2F"));}
            var local=HttpRequest.newBuilder(URI.create("http://localhost:"+server.getAddress().getPort()+(api?"/api":"/media"))).GET();
            if(api)local.header("Cookie",request.headers().firstValue("Cookie").orElse(""));
            var reply=http.send(local.build(),HttpResponse.BodyHandlers.ofByteArray());check(reply.body().length<=limit);return reply;
        };
        try{
            reset();var downloaded=source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});
            check(Arrays.equals(Files.readAllBytes(downloaded.path()),audio));check(apiCalls==1&&mediaCalls==1&&apiCookie&&!mediaCookie);check(!downloaded.toString().contains("OFFLINE_ACCOUNT"));Files.delete(downloaded.path());
            reset();try{source(folder,transport,(r,id)->PlatformAccountSource.DownloadCapability.UNAVAILABLE).acquire(new BurnCoordinator.Cancellation(),p->{});throw new AssertionError();}catch(IOException expected){check(expected.getMessage().equals("platform_download_capability_unavailable"));}check(mediaCalls==0);
            reset();item().addProperty("downloadAllowed",false);denied(folder,transport,"platform_download_denied");check(mediaCalls==0);
            reset();item().add("freeTrialInfo",JsonParser.parseString("{\"start\":0,\"end\":30000}"));denied(folder,transport,"platform_trial_only");check(mediaCalls==0);
            reset();item().addProperty("drm",true);denied(folder,transport,"platform_drm_unsupported");check(mediaCalls==0);
            reset();apiStatus=401;denied(folder,transport,"platform_login_expired");
            reset();raw.addProperty("code",301);denied(folder,transport,"platform_login_expired");
            reset();item().addProperty("code",403);denied(folder,transport,"platform_permission_denied");
            reset();item().addProperty("id",SONG+1);denied(folder,transport,"platform_response_invalid");
            reset();raw.getAsJsonArray("data").add(item().deepCopy());denied(folder,transport,"platform_response_invalid");
            reset();item().addProperty("url","http://m7.music.126.net/a%2Fb.wav?q=%2F");downloaded=source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});check(apiCalls==1&&mediaCalls==1);Files.delete(downloaded.path());
            reset();item().addProperty("url","https://evil.invalid/a.wav");denied(folder,transport,"platform_media_url_refused");
            reset();apiStatus=302;denied(folder,transport,"platform_redirect_refused");check(mediaCalls==0);
            reset();mediaStatus=302;denied(folder,transport,"platform_redirect_refused");check(apiCalls==1&&mediaCalls==1);
            reset();switchApi=true;denied(folder,transport,"platform_account_changed");check(mediaCalls==0);
            reset();switchMedia=true;denied(folder,transport,"platform_account_changed");
            reset();cookie.set("");denied(folder,transport,"platform_login_required");check(apiCalls==0);
            reset();var cancel=new BurnCoordinator.Cancellation();cancel.cancel();try{source(folder,transport,null).acquire(cancel,p->{});throw new AssertionError();}catch(InterruptedIOException expected){check(apiCalls==0);}
            reset();renew=true;downloaded=source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});check(apiCalls==2&&mediaCalls==2&&!mediaCookie);Files.delete(downloaded.path());
            reset();item().addProperty("expi",0);item().remove("type");raw.remove("OFFLINE_ONLY_downloadAllowed");downloaded=source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});check(apiCalls==1&&mediaCalls==1);Files.delete(downloaded.path());
            reset();encrypted=true;denied(folder,transport,"unsupported_audio");try(var files=Files.list(folder)){check(files.findAny().isEmpty());}
            reset();item().addProperty("type","encrypted");denied(folder,transport,"platform_drm_unsupported");check(mediaCalls==0);
            reset();item().add("freeTrialInfo",new JsonObject());item().remove("type");item().remove("expi");raw.remove("OFFLINE_ONLY_downloadAllowed");downloaded=source(folder,transport,null).acquire(new BurnCoordinator.Cancellation(),p->{});check(apiCalls==1&&mediaCalls==1);Files.delete(downloaded.path());
            reset();item().add("url",JsonNull.INSTANCE);denied(folder,transport,"platform_media_unavailable");check(mediaCalls==0);
            reset();simulatedBurn(folder,transport);check(apiCalls==1&&mediaCalls==1&&!mediaCookie);
            check(PlatformDownloadSource.songId("123456")==SONG);check(PlatformDownloadSource.songId("https://MUSIC.163.COM/song?id=123456")==SONG);
            check(PlatformDownloadSource.songId("https://music.163.com/song/media/outer/url?id=123456")==SONG);check(PlatformDownloadSource.songId("https://evil.invalid/song?id=123456")==null);
            check(PlatformDownloadSource.songId("https://music.163.com/#/song?id=123456")==SONG);check(PlatformDownloadSource.songId("https://music.163.com/#/song?id=123456&evil=x")==null);
            System.out.println("{\"platformFixtureChecks\":"+checks+",\"scenarios\":25,\"simulatedEndpoint\":true,\"sourceToBurnCoordinatorTested\":true,\"ordinaryMediaUsesProductionOverload\":true,\"realVipAccepted\":false,\"cloudRequests\":0}");
        }finally{server.stop(0);try(var files=Files.list(folder)){for(Path p:files.toList())Files.deleteIfExists(p);}Files.delete(folder);}
    }
}
