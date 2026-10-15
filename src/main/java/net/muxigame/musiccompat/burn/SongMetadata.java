package net.muxigame.musiccompat.burn;
import com.google.gson.*;import com.github.tartaricacid.netmusic.api.pojo.NetEaseMusicSong;import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import java.util.*;import java.io.IOException;import java.nio.file.Path;
/** Names/artists from existing source schemas, never from manual permission fields. */
public record SongMetadata(String title,String artist) {
 public SongMetadata {title=clean(title,120,"未命名音频");artist=clean(artist,1024,"未知作者");}
 public static String clean(String text,int max,String fallback){
  String value=text==null?"":text.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]"," ").strip();
  if(value.length()>max){int end=max;if(Character.isHighSurrogate(value.charAt(end-1)))end--;value=value.substring(0,end);}
  return value.isBlank()?fallback:value;
 }
 public static SongMetadata netease(JsonObject raw,long id)throws IOException {
  try{NetEaseMusicSong parsed=new Gson().fromJson(raw,NetEaseMusicSong.class);if(parsed.getCode()!=200||parsed.getSong()==null||parsed.getSong().getId()!=id)throw new IOException("platform_response_invalid");
   ItemMusicCD.SongInfo info=new ItemMusicCD.SongInfo(parsed.getSong());if(info.songName==null||info.songName.isBlank())throw new IOException("platform_response_invalid");
   return new SongMetadata(info.songName,info.artists==null?"":String.join(" / ",info.artists));
  }catch(IOException known){throw known;}catch(RuntimeException malformed){throw new IOException("platform_response_invalid");}
 }
 public static SongMetadata qq(JsonObject track)throws IOException {
  String name=text(track,"name");if(name.isBlank())name=text(track,"title");if(name.isBlank())throw new IOException("platform_response_invalid");
  var artists=new ArrayList<String>();JsonArray singers=track.getAsJsonArray("singer");if(singers!=null)for(var singer:singers){String value=text(singer.getAsJsonObject(),"name");if(!value.isBlank())artists.add(value);}
  return new SongMetadata(name,String.join(" / ",artists));
 }
 public static SongMetadata bilibili(JsonObject data,int page)throws IOException {
  String name=text(data,"title");if(name.isBlank())throw new IOException("platform_response_invalid");
  JsonArray pages=data.getAsJsonArray("pages");if(page>1&&pages!=null&&page<=pages.size()){String part=text(pages.get(page-1).getAsJsonObject(),"part");if(!part.isBlank())name+=" / "+part;}
  JsonObject owner=data.getAsJsonObject("owner");return new SongMetadata(name,owner==null?"":text(owner,"name"));
 }
 private static String text(JsonObject object,String key){return object!=null&&object.has(key)&&object.get(key).isJsonPrimitive()?object.get(key).getAsString():"";}
 public static SongMetadata local(Path file,String fallback){return LocalAudioMetadata.read(file,fallback);}
}
