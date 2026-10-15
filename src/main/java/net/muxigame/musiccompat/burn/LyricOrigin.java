package net.muxigame.musiccompat.burn;
import com.google.gson.*;
/** A bounded platform identifier only; never a URL, cookie or download authority. */
public record LyricOrigin(String platform,String identifier,int page) {
    public static final LyricOrigin NONE=new LyricOrigin("none","",1);
    public LyricOrigin {
        boolean valid=platform!=null&&identifier!=null&&page>=1&&page<=10000&&switch(platform){
            case "none" -> identifier.isEmpty()&&page==1;
            case "netease","qq" -> identifier.matches("[1-9][0-9]{0,18}")&&page==1;
            case "bilibili" -> identifier.matches("BV[A-Za-z0-9]{10}");
            default -> false;
        };
        if(!valid)throw new IllegalArgumentException("invalid_lyric_origin");
        if(platform.equals("netease")||platform.equals("qq"))Long.parseLong(identifier);
    }
    public JsonObject json(){JsonObject value=new JsonObject();value.addProperty("platform",platform);value.addProperty("identifier",identifier);value.addProperty("page",page);return value;}
    public static LyricOrigin parse(JsonObject value){
        if(value==null)return NONE;
        if(!value.keySet().equals(java.util.Set.of("platform","identifier","page")))throw new IllegalArgumentException("invalid_lyric_origin");
        return new LyricOrigin(value.get("platform").getAsString(),value.get("identifier").getAsString(),value.get("page").getAsInt());
    }
}
