package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.qa.entry042.*;import net.muxigame.musiccompat.burn.*;import com.github.tartaricacid.netmusic.api.lyric.*;import com.google.gson.*;import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Self-authored text only; no real platform request or borrowed song lyric. */
@Mixin(value=PlatformLyrics.class,remap=false) public abstract class Entry042LyricsMixin {
 @Inject(method="fetch",at=@At("HEAD"),cancellable=true) private static void owned(LyricOrigin origin,String title,CallbackInfoReturnable<LyricRecord> result){
  Entry042Scope.require();boolean absent=origin.platform().equals("none")||origin.identifier().equals("BV0000000000");
  if(absent){result.setReturnValue(null);}else{
   String label=origin.platform().equals("bilibili")?"Owned P"+origin.page():"Owned "+origin.platform();JsonObject value=new JsonObject();value.addProperty("code",200);JsonObject lrc=new JsonObject();lrc.addProperty("lyric","[00:00.00]"+label+" first\n[00:05.00]"+label+" second\n[00:15.00]"+label+" third\n[00:30.00]"+label+" fourth\n[00:45.00]"+label+" last");value.add("lrc",lrc);
   if(!origin.platform().equals("qq")&&!(origin.platform().equals("netease")&&origin.identifier().equals("12345"))){JsonObject translated=new JsonObject();translated.addProperty("lyric","[00:00.00]Owned translation first\n[00:05.00]Owned translation second\n[00:15.00]Owned translation third\n[00:30.00]Owned translation fourth\n[00:45.00]Owned translation last");value.add("tlyric",translated);}
   result.setReturnValue(LyricParser.parseLyric(value.toString(),title));
  }
  Entry042Fixture.event("owned-lyric-timeline-fixture",Map.of("platform",origin.platform(),"page",origin.page(),"noLyrics",absent,"actualPlatformCalls",0,"selfAuthoredText",true));
 }
}
