package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.JsonObject;import net.muxigame.musiccompat.burn.*;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Replaces only the pinned private-media HttpClient.send. No production-domain fallback. */
public final class Entry042PlaybackTransport {
 public static URI stableReference(HttpRequest request)throws IOException{
  var signed=request.uri();if(!request.method().equals("GET")||signed.getUserInfo()!=null||signed.getFragment()!=null||signed.getPort()!=-1||signed.getRawQuery()==null||signed.getRawQuery().length()>16384)throw new IOException("owned_signed_media_required");
  URI stable;try{stable=new URI(signed.getScheme(),signed.getAuthority(),signed.getPath(),null,null);}catch(URISyntaxException invalid){throw new IOException("owned_signed_media_required");}
  if(!BurnProtocol.media(stable.toString())||!stable.getRawPath().equals(signed.getRawPath()))throw new IOException("owned_signed_media_required");
  var query=new HashMap<String,String>();var allowed=Set.of("x-oss-signature-version","x-oss-credential","x-oss-date","x-oss-expires","x-oss-signature","x-oss-security-token","x-oss-additional-headers");
  try{for(var item:signed.getRawQuery().split("&")){var pair=item.split("=",2);if(pair.length!=2)throw new IllegalArgumentException();var name=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);var value=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);if(!allowed.contains(name)||value.isBlank()||query.put(name,value)!=null)throw new IllegalArgumentException();}
   if(!"OSS4-HMAC-SHA256".equals(query.get("x-oss-signature-version"))||!query.getOrDefault("x-oss-signature","").matches("[0-9a-f]{64}")||!query.getOrDefault("x-oss-date","").matches("[0-9]{8}T[0-9]{6}Z")||!query.getOrDefault("x-oss-credential","").endsWith("/cn-hangzhou/oss/aliyun_v4_request")||!query.containsKey("x-oss-security-token"))throw new IllegalArgumentException();int ttl=Integer.parseInt(query.get("x-oss-expires"));if(ttl<1||ttl>720)throw new IllegalArgumentException();
  }catch(RuntimeException invalid){throw new IOException("owned_signed_media_required");}return stable;
 }
 public static HttpResponse<InputStream> send(HttpRequest signed)throws Exception{
  Entry042Scope.require();var stable=stableReference(signed);var body=new JsonObject();body.addProperty("gameSession",Entry042Scope.session());body.addProperty("reference",stable.toString());
  var request=HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("/fixture/media")).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
  var response=Entry042Backend.send(request,(int)BurnCoordinator.MAX_BYTES,new BurnCoordinator.Cancellation(),n->{});
  Entry042PlaybackDiagnostics.event("media-loopback-send",Map.of("responseStatus",response.statusCode(),"bytes",response.body().length,"productionFetchAccountLeaseReader",true,"productionDomainRequests",0));
  return new Stream(signed,response,new ByteArrayInputStream(response.body()));
 }
 private record Stream(HttpRequest original,HttpResponse<byte[]> source,InputStream body) implements HttpResponse<InputStream>{
  public int statusCode(){return source.statusCode();}public HttpRequest request(){return original;}public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return source.headers();}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public URI uri(){return original.uri();}public HttpClient.Version version(){return source.version();}public String toString(){return "OwnedPrivateTransport[signed=<redacted>]";}
 }
 private Entry042PlaybackTransport(){}
}
