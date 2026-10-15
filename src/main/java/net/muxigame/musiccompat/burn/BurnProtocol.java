package net.muxigame.musiccompat.burn;

import java.net.URI;
import java.util.UUID;

/** Reviewed deployment origins; no client configuration can redirect authority. */
public final class BurnProtocol {
    public static final URI ACCOUNT=URI.create("https://account.muxigame.com/");
    public static final URI OSS=URI.create("https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/");
    private BurnProtocol(){}
    public static boolean uuid(String value){
        try{return UUID.fromString(value).toString().equals(value);}catch(RuntimeException invalid){return false;}
    }
    public static boolean media(String value){
        try{
            URI uri=URI.create(value);
            return "https".equals(uri.getScheme())&&OSS.getHost().equals(uri.getHost())&&uri.getPort()==-1
                &&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null
                &&uri.getRawPath().matches("/media/[0-9]{5,16}/[0-9a-f-]{36}\\.(mp3|wav)");
        }catch(RuntimeException invalid){return false;}
    }
}
