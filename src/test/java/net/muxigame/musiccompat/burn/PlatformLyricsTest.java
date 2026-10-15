package net.muxigame.musiccompat.burn;
import com.github.tartaricacid.netmusic.api.lyric.*;
import java.util.*;
public final class PlatformLyricsTest {
    private static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError(checks);}
    public static void main(String[] args)throws Exception{
        var spec=com.github.tartaricacid.netmusic.config.GeneralConfig.init();var memory=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(memory);
        // NeoForge seals this interface. Construct its actual in-memory carrier for this JVM only.
        var ctor=Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(com.electronwill.nightconfig.core.CommentedConfig.class,java.nio.file.Path.class,net.neoforged.fml.config.ModConfig.class);ctor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)ctor.newInstance(memory,null,null));
        LyricRecord raw=LyricParser.parseLyric("{\"code\":200,\"lrc\":{\"lyric\":\"[00:00.00]First\n[00:02.00]Second\n[00:05.00]Third\"},\"tlyric\":{\"lyric\":\"[00:00.00]Translation1\n[00:02.00]Translation2\n[00:05.00]Translation3\"}}","Title");
        check(raw!=null);var first=PlatformLyrics.fresh(raw);var second=PlatformLyrics.fresh(raw);first.updateCurrentLine(50);
        check(first.getLyrics().firstIntKey()==40);check(second.getLyrics().firstIntKey()==0);check(raw.getLyrics().firstIntKey()==0);check(first.getTransLyrics().firstIntKey()==40);
        second.updateCurrentLine(110);check(second.getLyrics().firstIntKey()==100);check(first.getLyrics().firstIntKey()==40);
        check(PlatformLyrics.fresh(null)==null);check(LyricParser.parseLyric("{\"code\":200,\"lrc\":{\"lyric\":\"\"}}","")==null);
        List<String> requests=new ArrayList<>();var origin=new LyricOrigin("bilibili","BV1Q5411x7aE",2);
        var record=PlatformLyrics.bilibili(origin,"fixture-cookie",request->{String url=request.uri().toString();requests.add(url);
            if(url.contains("web-interface")){check(request.headers().firstValue("Cookie").orElse("").equals("fixture-cookie"));return "{\"code\":0,\"data\":{\"aid\":123,\"pages\":[{\"cid\":111},{\"cid\":222}]}}";}
            if(url.contains("/player/")){check(url.endsWith("aid=123&cid=222"));return "{\"code\":0,\"data\":{\"subtitle\":{\"subtitles\":[{\"lan\":\"zh-CN\",\"subtitle_url\":\"//i0.hdslb.com/fixture.json\"}]}}}";}
            check(request.headers().firstValue("Cookie").isEmpty());return "{\"body\":[{\"from\":0,\"to\":2,\"content\":\"Subtitle first\"},{\"from\":2,\"to\":4,\"content\":\"Subtitle second\"}]}";
        });check(requests.size()==3);check(record!=null);record.updateCurrentLine(50);check(record.getLyrics().firstIntKey()==40);
        check(new LyricOrigin("qq","12345",1).json().get("identifier").getAsString().equals("12345"));
        System.out.println("{\"suite\":\"original_lyrics_and_partition_fixture\",\"checks\":"+checks+",\"realAccount\":false,\"ossCalls\":0}");
    }
}
