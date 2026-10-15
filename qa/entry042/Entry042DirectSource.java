package net.muxigame.musiccompat.qa.entry042;
import net.muxigame.musiccompat.burn.*;import java.nio.file.Path;import java.net.*;import java.lang.reflect.*;
/** QA-only transport seam; keep the product's package out of this separate module. */
public final class Entry042DirectSource {
 public static final String URL="https://entry042.qqmusic.qq.com/owned.wav";
 public static BurnCoordinator.Source create(){
  Entry042Scope.require();try{
   Class<?> addresses=Class.forName("net.muxigame.musiccompat.burn.BurnSources$AddressResolver"),fetcher=Class.forName("net.muxigame.musiccompat.burn.BurnSources$Fetcher");
   Object resolver=java.lang.reflect.Proxy.newProxyInstance(addresses.getClassLoader(),new Class<?>[]{addresses},(proxy,method,args)->{if(!method.getName().equals("resolve")||!args[0].equals("entry042.qqmusic.qq.com"))throw new IllegalStateException("owned_direct_host_required");return new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})};});
   Object transport=java.lang.reflect.Proxy.newProxyInstance(fetcher.getClassLoader(),new Class<?>[]{fetcher},(proxy,method,args)->{if(!method.getName().equals("fetch"))throw new IllegalStateException();return Entry042Fixture.send((java.net.http.HttpRequest)args[0],(int)BurnCoordinator.MAX_BYTES,(BurnCoordinator.Cancellation)args[1],(java.util.function.LongConsumer)args[2]);});
   var link=BurnSources.class.getDeclaredMethod("link",URI.class,Path.class,String.class,String.class,String.class,addresses,fetcher);link.setAccessible(true);
   return (BurnCoordinator.Source)link.invoke(null,URI.create(URL),Entry042Scope.root().resolve(Entry042Scope.role()+"/entry042-direct-temp"),"Owned direct fixture","CC0-1.0","Self-generated owned CC0",resolver,transport);
  }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("owned_source_seam_unavailable",unavailable);}
 }
 private Entry042DirectSource(){}
}
