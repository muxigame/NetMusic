package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;import java.nio.file.*;import java.util.*;import net.muxigame.musiccompat.burn.*;
/** QA response gates keep REAL product workers/menus active; never alter product deadlines. */
public final class Entry042Gate {
 public static void pass(String phase,BurnCoordinator.Cancellation cancel)throws Exception{
  Entry042Scope.require();long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(660);boolean recorded=false;
  while(true){
   cancel.check();Path path=Entry042Scope.root().resolve("coordinator/entry042-gate.json");if(!Files.exists(path))return;
   JsonObject value=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   if(!System.getProperty("qa.local.runId").equals(value.get("runId").getAsString()))throw new IllegalStateException("owned_gate_required");
   if(!phase.equals(value.get("phase").getAsString()))return;String mode=value.get("mode").getAsString();
   if(mode.equals("off"))return;
   if(!recorded){Entry042Fixture.event("response-gate-entered",Map.of("phase",phase,"mode",mode,"productionDeadlineChanged",false));recorded=true;}
   if(mode.equals("fail"))throw new java.io.IOException("music_service_unavailable");
   if(!mode.equals("hold"))throw new IllegalStateException("fixture_gate_mode_refused");
   if(System.nanoTime()>limit)throw new java.io.IOException("fixture_gate_timeout");Thread.sleep(50);
  }
 }
 private Entry042Gate(){}
}
