package net.muxigame.musiccompat.burn;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.security.MessageDigest;

public final class PrivatePlaybackTest {
    static int checks;
    static final URI REF=URI.create("https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/ea8bbe98-43f0-462c-bd51-fec644838448.mp3");
    static final byte[] DATA=new byte[]{1,2,3,4};
    static void check(boolean value){checks++;if(!value)throw new AssertionError();}
    static JsonObject answer(long now)throws Exception{
        JsonObject a=new JsonObject();a.addProperty("reference",REF.toString());
        String date=java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochSecond(now));
        a.addProperty("url",REF+"?x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-credential=STS.OFFLINE%2F20261012%2Fcn-hangzhou%2Foss%2Faliyun_v4_request&x-oss-date="+date+"&x-oss-expires=120&x-oss-signature="+"a".repeat(64)+"&x-oss-security-token=OFFLINE_TOKEN");
        a.addProperty("expiresAt",now+120);a.addProperty("maxBytes",DATA.length);a.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(DATA)));return a;
    }
    static final class Reply implements HttpResponse<InputStream>{
        final int status;final byte[] bytes;final Map<String,List<String>> headers;final AtomicInteger closes;
        Reply(int status,byte[] bytes,Map<String,List<String>> headers,AtomicInteger closes){this.status=status;this.bytes=bytes;this.headers=headers;this.closes=closes;}
        public int statusCode(){return status;}public HttpRequest request(){return HttpRequest.newBuilder(REF).GET().build();}
        public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return HttpHeaders.of(headers,(a,b)->true);}
        public InputStream body(){return new ByteArrayInputStream(bytes){public void close(){closes.incrementAndGet();}};}
        public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public URI uri(){return REF;}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
    }
    public static void main(String[] args)throws Exception{
        long now=System.currentTimeMillis()/1000;var good=answer(now);var lease=PrivatePlaybackProtocol.validate(REF,good,now);
        check(!lease.toString().contains("OFFLINE_TOKEN"));check(BurnProtocol.media(REF.toString()));check(!BurnProtocol.media(lease.transientUrl().toString()));
        for(String field:List.of("reference","url","expiresAt","maxBytes","sha256")){
            var bad=good.deepCopy();bad.remove(field);boolean refused=false;try{PrivatePlaybackProtocol.validate(REF,bad,now);}catch(IOException e){refused=true;}check(refused);
        }
        for(String url:List.of(good.get("url").getAsString().replace("https:","http:"),good.get("url").getAsString().replace("/10000/","/10001/"),good.get("url").getAsString()+"&x-oss-expires=120",good.get("url").getAsString()+"&callback=evil",good.get("url").getAsString().replace("x-oss-expires=120","x-oss-expires=9999"))){
            var bad=good.deepCopy();bad.addProperty("url",url);boolean refused=false;try{PrivatePlaybackProtocol.validate(REF,bad,now);}catch(IOException e){refused=true;}check(refused);
        }
        var expired=good.deepCopy();expired.addProperty("expiresAt",now-1);boolean refused=false;try{PrivatePlaybackProtocol.validate(REF,expired,now);}catch(IOException e){refused=true;}check(refused);
        AtomicInteger closes=new AtomicInteger(),requests=new AtomicInteger(),leases=new AtomicInteger();
        HttpRequest original=HttpRequest.newBuilder(REF).header("Authorization","MUST_NOT_FORWARD").header("Cookie","MUST_NOT_FORWARD").GET().build();
        var response=PrivateMediaReader.fetch(original,refresh->{leases.incrementAndGet();return lease;},()->true,request->{
            check(request.headers().firstValue("Authorization").isEmpty());check(request.headers().firstValue("Cookie").isEmpty());requests.incrementAndGet();return new Reply(200,DATA,Map.of("Content-Length",List.of("4")),closes);
        },new BurnCoordinator.Cancellation(),n->{});
        check(Arrays.equals(response.body(),DATA));check(response.uri().equals(REF));check(response.request().uri().equals(REF));check(!response.toString().contains("x-oss-signature"));check(closes.get()==1);check(leases.get()==1);
        closes.set(0);requests.set(0);leases.set(0);
        response=PrivateMediaReader.fetch(original,refresh->{int n=leases.incrementAndGet();check(refresh==(n==2));return lease;},()->true,request->{int n=requests.incrementAndGet();return new Reply(n==1?403:200,n==1?new byte[0]:DATA,Map.of("Content-Length",List.of(n==1?"0":"4")),closes);},new BurnCoordinator.Cancellation(),n->{});
        check(requests.get()==2&&leases.get()==2&&closes.get()==2);check(Arrays.equals(response.body(),DATA));
        requests.set(0);boolean failed=false;
        try{PrivateMediaReader.fetch(original,refresh->lease,()->true,request->{requests.incrementAndGet();return new Reply(403,new byte[0],Map.of(),closes);},new BurnCoordinator.Cancellation(),n->{});}catch(IOException e){failed=true;check(!e.toString().contains("OFFLINE_TOKEN"));}
        check(failed&&requests.get()==2);
        requests.set(0);failed=false;
        try{PrivateMediaReader.fetch(original,refresh->lease,()->false,request->{requests.incrementAndGet();return null;},new BurnCoordinator.Cancellation(),n->{});}catch(IOException e){failed=true;}
        check(failed&&requests.get()==0);
        var cancel=new BurnCoordinator.Cancellation();cancel.cancel();failed=false;
        try{PrivateMediaReader.fetch(original,refresh->lease,()->true,request->{requests.incrementAndGet();return null;},cancel,n->{});}catch(IOException e){failed=true;}
        check(failed&&requests.get()==0);
        failed=false;try{PrivateMediaReader.fetch(original,refresh->lease,()->true,request->new Reply(200,new byte[]{9,9,9,9},Map.of("Content-Length",List.of("4")),closes),new BurnCoordinator.Cancellation(),n->{});}catch(IOException e){failed=true;}check(failed);
        failed=false;try{PrivateMediaReader.fetch(HttpRequest.newBuilder(REF).header("Range","bytes=1-2").GET().build(),refresh->lease,()->true,request->new Reply(206,new byte[]{2,3},Map.of("Content-Length",List.of("2"),"Content-Range",List.of("bytes 1-2/99")),closes),new BurnCoordinator.Cancellation(),n->{});}catch(IOException e){failed=true;}check(failed);
        System.out.println("{\"privatePlaybackChecks\":"+checks+",\"networkRequests\":0,\"mcStarted\":false,\"success\":true}");
    }
}
