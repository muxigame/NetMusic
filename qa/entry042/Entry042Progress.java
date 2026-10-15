package net.muxigame.musiccompat.qa.entry042;
import net.muxigame.musiccompat.burn.BurnCoordinator;import java.util.*;
/** Records progress emitted by the actual product listener, not inferred from fixture gates. */
public final class Entry042Progress {
 private static BurnCoordinator.Progress last;private static final List<Map<String,Object>> rows=new ArrayList<>();
 public static void record(BurnCoordinator.Progress value){Entry042Scope.require();if(value.equals(last))return;last=value;var row=new LinkedHashMap<String,Object>();row.put("generation",value.generation().toString());row.put("stage",value.stage().name());row.put("code",value.code());row.put("bytes",value.bytes());row.put("total",value.total());row.put("atUtcMillis",System.currentTimeMillis());rows.add(row);Entry042Scope.write("entry042-progress.json",Map.of("runId",System.getProperty("qa.local.runId"),"actualProductProgress",true,"rows",rows));}
 private Entry042Progress(){}
}
