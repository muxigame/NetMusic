package net.muxigame.musiccompat.burn;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sound.sampled.*;

/** Synthetic account/API loopback fixtures; never reads real configuration or calls a platform. */
public final class ExistingPlatformClientSourceTest {
    static int checks,scenarios;static void check(boolean value){checks++;if(!value)throw new AssertionError("existing platform fixture failed");}
    static String mid="003OUlho2HcRHC",bv="BV1xx411c7mD";
    static AtomicReference<String> cookie=new AtomicReference<>("OFFLINE_PLATFORM_ACCOUNT");
    static AtomicInteger calls=new AtomicInteger(),mediaCalls=new AtomicInteger();
    static JsonObject song,vkey,view,play;static byte[] wave,media;static int apiStatus,mediaStatus;static boolean changeApi,changeMedia;
    static void reset(){
        calls.set(0);mediaCalls.set(0);cookie.set("OFFLINE_PLATFORM_ACCOUNT");apiStatus=mediaStatus=200;changeApi=changeMedia=false;media=wave;
        song=json("{\"code\":0,\"songinfo\":{\"code\":0,\"data\":{\"track_info\":{\"mid\":\""+mid+"\"}}}}");
        vkey=json("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"sip\":[\"http://isure.stream.qqmusic.qq.com/\"],\"midurlinfo\":[{\"songmid\":\""+mid+"\",\"purl\":\"audio.mp3?key=OFFLINE_ONLY\"}]}}}");
        view=json("{\"code\":0,\"data\":{\"cid\":123,\"pages\":[{\"cid\":123},{\"cid\":456}]}}");
        play=json("{\"code\":0,\"data\":{\"dash\":{\"audio\":[{\"bandwidth\":100,\"baseUrl\":\"https://xy.bilivideo.com/audio.m4a?fixture=ONLY\"}]}}}");
    }
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static byte[] body(HttpRequest request)throws Exception{
        var result=new java.util.concurrent.CompletableFuture<byte[]>();var bytes=new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>(){
            public void onSubscribe(java.util.concurrent.Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(java.nio.ByteBuffer b){byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);}
            public void onError(Throwable e){result.completeExceptionally(e);}
            public void onComplete(){result.complete(bytes.toByteArray());}
        });return result.get(5,java.util.concurrent.TimeUnit.SECONDS);
    }
    static ExistingPlatformClientSource.Route route(String value,String type){return ExistingPlatformClientSource.route(value,type);}
    static void routed(String input,String selected,ExistingPlatformClientSource.Platform p,String id,int page){scenarios++;var r=route(input,selected);check(r.platform()==p);check(r.identifier().equals(id));check(r.page()==page);}
    static BurnCoordinator.Source source(String input,String type,Path folder,ExistingPlatformClientSource.Transport transport){
        return ExistingPlatformClientSource.create(route(input,type),folder,"Synthetic owned fixture","OWNED-REDISTRIBUTION","Fixture creator",()->{
            String snapshot=cookie.get();return new ExistingPlatformClientSource.Session(snapshot,()->snapshot.equals(cookie.get()));
        },transport);
    }
    static void acquired(String input,String type,Path folder,ExistingPlatformClientSource.Transport transport)throws Exception{
        scenarios++;var a=source(input,type,folder,transport).acquire(new BurnCoordinator.Cancellation(),p->{});
        check(Arrays.equals(Files.readAllBytes(a.path()),media));check(!a.toString().contains("OFFLINE_PLATFORM_ACCOUNT"));Files.delete(a.path());
    }
    static void denied(String input,String type,Path folder,ExistingPlatformClientSource.Transport transport,String error)throws Exception{
        scenarios++;try{source(input,type,folder,transport).acquire(new BurnCoordinator.Cancellation(),p->{});throw new AssertionError("accepted denial");}
        catch(IOException expected){check(expected.getMessage().equals(error));check(!expected.toString().contains("OFFLINE_PLATFORM_ACCOUNT"));check(!expected.toString().contains("OFFLINE_ONLY"));}
        try(var files=Files.list(folder)){check(files.findAny().isEmpty());}
    }
    public static void main(String[] args)throws Exception{
        routed("1868207326","netease",ExistingPlatformClientSource.Platform.NETEASE,"1868207326",1);
        routed("\u200b\u3000１８６８２０７３２６\ufeff","netease",ExistingPlatformClientSource.Platform.NETEASE,"1868207326",1);
        routed("123456","qq_music",ExistingPlatformClientSource.Platform.QQ,"123456",1);
        routed("netmusiclib://source/qq_music?id=123456&duration=240","netease",ExistingPlatformClientSource.Platform.QQ,"123456",1);
        routed("netmusiclib://source/netease?id=1868207326&duration=180","qq_music",ExistingPlatformClientSource.Platform.NETEASE,"1868207326",1);
        routed("netmusiclib://source/bilibili?id="+bv+"&duration=240","netease",ExistingPlatformClientSource.Platform.BILIBILI,bv,1);
        routed("https://www.bilibili.com/video/"+bv+"/?p=2","qq_music",ExistingPlatformClientSource.Platform.BILIBILI,bv,2);
        routed("https://y.qq.com/n/ryqq/songDetail/"+mid,"netease",ExistingPlatformClientSource.Platform.QQ,mid,1);
        routed("https://y.qq.com/n/yqq/song/"+mid+".html","netease",ExistingPlatformClientSource.Platform.QQ,mid,1);
        routed("https://i.y.qq.com/v8/playsong.html?songid=123456","netease",ExistingPlatformClientSource.Platform.QQ,"123456",1);
        routed("https://music.163.com/#/song?id=1868207326","qq_music",ExistingPlatformClientSource.Platform.NETEASE,"1868207326",1);
        routed("https://example.org/legal/audio.wav","qq_music",ExistingPlatformClientSource.Platform.DIRECT,"https://example.org/legal/audio.wav",1);
        for(String bad:List.of("https://y.qq.com@evil.test/song?songid=123","netmusiclib://source/qq_music?id=123&id=456","netmusiclib://source/qq_music?id=9223372036854775808","https://b23.tv/SHORT","https://www.bilibili.com/video/"+bv+"?p=0","https://y.qq.com/playlist/123","not_a_song","18\u200b68207326")){
            scenarios++;check(route(bad,"netease").platform()==ExistingPlatformClientSource.Platform.INVALID);
        }
        // Host lookalikes remain ordinary direct input; the direct decoder rejects HTML.
        check(route("https://y.qq.com.evil.test/audio.wav","qq_music").platform()==ExistingPlatformClientSource.Platform.DIRECT);
        var bytes=new ByteArrayOutputStream();try(var pcm=new AudioInputStream(new ByteArrayInputStream(new byte[16000]),new AudioFormat(8000,16,1,true,false),8000)){AudioSystem.write(pcm,AudioFileFormat.Type.WAVE,bytes);}wave=bytes.toByteArray();reset();
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/api",exchange->{
            calls.incrementAndGet();check("OFFLINE_PLATFORM_ACCOUNT".equals(exchange.getRequestHeaders().getFirst("Cookie"))||cookie.get().isEmpty()&&exchange.getRequestHeaders().getFirst("Cookie")==null);
            String kind=exchange.getRequestHeaders().getFirst("Fixture-Kind");byte[] body=(switch(kind){case "song"->song;case "vkey"->vkey;case "view"->view;default->play;}).toString().getBytes(StandardCharsets.UTF_8);
            if(changeApi)cookie.set("OTHER_OFFLINE_ACCOUNT");exchange.sendResponseHeaders(apiStatus,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.createContext("/media",exchange->{
            mediaCalls.incrementAndGet();check(exchange.getRequestHeaders().getFirst("Cookie")==null);check(exchange.getRequestHeaders().getFirst("Authorization")==null);
            if(changeMedia)cookie.set("OTHER_OFFLINE_ACCOUNT");exchange.sendResponseHeaders(mediaStatus,media.length);try(var out=exchange.getResponseBody()){out.write(media);}
        });server.start();Path folder=Files.createTempDirectory("muxi-existing-platform-test-");
        ExistingPlatformClientSource.Transport transport=(request,limit,cancel)->{
            check(request.headers().firstValue("Authorization").isEmpty());String host=request.uri().getHost();
            boolean api=Set.of("u.y.qq.com","api.bilibili.com").contains(host);String kind="media";byte[] post=null;
            if(api){
                if(host.equals("u.y.qq.com")){
                    check(request.uri().toString().equals("https://u.y.qq.com/cgi-bin/musicu.fcg"));check(request.method().equals("POST"));
                    post=body(request);var payload=json(new String(post,StandardCharsets.UTF_8));kind=payload.has("songinfo")?"song":"vkey";
                    if(kind.equals("song"))check(payload.getAsJsonObject("songinfo").getAsJsonObject("param").get("song_id").getAsLong()==123456);
                    else{
                        var param=payload.getAsJsonObject("req_0").getAsJsonObject("param");check(param.getAsJsonArray("filename").size()==2);
                        check(param.getAsJsonArray("filename").get(0).getAsString().equals("M800"+mid+".mp3"));
                        check(param.getAsJsonArray("filename").get(1).getAsString().equals("M500"+mid+".mp3"));
                        check(param.getAsJsonArray("songmid").size()==2&&param.getAsJsonArray("songtype").size()==2);
                    }
                }else{
                    check(request.method().equals("GET"));kind=request.uri().getPath().endsWith("/view")?"view":"play";
                    if(kind.equals("play")){check(request.uri().getRawQuery().contains("fnval=4048"));if(expectedPage==2)check(request.uri().getRawQuery().contains("cid=456"));}
                }
            }else{
                check(request.uri().getScheme().equals("https"));check(request.headers().firstValue("Cookie").isEmpty());
                if(host.endsWith("bilivideo.com")){check(request.headers().firstValue("Referer").orElse("").equals("https://www.bilibili.com/"));check(request.headers().firstValue("Origin").orElse("").equals("https://www.bilibili.com"));}
            }
            var local=HttpRequest.newBuilder(URI.create("http://localhost:"+server.getAddress().getPort()+(api?"/api":"/media"))).header("Fixture-Kind",kind).GET();
            if(post!=null)local.POST(HttpRequest.BodyPublishers.ofByteArray(post)).header("Content-Type","application/json");
            request.headers().firstValue("Cookie").ifPresent(v->local.header("Cookie",v));request.headers().firstValue("Referer").ifPresent(v->local.header("Referer",v));
            return BurnHttp.send(local.build(),limit,cancel,n->{});
        };
        try{
            expectedSong=true;acquired("123456","qq_music",folder,transport);check(calls.get()==2&&mediaCalls.get()==1);
            reset();expectedSong=false;acquired(mid,"qq_music",folder,transport);check(calls.get()==1&&mediaCalls.get()==1);
            reset();expectedPage=2;acquired("https://www.bilibili.com/video/"+bv+"/?p=2","qq_music",folder,transport);check(calls.get()==2&&mediaCalls.get()==1);expectedPage=1;
            reset();cookie.set("");acquired(bv,"bilibili",folder,transport); // Actual public normal response may work without login.
            reset();vkey.getAsJsonObject("req_0").getAsJsonObject("data").getAsJsonArray("midurlinfo").get(0).getAsJsonObject().addProperty("purl","");
            denied(mid,"qq_music",folder,transport,"platform_permission_denied");check(mediaCalls.get()==0);
            reset();vkey.getAsJsonObject("req_0").addProperty("code",1000);denied(mid,"qq_music",folder,transport,"platform_permission_denied");
            reset();vkey.getAsJsonObject("req_0").getAsJsonObject("data").getAsJsonArray("midurlinfo").get(0).getAsJsonObject().addProperty("purl","RS02"+mid+".mp3");denied(mid,"qq_music",folder,transport,"platform_media_unavailable");check(mediaCalls.get()==0);
            reset();play.addProperty("code",-101);denied(bv,"bilibili",folder,transport,"platform_login_expired");
            reset();play.getAsJsonObject("data").addProperty("is_preview",1);denied(bv,"bilibili",folder,transport,"platform_trial_only");check(mediaCalls.get()==0);
            reset();play.getAsJsonObject("data").getAsJsonObject("dash").addProperty("drmProtected",true);denied(bv,"bilibili",folder,transport,"platform_drm_unsupported");
            reset();play.getAsJsonObject("data").getAsJsonObject("dash").remove("audio");denied(bv,"bilibili",folder,transport,"platform_media_unavailable");
            reset();play.getAsJsonObject("data").getAsJsonObject("dash").getAsJsonArray("audio").get(0).getAsJsonObject().addProperty("baseUrl","https://evil.test/steal");denied(bv,"bilibili",folder,transport,"platform_media_url_refused");
            reset();changeApi=true;denied(bv,"bilibili",folder,transport,"platform_account_changed");check(mediaCalls.get()==0);
            reset();changeMedia=true;denied(bv,"bilibili",folder,transport,"platform_account_changed");
            reset();apiStatus=302;denied(mid,"qq_music",folder,transport,"platform_redirect_refused");check(mediaCalls.get()==0);
            reset();mediaStatus=403;denied(mid,"qq_music",folder,transport,"platform_media_unavailable");
            reset();media="HTML account rejected".getBytes(StandardCharsets.UTF_8);denied(mid,"qq_music",folder,transport,"unsupported_audio");
            reset();scenarios++;var cancellation=new BurnCoordinator.Cancellation();cancellation.cancel();
            try{source(mid,"qq_music",folder,transport).acquire(cancellation,p->{});throw new AssertionError();}catch(InterruptedIOException expected){check(calls.get()==0);}
            reset();scenarios++;try{ExistingPlatformClientSource.create(route(mid,"qq_music"),folder,"fixture","OWNED-REDISTRIBUTION","owner",()->new ExistingPlatformClientSource.Session("bad\r\nCookie",()->true),transport).acquire(new BurnCoordinator.Cancellation(),p->{});throw new AssertionError();}catch(IOException expected){check(expected.getMessage().equals("platform_login_required"));check(calls.get()==0);}
            check(new ExistingPlatformClientSource.Session("SECRET",()->true).toString().equals("ExistingPlatformSession[redacted]"));
            System.out.println("{\"checks\":"+checks+",\"scenarios\":"+scenarios+",\"loopbackOnly\":true,\"realAccountRead\":false,\"realPlatformCalls\":0,\"nativeMcAccepted\":false}");
        }finally{server.stop(0);try(var files=Files.list(folder)){for(Path file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(folder);}
    }
    static boolean expectedSong;static int expectedPage=1;
}
