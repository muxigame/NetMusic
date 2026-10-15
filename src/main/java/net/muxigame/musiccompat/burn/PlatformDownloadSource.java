package net.muxigame.musiccompat.burn;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Pinned Better Login exposes play-info only, with no documented download
 * entitlement contract. Fail closed for platform input until a real provider
 * can verify both lawful download and redistribution. Local lawful files and
 * explicitly licensed public direct audio remain available.
 */
public final class PlatformDownloadSource {
    private PlatformDownloadSource(){}
    public static BurnCoordinator.Source createWithClient(String value,Path temp,String title,String license,String credit,BetterLoginClientSource client){
        Long song=songId(value);
        if(song!=null)return client.create(song,temp,title,license,credit);
        return create(value,temp,title,license,credit);
    }
    static Long songId(String value){
        try{
            String input=value.trim();if(input.matches("[1-9][0-9]{0,18}"))return Long.parseLong(input);
            URI uri=URI.create(input);String host=uri.getHost();if(host==null)return null;
            host=host.toLowerCase(Locale.ROOT);while(host.endsWith("."))host=host.substring(0,host.length()-1);
            if(!host.equals("music.163.com")||!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1)return null;
            if(uri.getRawFragment()!=null){
                if(!Set.of("","/").contains(uri.getPath())||uri.getRawQuery()!=null||!uri.getRawFragment().matches("/song\\?id=[1-9][0-9]{0,18}"))return null;
                return Long.parseLong(uri.getRawFragment().substring(9));
            }
            if(!Set.of("/song","/song/media/outer/url").contains(uri.getPath()))return null;
            String query=uri.getRawQuery();if(query==null||!query.matches("id=[1-9][0-9]{0,18}"))return null;
            return Long.parseLong(query.substring(3));
        }catch(RuntimeException invalid){return null;}
    }
    public static BurnCoordinator.Source create(String value,Path temp,String title,String license,String credit){
        return create(value,temp,title,license,credit,BurnSources::link);
    }
    interface LinkFactory {BurnCoordinator.Source create(URI uri,Path temp,String title,String license,String credit);}
    static BurnCoordinator.Source create(String value,Path temp,String title,String license,String credit,LinkFactory links){
        URI uri;
        try{uri=URI.create(value.trim());}catch(RuntimeException invalid){return denied();}
        String host=uri.getHost();
        if(host!=null){host=host.toLowerCase(Locale.ROOT);while(host.endsWith("."))host=host.substring(0,host.length()-1);}
        if(host==null||host.equals("music.163.com")||host.endsWith(".music.163.com")
            ||host.equals("music.126.net")||host.endsWith(".music.126.net"))return denied();
        return links.create(uri,temp,title,license,credit);
    }
    private static BurnCoordinator.Source denied(){
        return (cancel,progress)->{cancel.check();throw new IOException("platform_download_permission_unavailable");};
    }
}
