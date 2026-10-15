package net.muxigame.musiccompat.qa.entry042.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;import net.minecraft.server.level.ServerPlayer;import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;import com.github.tartaricacid.netmusic.inventory.*;import net.muxigame.musiccompat.qa.entry042.*;import net.muxigame.musiccompat.burn.BurnMenuAccess;import net.muxigame.musiccompat.burn.BurnServer;import java.util.*;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observation only, in the owned QA world: records real client click packets after server processing. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class Entry042ClickPacketMixin {
 @Shadow public ServerPlayer player;
 @Unique private static final List<Map<String,Object>> packets=new ArrayList<>();
 @Inject(method="handleContainerClick",at=@At("RETURN"))
 private void observe(ServerboundContainerClickPacket packet,CallbackInfo callback){
  var menu=player.containerMenu;if(!player.getGameProfile().getName().equals("10000")||!(menu instanceof CDBurnerMenu||menu instanceof ComputerMenu))return;
  Entry042Server.require(player);var in=menu instanceof CDBurnerMenu m?m.getInput():((ComputerMenu)menu).getInput();var row=new LinkedHashMap<String,Object>();row.put("slot",packet.getSlotNum());row.put("button",packet.getButtonNum());row.put("click",packet.getClickType().name());row.put("menuId",packet.getContainerId());var owned=Entry042DiscAudit.escrow(player);int marker=owned.isEmpty()?in.getStackInSlot(0).getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0):owned.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0);row.put("discMarker",marker);row.put("escrowCount",owned.getCount());row.put("markedInventoryCount",marker==0?0:Entry042DiscAudit.inventoryCount(player,marker));row.put("activeTask",Entry042DiscAudit.task(player));row.put("inputCount",in.getStackInSlot(0).getCount());row.put("outputCount",((BurnMenuAccess)menu).muxiBurn$output().getStackInSlot(0).getCount());row.put("carriedCount",menu.getCarried().getCount());row.put("serverLocked",BurnServer.locked(menu));row.put("atUtcMillis",System.currentTimeMillis());packets.add(row);if(!owned.isEmpty())Entry042DiscAudit.audit(player,"actual-client-click-with-escrow",true);Entry042Scope.write("entry042-click-packets.json",Map.of("runId",System.getProperty("qa.local.runId"),"actualClientPackets",true,"packets",packets));
 }
}
