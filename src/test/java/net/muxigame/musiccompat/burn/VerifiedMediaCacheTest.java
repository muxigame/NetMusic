package net.muxigame.musiccompat.burn;
import java.net.*;import java.net.http.*;import java.util.*;import java.util.concurrent.atomic.*;import java.io.*;
public final class VerifiedMediaCacheTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("media cache "+checks);}
 public static void main(String[] args)throws Exception{
  long now=System.currentTimeMillis()/1000;var lease=PrivatePlaybackProtocol.validate(PrivatePlaybackTest.REF,PrivatePlaybackTest.answer(now),now);var cache=new VerifiedMediaCache();var leases=new AtomicInteger();var requests=new AtomicInteger();var closes=new AtomicInteger();var original=HttpRequest.newBuilder(lease.reference()).GET().build();
  PrivateMediaReader.LeaseSource authorize=refresh->{leases.incrementAndGet();return lease;};PrivateMediaReader.Network network=request->{requests.incrementAndGet();return new PrivatePlaybackTest.Reply(200,PrivatePlaybackTest.DATA,Map.of("Content-Length",List.of("4")),closes);};
  var first=PrivateMediaReader.fetch(original,authorize,()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);first.body()[0]=99;
  var second=PrivateMediaReader.fetch(original,authorize,()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);check(Arrays.equals(second.body(),PrivatePlaybackTest.DATA));check(requests.get()==1&&leases.get()==2);check(second.uri().equals(lease.reference()));check(!second.toString().contains("x-oss-"));second.body()[0]=88;
  var third=PrivateMediaReader.fetch(original,authorize,()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);check(Arrays.equals(third.body(),PrivatePlaybackTest.DATA));check(leases.get()==3&&requests.get()==1);
  boolean denied=false;try{PrivateMediaReader.fetch(original,refresh->{throw new IOException("denied");},()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);}catch(IOException error){denied=true;}check(denied&&requests.get()==1);
  denied=false;try{PrivateMediaReader.fetch(original,authorize,()->false,network,new BurnCoordinator.Cancellation(),n->{},cache);}catch(IOException error){denied=true;}check(denied&&requests.get()==1);
  var bad=new PrivatePlaybackProtocol.Lease(lease.reference(),lease.transientUrl(),lease.expiresAt(),lease.size(),"f".repeat(64));denied=false;try{PrivateMediaReader.fetch(original,refresh->bad,()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);}catch(IOException error){denied=error.getMessage().equals("private_music_digest_refused");}check(denied&&requests.get()==2);
  var empty=new VerifiedMediaCache();denied=false;try{PrivateMediaReader.fetch(original,authorize,()->true,request->new PrivatePlaybackTest.Reply(200,new byte[]{9,9,9,9},Map.of("Content-Length",List.of("4")),closes),new BurnCoordinator.Cancellation(),n->{},empty);}catch(IOException error){denied=true;}check(denied&&empty.bytes()==0);
  var small=new VerifiedMediaCache(4,10);small.putVerified(lease,PrivatePlaybackTest.DATA,100);check(small.get(lease,109)!=null);check(small.get(lease,110)==null&&small.bytes()==0);small.putVerified(lease,PrivatePlaybackTest.DATA,200);small.clear();check(small.bytes()==0&&small.get(lease,201)==null);
  var next=new VerifiedMediaCache();check(next.get(lease,System.nanoTime())==null);
  var trace=MusicPlaybackMetrics.begin(lease.reference(),new Object());try(var scope=trace.bind()){PrivateMediaReader.fetch(original,authorize,()->true,network,new BurnCoordinator.Cancellation(),n->{},cache);}
  var rows=MusicPlaybackMetrics.recent();check(rows.stream().anyMatch(x->x.phase().equals("cache")&&x.outcome().equals("cache_hit")));check(rows.toString().indexOf("OFFLINE_TOKEN")==-1&&rows.toString().indexOf("x-oss-")==-1);
  denied=false;try{trace.event("provider-secret",0,0,"success");}catch(IllegalArgumentException error){denied=true;}check(denied);
  System.out.println("{\"verifiedMediaCacheChecks\":"+checks+",\"freshAuthorizationRequiredOnCacheHit\":true,\"cacheDoesNotBypassDigest\":true,\"transportRequests\":0,\"mcStarted\":false}");
 }
}
