package net.muxigame.musiccompat.burn;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Client-only account acquisition boundary. No platform credential enters upload. */
public final class PlatformAccountSource {
    public enum DownloadCapability { ALLOWED, DENIED, UNAVAILABLE }
    public interface DownloadAuthority {DownloadCapability assess(JsonObject raw,long song)throws Exception;}
    public interface Sessions {Session snapshot()throws Exception;}
    public interface Transport {HttpResponse<byte[]> send(HttpRequest request,int limit,BurnCoordinator.Cancellation cancel)throws Exception;}
    public static final class Session {
        private final String cookie,level;private final BooleanSupplier current;
        public Session(String cookie,String level,BooleanSupplier current){this.cookie=cookie;this.level=level;this.current=current;}
        void check()throws IOException{
            if(cookie==null||cookie.isBlank()||cookie.length()>16384||cookie.indexOf('\r')>=0||cookie.indexOf('\n')>=0)throw new IOException("platform_login_required");
            if(!current.getAsBoolean())throw new IOException("platform_account_changed");
            if(!Set.of("standard","higher","exhigh","lossless","hires").contains(level))throw new IOException("platform_capability_unavailable");
        }
        @Override public String toString(){return "PlatformClientSession[redacted]";}
    }
    private record Lease(URI url,long deadline,String format){@Override public String toString(){return "PlatformMediaLease[redacted]";}}
    private PlatformAccountSource(){}
    public static BurnCoordinator.Source create(long song,Path temp,String title,String license,String credit,Sessions sessions,Transport transport){
        // Technical acquisition of the current account's normal media response,
        // not a claim about permanent downloads or redistribution entitlement.
        return create(song,temp,title,license,credit,sessions,transport,(raw,id)->DownloadCapability.ALLOWED);
    }
    public static BurnCoordinator.Source create(long song,Path temp,String title,String license,String credit,Sessions sessions,Transport transport,DownloadAuthority authority){
        return (cancel,progress)->{
            if(song<1)throw new IOException("platform_song_id_required");
            cancel.check();Session session=sessions.snapshot();session.check();
            Lease lease=resolve(song,session,transport,authority,cancel);HttpResponse<byte[]> media=null;
            for(int attempt=0;attempt<2;attempt++){
                cancel.check();session.check();if(System.nanoTime()>=lease.deadline())throw new IOException("platform_media_expired");
                // No Cookie, Core Bearer, user filename or platform headers on media GET.
                HttpRequest request=HttpRequest.newBuilder(lease.url()).timeout(Duration.ofSeconds(120)).GET().build();
                media=transport.send(request,(int)BurnCoordinator.MAX_BYTES,cancel);session.check();cancel.check();
                if((media.statusCode()==401||media.statusCode()==403)&&attempt==0){lease=resolve(song,session,transport,authority,cancel);continue;}
                break;
            }
            if(media.statusCode()>=300&&media.statusCode()<400)throw new IOException("platform_redirect_refused");
            if(media.statusCode()!=200)throw new IOException("platform_media_unavailable");
            if(media.body().length==0||media.body().length>BurnCoordinator.MAX_BYTES)throw new IOException("invalid_audio_size");
            Path file=null;
            try{
                Files.createDirectories(temp);file=Files.createTempFile(temp,"muxi-platform-",".audio");
                Files.write(file,media.body());cancel.check();session.check();
                BurnSources.supportedHeader(file,cancel); // Actual plaintext audio, not a DRM/HTML container.
                progress.accept(new BurnCoordinator.Progress(null,BurnCoordinator.Stage.ACQUIRING,media.body().length,media.body().length,""));
                return new BurnCoordinator.Audio(file,title,license,credit,new LyricOrigin("netease",Long.toString(song),1));
            }catch(Exception failed){if(file!=null)Files.deleteIfExists(file);throw failed;}
        };
    }
    public static BurnCoordinator.Source createAutomatic(long song,Path temp,Sessions sessions,Transport transport){
        return (cancel,progress)->{
            Session session=sessions.snapshot();session.check();cancel.check();
            URI uri=URI.create("https://music.163.com/api/song/detail/?id="+song+"&ids=["+song+"]");
            HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Cookie",session.cookie).header("User-Agent","Mozilla/5.0").GET().build();
            var response=transport.send(request,262144,cancel);session.check();cancel.check();
            if(response.statusCode()==401||response.statusCode()==403)throw new IOException("platform_login_expired");
            if(response.statusCode()!=200)throw new IOException("platform_api_unavailable");
            JsonObject raw;try{raw=JsonParser.parseString(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException invalid){throw new IOException("platform_response_invalid");}
            if(raw.has("code")&&Set.of(301,401,403).contains(raw.get("code").getAsInt()))throw new IOException("platform_login_expired");
            SongMetadata info=SongMetadata.netease(raw,song);
            return create(song,temp,info.title(),"OWNED-REDISTRIBUTION",info.artist(),()->{session.check();return session;},transport).acquire(cancel,progress);
        };
    }
    private static Lease resolve(long song,Session session,Transport transport,DownloadAuthority authority,BurnCoordinator.Cancellation cancel)throws Exception{
        session.check();cancel.check();
        URI api=URI.create("https://music.163.com/api/song/enhance/player/url/v1?encodeType=flac&ids=["+song+"]&level="+session.level);
        HttpRequest request=HttpRequest.newBuilder(api).timeout(Duration.ofSeconds(15)).header("Cookie",session.cookie)
            .header("Content-Type","application/x-www-form-urlencoded")
            .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.6").GET().build();
        HttpResponse<byte[]> reply=transport.send(request,65536,cancel);session.check();cancel.check();
        if(reply.statusCode()==401||reply.statusCode()==403)throw new IOException("platform_login_expired");
        if(reply.statusCode()>=300&&reply.statusCode()<400)throw new IOException("platform_redirect_refused");
        if(reply.statusCode()!=200)throw new IOException("platform_api_unavailable");
        try{
            JsonObject raw=JsonParser.parseString(new String(reply.body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            int code=raw.get("code").getAsInt();if(code==301||code==401||code==403)throw new IOException("platform_login_expired");
            if(code!=200)throw new IOException("platform_permission_denied");
            JsonArray data=raw.getAsJsonArray("data");if(data==null||data.size()!=1)throw new IOException("platform_response_invalid");
            JsonObject item=data.get(0).getAsJsonObject();if(item.get("id").getAsLong()!=song)throw new IOException("platform_response_invalid");
            if(item.get("code").getAsInt()!=200)throw new IOException("platform_permission_denied");
            if(present(item,"freeTrialInfo")&&(!item.get("freeTrialInfo").isJsonObject()||item.getAsJsonObject("freeTrialInfo").size()>0)||positive(item,"trial")||positive(item,"preview"))throw new IOException("platform_trial_only");
            if(positive(item,"drm")||present(item,"drmInfo")||positive(item,"drmProtected")||positive(item,"encrypted"))throw new IOException("platform_drm_unsupported");
            String format=present(item,"type")?item.get("type").getAsString().toLowerCase(Locale.ROOT):"";
            if(Set.of("ncm","drm","encrypted").contains(format))throw new IOException("platform_drm_unsupported");
            for(String permission:List.of("canDownload","downloadAllowed"))
                if(item.has(permission)&&item.get(permission).isJsonPrimitive()&&item.get(permission).getAsJsonPrimitive().isBoolean()&&!item.get(permission).getAsBoolean())throw new IOException("platform_download_denied");
            DownloadCapability capability=authority.assess(raw,song);
            if(capability==DownloadCapability.DENIED)throw new IOException("platform_download_denied");
            if(capability!=DownloadCapability.ALLOWED)throw new IOException("platform_download_capability_unavailable");
            if(!present(item,"url")||item.get("url").getAsString().isBlank())throw new IOException("platform_media_unavailable");
            URI url=URI.create(item.get("url").getAsString());String host=url.getHost();
            if(!Set.of("https","http").contains(url.getScheme())||host==null||url.getUserInfo()!=null||url.getFragment()!=null||url.getPort()!=-1&&url.getPort()!=443)throw new IOException("platform_media_url_refused");
            host=host.toLowerCase(Locale.ROOT);
            if(!(host.endsWith(".music.126.net")||host.equals("music.126.net"))||host.endsWith("."))throw new IOException("platform_media_url_refused");
            if(url.getScheme().equals("http"))url=URI.create("https"+url.toString().substring(4));
            int expiry=present(item,"expi")?item.get("expi").getAsInt():120;
            // Missing/nonpositive lifetime is not an invented denial. The
            // actual media HTTP result determines expiry; local wait is bounded.
            return new Lease(url,System.nanoTime()+(expiry>0?Math.min(expiry,120):120)*1_000_000_000L,format);
        }catch(IOException known){throw known;}catch(Exception invalid){throw new IOException("platform_response_invalid");}
    }
    private static boolean present(JsonObject object,String name){return object.has(name)&&!object.get(name).isJsonNull();}
    private static boolean positive(JsonObject object,String name){
        if(!present(object,name))return false;JsonElement value=object.get(name);
        if(!value.isJsonPrimitive())return true;
        JsonPrimitive p=value.getAsJsonPrimitive();if(p.isBoolean())return p.getAsBoolean();
        if(p.isNumber())return p.getAsDouble()!=0;return !p.getAsString().isBlank()&&!p.getAsString().equalsIgnoreCase("false")&&!p.getAsString().equals("0");
    }
}
