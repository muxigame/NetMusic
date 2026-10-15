package net.muxigame.musiccompat.burn;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable references and transient leases are distinct; no signed URL is a CD. */
public final class PrivatePlaybackProtocol {
    private PrivatePlaybackProtocol(){}
    public record Lease(URI reference,URI transientUrl,long expiresAt,int size,String sha256){
        public String toString(){return "PrivateMusicLease[transientUrl=<redacted>]";}
    }
    public static Lease validate(URI reference,JsonObject answer,long now)throws java.io.IOException{
        try{
            if(!BurnProtocol.media(reference.toString())||!reference.toString().equals(answer.get("reference").getAsString()))throw new IllegalArgumentException();
            URI signed=URI.create(answer.get("url").getAsString());
            if(!"https".equals(signed.getScheme())||!reference.getHost().equals(signed.getHost())||signed.getPort()!=-1||signed.getUserInfo()!=null||signed.getFragment()!=null||!reference.getRawPath().equals(signed.getRawPath())||signed.getRawQuery()==null||signed.getRawQuery().length()>16384)throw new IllegalArgumentException();
            Map<String,String> query=new HashMap<>();
            Set<String> allowed=Set.of("x-oss-signature-version","x-oss-credential","x-oss-date","x-oss-expires","x-oss-signature","x-oss-security-token","x-oss-additional-headers");
            for(String part:signed.getRawQuery().split("&")){
                String[] pair=part.split("=",2);if(pair.length!=2)throw new IllegalArgumentException();
                String name=URLDecoder.decode(pair[0],StandardCharsets.UTF_8),value=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
                if(!allowed.contains(name)||value.isEmpty()||query.put(name,value)!=null)throw new IllegalArgumentException();
            }
            if(!"OSS4-HMAC-SHA256".equals(query.get("x-oss-signature-version"))||!query.getOrDefault("x-oss-signature","").matches("[0-9a-f]{64}")
                ||!query.getOrDefault("x-oss-date","").matches("[0-9]{8}T[0-9]{6}Z")||!query.getOrDefault("x-oss-credential","").endsWith("/cn-hangzhou/oss/aliyun_v4_request")
                ||!query.containsKey("x-oss-security-token"))throw new IllegalArgumentException();
            int ttl=Integer.parseInt(query.get("x-oss-expires"));long expiry=answer.get("expiresAt").getAsLong();
            long signedAt=java.time.LocalDateTime.parse(query.get("x-oss-date"),java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).toEpochSecond(java.time.ZoneOffset.UTC);
            int size=answer.get("maxBytes").getAsInt();String digest=answer.get("sha256").getAsString();
            if(ttl<1||ttl>720||signedAt>now+30||Math.abs(expiry-(signedAt+ttl))>2||expiry<=now+5||expiry>now+725||size<1||size>BurnCoordinator.MAX_BYTES||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            return new Lease(reference,signed,expiry,size,digest);
        }catch(RuntimeException error){throw new java.io.IOException("private_music_lease_refused");}
    }
}
