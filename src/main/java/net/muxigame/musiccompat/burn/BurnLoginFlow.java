package net.muxigame.musiccompat.burn;
import com.github.img.netmusicbetterlogin.client.gui.LoginScreen;import com.gly091020.netMusicListNeoforge.config.ConfigScreenGetter;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;import net.neoforged.neoforge.client.event.ClientTickEvent;
/** The same native LoginScreen that /nmbl login constructs. Keeps the owned menu/connection. */
public final class BurnLoginFlow {
 private static AbstractContainerScreen<?> parent;private static Object connection;
 private BurnLoginFlow(){}
 public static void open(AbstractContainerScreen<?> page,ExistingPlatformClientSource.Platform platform){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=page||mc.player.containerMenu!=page.getMenu())return;
  parent=page;connection=mc.getConnection();
  if(platform==ExistingPlatformClientSource.Platform.NETEASE)mc.setScreen(new LoginScreen(){@Override public void onClose(){finish();}@Override public void success(){var current=Minecraft.getInstance();if(parent==page&&current.screen==this&&current.getConnection()==connection&&current.player!=null&&current.player.containerMenu==page.getMenu())super.success();}});
  else mc.setScreen(ConfigScreenGetter.getConfigScreen(page));
 }
 public static void tick(ClientTickEvent.Post event){
  if(parent==null)return;var mc=Minecraft.getInstance();
  if(mc.player==null||mc.getConnection()!=connection||mc.player.containerMenu!=parent.getMenu()){parent=null;connection=null;return;}
  // Original success buttons close to null; return only for our own outstanding flow.
  if(mc.screen==null)finish();else if(mc.screen==parent){parent=null;connection=null;}
 }
 private static void finish(){var mc=Minecraft.getInstance();var target=parent;parent=null;Object original=connection;connection=null;if(target!=null&&mc.player!=null&&mc.getConnection()==original&&mc.player.containerMenu==target.getMenu())mc.setScreen(target);}
}
