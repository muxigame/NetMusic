package net.muxigame.musiccompat.burn;
import com.github.img.netmusicbetterlogin.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Reads only the physical client's original BetterLogin configuration in memory.
 * Acquires only the normal media returned for the current client account.
 */
public final class BetterLoginClientSource {
    private final Object connection;
    public BetterLoginClientSource(Object connection){this.connection=connection;}
    public static boolean hasLocalLogin(){String cookie=GeneralConfig.COOKIE.get();return cookie!=null&&!cookie.isBlank()&&cookie.length()<=16384&&cookie.indexOf('\r')<0&&cookie.indexOf('\n')<0;}
    public BurnCoordinator.Source createAutomatic(long song,Path temp){return PlatformAccountSource.createAutomatic(song,temp,sessions(),(request,limit,cancel)->BurnHttp.send(request,limit,cancel,n->{}));}
    private PlatformAccountSource.Sessions sessions(){return ()->{
        if(FMLEnvironment.dist!=Dist.CLIENT)throw new IOException("platform_client_session_unavailable");
        var mc=Minecraft.getInstance();if(mc.player==null||connection==null||mc.getConnection()!=connection)throw new IOException("platform_account_changed");
        String cookie=GeneralConfig.COOKIE.get();String level=GeneralConfig.LEVEL.get().toString().toLowerCase(Locale.ROOT);
        return new PlatformAccountSource.Session(cookie,level,()->mc.player!=null&&mc.getConnection()==connection&&cookie.equals(GeneralConfig.COOKIE.get()));
    };}
    public BurnCoordinator.Source create(long song,Path temp,String title,String license,String credit){
        return PlatformAccountSource.create(song,temp,title,license,credit,()->{
            if(FMLEnvironment.dist!=Dist.CLIENT)throw new IOException("platform_client_session_unavailable");
            var mc=Minecraft.getInstance();
            if(mc.player==null||connection==null||mc.getConnection()!=connection)throw new IOException("platform_account_changed");
            String cookie=GeneralConfig.COOKIE.get();
            String level=GeneralConfig.LEVEL.get().toString().toLowerCase(Locale.ROOT);
            return new PlatformAccountSource.Session(cookie,level,()->mc.player!=null&&mc.getConnection()==connection&&cookie.equals(GeneralConfig.COOKIE.get()));
        },(request,limit,cancel)->BurnHttp.send(request,limit,cancel,n->{}));
    }
}
