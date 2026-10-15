package net.muxigame.musiccompat.burn;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

public final class BurnNetwork {
    private static Consumer<Result> receiver=p->{};
    private BurnNetwork(){}
    public static void clientReceiver(Consumer<Result> value){receiver=value;}
    /** Actions: reserve=0, commit=1, explicit cancel=2, nonce-bound status query=3. */
    public record Request(String requestId,int menu,int action,String proof,boolean readOnly) implements CustomPacketPayload {
        public Request(String requestId,int menu,int action,String proof){this(requestId,menu,action,proof,true);}
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("muxi_music_compat","burn_request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeVarInt(p.menu);b.writeVarInt(p.action);b.writeUtf(p.proof,8192);b.writeBoolean(p.readOnly);},
            b->new Request(b.readUtf(36),b.readVarInt(),b.readVarInt(),b.readUtf(8192),b.readBoolean()));
        @Override public Type<Request> type(){return TYPE;}
        @Override public String toString(){return "MusicBurnRequest[proof=<redacted>]";}
    }
    /** Status: reservation=1, delivered=2, failed/refunded=3, active=4. */
    public record Result(String requestId,String nonce,int status) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("muxi_music_compat","burn_result_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeUtf(p.nonce,36);b.writeVarInt(p.status);},
            b->new Result(b.readUtf(36),b.readUtf(36),b.readVarInt()));
        @Override public Type<Result> type(){return TYPE;}
    }
    public static void register(IEventBus bus){bus.addListener(BurnNetwork::payloads);}
    private static void payloads(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("music-burn-1").optional();
        registrar.playToClient(Result.TYPE,Result.CODEC,(p,c)->receiver.accept(p));
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)BurnServer.request(player,p);});
    }
}
