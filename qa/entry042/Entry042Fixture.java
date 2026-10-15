package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;
import java.net.*;import java.net.http.*;import java.nio.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.Flow;import java.util.function.LongConsumer;
import net.muxigame.musiccompat.burn.*;
public final class Entry042Fixture {
 private static final Map<String,JsonObject> jobs=new ConcurrentHashMap<>();
 private static final Set<String> consumed=ConcurrentHashMap.newKeySet();
 private static final List<Map<String,Object>> events=new CopyOnWriteArrayList<>();
 public static void event(String kind,Map<String,Object> facts){Entry042Scope.require();var row=new LinkedHashMap<String,Object>();row.put("kind",kind);row.put("atUtcMillis",System.currentTimeMillis());row.putAll(facts);events.add(row);Entry042Scope.write("entry042-events-"+System.getProperty("qa.local.role")+".json",Map.of("events",events,"fixtureResponses",true,"actualPlatformCalls",0,"ossRequests",0,"productionLoginVerified",false));}
 public static JsonObject account(){Entry042Scope.require();var value=new JsonObject();value.addProperty("uid",Entry042Scope.uid());value.addProperty("gameSession",Entry042Scope.session());event("core-account-fixture",Map.of("actualCoreAccountVerified",false));return value;}
 static byte[] audio()throws Exception{
  Path file=Entry042Scope.root().resolve("entry042-owned-cc0.wav");byte[] data=Files.readAllBytes(file);
  if(data.length!=2646044||!HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)).equals("71d1443e9214c162e3efa1884a624182cc5ffa4afa315e137ca027f16fcf6c82"))throw new IllegalStateException("owned_fixture_digest_required");return data;
 }
 static String body(HttpRequest request)throws Exception{
  var future=new CompletableFuture<String>();var bytes=new java.io.ByteArrayOutputStream();
  request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
   Flow.Subscription subscription;public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
   public void onNext(ByteBuffer b){if(b.remaining()>16384-bytes.size()){subscription.cancel();future.completeExceptionally(new IllegalStateException("fixture_body_limit"));return;}byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);subscription.request(1);}
   public void onError(Throwable error){future.completeExceptionally(error);}public void onComplete(){future.complete(bytes.toString(StandardCharsets.UTF_8));}
  });return future.get(5,TimeUnit.SECONDS);
 }
 public static HttpResponse<byte[]> send(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel,LongConsumer progress)throws Exception{
  Entry042Scope.require();cancel.check();URI uri=request.uri();String host=uri.getHost(),path=uri.getPath();
  if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null)throw new IllegalStateException("entry042_network_refused");
  if(Integer.getInteger("muxi.music.entry042.backendPort",0)>0&&host.equals(BurnProtocol.ACCOUNT.getHost())){
   if(path.endsWith("/complete"))Entry042Gate.pass("verifying",cancel);
   if(path.endsWith("/burn-receipt/redeem"))Entry042Gate.pass("committing",cancel);
   return Entry042Backend.send(request,limit,cancel,progress);
  }
  if(Integer.getInteger("muxi.music.entry042.backendPort",0)>0&&host.equals(BurnProtocol.OSS.getHost())&&request.method().equals("POST")){Entry042Gate.pass("uploading",cancel);Entry042Backend.stage(request);return reply(request,201,new byte[0]);}
  JsonObject response=new JsonObject();byte[] bytes;int status=200;
  if(host.equals("music.163.com")&&path.equals("/api/song/detail/")){
   if(!request.headers().firstValue("Cookie").orElse("").equals(Entry042Scope.COOKIE))throw new IllegalStateException("owned_cookie_only");var matcher=java.util.regex.Pattern.compile("(?:^|&)id=([0-9]+)").matcher(uri.getQuery());if(!matcher.find())throw new IllegalStateException();long id=Long.parseLong(matcher.group(1));
   var song=new JsonObject();song.addProperty("id",id);song.addProperty("name","Owned Netease fixture title");song.addProperty("duration",60000);song.addProperty("fee",0);var artist=new JsonObject();artist.addProperty("name","Owned Netease fixture artist");var artists=new JsonArray();artists.add(artist);song.add("artists",artists);var songs=new JsonArray();songs.add(song);response.addProperty("code",200);response.add("songs",songs);event("netease-detail-fixture",Map.of("songId",id,"automaticNames",true,"actualAccountVerified",false));
  }else if(host.equals("music.163.com")&&path.equals("/api/song/enhance/player/url/v1")){
   boolean fixtureCookie=request.headers().firstValue("Cookie").orElse("").equals(Entry042Scope.COOKIE);if(!fixtureCookie)throw new IllegalStateException("owned_cookie_only");
   var matcher=java.util.regex.Pattern.compile("ids=\\[([0-9]+)\\]").matcher(uri.getQuery());if(!matcher.find())throw new IllegalStateException();long id=Long.parseLong(matcher.group(1));
   var item=new JsonObject();item.addProperty("id",id);item.addProperty("code",200);item.addProperty("url","https://entry042.music.126.net/owned.wav");item.addProperty("expi",120);item.addProperty("type","wav");var data=new JsonArray();data.add(item);response.addProperty("code",200);response.add("data",data);
   event("actual-betterlogin-client-config-fixture",Map.of("songId",id,"cookieFixtureMatched",true,"actualAccountVerified",false));
  }else if(host.equals("u.y.qq.com")&&path.equals("/cgi-bin/musicu.fcg")){
   if(!request.headers().firstValue("Cookie").orElse("").equals(Entry042Scope.COOKIE))throw new IllegalStateException("owned_cookie_only");
   var payload=JsonParser.parseString(body(request)).getAsJsonObject();String section=payload.has("songinfo")?"songinfo":"req_0";var data=new JsonObject();
   if(section.equals("songinfo")){var track=new JsonObject();track.addProperty("id",12345);track.addProperty("mid","003ENTRY042abc");track.addProperty("name","Owned QQ fixture title");var singer=new JsonObject();singer.addProperty("name","Owned QQ fixture artist");var singers=new JsonArray();singers.add(singer);track.add("singer",singers);data.add("track_info",track);}else{var url=new JsonObject();url.addProperty("purl","owned.wav");var urls=new JsonArray();urls.add(url);data.add("midurlinfo",urls);var sip=new JsonArray();sip.add("https://entry042.qqmusic.qq.com/");data.add("sip",sip);}
   var part=new JsonObject();part.addProperty("code",0);part.add("data",data);response.addProperty("code",0);response.add(section,part);event("qq-api-fixture",Map.of("section",section,"cookieFixtureMatched",true));
  }else if(host.equals("api.bilibili.com")&&(path.equals("/x/web-interface/view")||path.equals("/x/player/playurl"))){
   if(!request.headers().firstValue("Cookie").orElse("").equals(Entry042Scope.COOKIE))throw new IllegalStateException("owned_cookie_only");
   var data=new JsonObject();if(path.endsWith("view")){data.addProperty("title","Owned Bili fixture title");var owner=new JsonObject();owner.addProperty("name","Owned Bili fixture author");data.add("owner",owner);var pages=new JsonArray();for(int cid=41;cid<=42;cid++){var page=new JsonObject();page.addProperty("cid",cid);page.addProperty("part",cid==42?"Second part":"First part");pages.add(page);}data.add("pages",pages);}else{var track=new JsonObject();track.addProperty("bandwidth",1);track.addProperty("baseUrl","https://entry042.bilivideo.com/owned.wav");var audio=new JsonArray();audio.add(track);var dash=new JsonObject();dash.add("audio",audio);data.add("dash",dash);}
   response.addProperty("code",0);response.add("data",data);event("bilibili-api-fixture",Map.of("view",path.endsWith("view"),"page2CidMatched",uri.getQuery().contains("cid=42")));
  }else if(Set.of("entry042.music.126.net","entry042.qqmusic.qq.com","entry042.bilivideo.com").contains(host)&&path.equals("/owned.wav")){
   if(request.headers().firstValue("Cookie").isPresent()||request.headers().firstValue("Authorization").isPresent())throw new IllegalStateException("media_credentials_refused");Entry042Gate.pass("acquiring",cancel);bytes=audio();progress.accept(bytes.length);event("ordinary-platform-media-fixture",Map.of("host",host,"bytes",bytes.length,"credentialsAbsent",true));return reply(request,200,bytes);
  }else if(host.equals(BurnProtocol.ACCOUNT.getHost())&&path.equals("/api/launcher/music/uploads")){
   var value=JsonParser.parseString(body(request)).getAsJsonObject();String nonce=value.get("burnNonce").getAsString();if(!BurnProtocol.uuid(nonce))throw new IllegalStateException();
   String job=UUID.randomUUID().toString();var claims=new JsonObject();claims.addProperty("uid",10000);claims.addProperty("burnNonce",nonce);claims.addProperty("jobId",job);claims.addProperty("receiptId",UUID.randomUUID().toString());claims.addProperty("url",BurnProtocol.OSS.resolve("media/10000/"+job+".mp3").toString());claims.addProperty("title",value.get("title").getAsString());claims.addProperty("credit",value.get("credit").getAsString());claims.addProperty("license",value.get("license").getAsString());claims.addProperty("duration",3);claims.addProperty("version",1);jobs.put(job,claims);
   var fields=new JsonObject();fields.addProperty("x-oss-object-acl","private");fields.addProperty("x-oss-forbid-overwrite","true");fields.addProperty("x-oss-signature-version","OSS4-HMAC-SHA256");var upload=new JsonObject();upload.addProperty("url",BurnProtocol.OSS.toString());upload.add("fields",fields);upload.addProperty("expiresAt",System.currentTimeMillis()/1000.0+60);response.addProperty("jobId",job);response.add("upload",upload);event("upload-ticket-fixture",Map.of("nonceBound",true,"actualAccountVerified",false));
  }else if(host.equals(BurnProtocol.OSS.getHost())&&request.method().equals("POST")&&path.equals("/")){
   if(request.headers().firstValue("Cookie").isPresent()||request.headers().firstValue("Authorization").isPresent())throw new IllegalStateException();Entry042Gate.pass("uploading",cancel);event("upload-transport-fixture-no-network",Map.of("ossRequests",0,"bodyNotUploaded",true));return reply(request,201,new byte[0]);
  }else if(host.equals(BurnProtocol.ACCOUNT.getHost())&&path.matches("/api/launcher/music/uploads/[0-9a-f-]{36}/complete")){
   Entry042Gate.pass("verifying",cancel);String job=path.split("/")[5];var claims=jobs.get(job);if(claims==null)throw new IllegalStateException();response=claims.deepCopy();response.addProperty("proof","entry042-fixture."+Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8)));event("audio-complete-fixture",Map.of("actualBackendDecodeVerified",false));
  }else if(host.equals(BurnProtocol.ACCOUNT.getHost())&&path.matches("/api/launcher/music/uploads/[0-9a-f-]{36}/cancel")){
   event("cancel-fixture",Map.of("ossRequests",0));
  }else{event("unrecognized-network-refused",Map.of("host",host));throw new IllegalStateException("entry042_network_refused");}
  bytes=response.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>limit)throw new IllegalStateException();progress.accept(bytes.length);return reply(request,status,bytes);
 }
 public static JsonObject consume(String proof,long uid,String nonce)throws Exception{
  Entry042Scope.require();if(!System.getProperty("qa.local.role").equals("server")||uid!=10000||!proof.startsWith("entry042-fixture."))throw new IllegalStateException();
  Entry042Gate.pass("committing",new BurnCoordinator.Cancellation());
  var claims=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(proof.substring(17)),StandardCharsets.UTF_8)).getAsJsonObject();
  if(claims.get("uid").getAsLong()!=uid||!nonce.equals(claims.get("burnNonce").getAsString())||!BurnProtocol.media(claims.get("url").getAsString())||!consumed.add(claims.get("receiptId").getAsString()))throw new IllegalStateException("fixture_receipt_refused");
  event("receipt-verifier-fixture",Map.of("uidMatched",true,"nonceMatched",true,"productionCryptoVerified",false));return claims;
 }
 static HttpResponse<byte[]> reply(HttpRequest request,int code,byte[] data){return new HttpResponse<>(){
  public int statusCode(){return code;}public HttpRequest request(){return request;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}public byte[] body(){return data;}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
 };}
 private Entry042Fixture(){}
}
