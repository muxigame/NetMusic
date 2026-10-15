package net.muxigame.musiccompat.qa.entry042;
import com.github.tartaricacid.netmusic.inventory.*;import com.github.tartaricacid.netmusic.item.ItemMusicCD;import com.github.tartaricacid.netmusic.init.InitItems;
import net.muxigame.musiccompat.burn.*;import net.muxigame.musiccompat.burn.BurnMenuAccess;import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.SimpleMenuProvider;import net.minecraft.world.inventory.AbstractContainerMenu;import net.minecraft.world.item.ItemStack;import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;import net.neoforged.neoforge.event.tick.ServerTickEvent;import net.minecraft.commands.Commands;import java.util.*;
public final class Entry042Server {
 private static int ticks;private static final List<Map<String,Object>> events=new ArrayList<>();private static final Map<UUID,Boolean> openedTypes=new HashMap<>();
 public static void require(ServerPlayer player){Entry042Scope.require();if(!"10000".equals(player.getGameProfile().getName())||!"qa-world".equals(player.server.getWorldData().getLevelName()))throw new IllegalStateException("owned_player_world_required");}
 static net.neoforged.neoforge.items.ItemStackHandler input(AbstractContainerMenu menu){return menu instanceof CDBurnerMenu m?m.getInput():((ComputerMenu)menu).getInput();}
 public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("entry042")
  .requires(source->source.getEntity() instanceof ServerPlayer player&&Set.of("10000","10001").contains(player.getGameProfile().getName()))
  .then(Commands.literal("open-existing").then(Commands.argument("marker",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c->Entry042Playback.existing(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"marker")))))
  .then(Commands.literal("playback-place").then(Commands.argument("marker",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).then(Commands.argument("site",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1)).executes(c->Entry042Playback.place(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"marker"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"site"))))))
  .then(Commands.literal("playback-start").executes(c->Entry042Playback.action(c.getSource().getPlayerOrException(),"start")))
  .then(Commands.literal("playback-start-legacy").executes(c->Entry042Playback.legacyStart(c.getSource().getPlayerOrException())))
  .then(Commands.literal("playback-stop").executes(c->Entry042Playback.action(c.getSource().getPlayerOrException(),"stop")))
  .then(Commands.literal("playback-return").executes(c->Entry042Playback.action(c.getSource().getPlayerOrException(),"return")))
  .then(Commands.literal("join-listener").executes(c->Entry042Playback.join(c.getSource().getPlayerOrException())))
  .then(Commands.literal("reopen-existing-active").executes(c->reopen(c.getSource().getPlayerOrException(),null))
   .then(Commands.literal("computer").executes(c->reopen(c.getSource().getPlayerOrException(),true)))
   .then(Commands.literal("burner").executes(c->reopen(c.getSource().getPlayerOrException(),false))))
  .then(Commands.literal("open-burner").executes(c->open(c.getSource().getPlayerOrException(),false)))
  .then(Commands.literal("open-computer").executes(c->open(c.getSource().getPlayerOrException(),true)))
  .then(Commands.literal("slot-attacks").executes(c->attacks(c.getSource().getPlayerOrException())))
  .then(Commands.literal("audit-discs").executes(c->{Entry042DiscAudit.audit(c.getSource().getPlayerOrException(),"explicit-audit",true);return 1;}))
  .then(Commands.literal("stop-server-owned").executes(c->{var p=c.getSource().getPlayerOrException();require(p);Entry042DiscAudit.audit(p,"owned-server-stop-requested",true);try{java.nio.file.Files.writeString(Entry042Scope.root().resolve("stop-request.json"),new com.google.gson.Gson().toJson(Map.of("runId",System.getProperty("qa.local.runId"),"normalStopOnly",true,"musicServerStopTest",true)));}catch(java.io.IOException failed){throw new IllegalStateException(failed);}p.server.halt(false);return 1;}))
  .then(Commands.literal("close-menu").executes(c->{var p=c.getSource().getPlayerOrException();require(p);p.closeContainer();Entry042DiscAudit.audit(p,"normal-menu-close",true);return 1;}))
  .then(Commands.literal("lock-matrix").executes(c->matrix(c.getSource().getPlayerOrException())))
  .then(Commands.literal("unverified-write").executes(c->unverified(c.getSource().getPlayerOrException()))));}
 static void rememberMenu(ServerPlayer player,AbstractContainerMenu menu){if(menu instanceof ComputerMenu)openedTypes.put(player.getUUID(),true);else if(menu instanceof CDBurnerMenu)openedTypes.put(player.getUUID(),false);}
 static int reopen(ServerPlayer player,Boolean requested){
  require(player);var before=Entry042DiscAudit.task(player);invariant(Boolean.TRUE.equals(before.get("active")));
  ItemStack original=Entry042DiscAudit.escrow(player);invariant(original.getCount()==1);
  Boolean computer=requested==null?openedTypes.get(player.getUUID()):requested;if(computer==null)throw new IllegalStateException("owned_original_menu_type_unknown");
  player.closeContainer();
  player.openMenu(new SimpleMenuProvider((id,inventory,p)->computer?new ComputerMenu(id,inventory):new CDBurnerMenu(id,inventory),Component.literal("Entry042 existing background task")));
  rememberMenu(player,player.containerMenu);player.containerMenu.broadcastChanges();
  var after=Entry042DiscAudit.task(player);
  invariant(before.get("requestId").equals(after.get("requestId"))&&ItemStack.matches(original,Entry042DiscAudit.escrow(player)));
  invariant(input(player.containerMenu).getStackInSlot(0).isEmpty()&&((BurnMenuAccess)player.containerMenu).muxiBurn$output().getStackInSlot(0).isEmpty());
  events.add(Map.of("event","reopened-real-existing-task","requestId",before.get("requestId"),"discMarker",before.get("marker"),"menu",player.containerMenu.getClass().getSimpleName(),"newDiscCreated",false,"newTaskReserved",false,"atUtcMillis",System.currentTimeMillis()));
  Entry042DiscAudit.audit(player,"reopened-real-existing-task",true);return 1;
 }
 static int open(ServerPlayer player,boolean computer){require(player);if(Boolean.TRUE.equals(Entry042DiscAudit.task(player).get("active")))throw new IllegalStateException("use_reopen_existing_active");player.closeContainer();player.openMenu(new SimpleMenuProvider((id,inventory,p)->computer?new ComputerMenu(id,inventory):new CDBurnerMenu(id,inventory),Component.literal("Entry042 local fixture")));rememberMenu(player,player.containerMenu);input(player.containerMenu).setStackInSlot(0,Entry042DiscAudit.disc(player));player.containerMenu.broadcastChanges();events.add(Map.of("event","opened-real-menu","menu",player.containerMenu.getClass().getSimpleName(),"blankCd",true));return 1;}
 static int unverified(ServerPlayer player){require(player);AbstractContainerMenu menu=player.containerMenu;ItemStack before=input(menu).getStackInSlot(0).copy();var song=new ItemMusicCD.SongInfo("https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/00000000-0000-4000-8000-000000000042.mp3","UNVERIFIED FIXTURE",3,false);if(menu instanceof CDBurnerMenu m)m.setSongInfo(song);else if(menu instanceof ComputerMenu m)m.setSongInfo(song);else throw new IllegalStateException();boolean blocked=((BurnMenuAccess)menu).muxiBurn$output().getStackInSlot(0).isEmpty()&&ItemStack.matches(before,input(menu).getStackInSlot(0));if(!blocked)throw new IllegalStateException("unverified_write_not_blocked");events.add(Map.of("event","actual-unverified-write-blocked","menu",menu.getClass().getSimpleName()));return 1;}
 static void invariant(boolean value){if(!value)throw new IllegalStateException("native_lock_invariant_failed");}
 static int discs(ServerPlayer player){int n=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.getItem() instanceof ItemMusicCD)n+=stack.getCount();}return n;}
 static void escrowInvariant(ServerPlayer player,ItemStack original,AbstractContainerMenu menu){
  invariant(ItemStack.matches(original,Entry042DiscAudit.escrow(player))&&input(menu).getStackInSlot(0).isEmpty()&&((BurnMenuAccess)menu).muxiBurn$output().getStackInSlot(0).isEmpty()&&menu.getCarried().isEmpty());
  invariant(Entry042DiscAudit.inventoryCount(player,original.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0))==0);
 }
 static int attacks(ServerPlayer player){
  String stage="require";var menu=player.containerMenu;var completed=new ArrayList<String>();
  try{
   require(player);stage="input-handler";var in=input(menu);stage="output-handler";var out=((BurnMenuAccess)menu).muxiBurn$output();stage="initial-menu-lock";invariant(BurnServer.locked(menu));stage="initial-escrow";var before=Entry042DiscAudit.escrow(player);invariant(before.getCount()==1);int count=0;
   for(var click:net.minecraft.world.inventory.ClickType.values())for(int slot:new int[]{0,1})for(int button:new int[]{0,1,2,8,40}){stage="click:"+click+":"+slot+":"+button;menu.clicked(slot,button,click,player);stage+="/escrow-invariant";escrowInvariant(player,before,menu);completed.add(stage);count++;}
   for(var click:new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.QUICK_MOVE,net.minecraft.world.inventory.ClickType.QUICK_CRAFT,net.minecraft.world.inventory.ClickType.PICKUP_ALL}){stage="inventory-click:"+click;menu.clicked(2,0,click,player);stage+="/escrow-invariant";escrowInvariant(player,before,menu);completed.add(stage);count++;}
   stage="quick-move";invariant(menu.quickMoveStack(player,0).isEmpty());completed.add(stage);
   for(boolean simulate:new boolean[]{true,false}){stage="extract:"+simulate;invariant(in.extractItem(0,1,simulate).isEmpty());completed.add(stage);stage="insert:"+simulate;invariant(in.insertItem(0,before.copy(),simulate).getCount()==1);completed.add(stage);}
   stage="set-stack";in.setStackInSlot(0,ItemStack.EMPTY);completed.add(stage);
   stage="set-size";in.setSize(0);completed.add(stage);
   stage="deserialize";in.deserializeNBT(player.registryAccess(),new net.minecraft.nbt.CompoundTag());completed.add(stage);
   stage="mutate-returned-stack";in.getStackInSlot(0).setCount(0);completed.add(stage);
   stage="final-escrow-invariant";escrowInvariant(player,before,menu);completed.add(stage);stage="final-slot-count";invariant(in.getSlots()==1);completed.add(stage);
   stage="audit";Entry042DiscAudit.audit(player,"real-progress-slot-attacks",true);events.add(Map.of("event","real-task-locked-attacks","menu",menu.getClass().getSimpleName(),"clickAttempts",count,"discMarker",before.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0),"escrowUnaffected",true));stage="success-receipt";Entry042Scope.write("entry042-slot-attacks.json",Map.of("runId",System.getProperty("qa.local.runId"),"events",events,"completedStages",completed));return 1;
  }catch(Throwable failure){
   var row=new LinkedHashMap<String,Object>();row.put("runId",System.getProperty("qa.local.runId"));row.put("stage",stage);row.put("completedStages",completed);row.put("menuClass",menu.getClass().getName());row.put("errorType",failure.getClass().getName());row.put("message",String.valueOf(failure.getMessage()));var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));row.put("stack",trace.toString());
   try{row.put("menuLocked",BurnServer.locked(menu));row.put("inputClass",input(menu).getClass().getName());row.put("inputSlots",input(menu).getSlots());row.put("inputCount",input(menu).getStackInSlot(0).getCount());var out=((BurnMenuAccess)menu).muxiBurn$output();row.put("outputClass",out.getClass().getName());row.put("outputSlots",out.getSlots());row.put("outputCount",out.getStackInSlot(0).getCount());row.put("carriedCount",menu.getCarried().getCount());row.put("task",Entry042DiscAudit.task(player));}catch(Throwable diagnostic){row.put("snapshotError",diagnostic.toString());failure.addSuppressed(diagnostic);}
   try{Entry042Scope.write("entry042-slot-attacks-error.json",row);}catch(Throwable writeFailure){failure.addSuppressed(writeFailure);}
   if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw new IllegalStateException("slot_attacks_failed",failure);
  }
 }
 static void insertReturnedOriginal(ServerPlayer player,AbstractContainerMenu menu,int marker){
  invariant(menu.getCarried().isEmpty()&&input(menu).getStackInSlot(0).isEmpty());int source=-1;
  for(int i=2;i<menu.slots.size();i++){var stack=menu.slots.get(i).getItem();if(stack.getItem() instanceof ItemMusicCD&&stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0)==marker){source=i;break;}}
  invariant(source>=0);menu.clicked(source,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
  invariant(menu.getCarried().getCount()==1&&menu.getCarried().getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0)==marker);
  menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);invariant(menu.getCarried().isEmpty()&&input(menu).getStackInSlot(0).getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0)==marker);
 }
 static int matrix(ServerPlayer player){
  require(player);var menu=player.containerMenu;var in=input(menu);var out=((BurnMenuAccess)menu).muxiBurn$output();rememberMenu(player,menu);
  invariant(in instanceof BurnLockedItemHandler&&out instanceof BurnLockedItemHandler);invariant(in.getStackInSlot(0).getCount()==1&&out.getStackInSlot(0).isEmpty());
  int baseline=discs(player)+1;var before=in.getStackInSlot(0).copy();var stale=in.getStackInSlot(0);int marker=before.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0);String first=UUID.randomUUID().toString();
  BurnServer.request(player,new BurnNetwork.Request(first,menu.containerId,0,"",false));invariant(BurnServer.locked(menu));stale.setCount(0);escrowInvariant(player,before,menu);
  BurnServer.request(player,new BurnNetwork.Request(first,menu.containerId,0,"",false));invariant(BurnServer.locked(menu));
  BurnServer.request(player,new BurnNetwork.Request(UUID.randomUUID().toString(),menu.containerId,0,"",false));invariant(BurnServer.locked(menu));escrowInvariant(player,before,menu);
  int blocked=0;
  for(var click:net.minecraft.world.inventory.ClickType.values())for(int slot:new int[]{0,1})for(int button:new int[]{0,1,2,8,40}){menu.clicked(slot,button,click,player);escrowInvariant(player,before,menu);blocked++;}
  for(var click:new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.QUICK_MOVE,net.minecraft.world.inventory.ClickType.QUICK_CRAFT,net.minecraft.world.inventory.ClickType.PICKUP_ALL}){menu.clicked(2,0,click,player);escrowInvariant(player,before,menu);blocked++;}
  invariant(menu.quickMoveStack(player,0).isEmpty());for(boolean simulate:new boolean[]{true,false}){invariant(in.extractItem(0,1,simulate).isEmpty());invariant(in.insertItem(0,before.copy(),simulate).getCount()==1);}
  in.setStackInSlot(0,ItemStack.EMPTY);in.setSize(0);in.deserializeNBT(player.registryAccess(),new net.minecraft.nbt.CompoundTag());in.getStackInSlot(0).setCount(0);escrowInvariant(player,before,menu);invariant(in.getSlots()==1);
  BurnServer.request(player,new BurnNetwork.Request(first,menu.containerId,2,""));invariant(!BurnServer.locked(menu)&&in.getStackInSlot(0).isEmpty()&&Entry042DiscAudit.escrow(player).isEmpty()&&Entry042DiscAudit.inventoryCount(player,marker)==1&&discs(player)==baseline);
  insertReturnedOriginal(player,menu,marker);String second=UUID.randomUUID().toString();BurnServer.request(player,new BurnNetwork.Request(second,menu.containerId,0,"",false));escrowInvariant(player,before,menu);
  BurnServer.request(player,new BurnNetwork.Request(first,menu.containerId,2,""));invariant(BurnServer.locked(menu));escrowInvariant(player,before,menu);
  player.closeContainer();invariant(!BurnServer.locked(menu)&&!Boolean.TRUE.equals(Entry042DiscAudit.task(player).get("menuAttached"))&&discs(player)==baseline-1);escrowInvariant(player,before,menu);
  menu.removed(player);escrowInvariant(player,before,menu);invariant(discs(player)==baseline-1);
  BurnServer.request(player,new BurnNetwork.Request(second,menu.containerId,1,"late-fixture-refused"));
  BurnServer.request(player,new BurnNetwork.Request(second,menu.containerId,2,""));invariant(Entry042DiscAudit.escrow(player).isEmpty()&&discs(player)==baseline&&Entry042DiscAudit.inventoryCount(player,marker)==1);
  menu.removed(player);invariant(discs(player)==baseline&&Entry042DiscAudit.inventoryCount(player,marker)==1);Entry042DiscAudit.audit(player,"native-lock-cancelled-after-close",true);
  var row=new LinkedHashMap<String,Object>();row.put("event","native-lock-matrix-passed");row.put("menu",menu.getClass().getSimpleName());row.put("blockedClickAttempts",blocked);row.put("discMarker",marker);row.put("discCountBefore",baseline);row.put("discCountAfter",discs(player));row.put("duplicateRemovedNoCopy",true);row.put("closeRetainedEscrow",true);row.put("returnViaInventory",true);row.put("lateReceiptCallbackScheduled",true);row.put("actualCloudCalls",0);events.add(row);
  Entry042Scope.write("entry042-lock-matrix.json",Map.of("events",events,"fixtureOnly",true,"realOssCalls",0,"actualMcMenuAndHandlers",true,"timeoutDisconnectStoppingNotCovered",true));return 1;
 }
 public static void tick(ServerTickEvent.Post event){if(!System.getProperty("qa.local.role","").equals("server")||++ticks%20!=0)return;try{Entry042Scope.require();var players=event.getServer().getPlayerList().getPlayers();var rows=new ArrayList<Map<String,Object>>();for(var player:players){if(!player.getGameProfile().getName().equals("10000"))continue;Entry042DiscAudit.audit(player,"periodic",false);var menu=player.containerMenu;rememberMenu(player,menu);var task=Entry042DiscAudit.task(player);if(Boolean.TRUE.equals(task.get("active")))rows.add(Map.of("backgroundTask",task));if(menu instanceof CDBurnerMenu||menu instanceof ComputerMenu){var output=((BurnMenuAccess)menu).muxiBurn$output().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(output);rows.add(Map.of("menu",menu.getClass().getSimpleName(),"inputEmpty",input(menu).getStackInSlot(0).isEmpty(),"serverLocked",BurnServer.locked(menu),"outputCd",!output.isEmpty(),"fixtureReferenceShape",song!=null&&net.muxigame.musiccompat.burn.BurnProtocol.media(song.songUrl),"duration",song==null?0:song.songTime));}}
  Entry042Scope.write("entry042-server-status.json",Map.of("events",events,"menus",rows,"fixtureReceiptVerification",true,"actualCryptographicReceiptVerified",false,"ossRequests",0));}catch(Entry042Scope.ReceiptWriteFailure temporaryIo){System.out.println("ENTRY042_OBSERVER_IO_RETRY "+temporaryIo.getCause().getClass().getSimpleName());}}
 private Entry042Server(){}
}
