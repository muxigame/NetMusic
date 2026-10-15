package net.muxigame.musiccompat.burn;
import com.google.gson.*;import java.nio.file.*;import java.io.*;import java.nio.*;import java.nio.charset.*;import java.net.http.*;import java.util.*;import java.util.concurrent.atomic.*;
public final class AutomaticMetadataTest {
 private static int checks;private static final byte[] WAV={'R','I','F','F',36,0,0,0,'W','A','V','E'};
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("metadata check "+checks);}
 private static HttpResponse<byte[]> reply(HttpRequest request,int status,byte[] data){return new HttpResponse<>(){public int statusCode(){return status;}public HttpRequest request(){return request;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}public byte[] body(){return data;}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public java.net.URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}};}
 private static String body(HttpRequest request)throws Exception{var result=new java.util.concurrent.CompletableFuture<String>();var bytes=new ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<ByteBuffer>(){java.util.concurrent.Flow.Subscription subscription;public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;s.request(1);}public void onNext(ByteBuffer b){byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);subscription.request(1);}public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}public void onError(Throwable e){result.completeExceptionally(e);}});return result.get(2,java.util.concurrent.TimeUnit.SECONDS);}
 private static byte[] json(String value){return value.getBytes(StandardCharsets.UTF_8);}
 private static void file(Path folder,String name,byte[] data,String title,String artist)throws Exception{Path path=folder.resolve(name);Files.write(path,data);SongMetadata actual=SongMetadata.local(path,name.replaceFirst("\\.[^.]+$",""));check(actual.title().equals(title));check(actual.artist().equals(artist));}
 private static byte[] join(byte[]... parts)throws IOException{var out=new ByteArrayOutputStream();for(var part:parts)out.write(part);return out.toByteArray();}
 private static byte[] little(int value){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();}
 private static byte[] big(int value){return ByteBuffer.allocate(4).putInt(value).array();}
 private static byte[] field(String key,String value)throws IOException{byte[] bytes=json(key+"="+value);return join(little(bytes.length),bytes);}
 private static byte[] atom(String type,byte[] payload)throws IOException{return join(big(payload.length+8),type.getBytes(StandardCharsets.ISO_8859_1),payload);}
 private static byte[] frame(String id,String value)throws IOException{byte[] bytes=join(new byte[]{3},json(value));return join(json(id),big(bytes.length),new byte[2],bytes);}
 public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("music-metadata-owned-");try{
   byte[] frames=join(frame("TIT2","标签标题"),frame("TPE1","标签作者"));int size=frames.length;byte[] header={'I','D','3',3,0,0,(byte)(size>>21),(byte)((size>>14)&127),(byte)((size>>7)&127),(byte)(size&127)};
   file(folder,"id3.mp3",join(header,frames),"标签标题","标签作者");
   byte[] info=join(json("INFO"),json("INAM"),little(8),json("WavName"),new byte[]{0},json("IART"),little(10),json("WavArtist"),new byte[]{0});byte[] riff=join(json("RIFF"),little(info.length+12),json("WAVE"),json("LIST"),little(info.length),info);file(folder,"wave.wav",riff,"WavName","WavArtist");
   byte[] comments=join(little(0),little(2),field("TITLE","VorbisTitle"),field("ARTIST","VorbisArtist"));int n=comments.length;file(folder,"flac.flac",join(json("fLaC"),new byte[]{(byte)132,(byte)(n>>16),(byte)(n>>8),(byte)n},comments),"VorbisTitle","VorbisArtist");
   byte[] packet=join(new byte[]{3},json("vorbis"),comments);byte[] ogg=new byte[27];System.arraycopy(json("OggS"),0,ogg,0,4);ogg[26]=1;file(folder,"ogg.ogg",join(ogg,new byte[]{(byte)packet.length},packet),"VorbisTitle","VorbisArtist");
   byte[] name=atom("\u00a9nam",atom("data",join(big(1),big(0),json("Mp4Title"))));byte[] artist=atom("\u00a9ART",atom("data",join(big(1),big(0),json("Mp4Artist"))));file(folder,"mp4.m4a",join(atom("ftyp",json("M4A ")),atom("moov",atom("udta",atom("meta",join(big(0),atom("ilst",join(name,artist))))))),"Mp4Title","Mp4Artist");
   file(folder,"fallback.wav",WAV,"fallback","未知作者");file(folder,"invalid.mp3",new byte[]{'I','D','3',4,0,0,127,127,127,127},"invalid","未知作者");
   JsonObject song=JsonParser.parseString("{\"code\":200,\"songs\":[{\"id\":123,\"name\":\"NetTitle\",\"duration\":3000,\"artists\":[{\"name\":\"NetArtist\"}]}]}").getAsJsonObject();var parsed=SongMetadata.netease(song,123);check(parsed.title().equals("NetTitle"));check(parsed.artist().equals("NetArtist"));
   try{SongMetadata.netease(song,124);throw new AssertionError();}catch(IOException expected){check(expected.getMessage().equals("platform_response_invalid"));}
   AtomicInteger detail=new AtomicInteger(),media=new AtomicInteger();
   var net=PlatformAccountSource.createAutomatic(123,folder,()->new PlatformAccountSource.Session("OWNED_FIXTURE","higher",()->true),(request,limit,cancel)->{
    if(request.uri().getHost().equals("music.163.com")){check(request.headers().firstValue("Cookie").orElse("").equals("OWNED_FIXTURE"));if(request.uri().getPath().equals("/api/song/detail/")){detail.incrementAndGet();return reply(request,200,json(song.toString()));}return reply(request,200,json("{\"code\":200,\"data\":[{\"id\":123,\"code\":200,\"url\":\"https://owned.music.126.net/a.wav\"}]}"));}
    media.incrementAndGet();check(request.headers().firstValue("Cookie").isEmpty()&&request.headers().firstValue("Authorization").isEmpty());return reply(request,200,WAV);
   }).acquire(new BurnCoordinator.Cancellation(),p->{});check(net.title().equals("NetTitle")&&net.credit().equals("NetArtist"));check(detail.get()==1&&media.get()==1);Files.delete(net.path());
   var qq=ExistingPlatformClientSource.createAutomatic(ExistingPlatformClientSource.route("12345","qq_music"),folder,()->new ExistingPlatformClientSource.Session("OWNED_FIXTURE",()->true),(request,limit,cancel)->{
    if(request.uri().getHost().equals("u.y.qq.com")){if(body(request).contains("\"songinfo\""))return reply(request,200,json("{\"code\":0,\"songinfo\":{\"code\":0,\"data\":{\"track_info\":{\"mid\":\"003ENTRY042abc\",\"name\":\"QqTitle\",\"singer\":[{\"name\":\"QqArtist\"}]}}}}"));return reply(request,200,json("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"midurlinfo\":[{\"purl\":\"a.wav\"}],\"sip\":[\"https://owned.qqmusic.qq.com/\"]}}}"));}
    check(request.headers().firstValue("Cookie").isEmpty());return reply(request,200,WAV);
   }).acquire(new BurnCoordinator.Cancellation(),p->{});check(qq.title().equals("QqTitle")&&qq.credit().equals("QqArtist"));Files.delete(qq.path());
   var bili=ExistingPlatformClientSource.createAutomatic(ExistingPlatformClientSource.route("https://www.bilibili.com/video/BV1Q5411x7aE?p=2","bilibili"),folder,()->new ExistingPlatformClientSource.Session("OWNED_FIXTURE",()->true),(request,limit,cancel)->{
    if(request.uri().getPath().endsWith("view"))return reply(request,200,json("{\"code\":0,\"data\":{\"title\":\"VideoTitle\",\"owner\":{\"name\":\"VideoAuthor\"},\"pages\":[{\"cid\":41},{\"cid\":42,\"part\":\"SecondPart\"}]}}"));
    if(request.uri().getPath().endsWith("playurl")){check(request.uri().getQuery().contains("cid=42"));return reply(request,200,json("{\"code\":0,\"data\":{\"dash\":{\"audio\":[{\"baseUrl\":\"https://owned.bilivideo.com/a.wav\"}]}}}"));}
    check(request.headers().firstValue("Cookie").isEmpty());return reply(request,200,WAV);
   }).acquire(new BurnCoordinator.Cancellation(),p->{});check(bili.title().equals("VideoTitle / SecondPart")&&bili.credit().equals("VideoAuthor"));Files.delete(bili.path());
   Path preference=folder.resolve("preference.json");BurnConfirmation toggle=new BurnConfirmation(preference);check(!toggle.get());check(toggle.set(true));check(new BurnConfirmation(preference).get());check(Files.readString(preference).equals("{\"confirmed\":true}"));check(toggle.set(false));check(!new BurnConfirmation(preference).get());Files.writeString(preference,"{\"confirmed\":\"true\"}");check(!new BurnConfirmation(preference).get());
   check(SongMetadata.clean("a\nb",120,"").equals("a b"));check(new SongMetadata("x".repeat(140),"a".repeat(2000)).title().length()==120);check(new SongMetadata(""," ").artist().equals("未知作者"));
   System.out.println("{\"metadataAndConfirmationChecks\":"+checks+",\"actualNetworkCalls\":0,\"accountDataRead\":false,\"mcStarted\":false}");
  }finally{try(var files=Files.walk(folder)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
