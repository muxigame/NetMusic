package net.muxigame.musiccompat.burn;
import java.util.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;
import io.netty.channel.embedded.EmbeddedChannel;import net.minecraft.network.*;import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;import net.neoforged.neoforge.network.registration.*;
/** Replay the production refusal with the actual pinned NeoForge registry and real Netty connection. */
public final class PlaybackDeliveryTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("delivery check "+checks);}
 static final class Peer implements AutoCloseable {
  final Connection connection=new Connection(PacketFlow.SERVERBOUND);final EmbeddedChannel channel=new EmbeddedChannel();
  ConnectionProtocol protocol=ConnectionProtocol.PLAY;final ServerCommonPacketListener listener;
  Peer(boolean enabled)throws Exception{var field=Connection.class.getDeclaredField("channel");field.setAccessible(true);field.set(connection,channel);
   listener=(ServerCommonPacketListener)Proxy.newProxyInstance(ServerCommonPacketListener.class.getClassLoader(),new Class<?>[]{ServerCommonPacketListener.class},(proxy,method,args)->{
    if(method.getName().equals("getConnection"))return connection;if(method.getName().equals("protocol"))return protocol;
    if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);throw new UnsupportedOperationException(method.getName());});
   setup(enabled,true);
  }
  void setup(boolean state,boolean report){var channels=new HashMap<net.minecraft.resources.ResourceLocation,NetworkChannel>();if(state)channels.put(PlaybackNetwork.State.TYPE.id(),new NetworkChannel(PlaybackNetwork.State.TYPE.id(),"private-audio-1"));if(report)channels.put(PlaybackNetwork.Report.TYPE.id(),new NetworkChannel(PlaybackNetwork.Report.TYPE.id(),"private-audio-1"));ChannelAttributes.setPayloadSetup(connection,new NetworkPayloadSetup(Map.of(ConnectionProtocol.PLAY,channels)));}
  public void close(){channel.finishAndReleaseAll();}
 }
 public static void main(String[] args)throws Exception{
  net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var payload=new PlaybackNetwork.State("minecraft:overworld",net.minecraft.core.BlockPos.ZERO,UUID.randomUUID().toString(),BurnProtocol.OSS.toString()+"media/"+"10000/00000000-0000-4000-8000-000000000001.wav","Owned",60,1,true,true,true);
  var packet=new ClientboundCustomPayloadPacket(payload);int[] calls={0},errors={0};
  try(var missing=new Peer(false);var good=new Peer(true)){
   check(missing.connection.isConnected());boolean rejected=false;try{NetworkRegistry.checkPacket(packet,missing.listener);}catch(UnsupportedOperationException error){rejected=error.getMessage().contains("private_playback_v1")&&error.getMessage().contains("may not be sent to the client");}check(rejected);
   check(PlaybackDelivery.send(missing.listener,true,()->{calls[0]++;NetworkRegistry.checkPacket(packet,missing.listener);},error->errors[0]++)==PlaybackDelivery.Result.UNSUPPORTED);check(calls[0]==0&&errors[0]==0);
   check(PlaybackDelivery.send(good.listener,true,()->{calls[0]++;NetworkRegistry.checkPacket(packet,good.listener);},error->errors[0]++)==PlaybackDelivery.Result.SENT);check(calls[0]==1);
   good.setup(true,false);check(PlaybackDelivery.status(good.listener,true)==PlaybackDelivery.Result.UNSUPPORTED);good.setup(true,true);
   good.protocol=ConnectionProtocol.CONFIGURATION;check(PlaybackDelivery.status(good.listener,true)==PlaybackDelivery.Result.NOT_PLAY);good.protocol=ConnectionProtocol.PLAY;
   check(PlaybackDelivery.status(good.listener,false)==PlaybackDelivery.Result.CLOSED);check(PlaybackDelivery.status(null,true)==PlaybackDelivery.Result.CLOSED);
   // Capability disappears after precheck; the actual original exception must stay inside this recipient.
   check(PlaybackDelivery.send(good.listener,true,()->{good.setup(false,false);NetworkRegistry.checkPacket(packet,good.listener);},error->{check(error instanceof UnsupportedOperationException);errors[0]++;})==PlaybackDelivery.Result.REJECTED);check(errors[0]==1);
   good.setup(true,true);check(PlaybackDelivery.send(good.listener,true,()->NetworkRegistry.checkPacket(packet,good.listener),error->errors[0]++)==PlaybackDelivery.Result.SENT);
   for(int i=0;i<200;i++){check(PlaybackDelivery.send(missing.listener,true,()->{throw new AssertionError("unsupported peer was sent a payload");},error->errors[0]++)==PlaybackDelivery.Result.UNSUPPORTED);check(PlaybackDelivery.send(good.listener,true,()->NetworkRegistry.checkPacket(packet,good.listener),error->errors[0]++)==PlaybackDelivery.Result.SENT);}
   check(errors[0]==1);good.channel.close();check(PlaybackDelivery.status(good.listener,true)==PlaybackDelivery.Result.CLOSED);
  }
  System.out.println("{\"deliveryChecks\":"+checks+",\"actualPinnedNeoForgeRefusalReproduced\":true,\"actualNettyConnection\":true,\"recipientRaceContained\":true,\"mcStarted\":false,\"cloudCalls\":0}");
 }
}
