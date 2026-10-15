package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import com.github.img.netmusicbetterlogin.config.GeneralConfig;import com.gly091020.netMusicListNeoforge.NetMusicList;import com.gly091020.netMusicListNeoforge.api.musicSource.IExtraMusicSource;
import com.github.tartaricacid.netmusic.client.gui.*;import com.github.tartaricacid.netmusic.item.ItemMusicCD;import net.muxigame.musiccompat.burn.BurnMenuAccess;import net.muxigame.musiccompat.burn.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;import net.neoforged.neoforge.common.NeoForge;import net.neoforged.neoforge.client.event.ClientTickEvent;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
public final class Entry042Client {
 private Object passportConnection;private int last=-1,ticks;private String lastCommand="",lastClosedMenu="";private final List<Map<String,Object>> closeActions=new ArrayList<>();private boolean cookieInstalled,resultsDirty;private final List<Map<String,Object>> results=new ArrayList<>();
 public Entry042Client(){Entry042Scope.require();NeoForge.EVENT_BUS.addListener(this::tick);}
 static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static AbstractContainerScreen<?> screen(){var screen=Minecraft.getInstance().screen;if(!(screen instanceof CDBurnerMenuScreen)&&!(screen instanceof ComputerMenuScreen))throw new IllegalStateException("normal_music_page_required");return (AbstractContainerScreen<?>)screen;}
 static Button button(String label){return Minecraft.getInstance().screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();}
 static boolean jobStarted(AbstractContainerScreen<?> page)throws Exception{for(Field f:page.getClass().getDeclaredFields())if(f.getName().contains("muxiBurn$job")){f.setAccessible(true);Object job=f.get(page);if(job==null)return false;return ((BurnCoordinator)job).running();}return false;}
 static Map<String,Object> background()throws Exception{
  var row=new LinkedHashMap<String,Object>();Object session=BurnScreenRuntime.session();
  if(!(session instanceof BurnClientSession)){row.put("active",false);return row;}
  Object task=field(session,"active");if(task==null){row.put("active",false);return row;}
  var mc=Minecraft.getInstance();var job=(BurnCoordinator)field(task,"job");String request=(String)field(task,"request");
  boolean connection=field(task,"connection")==mc.getConnection()&&mc.getConnection()!=null;
  row.put("active",connection&&job.running());row.put("running",job.running());row.put("connectionCurrent",connection);row.put("requestId",request);row.put("originMenuId",field(task,"menuId"));row.put("noncePresent",BurnProtocol.uuid((String)field(task,"nonce")));row.put("serverTerminal",field(task,"serverTerminal"));
  Object current=field(job,"current");if(current!=null)row.put("generation",field(current,"id").toString());
  Object latest=field(task,"latest");if(latest instanceof BurnCoordinator.Progress progress){
   boolean same=progress.generation().toString().equals(request);row.put("progressMatchesRequest",same);
   if(same){row.put("stage",progress.stage().name());row.put("code",progress.code());row.put("discReturned",progress.discReturned());}
  }
  return row;
 }
 private void closeUi(String route)throws Exception{
  var mc=Minecraft.getInstance();var page=screen();lastClosedMenu=page instanceof ComputerMenuScreen?"computer":"burner";
  Map<String,Object> before=background();boolean handled;
  if(route.equals("esc"))handled=page.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
  else if(route.equals("onclose")){page.onClose();handled=true;}
  else{
   AbstractWidget close=null;
   for(var child:page.children())if(child instanceof AbstractWidget widget){
    String label=widget.getMessage().getString();var contents=widget.getMessage().getContents();
    boolean named=Set.of("X","x","×","✕","✖","关闭","Close","close","CLOSE").contains(label)||contents instanceof net.minecraft.network.chat.contents.TranslatableContents translation&&(translation.getKey().equals("gui.close")||translation.getKey().endsWith(".close"));
    if(named&&widget.visible){if(close!=null)throw new IllegalStateException("native_close_x_ambiguous");close=widget;}
   }
   if(close==null){Entry042Fixture.event("native-ui-x-control-not-present",Map.of("menu",page.getClass().getSimpleName(),"fakeButtonAdded",false,"osWindowCloseTested",false));throw new IllegalStateException("native_close_x_not_present");}
   if(!close.active){Entry042Fixture.event("native-ui-x-control-disabled",Map.of("menu",page.getClass().getSimpleName(),"x",close.getX(),"y",close.getY(),"width",close.getWidth(),"height",close.getHeight(),"label",close.getMessage().getString(),"active",false));throw new IllegalStateException("native_close_x_disabled");}
   handled=page.mouseClicked(close.getX()+close.getWidth()/2.0,close.getY()+close.getHeight()/2.0,0);
  }
  if(mc.screen==page)throw new IllegalStateException("native_music_close_did_not_close");
  var row=new LinkedHashMap<String,Object>();row.put("runId",System.getProperty("qa.local.runId"));row.put("route",route);row.put("menu",page.getClass().getSimpleName());row.put("atUtcMillis",System.currentTimeMillis());row.put("beforeTask",before);row.put("afterTask",background());row.put("originalScreenInputInvoked",true);row.put("handlerReturned",handled);row.put("screenClosed",true);row.put("osWindowCloseTested",false);row.put("physicalOSInput",false);closeActions.add(row);
  Entry042Scope.write("entry042-ui-close-actions.json",Map.of("runId",System.getProperty("qa.local.runId"),"actions",closeActions));
 }
 private static List<Map<String,Object>> inventoryCds(){
  var result=new ArrayList<Map<String,Object>>();var mc=Minecraft.getInstance();if(mc.player==null)return result;
  for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
   var item=mc.player.getInventory().getItem(i);int marker=item.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST,0);var song=ItemMusicCD.getSongInfo(item);
   if(marker>0&&song!=null&&BurnProtocol.media(song.songUrl)){var row=new LinkedHashMap<String,Object>();row.put("slot",i);row.put("marker",marker);row.put("count",item.getCount());row.put("reference",song.songUrl);row.put("title",song.songName);row.put("artists",song.artists==null?List.of():song.artists);row.put("readOnly",song.readOnly);result.add(row);}
  }
  return result;
 }
 static void source(String wanted){var page=screen();for(var widget:page.children())if(widget instanceof CycleButton<?> cycle){Object value=cycle.getValue();if(value instanceof String||value instanceof IExtraMusicSource){for(int count=0;count<4;count++){value=cycle.getValue();String type=value instanceof String s?s:((IExtraMusicSource)value).getType();if(type.equals(wanted))return;cycle.onPress();}}}throw new IllegalStateException("platform_control_not_found");}
 static void confirmation(boolean wanted){var page=screen();for(var child:page.children())if(child instanceof Checkbox box&&box.getMessage().getString().equals("确认")){if(box.selected()!=wanted)box.onPress();if(BurnScreenRuntime.session().permissionConfirmed(page)!=wanted)throw new IllegalStateException("confirmation_value_mismatch");return;}throw new IllegalStateException("confirmation_widget_not_found");}
 static void permission(){confirmation(true);}
 static void craft(){var page=screen();for(var widget:page.children())if(widget instanceof Button b&&b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().contains("craft")){b.onPress();return;}throw new IllegalStateException("original_craft_button_not_found");}
 void tick(ClientTickEvent.Post event){try{
  Entry042Scope.require();if(resultsDirty){Entry042Scope.write(Entry042Scope.clientLeaf("results"),results);resultsDirty=false;}var mc=Minecraft.getInstance();ticks++;
  if(!cookieInstalled&&mc.player!=null){if(!mc.player.getGameProfile().getName().equals(Long.toString(Entry042Scope.uid())))throw new IllegalStateException();GeneralConfig.COOKIE.set(Entry042Scope.COOKIE);NetMusicList.CONFIG.qqCookie=Entry042Scope.COOKIE;NetMusicList.CONFIG.bilibiliCookie=Entry042Scope.COOKIE;cookieInstalled=true;}
  if(mc.player!=null&&Integer.getInteger("muxi.music.entry042.backendPort",0)>0&&(passportConnection!=mc.getConnection()||ticks%200==0)){PrivatePlayback.remember(Entry042Fixture.account(),mc.getConnection());passportConnection=mc.getConnection();}
  if(ticks%10==0){Entry042PlaybackClient.observe();var row=new LinkedHashMap<String,Object>();row.put("runId",System.getProperty("qa.local.runId"));row.put("pid",ProcessHandle.current().pid());row.put("screenClass",mc.screen==null?"NONE":mc.screen.getClass().getSimpleName());row.put("backgroundTask",background());row.put("inventoryFixtureCds",inventoryCds());row.put("chooserOpen",LocalAudioChooser.active());row.put("overlayClass",mc.getOverlay()==null?"NONE":mc.getOverlay().getClass().getSimpleName());row.put("guiScaledWidth",mc.getWindow().getGuiScaledWidth());row.put("guiScaledHeight",mc.getWindow().getGuiScaledHeight());var widgetFacts=new ArrayList<Map<String,Object>>();if(mc.screen!=null)for(var widget:mc.screen.children())if(widget instanceof AbstractWidget w)widgetFacts.add(Map.of("type",w.getClass().getSimpleName(),"label",w.getMessage().getString(),"x",w.getX(),"y",w.getY(),"width",w.getWidth(),"height",w.getHeight()));row.put("actualWidgetGeometry",widgetFacts);row.put("nativeLoginScreen",mc.screen instanceof com.github.img.netmusicbetterlogin.client.gui.LoginScreen);row.put("nativeLoginSuccessScreen",mc.screen instanceof com.github.img.netmusicbetterlogin.client.gui.LoginSuccessScreen);row.put("fixtureCookiesInstalled",cookieInstalled);row.put("localLoginPresent",BetterLoginClientSource.hasLocalLogin());row.put("realAccountVerified",false);row.put("ossRequests",0);
   if(mc.screen instanceof CDBurnerMenuScreen||mc.screen instanceof ComputerMenuScreen){var page=screen();row.put("audioJobStarted",jobStarted(page));row.put("actualProgressText",((net.minecraft.network.chat.Component)field(page,"tips")).getString());row.put("permissionConfirmed",BurnScreenRuntime.session().permissionConfirmed(page));row.put("readOnlySelected",((Checkbox)field(page,"readOnlyButton")).selected());if(page instanceof ComputerMenuScreen){row.put("sourceType",BurnScreenRuntime.session().sourceType(page));row.put("urlCharacters",((EditBox)field(page,"urlTextField")).getValue().length());row.put("originalTitleConnected",((EditBox)field(page,"nameTextField")).getValue().equals(BurnScreenRuntime.session().title(page)));row.put("durationAutomatic",((EditBox)field(page,"timeTextField")).getValue().equals("自动"));}var song=ItemMusicCD.getSongInfo(((BurnMenuAccess)page.getMenu()).muxiBurn$output().getStackInSlot(0));row.put("outputFixtureCd",song!=null&&BurnProtocol.media(song.songUrl));if(song!=null){row.put("outputTitle",song.songName);row.put("outputArtists",song.artists==null?List.of():song.artists);}row.put("guiWidth",page.width);row.put("guiHeight",page.height);}
   Entry042Scope.write(Entry042Scope.clientLeaf("status"),row);}
  Path commandFile=Entry042Scope.command();if(!Files.exists(commandFile))return;var command=JsonParser.parseString(Files.readString(commandFile)).getAsJsonObject();int id=command.get("id").getAsInt();if(id<=last||!System.getProperty("qa.local.runId").equals(command.get("runId").getAsString()))return;last=id;String cmd=command.get("cmd").getAsString();lastCommand=cmd;
  switch(cmd){
   case "open-burner","open-computer","unverified-write","lock-matrix","slot-attacks","audit-discs","close-menu","stop-server-owned"->{if(mc.getConnection()==null)throw new IllegalStateException();mc.getConnection().sendCommand("entry042 "+cmd);}
   case "close-ui-esc"->closeUi("esc");
   case "close-ui-x"->closeUi("x");
   case "close-ui-onclose"->closeUi("onclose");
   case "reopen-existing-active"->{
    if(mc.getConnection()==null||!Boolean.TRUE.equals(background().get("active")))throw new IllegalStateException("actual_background_task_required");
    String kind=command.has("menu")?command.get("menu").getAsString():lastClosedMenu;
    if(!Set.of("computer","burner").contains(kind))throw new IllegalStateException("owned_original_menu_type_unknown");
    mc.getConnection().sendCommand("entry042 reopen-existing-active "+kind);
   }
   case "query-active"->{
    Object session=BurnScreenRuntime.session();Object task=field(session,"active");
    if(task==null||field(task,"connection")!=mc.getConnection()||mc.getConnection()==null)throw new IllegalStateException("actual_background_task_required");
    String request=(String)field(task,"request"),nonce=(String)field(task,"nonce");
    if(!BurnProtocol.uuid(request)||!BurnProtocol.uuid(nonce))throw new IllegalStateException("actual_background_nonce_required");
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BurnNetwork.Request(request,(int)field(task,"menuId"),3,nonce));
   }
   case "configure"->{var page=screen();source(command.get("platform").getAsString());((EditBox)field(page,page instanceof ComputerMenuScreen?"urlTextField":"textField")).setValue(command.get("value").getAsString());}
   case "readonly"->{var box=(Checkbox)field(screen(),"readOnlyButton");if(!box.selected())box.onPress();}
   case "readonly-false"->{var box=(Checkbox)field(screen(),"readOnlyButton");if(box.selected())box.onPress();}
   case "open-existing"->mc.getConnection().sendCommand("entry042 open-existing "+command.get("marker").getAsInt());
   case "permission"->permission();
   case "unconfirm"->confirmation(false);
   case "clear-local-login"->{GeneralConfig.COOKIE.set("");Entry042Fixture.event("owned-login-cookie-cleared",Map.of("realAccountRead",false));}
   case "login"->button("登录").onPress();
   case "login-return"->{if(!(mc.screen instanceof com.github.img.netmusicbetterlogin.client.gui.LoginScreen))throw new IllegalStateException("actual_login_screen_required");mc.screen.onClose();}
   case "login-success-fixture"->{if(!(mc.screen instanceof com.github.img.netmusicbetterlogin.client.gui.LoginScreen page))throw new IllegalStateException("actual_login_screen_required");GeneralConfig.COOKIE.set(Entry042Scope.COOKIE);page.success();Entry042Fixture.event("native-login-success-page-fixture",Map.of("actualPlatformLoginVerified",false));}
   case "login-confirm"->{if(!(mc.screen instanceof com.github.img.netmusicbetterlogin.client.gui.LoginSuccessScreen page))throw new IllegalStateException("actual_success_screen_required");var first=page.children().stream().filter(w->w instanceof Button).map(w->(Button)w).findFirst().orElseThrow();first.onPress();}
   case "network-click"->{var page=screen();int slot=command.get("slot").getAsInt(),button=command.get("button").getAsInt();if(slot!=0&&slot!=1)throw new IllegalStateException();var type=net.minecraft.world.inventory.ClickType.valueOf(command.get("click").getAsString());mc.gameMode.handleInventoryMouseClick(page.getMenu().containerId,slot,button,type,mc.player);}
   case "gate"->{String phase=command.get("phase").getAsString(),mode=command.get("mode").getAsString();if(!Set.of("acquiring","uploading","verifying","committing").contains(phase)||!Set.of("off","hold","fail").contains(mode))throw new IllegalStateException("gate_command_refused");Entry042Scope.write("entry042-gate.json",Map.of("runId",System.getProperty("qa.local.runId"),"phase",phase,"mode",mode));}
   case "disconnect"->{if(mc.player==null)throw new IllegalStateException();mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen());}
   case "reconnect"->{if(mc.player!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen||mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen))throw new IllegalStateException();mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:"+Integer.getInteger("qa.local.port")),new net.minecraft.client.multiplayer.ServerData("Owned music QA","127.0.0.1",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);}
   case "craft"->craft();
   case "pick"->button("本地刻录").onPress();
   case "cancel"->button("取消刻录").onPress();
   case "pause-focus-true"->mc.options.pauseOnLostFocus=true;
   case "pause-focus-false"->mc.options.pauseOnLostFocus=false;
   case "observe"->{}
   case "playback-place"->{if(mc.getConnection()==null)throw new IllegalStateException();mc.getConnection().sendCommand("entry042 playback-place "+command.get("marker").getAsInt()+" "+command.get("site").getAsInt());}
   case "playback-start","playback-start-legacy","playback-return","playback-stop","join-listener"->{if(mc.getConnection()==null)throw new IllegalStateException();mc.getConnection().sendCommand("entry042 "+cmd);}
   case "audio-enable-owned"->{mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.RECORDS).set(1.0);mc.getSoundManager().updateSourceVolume(net.minecraft.sounds.SoundSource.MASTER,1f);mc.getSoundManager().updateSourceVolume(net.minecraft.sounds.SoundSource.RECORDS,1f);Entry042Fixture.event("owned-music-audio-enabled",Map.of("ownCloneOnly",true,"systemVolumeChanged",false));}
   case "audio-mute-owned"->{mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.getSoundManager().updateSourceVolume(net.minecraft.sounds.SoundSource.MASTER,0f);}
   case "audio-pause"->mc.getSoundManager().pause();
   case "audio-resume"->mc.getSoundManager().resume();
   case "lyrics-on"->com.github.tartaricacid.netmusic.config.GeneralConfig.ENABLE_PLAYER_LYRICS.set(true);
   case "lyrics-off"->com.github.tartaricacid.netmusic.config.GeneralConfig.ENABLE_PLAYER_LYRICS.set(false);
   case "finish"->{mc.options.pauseOnLostFocus=false;if(mc.player!=null)mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen());mc.stop();}
   default->throw new IllegalStateException("owned_command_refused");
  }
  results.add(Map.of("id",id,"cmd",cmd,"ok",true,"actualWidgetsInvoked",Set.of("configure","permission","craft","pick","cancel","close-ui-esc","close-ui-x","close-ui-onclose").contains(cmd),"physicalOSInput",false));resultsDirty=true;Entry042Scope.write(Entry042Scope.clientLeaf("results"),results);resultsDirty=false;
 }catch(Entry042Scope.ReceiptWriteFailure temporaryIo){System.out.println("ENTRY042_CLIENT_RECEIPT_IO_RETRY "+temporaryIo.getCause().getClass().getSimpleName());}catch(java.io.IOException|JsonParseException transientRead){}catch(Throwable error){String code=error.getMessage();if(code==null||!code.matches("[a-z0-9_]{1,120}"))code="qa_action_failed";results.add(Map.of("id",last,"cmd",lastCommand,"ok",false,"errorType",error.getClass().getSimpleName(),"errorCode",code));try{resultsDirty=true;Entry042Scope.write(Entry042Scope.clientLeaf("results"),results);resultsDirty=false;}catch(Exception ignored){}}}
}
