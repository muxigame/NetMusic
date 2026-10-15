package net.muxigame.musiccompat.qa.entry042;
import net.muxigame.musiccompat.burn.*;import net.minecraft.client.Minecraft;import java.util.*;
/** Observes actual nonce-bound replies after the production receiver runs; no state writes. */
public final class Entry042BurnReplies {
 private static final LinkedHashMap<String,Long> counts=new LinkedHashMap<>();
 private static Object field(Object owner,String name)throws ReflectiveOperationException{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 public static synchronized void received(Object session,BurnNetwork.Result result){
  if(result.status()!=4)return;Entry042Scope.require();
  try{var task=field(session,"active");if(task==null)return;var mc=Minecraft.getInstance();
   boolean request=result.requestId().equals(field(task,"request")),nonce=BurnProtocol.uuid(result.nonce())&&result.nonce().equals(field(task,"nonce")),connection=mc.player!=null&&mc.getConnection()==field(task,"connection");
   if(!request||!nonce||!connection)return;long count=counts.getOrDefault(result.requestId(),0L)+1;counts.put(result.requestId(),count);if(counts.size()>64)counts.remove(counts.keySet().iterator().next());
   Entry042Scope.write("entry042-burn-query-"+Entry042Scope.role()+".json",Map.of("runId",System.getProperty("qa.local.runId"),"requestId",result.requestId(),"replyCount",count,"atUtcMillis",System.currentTimeMillis(),"status",4,"sameRequest",request,"sameNonce",nonce,"sameOriginalConnection",connection,"productionReceiverReturned",true));
  }catch(ReflectiveOperationException error){throw new IllegalStateException("owned_burn_query_observer_failed",error);}
 }
 private Entry042BurnReplies(){}
}
