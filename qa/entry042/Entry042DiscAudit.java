package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import com.github.tartaricacid.netmusic.inventory.*;import com.github.tartaricacid.netmusic.item.ItemMusicCD;import com.github.tartaricacid.netmusic.init.InitItems;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.item.ItemStack;import net.minecraft.core.component.DataComponents;import net.minecraft.world.entity.item.ItemEntity;import net.muxigame.musiccompat.burn.BurnMenuAccess;import java.util.*;import java.nio.file.*;
/** Marker stays on the SAME original fixture CD, allowing exact return/write counts per attempt. */
public final class Entry042DiscAudit {
 private static final Map<Integer,String> cases=new LinkedHashMap<>();
 private static boolean loaded;private static long revision;private static final List<Map<String,Object>> history=new ArrayList<>();
 static void load(){Entry042Scope.require();if(loaded)return;loaded=true;try{Path p=Entry042Scope.root().resolve("coordinator/entry042-disc-cases.json");if(Files.exists(p)){var a=JsonParser.parseString(Files.readString(p)).getAsJsonArray();for(var e:a){var x=e.getAsJsonObject();cases.put(x.get("marker").getAsInt(),x.get("caseId").getAsString());}}}catch(Exception bad){throw new IllegalStateException("owned_disc_cases_invalid");}}
 static ItemStack disc(ServerPlayer player){Entry042Server.require(player);load();int marker;do{marker=UUID.randomUUID().hashCode()&0x7fffffff;}while(marker==0||cases.containsKey(marker));cases.put(marker,UUID.randomUUID().toString());var stack=new ItemStack(InitItems.MUSIC_CD.get());stack.set(DataComponents.REPAIR_COST,marker);save();return stack;}
 static void save(){var rows=new ArrayList<Map<String,Object>>();cases.forEach((m,id)->rows.add(Map.of("marker",m,"caseId",id)));Entry042Scope.write("entry042-disc-cases.json",rows);}
 private static boolean pending(ServerPlayer player){try{var field=net.muxigame.musiccompat.burn.BurnServer.class.getDeclaredField("pending");field.setAccessible(true);return ((Map<?,?>)field.get(null)).containsKey(player.getUUID());}catch(Exception changed){throw new IllegalStateException("pinned_product_pending_field_changed");}}
 private static Object context(ServerPlayer player){
  Entry042Server.require(player);
  try{var f=net.muxigame.musiccompat.burn.BurnServer.class.getDeclaredField("contexts");f.setAccessible(true);Object value=((Map<?,?>)f.get(null)).get(player.getUUID());if(value!=null&&read(value,"player")!=player)throw new IllegalStateException("wrong_connection_escrow");return value;}
  catch(ReflectiveOperationException changed){throw new IllegalStateException("pinned_product_escrow_field_changed",changed);}
 }
 private static Object read(Object value,String field)throws ReflectiveOperationException{var f=value.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(value);}
 public static ItemStack escrow(ServerPlayer player){
  Object value=context(player);if(value==null)return ItemStack.EMPTY;
  try{return ((net.muxigame.musiccompat.burn.BurnEscrow)read(value,"escrow")).original();}
  catch(ReflectiveOperationException changed){throw new IllegalStateException("pinned_product_escrow_field_changed",changed);}
 }
 public static Map<String,Object> task(ServerPlayer player){
  Object value=context(player);var row=new LinkedHashMap<String,Object>();row.put("active",value!=null);
  if(value==null){row.put("escrowCount",0);return row;}
  try{
   ItemStack item=((net.muxigame.musiccompat.burn.BurnEscrow)read(value,"escrow")).original();Object menu=read(value,"menu");
   row.put("requestId",read(value,"request"));row.put("originMenuId",read(value,"menuId"));row.put("menuAttached",menu!=null);row.put("menuCurrent",menu!=null&&player.containerMenu==menu);
   row.put("escrowCount",item.getCount());row.put("marker",item.getOrDefault(DataComponents.REPAIR_COST,0));row.put("readOnly",read(value,"readOnly"));row.put("expiresAtUtcMillis",read(value,"expires"));row.put("samePlayerObject",read(value,"player")==player);row.put("noncePresent",net.muxigame.musiccompat.burn.BurnProtocol.uuid((String)read(value,"nonce")));return row;
  }catch(ReflectiveOperationException changed){throw new IllegalStateException("pinned_product_escrow_field_changed",changed);}
 }
 public static int inventoryCount(ServerPlayer player,int marker){int n=0;for(int i=0;i<player.getInventory().getContainerSize();i++)n+=count(player.getInventory().getItem(i),marker);return n;}
 private static int count(ItemStack stack,int marker){return stack.getItem() instanceof ItemMusicCD&&stack.getOrDefault(DataComponents.REPAIR_COST,0)==marker?stack.getCount():0;}
 public static void audit(ServerPlayer player,String event,boolean requireOnce){
  Entry042Server.require(player);load();var rows=new ArrayList<Map<String,Object>>();var task=task(player);ItemStack owned=escrow(player);
  for(var entry:cases.entrySet()){
   int marker=entry.getKey(),inventory=0,input=0,output=0,carried=0,loose=0,escrow=count(owned,entry.getKey());boolean burned=false;
   for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);inventory+=count(stack,marker);if(count(stack,marker)>0&&ItemMusicCD.getSongInfo(stack)!=null)burned=true;}
   var menu=player.containerMenu;carried=count(menu.getCarried(),marker);Entry042Server.rememberMenu(player,menu);if(escrow>0&&ItemMusicCD.getSongInfo(owned)!=null)burned=true;
   if(menu instanceof CDBurnerMenu||menu instanceof ComputerMenu){input=count(Entry042Server.input(menu).getStackInSlot(0),marker);var result=((BurnMenuAccess)menu).muxiBurn$output().getStackInSlot(0);output=count(result,marker);if(output>0&&ItemMusicCD.getSongInfo(result)!=null)burned=true;}
   for(var level:player.server.getAllLevels())for(var entity:level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(64)))loose+=count(entity.getItem(),marker);
   int placed=Entry042Playback.count(player,marker);if(placed>0)burned=true;int total=inventory+input+output+carried+loose+placed+escrow;var row=new LinkedHashMap<String,Object>();row.put("caseId",entry.getValue());row.put("marker",marker);row.put("inventory",inventory);row.put("escrow",escrow);if(escrow>0)row.put("taskRequestId",task.get("requestId"));row.put("input",input);row.put("output",output);row.put("carried",carried);row.put("loose",loose);row.put("total",total);row.put("playerBlock",placed);
   for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(count(stack,marker)>0){var song=ItemMusicCD.getSongInfo(stack);if(song!=null){row.put("reference",song.songUrl);row.put("readOnly",song.readOnly);}}}row.put("burned",burned);rows.add(row);
   if(requireOnce&&total!=1)throw new IllegalStateException("original_fixture_disc_not_exactly_once");
  }
  if(!event.equals("periodic")){history.add(Map.of("event",event,"cases",rows,"atUtcMillis",System.currentTimeMillis()));Entry042Scope.write("entry042-disc-lifecycle.json",Map.of("runId",System.getProperty("qa.local.runId"),"history",history));}
  Entry042Scope.write("entry042-disc-counts.json",Map.of("runId",System.getProperty("qa.local.runId"),"event",event,"revision",++revision,"atUtcMillis",System.currentTimeMillis(),"cases",rows,"actualServerStacks",true,"menuLocked",net.muxigame.musiccompat.burn.BurnServer.locked(player.containerMenu),"receiptPending",pending(player),"fixtureIdentityMarker",true,"activeTask",task));
 }
 public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p&&p.getGameProfile().getName().equals("10000"))try{audit(p,"logout-after-product-unlock",true);}catch(Entry042Scope.ReceiptWriteFailure temporaryIo){System.out.println("ENTRY042_LOGOUT_RECEIPT_IO_FAILURE "+temporaryIo.getCause().getClass().getSimpleName());}}
 public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event){for(var p:event.getServer().getPlayerList().getPlayers())if(p.getGameProfile().getName().equals("10000"))try{audit(p,"server-stopping-after-product-return",true);}catch(Entry042Scope.ReceiptWriteFailure temporaryIo){System.out.println("ENTRY042_STOP_RECEIPT_IO_FAILURE "+temporaryIo.getCause().getClass().getSimpleName());}}
 private Entry042DiscAudit(){}
}
