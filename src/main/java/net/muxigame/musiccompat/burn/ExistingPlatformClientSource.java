package net.muxigame.musiccompat.burn;

import com.google.gson.*;
import com.gly091020.netMusicListNeoforge.NetMusicList;
import com.gly091020.netMusicListNeoforge.media.qq_music.QQMusicUtil;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Acquires the existing QQ/Bilibili account's normal media on the physical client.
 * Credentials and media leases remain in memory and never enter upload metadata. */
public final class ExistingPlatformClientSource {
    public enum Platform { NETEASE, QQ, BILIBILI, DIRECT, INVALID }
    public record Route(Platform platform,String identifier,int page) {
        @Override public String toString(){return "ExistingPlatformRoute["+platform+",page="+page+"]";}
    }
    public interface Sessions {Session snapshot()throws Exception;}
    public interface Transport {HttpResponse<byte[]> send(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel)throws Exception;}
    public static final class Session {
        private final String cookie; private final BooleanSupplier current;
        public Session(String cookie,BooleanSupplier current){this.cookie=cookie==null?"":cookie;this.current=current;}
        void check()throws IOException{
            if(cookie.length()>16384||cookie.indexOf('\r')>=0||cookie.indexOf('\n')>=0)throw new IOException("platform_login_required");
            if(!current.getAsBoolean())throw new IOException("platform_account_changed");
        }
        @Override public String toString(){return "ExistingPlatformSession[redacted]";}
    }
    private ExistingPlatformClientSource(){}
    /** Explicit source URIs/page hosts win over UI selection; public direct audio stays direct. */
    public static Route route(String value,String selectedType){
        try{
            String input=normalizeInput(value);
            if(input.length()>8192)return invalid();
            URI uri=URI.create(input);String scheme=uri.getScheme();
            if("netmusiclib".equals(scheme)){
                if(uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null)return invalid();
                String type=uri.getPath();type=type==null||type.isEmpty()?uri.getHost():type.replaceFirst("^/","");
                return identified(type,query(uri,"id"),1);
            }
            if(scheme!=null){
                if(!Set.of("http","https").contains(scheme.toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)return invalid();
                String host=uri.getHost().toLowerCase(Locale.ROOT);while(host.endsWith("."))host=host.substring(0,host.length()-1);
                if(host.equals("music.163.com")){
                    Long song=PlatformDownloadSource.songId(input);return song==null?invalid():new Route(Platform.NETEASE,song.toString(),1);
                }
                if(host.equals("y.qq.com")||host.equals("i.y.qq.com")){
                    String id=query(uri,"songid");if(id==null)id=query(uri,"songmid");
                    if(id==null&&uri.getPath()!=null){var m=java.util.regex.Pattern.compile("/(?:n/ryqq/songDetail|song)/([A-Za-z0-9]+)(?:\\.html)?/?$").matcher(uri.getPath());if(m.find())id=m.group(1);}
                    return identified("qq_music",id,1);
                }
                if(host.equals("bilibili.com")||host.equals("www.bilibili.com")||host.equals("m.bilibili.com")){
                    var m=java.util.regex.Pattern.compile("/video/(BV[A-Za-z0-9]{10})(?:/|$)").matcher(uri.getPath());
                    String page=query(uri,"p");int p=page==null?1:Integer.parseInt(page);
                    return m.find()?identified("bilibili",m.group(1),p):invalid();
                }
                // Page short links need redirects, which this acquisition boundary does not follow.
                if(host.equals("b23.tv")||host.endsWith(".music.163.com")||host.equals("music.126.net")||host.endsWith(".music.126.net"))return invalid();
                return new Route(Platform.DIRECT,input,1);
            }
            if(input.matches("BV[A-Za-z0-9]{10}"))return identified("bilibili",input,1);
            return identified(selectedType,input,1);
        }catch(RuntimeException invalid){return invalid();}
    }
    private static Route invalid(){return new Route(Platform.INVALID,"",1);}
    private static Route identified(String type,String id,int page){
        if(type==null||id==null||page<1||page>10000)return invalid();
        if(type.equals("bilibili")&&id.matches("BV[A-Za-z0-9]{10}"))return new Route(Platform.BILIBILI,id,page);
        if(type.equals("qq_music")&&(id.matches("[1-9][0-9]{0,18}")||id.matches("[A-Za-z0-9]{14}"))){if(id.matches("[0-9]+"))Long.parseLong(id);return new Route(Platform.QQ,id,1);}
        if(Set.of("netease","netmusic","netease_music","").contains(type)&&id.matches("[1-9][0-9]{0,18}")){Long.parseLong(id);return new Route(Platform.NETEASE,id,1);}
        return invalid();
    }
    private static String normalizeInput(String value){
        if(value==null)return "";int start=0,end=value.length();
        while(start<end&&edge(value.charAt(start)))start++;
        while(end>start&&edge(value.charAt(end-1)))end--;
        String input=value.substring(start,end);
        String digits=java.text.Normalizer.normalize(input,java.text.Normalizer.Form.NFKC);
        return digits.matches("[0-9]+")?digits:input;
    }
    private static boolean edge(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c)||c=='\u200b'||c=='\ufeff';}
    private static String query(URI uri,String wanted){
        String query=uri.getRawQuery();if(query==null)return null;String answer=null;
        for(String pair:query.split("&")){String[] p=pair.split("=",2);if(p.length==2&&URLDecoder.decode(p[0],StandardCharsets.UTF_8).equals(wanted)){
            if(answer!=null)throw new IllegalArgumentException();answer=URLDecoder.decode(p[1],StandardCharsets.UTF_8);
        }}return answer;
    }
    public static BurnCoordinator.Source createClient(Route route,Path temp,String title,String license,String credit,Object connection){
        return client(route,temp,title,license,credit,connection,false);
    }
    public static BurnCoordinator.Source createAutomaticClient(Route route,Path temp,Object connection){return client(route,temp,"","OWNED-REDISTRIBUTION","",connection,true);}
    private static BurnCoordinator.Source client(Route route,Path temp,String title,String license,String credit,Object connection,boolean automatic){
        return createInternal(route,temp,title,license,credit,()->{
            if(FMLEnvironment.dist!=Dist.CLIENT)throw new IOException("platform_client_session_unavailable");
            var mc=Minecraft.getInstance();
            if(connection==null||mc.player==null||mc.getConnection()!=connection)throw new IOException("platform_account_changed");
            String cookie=configCookie(route.platform());
            return new Session(cookie,()->mc.player!=null&&mc.getConnection()==connection&&Objects.equals(cookie,configCookie(route.platform())));
        },ExistingPlatformClientSource::clientTransport,automatic);
    }
    private static HttpResponse<byte[]> clientTransport(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel)throws Exception{
        for(InetAddress address:InetAddress.getAllByName(request.uri().getHost()))
            if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||address instanceof Inet6Address&&(address.getAddress()[0]&0xfe)==0xfc)throw new IOException("private_address_unavailable");
        return BurnHttp.send(request,limit,cancel,n->{});
    }
    private static String configCookie(Platform platform){return platform==Platform.QQ?NetMusicList.CONFIG.qqCookie:NetMusicList.CONFIG.bilibiliCookie;}
    public static BurnCoordinator.Source create(Route route,Path temp,String title,String license,String credit,Sessions sessions,Transport transport){return createInternal(route,temp,title,license,credit,sessions,transport,false);}
    public static BurnCoordinator.Source createAutomatic(Route route,Path temp,Sessions sessions,Transport transport){return createInternal(route,temp,"","OWNED-REDISTRIBUTION","",sessions,transport,true);}
    private static BurnCoordinator.Source createInternal(Route route,Path temp,String title,String license,String credit,Sessions sessions,Transport transport,boolean automatic){
        return (cancel,progress)->{
            cancel.check();if(route.platform()!=Platform.QQ&&route.platform()!=Platform.BILIBILI)throw new IOException("platform_source_invalid");
            Session session=sessions.snapshot();session.check();
            SongMetadata[] info={null};LyricOrigin[] origin={route.platform()==Platform.BILIBILI?new LyricOrigin("bilibili",route.identifier(),route.page()):route.identifier().matches("[1-9][0-9]{0,18}")?new LyricOrigin("qq",route.identifier(),1):LyricOrigin.NONE};java.util.function.Consumer<SongMetadata> metadata=automatic?value->info[0]=value:null;
            URI media;
            try{media=route.platform()==Platform.QQ?qq(route,session,transport,cancel,metadata,origin):bilibili(route,session,transport,cancel,metadata);}
            catch(RuntimeException invalid){throw new IOException("platform_response_invalid");}
            HttpRequest.Builder builder=HttpRequest.newBuilder(media).timeout(Duration.ofSeconds(120)).header("User-Agent","Mozilla/5.0");
            if(route.platform()==Platform.BILIBILI)builder.header("Referer","https://www.bilibili.com/").header("Origin","https://www.bilibili.com");
            // Deliberately no platform Cookie or Muxi Bearer on the media request.
            HttpResponse<byte[]> response=transport.send(builder.GET().build(),(int)BurnCoordinator.MAX_BYTES,cancel);
            session.check();cancel.check();status(response,true);
            if(response.body().length==0||response.body().length>BurnCoordinator.MAX_BYTES)throw new IOException("invalid_audio_size");
            Path file=null;
            try{
                Files.createDirectories(temp);file=Files.createTempFile(temp,"muxi-platform-",".audio");Files.write(file,response.body());
                cancel.check();session.check();BurnSources.supportedHeader(file,cancel);
                progress.accept(new BurnCoordinator.Progress(null,BurnCoordinator.Stage.ACQUIRING,response.body().length,response.body().length,""));
                if(automatic&&info[0]==null)throw new IOException("platform_response_invalid");
                return new BurnCoordinator.Audio(file,automatic?info[0].title():title,license,automatic?info[0].artist():credit,origin[0]);
            }catch(Exception failed){if(file!=null)Files.deleteIfExists(file);throw failed;}
        };
    }
    private static URI qq(Route route,Session session,Transport transport,BurnCoordinator.Cancellation cancel,java.util.function.Consumer<SongMetadata> metadata,LyricOrigin[] origin)throws Exception{
        String mid=route.identifier();
        if(mid.matches("[1-9][0-9]{0,18}")||metadata!=null){
            JsonObject detail=QQMusicUtil.buildSongInfoData(mid.matches("[0-9]+")?Long.parseLong(mid):0).getAsJsonObject();
            if(!mid.matches("[0-9]+")){var param=detail.getAsJsonObject("songinfo").getAsJsonObject("param");param.remove("song_id");param.addProperty("song_mid",mid);}
            JsonObject result=api(URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg"),detail.toString(),session,transport,cancel);
            JsonObject section=section(result,"songinfo");JsonObject data=section.getAsJsonObject("data");
            JsonObject track=data.getAsJsonObject("track_info");if(track.has("id")&&!track.get("id").isJsonNull()){String id=track.get("id").getAsString();if(id.matches("[1-9][0-9]{0,18}"))origin[0]=new LyricOrigin("qq",id,1);}if(metadata!=null)metadata.accept(SongMetadata.qq(track));mid=text(track,"mid");
            if(mid==null||!mid.matches("[A-Za-z0-9]{14}"))throw new IOException("platform_response_invalid");
        }
        JsonObject payload=QQMusicUtil.buildSongURLData(mid).getAsJsonObject();
        JsonObject param=payload.getAsJsonObject("req_0").getAsJsonObject("param");
        // Keep the original normal MP3 candidates; do not use the additional
        // RS02 variant as a fallback when those normal candidates are denied.
        JsonArray names=param.getAsJsonArray("filename");
        for(int index=names.size()-1;index>=0;index--)if(names.get(index).getAsString().startsWith("RS02")){
            names.remove(index);param.getAsJsonArray("songmid").remove(index);param.getAsJsonArray("songtype").remove(index);
        }
        JsonObject result=api(URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg"),payload.toString(),session,transport,cancel);
        JsonObject data=section(result,"req_0").getAsJsonObject("data");restrictions(data);
        JsonArray urls=data.getAsJsonArray("midurlinfo"),servers=data.getAsJsonArray("sip");
        if(urls==null||servers==null||servers.isEmpty())throw new IOException("platform_media_unavailable");
        for(JsonElement item:urls){JsonObject entry=item.getAsJsonObject();restrictions(entry);String purl=text(entry,"purl");
            if(purl==null||purl.isBlank())continue;
            if(purl.startsWith("RS02"))throw new IOException("platform_media_unavailable");
            String returnedMid=text(entry,"songmid");
            if(returnedMid!=null&&!returnedMid.equals(mid))throw new IOException("platform_response_invalid");
            return mediaUrl(URI.create(servers.get(0).getAsString()).resolve(purl),Platform.QQ);
        }
        throw new IOException("platform_permission_denied");
    }
    private static JsonObject section(JsonObject root,String name)throws IOException{
        if(root.has("code")&&root.get("code").getAsInt()!=0)throw new IOException("platform_permission_denied");
        JsonObject section=root.getAsJsonObject(name);if(section==null)throw new IOException("platform_response_invalid");
        if(section.has("code")&&section.get("code").getAsInt()!=0)throw new IOException("platform_permission_denied");return section;
    }
    private static URI bilibili(Route route,Session session,Transport transport,BurnCoordinator.Cancellation cancel,java.util.function.Consumer<SongMetadata> metadata)throws Exception{
        JsonObject view=api(URI.create("https://api.bilibili.com/x/web-interface/view?bvid="+route.identifier()),null,session,transport,cancel);
        JsonObject data=biliData(view);if(metadata!=null)metadata.accept(SongMetadata.bilibili(data,route.page()));long cid;
        JsonArray pages=data.getAsJsonArray("pages");
        if(pages!=null&&!pages.isEmpty()){
            if(route.page()>pages.size())throw new IOException("platform_source_invalid");cid=pages.get(route.page()-1).getAsJsonObject().get("cid").getAsLong();
        }else{if(route.page()!=1)throw new IOException("platform_source_invalid");cid=data.get("cid").getAsLong();}
        if(cid<1)throw new IOException("platform_response_invalid");
        JsonObject play=biliData(api(URI.create("https://api.bilibili.com/x/player/playurl?bvid="+route.identifier()+"&cid="+cid+"&fnval=4048"),null,session,transport,cancel));
        restrictions(play);JsonObject dash=play.getAsJsonObject("dash");if(dash==null)throw new IOException("platform_media_unavailable");restrictions(dash);
        JsonArray audio=dash.getAsJsonArray("audio");if(audio==null||audio.isEmpty())throw new IOException("platform_media_unavailable");
        JsonObject selected=null;long bandwidth=-1;
        for(JsonElement item:audio){JsonObject entry=item.getAsJsonObject();restrictions(entry);long current=entry.has("bandwidth")?entry.get("bandwidth").getAsLong():0;
            if(selected==null||current>bandwidth){selected=entry;bandwidth=current;}}
        String url=text(selected,"baseUrl");if(url==null)url=text(selected,"base_url");if(url==null||url.isBlank())throw new IOException("platform_media_unavailable");
        return mediaUrl(URI.create(url),Platform.BILIBILI);
    }
    private static JsonObject biliData(JsonObject root)throws IOException{
        int code=root.get("code").getAsInt();if(code==-101||code==-111)throw new IOException("platform_login_expired");
        if(code!=0)throw new IOException("platform_permission_denied");JsonObject data=root.getAsJsonObject("data");if(data==null)throw new IOException("platform_response_invalid");return data;
    }
    private static JsonObject api(URI uri,String payload,Session session,Transport transport,BurnCoordinator.Cancellation cancel)throws Exception{
        session.check();cancel.check();HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent","Mozilla/5.0");
        if(!session.cookie.isBlank())b.header("Cookie",session.cookie);
        b.header("Referer",uri.getHost().equals("api.bilibili.com")?"https://www.bilibili.com/":"https://y.qq.com/");
        if(payload==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
        HttpResponse<byte[]> response=transport.send(b.build(),262144,cancel);session.check();cancel.check();status(response,false);
        try{return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException invalid){throw new IOException("platform_response_invalid");}
    }
    private static void status(HttpResponse<byte[]> response,boolean media)throws IOException{
        if(response.statusCode()>=300&&response.statusCode()<400)throw new IOException("platform_redirect_refused");
        if(response.statusCode()==401||response.statusCode()==403)throw new IOException(media?"platform_media_unavailable":"platform_login_expired");
        if(response.statusCode()!=200)throw new IOException(media?"platform_media_unavailable":"platform_api_unavailable");
    }
    private static URI mediaUrl(URI uri,Platform platform)throws IOException{
        String host=uri.getHost();
        if(host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1&&uri.getPort()!=443||!Set.of("https","http").contains(uri.getScheme()))throw new IOException("platform_media_url_refused");
        host=host.toLowerCase(Locale.ROOT);boolean allowed=platform==Platform.QQ?(host.endsWith(".qq.com")||host.endsWith(".qqmusic.qq.com")):
            (host.endsWith(".bilivideo.com")||host.endsWith(".bilivideo.cn")||host.endsWith(".hdslb.com")||host.endsWith(".bilibili.com"));
        if(!allowed)throw new IOException("platform_media_url_refused");
        return uri.getScheme().equals("http")?URI.create("https"+uri.toString().substring(4)):uri;
    }
    private static String text(JsonObject object,String name){return object.has(name)&&!object.get(name).isJsonNull()?object.get(name).getAsString():null;}
    private static boolean positive(JsonObject object,String name){
        if(!object.has(name)||object.get(name).isJsonNull())return false;JsonElement value=object.get(name);
        if(!value.isJsonPrimitive())return value.isJsonObject()?value.getAsJsonObject().size()>0:true;
        JsonPrimitive p=value.getAsJsonPrimitive();return p.isBoolean()?p.getAsBoolean():p.isNumber()?p.getAsDouble()!=0:!Set.of("","0","false").contains(p.getAsString().toLowerCase(Locale.ROOT));
    }
    private static void restrictions(JsonObject object)throws IOException{
        for(String key:List.of("is_preview","isPreview","trial","preview","freeTrialInfo"))if(positive(object,key))throw new IOException("platform_trial_only");
        for(String key:List.of("drm","drmInfo","drmProtected","encrypted"))if(positive(object,key))throw new IOException("platform_drm_unsupported");
        for(String key:List.of("canDownload","downloadAllowed"))if(object.has(key)&&object.get(key).isJsonPrimitive()&&object.get(key).getAsJsonPrimitive().isBoolean()&&!object.get(key).getAsBoolean())throw new IOException("platform_download_denied");
    }
}
