package net.muxigame.musiccompat.burn;
import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.*;import java.io.*;import java.nio.*;import java.util.*;import java.util.function.*;
/** Decoded-frame seek, then bounded ordinary PCM reads. Compressed bytes are never range-seeked. */
public final class PrivatePcmStream implements AudioStream {
 private final AudioInputStream input;private final AudioFormat format;public final PrivateSoundState state;
 private final ArrayDeque<Double> queued=new ArrayDeque<>();private double processed;private boolean closed;public volatile double base;private long skippedBytes;
 public PrivatePcmStream(AudioInputStream input,PrivateSoundState state)throws IOException{this.input=input;format=input.getFormat();this.state=state;
  int frame=format.getFrameSize();if(!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())||frame<1||frame>4||format.getSampleSizeInBits()!=16||format.isBigEndian()||!Float.isFinite(format.getFrameRate())||format.getFrameRate()<8000||format.getFrameRate()>192000)throw new IOException("unsupported_private_pcm");
  long frames=(long)Math.floor(state.seekTarget()*format.getFrameRate());long remaining=frames*frame;byte[] chunk=new byte[8192/frame*frame];long skipped=0;
  try{while(remaining>0){if(state.cancelled)throw new IOException("playback_cancelled");int n=readFrames(chunk,0,(int)Math.min(chunk.length,remaining));if(n<0)throw new EOFException("playback_position_after_eof");if(n==0)throw new IOException("empty_pcm_read");if(n%frame!=0)throw new IOException("partial_pcm_frame");remaining-=n;skipped+=n;}}
  catch(IOException e){input.close();throw e;}skippedBytes=skipped;base=skipped/(double)frame/format.getFrameRate();
 }
 /** The fixed MPEG SPI can return 0 once immediately before -1 at EOF.
  * Keep frame-sized reads, cancellation and finite stalls; never mistake that transient 0 for corrupt PCM. */
 private int readFrames(byte[] bytes,int offset,int length)throws IOException{
  long deadline=System.nanoTime()+2_000_000_000L;int empty=0;
  for(;;){
   if(state.cancelled||Thread.currentThread().isInterrupted())throw new IOException("playback_cancelled");
   int count=input.read(bytes,offset,length);if(count!=0)return count;
   if(++empty>=64||System.nanoTime()>=deadline)throw new IOException("private_pcm_stalled");
   java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L);
  }
 }
 public AudioFormat getFormat(){return format;}
 public synchronized ByteBuffer read(int requested)throws IOException{
  if(closed||state.cancelled)throw new IOException("playback_cancelled");int frame=format.getFrameSize();
  if(!state.seekCommitted){long target=(long)Math.floor(state.seekTarget()*format.getFrameRate())*frame;byte[] discard=new byte[8192/frame*frame];while(skippedBytes<target){if(state.cancelled)throw new IOException("playback_cancelled");int n=readFrames(discard,0,(int)Math.min(discard.length,target-skippedBytes));if(n<0)throw new EOFException("playback_position_after_eof");if(n==0||n%frame!=0)throw new IOException("invalid_pcm_read");skippedBytes+=n;}base=skippedBytes/(double)frame/format.getFrameRate();state.seekCommitted=true;}int length=Math.min(1048576,requested/frame*frame);if(length<frame)throw new IOException("invalid_pcm_buffer");byte[] bytes=new byte[length];int used=0;
  while(used<length){int n=readFrames(bytes,used,length-used);if(n<0)break;if(n==0)throw new IOException("empty_pcm_read");used+=n;}if(used==0)return null;if(used%frame!=0)throw new IOException("partial_pcm_frame");
  var buffer=ByteBuffer.allocateDirect(used).order(ByteOrder.nativeOrder());buffer.put(bytes,0,used).flip();queued.add(used/(double)frame/format.getFrameRate());return buffer;
 }
 public synchronized void processed(int count){for(int i=0;i<count;i++){Double value=queued.poll();if(value!=null)processed+=value;}}
 public synchronized double position(double offset){return base+processed+Math.max(0,offset);}
 public synchronized void close()throws IOException{if(!closed){closed=true;queued.clear();input.close();}}
}
