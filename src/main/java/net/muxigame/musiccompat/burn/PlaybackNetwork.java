package net.muxigame.musiccompat.burn;
import net.minecraft.core.BlockPos;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.IEventBus;import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;import java.util.function.Consumer;
public final class PlaybackNetwork {
 private static Consumer<State> receiver=p->{};
 public static void receiver(Consumer<State> r){receiver=r;}
 public record State(String dimension,BlockPos pos,String epoch,String reference,String title,int duration,double seconds,boolean moving,boolean prepared,boolean active)implements CustomPacketPayload {
  public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("muxi_music_compat","private_playback_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.dimension,128);b.writeBlockPos(p.pos);b.writeUtf(p.epoch,36);b.writeUtf(p.reference,512);b.writeUtf(p.title,256);b.writeVarInt(p.duration);b.writeDouble(p.seconds);b.writeBoolean(p.moving);b.writeBoolean(p.prepared);b.writeBoolean(p.active);},b->new State(b.readUtf(128),b.readBlockPos(),b.readUtf(36),b.readUtf(512),b.readUtf(256),b.readVarInt(),b.readDouble(),b.readBoolean(),b.readBoolean(),b.readBoolean()));public Type<State> type(){return TYPE;}
 }
 public record Report(BlockPos pos,String epoch,double seconds,boolean moving)implements CustomPacketPayload {
  public static final Type<Report> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("muxi_music_compat","private_audio_point_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Report> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.epoch,36);b.writeDouble(p.seconds);b.writeBoolean(p.moving);},b->new Report(b.readBlockPos(),b.readUtf(36),b.readDouble(),b.readBoolean()));public Type<Report> type(){return TYPE;}
 }
 public static void register(IEventBus bus){bus.addListener(PlaybackNetwork::payloads);}
 private static void payloads(RegisterPayloadHandlersEvent event){var r=event.registrar("private-audio-1").optional();r.playToClient(State.TYPE,State.CODEC,(p,c)->receiver.accept(p));r.playToServer(Report.TYPE,Report.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)PlaybackServer.report(player,p);});}
 private PlaybackNetwork(){}
}
