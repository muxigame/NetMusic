package net.muxigame.musiccompat.burn;
/** Shared by the client tick and its own audio channel. No preparation/wall clock drives lyrics. */
public final class PrivateSoundState {
 public volatile boolean cancelled,ready,paused;public volatile double seconds;
 public volatile PlaybackLifecycle.Binding lifetime;private BurnCoordinator.Cancellation fetching;
 public volatile double target;public volatile boolean moving;public volatile long receivedNanos;public final int duration;
 public volatile boolean seekCommitted,followPause,timelinePaused,localPaused;
 public PrivateSoundState(double target,boolean moving,int duration){this.target=target;this.moving=moving;this.duration=duration;receivedNanos=System.nanoTime();}
 public synchronized void updateTarget(double seconds,boolean moving,boolean prepared){if(!Double.isFinite(seconds)||seconds<0||seconds>duration)return;if(!seekCommitted){target=seconds;this.moving=moving;receivedNanos=System.nanoTime();}if(followPause)timelinePaused=prepared&&!moving;}
 public synchronized double seekTarget(){return Math.min(duration,target+(moving?Math.max(0,System.nanoTime()-receivedNanos)/1e9:0));}
 public synchronized void sample(double seconds,boolean paused){if(cancelled||!Double.isFinite(seconds)||seconds<0)return;this.seconds=Math.max(this.seconds,Math.min(duration,seconds));this.paused=paused;ready=true;}
 public synchronized void bindFetch(BurnCoordinator.Cancellation value){if(cancelled)value.cancel();else fetching=value;}
 public synchronized void clearFetch(BurnCoordinator.Cancellation value){if(fetching==value)fetching=null;}
 public synchronized void cancel(){cancelled=true;if(fetching!=null)fetching.cancel();if(lifetime!=null)lifetime.retire();}
 public int lyricTick(){return ready?(int)Math.floor(seconds*20):0;}
}
