import com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager;
import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.sound.sampled.*;

/** Offline temporary probe, not a production source or registered test suite. */
public final class FixedMp3SpiProbe {
    static final class TraceInput extends ByteArrayInputStream {
        final List<Map<String,Object>> calls=new ArrayList<>();TraceInput(byte[] bytes){super(bytes);}
        String caller(){return StackWalker.getInstance().walk(s->s.filter(f->!f.getClassName().contains("FixedMp3SpiProbe")&&!f.getClassName().startsWith("java.io.")).findFirst().map(f->f.getClassName()+"."+f.getMethodName()).orElse("other"));}
        public synchronized void mark(int limit){if(calls.size()<100)calls.add(Map.of("op","mark","position",pos,"limit",limit,"caller",caller()));super.mark(limit);}
        public synchronized void reset(){int previous=pos;super.reset();if(calls.size()<100)calls.add(Map.of("op","reset","before",previous,"after",pos,"caller",caller()));}
    }
    static Map<String,Object> decode(Path file,boolean originalManager,int channels)throws Exception{
        AudioInputStream input=null;Map<String,Object> result=new LinkedHashMap<>();
        result.put("file",file.getFileName().toString());result.put("entry",originalManager?"AudioStreamHandlerManager.handle(fileURL)":"AudioSystem.getAudioInputStream(InputStream)");result.put("targetChannels",channels);result.put("stage","initial_audio_input_stream");
        try{
            input=originalManager?AudioStreamHandlerManager.handle(file.toUri().toURL()):AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)));
            AudioFormat f=input.getFormat();result.put("inputEncoding",f.getEncoding().toString());result.put("inputFormat",f.toString());
            result.put("initialReadSucceeded",true);result.put("stage","first_pcm_conversion");
            // Exactly the two AudioSystem conversions used by current private
            // getStream, trying both explicit channel choices without reading
            // the user's FML/configuration singleton.
            if(!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())){
                var decoded=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits(),f.getChannels(),f.getChannels()*(f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits())/8,f.getSampleRate(),false);
                input=AudioSystem.getAudioInputStream(decoded,input);f=input.getFormat();
            }
            result.put("firstPcmFormat",f.toString());result.put("stage","target_pcm_conversion");
            var pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),16,channels,channels*2,f.getSampleRate(),false);
            input=AudioSystem.getAudioInputStream(pcm,input);AudioFormat out=input.getFormat();
            if(!AudioFormat.Encoding.PCM_SIGNED.equals(out.getEncoding())||out.getSampleSizeInBits()!=16||out.isBigEndian()||out.getChannels()!=channels||out.getFrameSize()!=channels*2)throw new IOException("private_pcm_format_mismatch");
            result.put("pcmFormat",out.toString());result.put("stage","read_pcm_to_eof");
            byte[] buffer=new byte[8192/out.getFrameSize()*out.getFrameSize()];long bytes=0;boolean nonzero=false;int count,zeros=0,consecutiveZeros=0;MessageDigest hash=MessageDigest.getInstance("SHA-256");var trace=new ArrayList<Map<String,Object>>();
            while(true){count=input.read(buffer);if(trace.size()<64)trace.add(Map.of("returned",count,"beforeFrames",bytes/out.getFrameSize()));
                if(count==-1){result.put("readToEof",true);break;}
                if(count==0){zeros++;consecutiveZeros++;result.put("zeroReads",zeros);result.put("firstZeroAtFrames",result.getOrDefault("firstZeroAtFrames",bytes/out.getFrameSize()));result.put("readTrace",trace);result.put("pcmBytes",bytes);if(consecutiveZeros>=50)throw new IOException("bounded_zero_read_timeout");Thread.sleep(2);continue;}
                consecutiveZeros=0;if(count<0||count%out.getFrameSize()!=0)throw new IOException("partial_pcm_frame");bytes+=count;if(bytes>44100L*4)throw new IOException("probe_exceeds_one_second");for(int i=0;i<count;i++)nonzero|=buffer[i]!=0;hash.update(buffer,0,count);
            }
            long frames=bytes/out.getFrameSize();if(frames<1||frames>out.getSampleRate()||!nonzero)throw new IOException("no_real_short_pcm");
            result.put("pcmFormat",out.toString());result.put("pcmBytes",bytes);result.put("frames",frames);result.put("decodedSeconds",frames/out.getFrameRate());result.put("decodedSha256",HexFormat.of().formatHex(hash.digest()));result.put("nonzeroPcm",nonzero);result.put("zeroReads",zeros);result.put("readTrace",trace);result.put("currentPrivateStrictZeroReadWouldReject",zeros>0);result.put("success",true);return result;
        }catch(Throwable failed){failed.printStackTrace(System.err);result.put("success",false);result.put("errorType",failed.getClass().getName());result.put("errorMessage",failed.getMessage()==null?"":failed.getMessage());return result;
        }finally{if(input!=null)input.close();}
    }
    public static void main(String[] args)throws Exception{
        var readers=new ArrayList<Map<String,Object>>();
        for(var reader:ServiceLoader.load(javax.sound.sampled.spi.AudioFileReader.class)){
            var r=new LinkedHashMap<String,Object>();r.put("class",reader.getClass().getName());
            try{var stream=reader.getAudioInputStream(new BufferedInputStream(Files.newInputStream(Path.of(args[1]))));r.put("mp3Read",true);r.put("format",stream.getFormat().toString());stream.close();}
            catch(Throwable error){r.put("mp3Read",false);r.put("errorType",error.getClass().getName());r.put("errorMessage",error.getMessage());}
            try{var stream=reader.getAudioInputStream(new BufferedInputStream(Files.newInputStream(Path.of(args[0]))));r.put("wavRead",true);r.put("wavFormat",stream.getFormat().toString());stream.close();}
            catch(Throwable error){r.put("wavRead",false);r.put("wavErrorType",error.getClass().getName());}
            readers.add(r);
        }
        List<Map<String,Object>> results=new ArrayList<>();for(String name:args)for(int channels:new int[]{1,2})try{results.add(decode(Path.of(name),false,channels));}catch(Throwable failed){failed.printStackTrace(System.err);results.add(Map.of("file",Path.of(name).getFileName().toString(),"entry","AudioSystem.getAudioInputStream(InputStream)","targetChannels",channels,"success",false,"errorType",failed.getClass().getName()));}
        boolean managerInit=false;String managerError="";
        try{AudioStreamHandlerManager.init();managerInit=true;}catch(Throwable blocked){managerError=blocked.getClass().getName();}
        if(managerInit)for(String name:args)for(int channels:new int[]{1,2}){
            try{results.add(decode(Path.of(name),true,channels));}
            catch(Throwable failed){failed.printStackTrace(System.err);results.add(Map.of("file",Path.of(name).getFileName().toString(),"entry","AudioStreamHandlerManager.handle(fileURL)","targetChannels",channels,"success",false,"errorType",failed.getClass().getName()));}
        }
        TraceInput trace=new TraceInput(Files.readAllBytes(Path.of(args[0])));var traceResult=new LinkedHashMap<String,Object>();
        try(var stream=AudioSystem.getAudioInputStream(trace)){traceResult.put("success",true);traceResult.put("format",stream.getFormat().toString());}catch(Throwable blocked){traceResult.put("success",false);traceResult.put("errorType",blocked.getClass().getName());}traceResult.put("providerMarkReset",trace.calls);
        var summary=new LinkedHashMap<String,Object>();summary.put("spiDecodedToActualPcm",results.stream().filter(row->"AudioSystem.getAudioInputStream(InputStream)".equals(row.get("entry"))).allMatch(row->Boolean.TRUE.equals(row.get("success"))));summary.put("mp3SpiDecodedToActualPcm",results.stream().filter(row->row.get("file").toString().endsWith(".mp3")&&"AudioSystem.getAudioInputStream(InputStream)".equals(row.get("entry"))).allMatch(row->Boolean.TRUE.equals(row.get("success"))));summary.put("wavByteArrayProbe",traceResult);summary.put("readers",readers);summary.put("managerInitialized",managerInit);summary.put("managerErrorType",managerError);summary.put("results",results);summary.put("mcStarted",false);summary.put("openAlAccepted",false);summary.put("networkCalls",0);summary.put("realAccountRead",false);
        System.out.println(new Gson().toJson(summary));
        if(!managerInit||results.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("success"))))System.exit(2);
    }
}
