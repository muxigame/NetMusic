package net.muxigame.musiccompat;

import net.muxigame.musiccompat.burn.BurnNetwork;
import net.muxigame.musiccompat.burn.BurnScreenRuntime;
import net.muxigame.musiccompat.burn.BurnClientSession;
import net.muxigame.musiccompat.burn.BurnServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Integrated NetMusic services; protocol and saved-data keys retain their original names. */
public final class IntegratedMusic {
    private IntegratedMusic(){}
    public static void register(IEventBus bus){
        BurnNetwork.register(bus);
        BurnServer.registerEvents();
        net.muxigame.musiccompat.burn.PlaybackNetwork.register(bus);
        NeoForge.EVENT_BUS.addListener(net.muxigame.musiccompat.burn.PlaybackServer::sent);
        NeoForge.EVENT_BUS.addListener(net.muxigame.musiccompat.burn.PlaybackServer::tick);
        NeoForge.EVENT_BUS.addListener(net.muxigame.musiccompat.burn.PlaybackServer::stopped);
        NeoForge.EVENT_BUS.addListener(BurnServer::logout);
        NeoForge.EVENT_BUS.addListener(BurnServer::stopped);
        // Return original menu items while other mods inventory listeners are alive.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,BurnServer::stopping);
        NeoForge.EVENT_BUS.addListener(BurnServer::tick);
        NeoForge.EVENT_BUS.addListener(BurnServer::death);
        if(FMLEnvironment.dist==Dist.CLIENT)bus.addListener(IntegratedMusic::clientSetup);
    }
    private static void clientSetup(FMLClientSetupEvent event){event.enqueueWork(()->{BurnScreenRuntime.install(new BurnClientSession());net.muxigame.musiccompat.burn.PlaybackNetwork.receiver(net.muxigame.musiccompat.burn.PlaybackClient::accept);NeoForge.EVENT_BUS.addListener(net.muxigame.musiccompat.burn.PlaybackClient::tick);});}
}
