package net.muxigame.musiccompat.qa.entry042;
import net.neoforged.fml.common.Mod;import net.neoforged.bus.api.IEventBus;import net.neoforged.fml.loading.FMLEnvironment;import net.neoforged.api.distmarker.Dist;import net.neoforged.neoforge.common.NeoForge;import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@Mod("muxi_music_entry042_qa") public final class Entry042Mod {
 public Entry042Mod(IEventBus bus){Entry042Scope.require();NeoForge.EVENT_BUS.addListener(Entry042Server::commands);NeoForge.EVENT_BUS.addListener(Entry042Lifecycle::commands);NeoForge.EVENT_BUS.addListener(Entry042Lifecycle::tick);NeoForge.EVENT_BUS.addListener(Entry042Server::tick);NeoForge.EVENT_BUS.addListener(Entry042DiscAudit::logout);NeoForge.EVENT_BUS.addListener(Entry042DiscAudit::stopping);if(FMLEnvironment.dist==Dist.CLIENT)bus.addListener(Entry042Mod::client);}
 static void client(FMLClientSetupEvent event){event.enqueueWork(Entry042Client::new);event.enqueueWork(Entry042LifecycleClient::new);}
}
