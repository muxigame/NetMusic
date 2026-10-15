package net.muxigame.musiccompat.burn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.minecraft.network.ConnectionProtocol;import java.util.function.Consumer;
/** Optional registration requires per-connection negotiation. Never let one recipient abort fan-out. */
public final class PlaybackDelivery {
 public enum Result {READY,SENT,CLOSED,NOT_PLAY,UNSUPPORTED,REJECTED}
 public static Result status(ICommonPacketListener listener,boolean accepting){
  if(listener==null||listener.getConnection()==null||!listener.getConnection().isConnected()||!accepting)return Result.CLOSED;
  if(listener.protocol()!=ConnectionProtocol.PLAY)return Result.NOT_PLAY;
  if(!listener.hasChannel(PlaybackNetwork.State.TYPE)||!listener.hasChannel(PlaybackNetwork.Report.TYPE))return Result.UNSUPPORTED;
  return Result.READY;
 }
 public static Result send(ICommonPacketListener listener,boolean accepting,Runnable operation,Consumer<RuntimeException> rejected){
  try{var result=status(listener,accepting);if(result!=Result.READY)return result;operation.run();return Result.SENT;}
  catch(RuntimeException error){rejected.accept(error);return Result.REJECTED;}
 }
 private PlaybackDelivery(){}
}
