package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import java.nio.file.*;import java.net.*;import java.net.http.*;import java.util.*;import net.muxigame.musiccompat.burn.*;
public final class Entry042FixtureTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError();}
 static HttpResponse<byte[]> send(String uri,String body,String cookie)throws Exception{var b=HttpRequest.newBuilder(URI.create(uri));if(cookie!=null)b.header("Cookie",cookie);if(body!=null)b.POST(HttpRequest.BodyPublishers.ofString(body));else b.GET();return Entry042Fixture.send(b.build(),200000,new BurnCoordinator.Cancellation(),n->{});}
 public static void main(String[] args)throws Exception{
  boolean refused=false;try{send("https://example.invalid/",null,null);}catch(IllegalStateException expected){refused=true;}check(refused);
  Path root=Files.createTempDirectory("entry042-guard-");Files.createDirectories(root.resolve("host"));Files.createDirectories(root.resolve("server"));Files.createDirectories(root.resolve("coordinator"));
  String run=UUID.randomUUID().toString().replace("-","");var owner=new JsonObject();owner.addProperty("runId",run);owner.addProperty("instanceRoot",root.toString());var pins=new JsonArray();var pin=new JsonObject();pin.addProperty("sha256",Entry042Scope.PRODUCT);pins.add(pin);owner.add("inputArtifacts",pins);Files.writeString(root.resolve("local-mc-owner.json"),owner.toString());
  System.setProperty("qa.local.root",root.toString());System.setProperty("qa.local.runId",run);System.setProperty("qa.local.role","host");System.setProperty("user.dir",root.resolve("host").toString());System.setProperty("muxi.music.entry042.qa","true");Entry042Scope.require();check(true);check(Entry042DirectSource.create()!=null);
  var reply=send("https://music.163.com/api/song/enhance/player/url/v1?ids=[1868207326]&level=higher",null,Entry042Scope.COOKIE);check(reply.statusCode()==200);check(JsonParser.parseString(new String(reply.body())).getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsLong()==1868207326L);
  refused=false;try{send("https://music.163.com/api/song/enhance/player/url/v1?ids=[1]",null,"wrong-owned-fixture");}catch(IllegalStateException expected){refused=true;}check(refused);
  check(send("https://u.y.qq.com/cgi-bin/musicu.fcg","{\"songinfo\":{}}",Entry042Scope.COOKIE).statusCode()==200);
  check(send("https://api.bilibili.com/x/web-interface/view?bvid=BV1Q5411x7aE",null,Entry042Scope.COOKIE).statusCode()==200);
  String nonce=UUID.randomUUID().toString();var request=new JsonObject();request.addProperty("burnNonce",nonce);request.addProperty("title","Owned fixture");request.addProperty("credit","Owned fixture author");request.addProperty("license","OWNED-REDISTRIBUTION");
  var ticket=JsonParser.parseString(new String(send(BurnProtocol.ACCOUNT.resolve("api/launcher/music/uploads").toString(),request.toString(),null).body())).getAsJsonObject();String job=ticket.get("jobId").getAsString();check(BurnProtocol.uuid(job));
  var complete=JsonParser.parseString(new String(send(BurnProtocol.ACCOUNT.resolve("api/launcher/music/uploads/"+job+"/complete").toString(),"{}",null).body())).getAsJsonObject();String proof=complete.get("proof").getAsString();check(proof.startsWith("entry042-fixture."));
  System.setProperty("qa.local.role","server");System.setProperty("user.dir",root.resolve("server").toString());
  refused=false;try{Entry042Fixture.consume(proof,10000,UUID.randomUUID().toString());}catch(IllegalStateException expected){refused=true;}check(refused);
  check(Entry042Fixture.consume(proof,10000,nonce).get("title").getAsString().equals("Owned fixture"));
  refused=false;try{Entry042Fixture.consume(proof,10000,nonce);}catch(IllegalStateException expected){refused=true;}check(refused);
  refused=false;try{send("https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/"+job+".mp3",null,null);}catch(IllegalStateException expected){refused=true;}check(refused);
  Entry042Scope.write("entry042-gate.json",Map.of("runId",run,"phase","acquiring","mode","fail"));
  refused=false;try{Entry042Gate.pass("acquiring",new BurnCoordinator.Cancellation());}catch(java.io.IOException expected){refused=true;}check(refused);
  Entry042Gate.pass("uploading",new BurnCoordinator.Cancellation());check(true);
  Entry042Scope.write("entry042-gate.json",Map.of("runId",run,"phase","acquiring","mode","off"));Entry042Gate.pass("acquiring",new BurnCoordinator.Cancellation());check(true);
  Entry042Scope.write("entry042-gate.json",Map.of("runId",run,"phase","acquiring","mode","hold"));var cancellation=new BurnCoordinator.Cancellation();cancellation.cancel();refused=false;try{Entry042Gate.pass("acquiring",cancellation);}catch(java.io.InterruptedIOException expected){refused=true;}check(refused);
  Entry042Scope.write("entry042-gate.json",Map.of("runId","wrong-owned-run","phase","acquiring","mode","off"));refused=false;try{Entry042Gate.pass("acquiring",new BurnCoordinator.Cancellation());}catch(IllegalStateException expected){refused=true;}check(refused);
  // Exercise a real Windows sharing violation on an owned temporary evidence file.
  if(System.getProperty("os.name").startsWith("Windows")){
   Entry042Scope.write("entry042-io-sharing.json",Map.of("step","before"));
   String locked=root.resolve("coordinator/entry042-io-sharing.json").toString().replace("'","''");
   String script="$fixtureStream=[System.IO.File]::Open('"+locked+"',[System.IO.FileMode]::Open,[System.IO.FileAccess]::Read,[System.IO.FileShare]::Read);[Console]::Out.WriteLine('LOCK_HELD');[Console]::Out.Flush();Start-Sleep -Milliseconds 150;$fixtureStream.Dispose()";
   Process sharing=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start();
   check(new java.io.BufferedReader(new java.io.InputStreamReader(sharing.getInputStream())).readLine().equals("LOCK_HELD"));
   Entry042Scope.write("entry042-io-sharing.json",Map.of("step","ioRecovered"));
   check(sharing.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)&&sharing.exitValue()==0&&Files.readString(root.resolve("coordinator/entry042-io-sharing.json")).contains("ioRecovered"));
  }
  System.setProperty("muxi.music.entry042.qa","false");refused=false;try{send("https://example.invalid/",null,null);}catch(IllegalStateException expected){refused=true;}check(refused);
  System.out.println("{\"scopeAndFixtureChecks\":"+checks+",\"actualHttpCalls\":0,\"ossRequests\":0,\"mcStarted\":false,\"realCookieRead\":false}");
 }
}
