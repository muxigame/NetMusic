package net.muxigame.musiccompat.burn;
import java.io.*;import java.net.URI;import java.net.http.*;import java.security.MessageDigest;import java.time.Duration;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.function.*;
/** Every use obtains a fresh authorized lease. Only fully hashed, full responses enter the bounded connection cache. */
public final class PrivateMediaReader {
 public interface LeaseSource {PrivatePlaybackProtocol.Lease get(boolean refresh)throws Exception;}
 public interface Network {HttpResponse<InputStream> send(HttpRequest request)throws Exception;}
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Private music deadline");t.setDaemon(true);return t;});
 public static HttpResponse<byte[]> fetch(HttpRequest original,LeaseSource leases,BooleanSupplier live,Network network,BurnCoordinator.Cancellation cancel,LongConsumer progress)throws Exception{return fetch(original,leases,live,network,cancel,progress,null);}
 private static MusicPlaybackMetrics.Phase phase(String name){var trace=MusicPlaybackMetrics.current();return trace==null?null:trace.phase(name);}
 public static HttpResponse<byte[]> fetch(HttpRequest original,LeaseSource leases,BooleanSupplier live,Network network,BurnCoordinator.Cancellation cancel,LongConsumer progress,VerifiedMediaCache cache)throws Exception{
  if(!BurnProtocol.media(original.uri().toString())||!original.method().equals("GET"))throw new IOException("private_music_reference_required");
  Optional<String> range=original.headers().firstValue("Range");if(range.isPresent()&&!range.get().matches("bytes=[0-9]+-[0-9]*"))throw new IOException("private_music_range_refused");
  for(int attempt=0;attempt<2;attempt++){
   cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");
   var lease=leases.get(attempt>0);if(!lease.reference().equals(original.uri()))throw new IOException("private_music_lease_refused");
   cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");
   if(cache!=null&&range.isEmpty()){
    byte[] known=cache.get(lease,System.nanoTime());var trace=MusicPlaybackMetrics.current();if(trace!=null)trace.event("cache",0,known==null?0:known.length,known==null?"cache_miss":"cache_hit");
    if(known!=null){cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");progress.accept(known.length);return new CachedResponse(original,known);}
   }
   var builder=HttpRequest.newBuilder(lease.transientUrl()).timeout(Duration.ofSeconds(15)).GET();range.ifPresent(value->builder.header("Range",value));
   HttpResponse<InputStream> response;
   try(var headers=phase("headers")){response=network.send(builder.build());if(headers!=null)headers.success();}
   catch(Exception error){throw new IOException(error instanceof HttpTimeoutException?"private_music_headers_timeout":"private_music_read_unavailable");}
   try(InputStream input=response.body()){
    if((response.statusCode()==401||response.statusCode()==403)&&attempt==0)continue;
    if(response.statusCode()!=200&&response.statusCode()!=206)throw new IOException("private_music_read_unavailable");
    long length=response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if(length<1||length>lease.size()||!response.headers().firstValue("Content-Encoding").orElse("identity").equals("identity"))throw new IOException("private_music_size_refused");
    if(response.statusCode()==206){
     var matcher=java.util.regex.Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(response.headers().firstValue("Content-Range").orElse(""));
     if(!matcher.matches()||Long.parseLong(matcher.group(3))!=lease.size()||Long.parseLong(matcher.group(2))-Long.parseLong(matcher.group(1))+1!=length)throw new IOException("private_music_range_refused");
    }
    var timedOut=new AtomicBoolean();var closer=TIMER.schedule(()->{timedOut.set(true);try{input.close();}catch(IOException ignored){}},15,TimeUnit.SECONDS);
    var bytes=new ByteArrayOutputStream();cancel.bind(input);long start=System.nanoTime();boolean first=true;
    try(var body=phase("body")){
     byte[] buffer=new byte[65536];int count;
     while((count=input.read(buffer))!=-1){
      cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");if(timedOut.get())throw new IOException("private_music_body_timeout");
      if(count==0)continue;if(first){var trace=MusicPlaybackMetrics.current();if(trace!=null)trace.event("first_body",System.nanoTime()-start,count,"success");first=false;}
      if(bytes.size()+count>length)throw new IOException("private_music_size_refused");bytes.write(buffer,0,count);progress.accept(bytes.size());
     }
     if(timedOut.get())throw new IOException("private_music_body_timeout");if(body!=null){body.bytes(bytes.size());body.success();}
    }catch(Exception error){if(timedOut.get())throw new IOException("private_music_body_timeout");throw error;}finally{closer.cancel(false);cancel.unbind(input);}
    byte[] data=bytes.toByteArray();if(data.length!=length)throw new IOException("private_music_truncated");
    if(response.statusCode()==200){
     try(var hash=phase("hash")){
      if(length!=lease.size()||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(lease.sha256()))throw new IOException("private_music_digest_refused");
      if(hash!=null){hash.bytes(data.length);hash.success();}
     }
     cancel.check();if(!live.getAsBoolean())throw new IOException("live_music_connection_required");if(cache!=null&&range.isEmpty())cache.putVerified(lease,data,System.nanoTime());
    }
    return new Response(original,response,data);
   }catch(Exception error){
    if(error instanceof IOException&&Set.of("private_music_size_refused","private_music_range_refused","private_music_truncated","private_music_digest_refused","private_music_body_timeout","live_music_connection_required","cancelled").contains(error.getMessage()))throw new IOException(error.getMessage());
    throw new IOException("private_music_read_unavailable");
   }
  }
  throw new IOException("private_music_refresh_unavailable");
 }
 private record Response(HttpRequest original,HttpResponse<?> transport,byte[] body) implements HttpResponse<byte[]>{
  public int statusCode(){return transport.statusCode();}public HttpRequest request(){return original;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}
  public HttpHeaders headers(){return transport.headers();}public Optional<javax.net.ssl.SSLSession> sslSession(){return transport.sslSession();}public URI uri(){return original.uri();}public HttpClient.Version version(){return transport.version();}
  public String toString(){return "PrivateMusicResponse[url=<redacted>]";}
 }
 private record CachedResponse(HttpRequest original,byte[] body) implements HttpResponse<byte[]>{
  public int statusCode(){return 200;}public HttpRequest request(){return original;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}
  public HttpHeaders headers(){return HttpHeaders.of(Map.of("Content-Length",List.of(Integer.toString(body.length))),(a,b)->true);}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}
  public URI uri(){return original.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}public String toString(){return "PrivateMusicCacheResponse[url=<redacted>]";}
 }
 private PrivateMediaReader(){}
}
