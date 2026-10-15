package net.muxigame.musiccompat.burn;
import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.api.lyric.*;
import com.gly091020.netMusicListNeoforge.media.qq_music.QQMusicUtil;
import com.gly091020.netMusicListNeoforge.media.bilibili.BiliBiliUtil;
import com.gly091020.netMusicListNeoforge.NetMusicList;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import oshi.util.tuples.Pair;
/** Original parsers and original client login only. Missing lyrics never fail audio. */
public final class PlatformLyrics {
    private PlatformLyrics(){}
    public static LyricRecord fetch(LyricOrigin origin,String title)throws Exception{
        LyricRecord value=switch(origin.platform()){
            case "netease" -> LyricParser.parseLyric(NetMusic.NET_EASE_WEB_API.lyric(Long.parseLong(origin.identifier())),title);
            case "qq" -> QQMusicUtil.getLyric(origin.identifier());
            // Original fetchRawLyrics always takes the first CID; explicitly select the burned page.
            case "bilibili" -> bilibili(origin,NetMusicList.CONFIG.bilibiliCookie,PlatformLyrics::send);
            default -> null;
        };
        return fresh(value);
    }
    interface TextFetcher {String get(HttpRequest request)throws Exception;}
    private static String send(HttpRequest request)throws Exception{
        var reply=BurnHttp.send(request,262144,new BurnCoordinator.Cancellation(),n->{});
        if(reply.statusCode()!=200)throw new java.io.IOException("lyrics_unavailable");
        return new String(reply.body(),StandardCharsets.UTF_8);
    }
    private static HttpRequest request(URI uri,String cookie,boolean api){
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent","Mozilla/5.0").header("Referer","https://www.bilibili.com/");
        if(api&&cookie!=null&&!cookie.isBlank()&&cookie.length()<=16384&&cookie.indexOf('\r')<0&&cookie.indexOf('\n')<0)builder.header("Cookie",cookie);
        return builder.GET().build();
    }
    static LyricRecord bilibili(LyricOrigin origin,String cookie,TextFetcher fetch)throws Exception{
        JsonObject view=JsonParser.parseString(fetch.get(request(URI.create("https://api.bilibili.com/x/web-interface/view?bvid="+origin.identifier()),cookie,true))).getAsJsonObject();
        if(view.get("code").getAsInt()!=0)return null;var data=view.getAsJsonObject("data");
        var pages=data.getAsJsonArray("pages");if(pages==null||origin.page()>pages.size())return null;
        long cid=pages.get(origin.page()-1).getAsJsonObject().get("cid").getAsLong(),aid=data.get("aid").getAsLong();if(cid<1||aid<1)return null;
        JsonObject player=JsonParser.parseString(fetch.get(request(URI.create("https://api.bilibili.com/x/player/wbi/v2?aid="+aid+"&cid="+cid),cookie,true))).getAsJsonObject();
        if(player.get("code").getAsInt()!=0)return null;
        var subtitles=player.getAsJsonObject("data").getAsJsonObject("subtitle").getAsJsonArray("subtitles");if(subtitles==null)return null;
        List<Pair<String,String>> available=new ArrayList<>();int count=0;
        for(var item:subtitles){
            if(++count>8)break;var value=item.getAsJsonObject();String language=value.get("lan").getAsString();if(language.contains("ai"))continue;
            String address=value.get("subtitle_url").getAsString();if(address.startsWith("//"))address="https:"+address;
            URI uri=URI.create(address);String host=uri.getHost();
            if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1||!(host.endsWith(".hdslb.com")||host.endsWith(".bilibili.com")))continue;
            available.add(new Pair<>(language,fetch.get(request(uri,"",false))));
        }
        return available.isEmpty()?null:fresh(BiliBiliUtil.choiceLyric(available));
    }
    public static LyricRecord fresh(LyricRecord record){
        if(record==null||record.getLyrics()==null||record.getLyrics().isEmpty())return null;
        // updateCurrentLine removes old rows. No two sounds may share these maps.
        return new LyricRecord(new Int2ObjectRBTreeMap<>(record.getLyrics()),record.getTransLyrics()==null?null:new Int2ObjectRBTreeMap<>(record.getTransLyrics()));
    }
}
