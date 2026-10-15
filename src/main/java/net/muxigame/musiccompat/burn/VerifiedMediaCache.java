package net.muxigame.musiccompat.burn;
import java.util.*;import java.net.URI;
/** Small memory-only cache of fully verified bytes. A fresh authorized lease is required before every lookup. */
public final class VerifiedMediaCache {
 private record Key(URI reference,String sha256,long size){}private record Entry(byte[] data,long until){}
 private final long maximum,ttl;private long bytes;private final LinkedHashMap<Key,Entry> entries=new LinkedHashMap<>(4,.75f,true);
 public VerifiedMediaCache(){this(16L*1024*1024,90_000_000_000L);}VerifiedMediaCache(long maximum,long ttl){this.maximum=maximum;this.ttl=ttl;}
 private void prune(long now){var it=entries.entrySet().iterator();while(it.hasNext()){var e=it.next();if(e.getValue().until<=now){bytes-=e.getValue().data.length;it.remove();}}}
 public synchronized byte[] get(PrivatePlaybackProtocol.Lease lease,long now){prune(now);var e=entries.get(new Key(lease.reference(),lease.sha256(),lease.size()));return e==null?null:e.data.clone();}
 synchronized void putVerified(PrivatePlaybackProtocol.Lease lease,byte[] data,long now){
  prune(now);if(data.length!=lease.size()||data.length>maximum)return;var key=new Key(lease.reference(),lease.sha256(),lease.size());var previous=entries.remove(key);if(previous!=null)bytes-=previous.data.length;
  while(!entries.isEmpty()&&(entries.size()>=4||bytes+data.length>maximum)){var it=entries.entrySet().iterator();var e=it.next();bytes-=e.getValue().data.length;it.remove();}
  entries.put(key,new Entry(data.clone(),now+ttl));bytes+=data.length;
 }
 public synchronized void clear(){entries.clear();bytes=0;}
 synchronized long bytes(){return bytes;}
}
