package net.muxigame.musiccompat.burn;
import com.google.gson.*;import java.nio.file.*;import java.io.IOException;
/** One local user choice. No account fields, tokens or cloud settings. */
public final class BurnConfirmation {
 private final Path file;private boolean confirmed;
 public BurnConfirmation(Path file){this.file=file;try{if(Files.isRegularFile(file)&&Files.size(file)<=128){var value=JsonParser.parseString(Files.readString(file)).getAsJsonObject();confirmed=value.has("confirmed")&&value.get("confirmed").isJsonPrimitive()&&value.get("confirmed").getAsJsonPrimitive().isBoolean()&&value.get("confirmed").getAsBoolean();}}catch(Exception invalid){confirmed=false;}}
 public boolean get(){return confirmed;}
 public boolean set(boolean value){confirmed=value;try{Files.createDirectories(file.getParent());Path pending=file.resolveSibling(file.getFileName()+".pending");Files.writeString(pending,"{\"confirmed\":"+value+"}");Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return true;}catch(IOException unavailable){return false;}}
}
