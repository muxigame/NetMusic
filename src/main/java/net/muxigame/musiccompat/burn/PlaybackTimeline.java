package net.muxigame.musiccompat.burn;
import java.util.*;
/** Server-thread ledger. Only the elected listener can advance the shared audio baseline. */
public final class PlaybackTimeline {
 public record Point(double seconds,boolean moving,long tick){}
 public final String epoch=UUID.randomUUID().toString();public final int duration;
 // Reports arrive every ten ticks. A connected listener whose audio never starts
 // must not keep healthy listeners from establishing the late-join baseline.
 private static final long REPORT_LEASE_TICKS=100;
 private final long started;private final double restored;private UUID leader;private Point point;
 public PlaybackTimeline(int duration,long tick,double restored){if(duration<1||duration>14400||!Double.isFinite(restored)||restored<0||restored>duration)throw new IllegalArgumentException();this.duration=duration;started=tick;this.restored=restored;}
 public void elect(UUID listener){if(leader==null)leader=listener;}
 public boolean report(UUID listener,String epoch,double seconds,boolean moving,long now){
  if(listener==null||!this.epoch.equals(epoch)||!Double.isFinite(seconds)||seconds<0||seconds>duration||seconds>restored+Math.max(0,now-started)/20.0+10)return false;
  long lastReport=point==null?started:point.tick;
  if(!Objects.equals(leader,listener)&&leader!=null&&now-lastReport<REPORT_LEASE_TICKS)return false;
  if(point!=null&&(now<point.tick||seconds+.15<point.seconds||seconds>point.seconds+Math.max(0,now-point.tick)/20.0+1))return false;
  leader=listener;point=new Point(seconds,moving,now);return true;
 }
 public Point snapshot(long now){if(point==null)return new Point(Math.min(duration,restored+Math.max(0,now-started)/20.0),true,now);return new Point(Math.min(duration,point.seconds+(point.moving?Math.max(0,now-point.tick)/20.0:0)),point.moving,now);}
 public UUID leader(){return leader;}
 public double initial(){return restored;}
 public void release(UUID listener){if(Objects.equals(listener,leader))leader=null;}
 public boolean prepared(){return point!=null;}
}
