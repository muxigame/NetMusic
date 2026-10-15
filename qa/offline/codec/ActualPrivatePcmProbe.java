import com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager;
import com.google.gson.Gson;
import net.muxigame.musiccompat.burn.PrivatePcmStream;
import net.muxigame.musiccompat.burn.PrivateSoundState;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.sound.sampled.*;
/** Uses the owner's actual compiled product class, never a replacement/stub. */
public final class ActualPrivatePcmProbe {
    public static void main(String[] args)throws Exception{
        AudioStreamHandlerManager.init();List<Map<String,Object>> cases=new ArrayList<>();boolean success=true;
        for(String name:args)for(int channels:new int[]{1,2}){
            var row=new LinkedHashMap<String,Object>();row.put("file",Path.of(name).getFileName().toString());row.put("channels",channels);AudioInputStream input=null;
            try{
                input=AudioStreamHandlerManager.handle(Path.of(name).toUri().toURL());var f=input.getFormat();row.put("inputFormat",f.toString());
                if(!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())){var first=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits(),f.getChannels(),f.getChannels()*(f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits())/8,f.getSampleRate(),false);input=AudioSystem.getAudioInputStream(first,input);f=input.getFormat();}
                var pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),16,channels,channels*2,f.getSampleRate(),false);input=AudioSystem.getAudioInputStream(pcm,input);
                var state=new PrivateSoundState(0,false,1);long bytes=0;int reads=0;MessageDigest hash=MessageDigest.getInstance("SHA-256");
                try(var product=new PrivatePcmStream(input,state)){
                    ByteBuffer buffer;while((buffer=product.read(8192))!=null){reads++;int count=buffer.remaining();if(count<1||count%pcm.getFrameSize()!=0)throw new IOException("partial_product_frame");byte[] data=new byte[count];buffer.get(data);hash.update(data);bytes+=count;if(bytes>44100L*4)throw new IOException("probe_over_limit");}
                }
                if(bytes<1)throw new IOException("product_no_pcm");row.put("readToEof",true);row.put("pcmFormat",pcm.toString());row.put("frames",bytes/pcm.getFrameSize());row.put("pcmBytes",bytes);row.put("readCallsWithData",reads);row.put("pcmSha256",HexFormat.of().formatHex(hash.digest()));row.put("success",true);
            }catch(Throwable error){success=false;row.put("success",false);row.put("errorType",error.getClass().getName());row.put("errorMessage",error.getMessage()==null?"":error.getMessage());error.printStackTrace(System.err);}
            finally{if(input!=null)try{input.close();}catch(IOException ignored){}}
            cases.add(row);
        }
        byte[] actual;try(InputStream in=PrivatePcmStream.class.getResourceAsStream("/net/muxigame/musiccompat/burn/PrivatePcmStream.class")){actual=in.readAllBytes();}
        var result=new LinkedHashMap<String,Object>();result.put("success",success);result.put("cases",cases);result.put("actualPrivatePcmClassSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)));result.put("actualPrivatePcmCodeSource",PrivatePcmStream.class.getProtectionDomain().getCodeSource().getLocation().toString());result.put("mcStarted",false);result.put("openAlAccepted",false);result.put("networkCalls",0);result.put("stubProductClass",false);
        System.out.println(new Gson().toJson(result));if(!success)System.exit(2);
    }
}
