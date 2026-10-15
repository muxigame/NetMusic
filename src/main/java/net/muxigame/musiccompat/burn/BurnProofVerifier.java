package net.muxigame.musiccompat.burn;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;

/** Existing public OIDC JWKS; no core server key or account/platform token. */
public final class BurnProofVerifier {
    private volatile JsonArray keys;
    private volatile long refreshed;
    public JsonObject consume(String proof,long uid,String nonce)throws Exception{
        JsonObject claims=verify(proof,uid,nonce);
        JsonObject body=new JsonObject();body.addProperty("proof",proof);
        HttpRequest request=HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("api/minecraft/music/burn-receipt/redeem"))
            .timeout(Duration.ofSeconds(8)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        var response=BurnHttp.send(request,65536,new BurnCoordinator.Cancellation(),n->{});
        if(response.statusCode()!=200)throw new IOException("receipt_unavailable");
        JsonObject receipt=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
        for(String field:List.of("jobId","receiptId","url","title","sha256","bytes","duration","license","credit"))
            if(!Objects.equals(receipt.get(field),claims.get(field)))throw new IOException("invalid_receipt");
        return claims;
    }
    private JsonObject verify(String proof,long uid,String nonce)throws Exception{
        if(proof==null||proof.length()>8192)throw new IOException("invalid_burn_proof");
        String[] parts=proof.split("\\.",-1);
        if(parts.length!=3||Arrays.stream(parts).anyMatch(s->!s.matches("[A-Za-z0-9_-]+")))throw new IOException("invalid_burn_proof");
        JsonObject header=json(parts[0]),claims=json(parts[1]);
        if(!"RS256".equals(header.get("alg").getAsString()))throw new IOException("invalid_burn_proof");
        PublicKey key=key(header.get("kid").getAsString());
        Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(key);
        verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        if(!verifier.verify(Base64.getUrlDecoder().decode(parts[2])))throw new IOException("invalid_burn_proof");
        long now=System.currentTimeMillis()/1000,iat=claims.get("iat").getAsLong(),exp=claims.get("exp").getAsLong();
        if(!"https://account.muxigame.com".equals(claims.get("iss").getAsString())
            ||!"muxi-music-burn".equals(claims.get("aud").getAsString())||!"music-burn-v1".equals(claims.get("purpose").getAsString())
            ||claims.get("uid").getAsLong()!=uid||!Long.toString(uid).equals(claims.get("sub").getAsString())
            ||!nonce.equals(claims.get("burnNonce").getAsString())||now<iat-5||now>=exp||exp-iat>120||exp<=iat
            ||!BurnProtocol.uuid(claims.get("jobId").getAsString())||!BurnProtocol.uuid(claims.get("receiptId").getAsString())
            ||!BurnProtocol.uuid(claims.get("gameSession").getAsString())||!BurnProtocol.media(claims.get("url").getAsString())
            ||!claims.get("url").getAsString().contains("/media/"+uid+"/"+claims.get("jobId").getAsString()+".")
            ||claims.get("duration").getAsInt()<1||claims.get("duration").getAsInt()>600)
            throw new IOException("invalid_burn_proof");
        return claims;
    }
    private static JsonObject json(String part){
        return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(part),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private synchronized PublicKey key(String kid)throws Exception{
        if(!kid.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("invalid_burn_proof");
        long now=System.currentTimeMillis();
        if(keys==null||now-refreshed>900_000){
            var request=HttpRequest.newBuilder(BurnProtocol.ACCOUNT.resolve("oauth/jwks.json")).timeout(Duration.ofSeconds(5)).GET().build();
            var result=BurnHttp.send(request,65536,new BurnCoordinator.Cancellation(),n->{});
            if(result.statusCode()!=200)throw new IOException("public_signing_key_unavailable");
            keys=JsonParser.parseString(new String(result.body(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("keys");refreshed=now;
        }
        for(JsonElement element:keys){
            var value=element.getAsJsonObject();
            if(!kid.equals(value.get("kid").getAsString())||!"RSA".equals(value.get("kty").getAsString())
                ||value.has("alg")&&!"RS256".equals(value.get("alg").getAsString()))continue;
            String n=value.get("n").getAsString(),e=value.get("e").getAsString();
            if(n.length()>1400||e.length()>16)throw new IOException("invalid_public_signing_key");
            BigInteger modulus=new BigInteger(1,Base64.getUrlDecoder().decode(n));
            if(modulus.bitLength()<2048||modulus.bitLength()>8192)throw new IOException("invalid_public_signing_key");
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,new BigInteger(1,Base64.getUrlDecoder().decode(e))));
        }
        throw new IOException("public_signing_key_unavailable");
    }
}
