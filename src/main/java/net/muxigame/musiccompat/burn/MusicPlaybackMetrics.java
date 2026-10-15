package net.muxigame.musiccompat.burn;
import java.net.URI;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
/** Deliberately excludes addresses, account data, tokens, proof, headers, response bodies and exception text. */
public final class MusicPlaybackMetrics {
 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
 private static final Set<String> PHASES=Set.of("account","lease","headers","first_body","body","hash","cache","lyrics_origin","lyrics_platform","total","notice","decode","pcm_ready","openal_start");
 private static final Set<String> OUTCOMES=Set.of("success","failed","cancelled","prepare_timeout","stopped","cache_hit","cache_miss");
 private static final ArrayDeque<Sample> recent=new ArrayDeque<>();private static final ThreadLocal<Trace> current=new ThreadLocal<>();private record ConnectionKey(java.lang.ref.WeakReference<Object> key,int generation){}private static final ArrayDeque<ConnectionKey> connections=new ArrayDeque<>();private static int generation;
 public record Sample(String trace,String media,int connection,String phase,long atUtcMillis,long elapsedMillis,long bytes,String outcome){}
 private static synchronized int generation(Object key){if(key==null)return 0;connections.removeIf(value->value.key.get()==null);for(var value:connections)if(value.key.get()==key)return value.generation;if(connections.size()>=16)connections.removeFirst();int next=++generation;connections.addLast(new ConnectionKey(new java.lang.ref.WeakReference<>(key),next));return next;}
 public static Trace begin(URI reference,Object key){if(!BurnProtocol.media(reference.toString()))throw new IllegalArgumentException("canonical_media_required");return new Trace(reference,generation(key));}
 public static Trace current(){return current.get();}
 public static synchronized List<Sample> recent(){return List.copyOf(recent);}
 private static synchronized void record(Sample value){if(recent.size()>=256)recent.removeFirst();recent.addLast(value);LOG.info("Private music phase: trace={} media={} connection={} phase={} elapsed_ms={} bytes={} outcome={}",value.trace,value.media,value.connection,value.phase,value.elapsedMillis,value.bytes,value.outcome);}
 public static void notice(String reference,String code){if(!OUTCOMES.contains(code))throw new IllegalArgumentException("stable_code_required");try{var trace=new Trace(URI.create(reference),0);trace.event("notice",0,0,code);}catch(IllegalArgumentException ignored){}}
 public static final class Trace {
  private final String id=UUID.randomUUID().toString(),media;private final int connection;
  private Trace(URI reference,int connection){this.connection=connection;try{media=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reference.toString().getBytes(StandardCharsets.UTF_8))).substring(0,16);}catch(Exception impossible){throw new IllegalStateException();}}
  public Scope bind(){var prior=current.get();current.set(this);return new Scope(prior);}
  public Phase phase(String name){if(!PHASES.contains(name))throw new IllegalArgumentException("stable_phase_required");return new Phase(this,name);}
  public void event(String phase,long elapsedNanos,long bytes,String outcome){if(!PHASES.contains(phase)||!OUTCOMES.contains(outcome))throw new IllegalArgumentException("stable_phase_required");record(new Sample(id,media,connection,phase,System.currentTimeMillis(),Math.max(0,elapsedNanos/1_000_000L),Math.max(0,bytes),outcome));}
 }
 public static final class Scope implements AutoCloseable {private final Trace prior;private Scope(Trace prior){this.prior=prior;}public void close(){if(prior==null)current.remove();else current.set(prior);}}
 public static final class Phase implements AutoCloseable {private final Trace trace;private final String name;private final long started=System.nanoTime();private long bytes;private String outcome="failed";private Phase(Trace trace,String name){this.trace=trace;this.name=name;}public void success(){outcome="success";}public void bytes(long value){bytes=value;}public void cancelled(){outcome="cancelled";}public void close(){trace.event(name,System.nanoTime()-started,bytes,outcome);}}
 private MusicPlaybackMetrics(){}
}
