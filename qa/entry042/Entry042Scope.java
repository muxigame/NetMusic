package net.muxigame.musiccompat.qa.entry042;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class Entry042Scope {
 private static final String HISTORICAL_PRODUCT="9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216";
 private static final Properties BUILD=build();
 public static final String PRODUCT=BUILD.getProperty("productSha256",HISTORICAL_PRODUCT);
 private static Properties build(){try{
  var values=new Properties();try(var input=Entry042Scope.class.getResourceAsStream("/entry042-current.properties")){if(input!=null)values.load(input);}
  if(!values.isEmpty()&&(!"unified-current-v1".equals(values.getProperty("scope"))||!values.getProperty("productSha256","").matches("[0-9a-f]{64}")))throw new IllegalStateException();return values;
 }catch(Exception invalid){throw new IllegalStateException("entry042_build_scope_invalid",invalid);}}
 private static final String SELF_SHA=selfSha();
 private static String selfSha(){if(BUILD.isEmpty())return "";try{
  // NeoForge uses union: code-source URLs, which are not ordinary jar paths.
  // Bind the loaded class and build resource to exactly one physical role jar.
  byte[] loaded;try(var input=Entry042Scope.class.getResourceAsStream("Entry042Scope.class")){loaded=input.readAllBytes();}
  String found=null;try(var paths=Files.list(Path.of(System.getProperty("user.dir")).resolve("mods"))){
   for(var path:paths.filter(p->p.getFileName().toString().endsWith(".jar")).toList())try(var jar=new java.util.zip.ZipFile(path.toFile())){
    var entry=jar.getEntry("net/muxigame/musiccompat/qa/entry042/Entry042Scope.class");var resource=jar.getEntry("entry042-current.properties");if(entry==null||resource==null)continue;
    byte[] bytes;try(var input=jar.getInputStream(entry)){bytes=input.readAllBytes();}if(!Arrays.equals(loaded,bytes))continue;
    var values=new Properties();try(var input=jar.getInputStream(resource)){values.load(input);}if(!values.equals(BUILD))continue;
    if(found!=null)throw new IllegalStateException("duplicate_current_qa_jar");found=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
   }
  }if(found==null)throw new IllegalStateException("current_qa_jar_missing");return found;
 }catch(Exception invalid){throw new IllegalStateException("entry042_current_jar_required",invalid);}}
 public static final String COOKIE="entry042_owned_fixture=1";
 public static Path root(){return Path.of(System.getProperty("qa.local.root","missing")).toAbsolutePath().normalize();}
 public static void require(){try{
  if(!Boolean.getBoolean("muxi.music.entry042.qa"))throw new IllegalStateException();
  var marker=JsonParser.parseString(Files.readString(root().resolve("local-mc-owner.json"))).getAsJsonObject();
  String run=System.getProperty("qa.local.runId","");
  if(!run.matches("[0-9a-f]{32}")||!run.equals(marker.get("runId").getAsString())||!root().toString().equals(marker.get("instanceRoot").getAsString()))throw new IllegalStateException();
  boolean pinned=false;for(var item:marker.getAsJsonArray("inputArtifacts"))if(PRODUCT.equals(item.getAsJsonObject().get("sha256").getAsString()))pinned=true;
  if(!BUILD.isEmpty()){
   var scope=JsonParser.parseString(Files.readString(root().resolve("entry042-current-scope.json"))).getAsJsonObject();
   if(!marker.get("hidden").getAsBoolean())throw new IllegalStateException();
   boolean qaPinned=false;for(var item:marker.getAsJsonArray("inputArtifacts"))if(SELF_SHA.equals(item.getAsJsonObject().get("sha256").getAsString()))qaPinned=true;
   if(!marker.get("offlineLoopback").getAsBoolean()||marker.get("productionMutation").getAsBoolean()||!qaPinned||!run.equals(scope.get("runId").getAsString())||!PRODUCT.equals(scope.get("productSha256").getAsString())||!SELF_SHA.equals(scope.get("qaSha256").getAsString())||!root().toString().equals(scope.get("instanceRoot").getAsString()))throw new IllegalStateException();
   var allowed=Path.of(scope.get("runsRoot").getAsString()).toAbsolutePath().normalize();
   if(!allowed.equals(allowed.toRealPath())||!root().equals(root().toRealPath())||!root().getParent().equals(allowed)
       ||!root().getFileName().toString().matches("[a-z0-9]+(?:-[a-z0-9]+)*")||!allowed.getFileName().toString().equals("runs"))throw new IllegalStateException();
   var environment=allowed.getParent();
   if(environment.getFileName().toString().matches("client-[1-9][0-9]*"))environment=environment.getParent();
   if(!environment.getFileName().toString().equals("test-environment")
       ||!Path.of(marker.get("projectRoot").getAsString()).toRealPath().getParent().resolve("test-environment").equals(environment))throw new IllegalStateException();
  }
  String role=System.getProperty("qa.local.role","");
  if(!pinned||!Set.of("host","guest","server").contains(role)||!Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(root().resolve(role)))throw new IllegalStateException();
 }catch(Exception invalid){throw new IllegalStateException("entry042_owned_scope_required");}}
 public static final class ReceiptWriteFailure extends IllegalStateException {public ReceiptWriteFailure(Exception cause){super("entry042_receipt_write_failed",cause);}}
 public static synchronized void write(String leaf,Object value){
  require();if(!leaf.matches("entry042-[a-z-]+\\.json"))throw new IllegalArgumentException();
  Path target=root().resolve("coordinator").resolve(leaf),pending=target.resolveSibling(leaf+"."+UUID.randomUUID()+".pending");
  try{
   Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(value));
   for(int attempt=0;;attempt++)try{Files.move(pending,target,StandardCopyOption.REPLACE_EXISTING);return;}
   catch(FileSystemException busy){if(attempt>=39)throw busy;try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw busy;}}
  }catch(java.io.IOException failure){throw new ReceiptWriteFailure(failure);}
 }
 public static String role(){return System.getProperty("qa.local.role","");}
 public static long uid(){require();return role().equals("guest")?10001:10000;}
 public static String session(){return role().equals("guest")?"00000000-0000-4000-8000-000000000043":"00000000-0000-4000-8000-000000000042";}
 public static String clientLeaf(String kind){return "entry042-client-"+kind+(role().equals("guest")?"-guest":"")+".json";}
 public static Path command(){return root().resolve("coordinator/entry042-command"+(role().equals("guest")?"-guest":"")+".json");}
 private Entry042Scope(){}
}
