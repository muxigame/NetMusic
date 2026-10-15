package net.muxigame.musiccompat.burn;
import javax.sound.sampled.*;import java.io.*;import java.util.concurrent.atomic.*;
/** Actual AudioInputStream zero/EOF edges, not a substitute for the separate fixed-MPEG codec probe. */
public final class PrivatePcmZeroReadTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("PCM zero edge "+checks);}
 static final AudioFormat FORMAT=new AudioFormat(8000,16,2,true,false);
 static final class Source extends AudioInputStream {
  final byte[] data;int offset,calls;final boolean alwaysZero;final PrivateSoundState state;
  Source(byte[] data,boolean alwaysZero,PrivateSoundState state){super(new ByteArrayInputStream(data),FORMAT,data.length/4);this.data=data;this.alwaysZero=alwaysZero;this.state=state;}
  public int read(byte[] target,int start,int length){calls++;if(alwaysZero){if(state!=null&&calls==3)state.cancel();return 0;}if(calls==1||offset==data.length&&calls%2==1)return 0;if(offset==data.length)return -1;int count=Math.min(length,data.length-offset);System.arraycopy(data,offset,target,start,count);offset+=count;return count;}
 }
 public static void main(String[] args)throws Exception{
  byte[] bytes=new byte[64];var state=new PrivateSoundState(0,false,1);var source=new Source(bytes,false,null);var stream=new PrivatePcmStream(source,state);check(stream.read(128).remaining()==64);check(stream.read(128)==null);check(source.calls>=4);stream.close();
  state=new PrivateSoundState(.001,false,1);source=new Source(bytes,false,null);stream=new PrivatePcmStream(source,state);check(Math.abs(stream.base-.001)<.00001);check(stream.read(128).remaining()==32);check(stream.read(128)==null);stream.close();
  state=new PrivateSoundState(0,false,1);source=new Source(bytes,true,null);stream=new PrivatePcmStream(source,state);boolean failed=false;long start=System.nanoTime();try{stream.read(128);}catch(IOException error){failed=error.getMessage().equals("private_pcm_stalled");}check(failed&&source.calls==64&&System.nanoTime()-start<2_000_000_000L);stream.close();
  state=new PrivateSoundState(0,false,1);source=new Source(bytes,true,state);stream=new PrivatePcmStream(source,state);failed=false;try{stream.read(128);}catch(IOException error){failed=error.getMessage().equals("playback_cancelled");}check(failed&&source.calls==3);stream.close();
  state=new PrivateSoundState(0,false,1);var first=new BurnCoordinator.Cancellation();var second=new BurnCoordinator.Cancellation();state.bindFetch(first);state.bindFetch(second);state.clearFetch(first);state.cancel();check(second.cancelled()&&!first.cancelled());
  var late=new BurnCoordinator.Cancellation();state.bindFetch(late);check(late.cancelled());
  for(String label:new String[]{"×","✕","✖","X","关闭","Close"," close "})check(BurnScreenRuntime.closeControl(label));
  check(!BurnScreenRuntime.closeControl("取消刻录")&&!BurnScreenRuntime.closeControl(null));
  System.out.println("{\"pcmZeroReadChecks\":"+checks+",\"finiteZeroRetry\":true,\"cancelledStallStops\":true,\"mcStarted\":false}");
 }
}
