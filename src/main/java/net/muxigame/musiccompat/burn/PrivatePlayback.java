package net.muxigame.musiccompat.burn;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Existing active launcher identity only. Signed addresses never leave this
 * fetch boundary, reach upstream logging, or replace persistent SongInfo URLs. */
public final class PrivatePlayback {
    private record Account(Object connection,String session,long uid,long until){}
    private static volatile Account cached;
    private static final ThreadLocal<BurnCoordinator.Cancellation> decodeRead=new ThreadLocal<>();
    public static ReadScope bindRead(BurnCoordinator.Cancellation cancellation){var previous=decodeRead.get();decodeRead.set(cancellation);return new ReadScope(previous);}
    public static final class ReadScope implements AutoCloseable{private final BurnCoordinator.Cancellation previous;private ReadScope(BurnCoordinator.Cancellation value){previous=value;}public void close(){if(previous==null)decodeRead.remove();else decodeRead.set(previous);}}
    private static final PrivateAccountWait.Flights flights=new PrivateAccountWait.Flights();
    private static Object mediaConnection;private static VerifiedMediaCache mediaCache=new VerifiedMediaCache();
    private static synchronized VerifiedMediaCache mediaCache(Object connection){if(mediaConnection!=connection){mediaCache.clear();mediaCache=new VerifiedMediaCache();mediaConnection=connection;}return mediaCache;}
    private static synchronized void invalidate(Account value){if(cached==value)cached=null;}
    private static final HttpClient MEDIA=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){
            public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}
            public void connectFailed(URI uri,java.net.SocketAddress address,IOException error){}
        }).build();
    private static final ScheduledExecutorService DEADLINE=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Private music read deadline");t.setDaemon(true);return t;});
    private PrivatePlayback(){}
    public static synchronized void remember(JsonObject account,Object connection){
        try{String session=account.get("gameSession").getAsString();long uid=account.get("uid").getAsLong();if(connection!=null&&Minecraft.getInstance().player!=null&&Minecraft.getInstance().getConnection()==connection&&BurnProtocol.uuid(session)&&uid>=10000)cached=new Account(connection,session,uid,System.currentTimeMillis()+30_000);}catch(RuntimeException ignored){}
    }
    public static JsonObject remembered(Object connection){
        Account value=cached;
        if(!current(value)||value.connection!=connection||value.until<=System.currentTimeMillis())return null;
        JsonObject account=new JsonObject();account.addProperty("uid",value.uid);account.addProperty("gameSession",value.session);return account;
    }
    private static boolean current(Account account){var mc=Minecraft.getInstance();return account!=null&&mc.player!=null&&mc.getConnection()==account.connection;}
    private static Account account(BurnCoordinator.Cancellation cancel)throws Exception{
        Account value=cached;if(current(value)&&value.until>System.currentTimeMillis())return value;
        var mc=Minecraft.getInstance();Object connection=mc.getConnection();
        if(connection==null||mc.player==null)throw new IOException("live_music_connection_required");
        // Do not clear a same-connection account that can arrive concurrently here.
        var result=flights.get(connection,pending->mc.execute(()->{
            if(mc.getConnection()!=connection||mc.player==null){pending.complete("");return;}
            try{Class.forName("net.muxigame.core.client.TerminalPassportApi").getMethod("request",Consumer.class).invoke(null,(Consumer<String>)pending::complete);}
            catch(ReflectiveOperationException e){pending.complete("");}
        }));
        return PrivateAccountWait.await(()->{
            Account known=cached;return current(known)&&known.connection==connection&&known.until>System.currentTimeMillis()?known:null;
        },result,body->{
            if(body.length()>16384)throw new IOException("music_account_refused");
            JsonObject decoded;try{decoded=JsonParser.parseString(body).getAsJsonObject();if(!BurnProtocol.uuid(decoded.get("gameSession").getAsString())||decoded.get("uid").getAsLong()<10000)throw new IllegalArgumentException();remember(decoded,connection);}catch(RuntimeException refused){throw new IOException("music_account_refused");}
            Account known=cached;if(!current(known)||known.connection!=connection||known.until<=System.currentTimeMillis()||!known.session.equals(decoded.get("gameSession").getAsString())||known.uid!=decoded.get("uid").getAsLong())throw new IOException("live_music_connection_required");return known;
        },()->mc.getConnection()==connection&&mc.player!=null,cancel,System.nanoTime()+PlaybackBudgets.ACCOUNT_NANOS);
    }
    private static String token()throws IOException{
        try{String value=(String)Class.forName("net.muxigame.core.client.TerminalPassportApi").getMethod("freshAccessToken").invoke(null);
            if(value==null||value.isBlank()||value.length()>16384||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IOException();return value;
        }catch(Exception e){throw new IOException("music_account_access_unavailable");}
    }
    private static PrivatePlaybackProtocol.Lease lease(URI reference,Account account,BurnCoordinator.Cancellation cancel,boolean refresh)throws Exception{
        if(!current(account))throw new IOException("live_music_connection_required");
        if(!System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())throw new IOException("private_music_debug_logging_refused");
        JsonObject body=new JsonObject();body.addProperty("gameSession",account.session);body.addProperty("reference",reference.toString());
        body.addProperty("refresh",refresh);
        HttpRequest request=HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("api/launcher/music/playback"))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        var result=BurnHttp.send(request,32768,cancel,n->{});
        if(result.statusCode()!=200){invalidate(account);throw new IOException("private_music_authorization_unavailable");}
        if(!current(account))throw new IOException("live_music_connection_required");
        try{return PrivatePlaybackProtocol.validate(reference,JsonParser.parseString(new String(result.body(),StandardCharsets.UTF_8)).getAsJsonObject(),System.currentTimeMillis()/1000);}
        catch(RuntimeException error){throw new IOException("private_music_lease_refused");}
    }
    public record LyricsSource(LyricOrigin origin,String title){}
    public static LyricsSource lyricOrigin(URI reference,BurnCoordinator.Cancellation cancel)throws Exception{
        if(!BurnProtocol.media(reference.toString())||!System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())return new LyricsSource(LyricOrigin.NONE,"");
        Object connection=Minecraft.getInstance().getConnection();var trace=MusicPlaybackMetrics.begin(reference,connection);
        try(var scope=trace.bind()){
            Account account;try(var phase=trace.phase("account")){account=account(cancel);phase.success();}
            if(Minecraft.getInstance().getConnection()!=connection||!current(account))throw new IOException("live_music_connection_required");
            JsonObject body=new JsonObject();body.addProperty("gameSession",account.session);body.addProperty("reference",reference.toString());
            HttpRequest request=HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("api/launcher/music/lyrics"))
                .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
            try(var phase=trace.phase("lyrics_origin")){
                var reply=BurnHttp.send(request,4096,cancel,n->{});
                if(reply.statusCode()!=200||!current(account))return new LyricsSource(LyricOrigin.NONE,"");
                JsonObject answer=JsonParser.parseString(new String(reply.body(),StandardCharsets.UTF_8)).getAsJsonObject();
                if(!reference.toString().equals(answer.get("reference").getAsString()))return new LyricsSource(LyricOrigin.NONE,"");
                var source=new LyricsSource(LyricOrigin.parse(answer.getAsJsonObject("lyricOrigin")),SongMetadata.clean(answer.has("title")?answer.get("title").getAsString():"",120,""));phase.success();return source;
            }
        }
    }
    public static HttpResponse<byte[]> fetch(HttpRequest original,BurnCoordinator.Cancellation cancel,java.util.function.LongConsumer progress)throws Exception{
        Object connection=Minecraft.getInstance().getConnection();var trace=MusicPlaybackMetrics.begin(original.uri(),connection);
        try(var scope=trace.bind();var total=trace.phase("total")){
            Account account;try(var phase=trace.phase("account")){account=account(cancel);phase.success();}if(Minecraft.getInstance().getConnection()!=connection)throw new IOException("live_music_connection_required");
            var response=PrivateMediaReader.fetch(original,refresh->{try(var phase=trace.phase("lease")){var lease=lease(original.uri(),account,cancel,refresh);phase.success();return lease;}},()->current(account),
                request->MEDIA.send(request,HttpResponse.BodyHandlers.ofInputStream()),cancel,progress,mediaCache(connection));
            total.bytes(response.body().length);total.success();return response;
        }
    }
    public static HttpResponse<InputStream> stream(HttpRequest request)throws Exception{
        var cancellation=decodeRead.get();var bytes=fetch(request,cancellation==null?new BurnCoordinator.Cancellation():cancellation,n->{});return new StreamResponse(bytes,new ByteArrayInputStream(bytes.body()));
    }
    private record BytesResponse(HttpRequest original,HttpResponse<?> source,byte[] body) implements HttpResponse<byte[]>{
        public int statusCode(){return source.statusCode();}public HttpRequest request(){return original;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return source.headers();}public Optional<javax.net.ssl.SSLSession> sslSession(){return source.sslSession();}public URI uri(){return original.uri();}public HttpClient.Version version(){return source.version();}
        public String toString(){return "PrivateMusicResponse[url=<redacted>]";}
    }
    private record StreamResponse(HttpResponse<byte[]> source,InputStream body) implements HttpResponse<InputStream>{
        public int statusCode(){return source.statusCode();}public HttpRequest request(){return source.request();}public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return source.headers();}public Optional<javax.net.ssl.SSLSession> sslSession(){return source.sslSession();}public URI uri(){return source.uri();}public HttpClient.Version version(){return source.version();}
        public String toString(){return "PrivateMusicStream[url=<redacted>]";}
    }
}
