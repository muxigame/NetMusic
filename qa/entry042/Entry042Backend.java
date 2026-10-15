package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import net.muxigame.musiccompat.burn.*;
import java.net.*;import java.net.http.*;import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.Flow;import java.util.function.LongConsumer;
/** Loopback only adapter; the actual production service/proof/reader run on fixture bytes. */
public final class Entry042Backend {
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,java.net.SocketAddress address,IOException error){}}).build();
 static URI origin(){Entry042Scope.require();int port=Integer.getInteger("muxi.music.entry042.backendPort",0);if(port<1024||port>65535)throw new IllegalStateException("owned_backend_port_required");return URI.create("http://127.0.0.1:"+port);}
 static byte[] bytes(HttpRequest request,int max)throws Exception{
  var future=new CompletableFuture<byte[]>();var out=new ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
   Flow.Subscription s;public void onSubscribe(Flow.Subscription value){s=value;s.request(1);}public void onNext(ByteBuffer b){if(b.remaining()>max-out.size()){s.cancel();future.completeExceptionally(new IOException("owned_fixture_body_limit"));return;}byte[] v=new byte[b.remaining()];b.get(v);out.writeBytes(v);s.request(1);}public void onError(Throwable error){future.completeExceptionally(error);}public void onComplete(){future.complete(out.toByteArray());}});return future.get(5,TimeUnit.SECONDS);
 }
 public static HttpResponse<byte[]> send(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel,LongConsumer progress)throws Exception{
  cancel.check();String path=request.uri().getPath();if(!path.startsWith("/api/launcher/music/")&&!Set.of("/oauth/jwks.json","/api/minecraft/music/burn-receipt/redeem","/fixture/stage","/fixture/media").contains(path))throw new IllegalStateException("owned_backend_route_required");
  var b=HttpRequest.newBuilder(origin().resolve(path)).timeout(Duration.ofSeconds(12)).header("X-Entry042-Role",Entry042Scope.role()).header("Authorization","Bearer entry042-owned-account-fixture").header("Content-Type","application/json");
  if(request.method().equals("GET"))b.GET();else b.POST(HttpRequest.BodyPublishers.ofByteArray(bytes(request,65536)));
  var response=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());if(response.body().length>limit)throw new IOException("owned_backend_response_limit");cancel.check();progress.accept(response.body().length);return response;
 }
 static JsonObject json(String path,JsonObject body)throws Exception{
  var response=send(HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve(path)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),65536,new BurnCoordinator.Cancellation(),n->{});
  if(response.statusCode()!=200)throw new IOException("owned_backend_unavailable");return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
 }
 static JsonObject session(){var value=new JsonObject();value.addProperty("gameSession",Entry042Scope.session());return value;}
 public static void stage(HttpRequest request)throws Exception{
  byte[] expected=Entry042Fixture.audio(),multipart=bytes(request,expected.length+32768);String text=new String(multipart,StandardCharsets.ISO_8859_1);var matcher=java.util.regex.Pattern.compile("name=\"key\"\r\n\r\n(incoming/(?:10000|10001)/([0-9a-f-]{36})\\.wav)\r\n").matcher(text);
  if(!matcher.find())throw new IOException("owned_upload_key_required");int file=text.indexOf("name=\"file\"");int start=text.indexOf("\r\n\r\n",file)+4;
  if(file<0||start<4||start+expected.length>multipart.length||!Arrays.equals(expected,Arrays.copyOfRange(multipart,start,start+expected.length)))throw new IOException("actual_fixture_upload_bytes_required");
  var body=session();body.addProperty("jobId",matcher.group(2));json("/fixture/stage",body);
 }
 public static HttpResponse<byte[]> media(HttpRequest original,BurnCoordinator.Cancellation cancel,LongConsumer progress)throws Exception{
  final Object connection=net.minecraft.client.Minecraft.getInstance().getConnection();if(connection==null)throw new IOException("owned_connection_required");
  return PrivateMediaReader.fetch(original,refresh->{var body=session();body.addProperty("reference",original.uri().toString());body.addProperty("refresh",refresh);return PrivatePlaybackProtocol.validate(original.uri(),json("/api/launcher/music/playback",body),System.currentTimeMillis()/1000);},()->net.minecraft.client.Minecraft.getInstance().getConnection()==connection,request->{
   var body=session();body.addProperty("reference",original.uri().toString());var response=send(HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("/fixture/media")).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),(int)BurnCoordinator.MAX_BYTES,cancel,n->{});
   return new Stream(response,new ByteArrayInputStream(response.body()));
  },cancel,progress);
 }
 private record Stream(HttpResponse<byte[]> source,InputStream body) implements HttpResponse<InputStream>{public int statusCode(){return source.statusCode();}public HttpRequest request(){return source.request();}public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return source.headers();}public Optional<javax.net.ssl.SSLSession> sslSession(){return source.sslSession();}public URI uri(){return source.uri();}public HttpClient.Version version(){return source.version();}}
 private Entry042Backend(){}
}
