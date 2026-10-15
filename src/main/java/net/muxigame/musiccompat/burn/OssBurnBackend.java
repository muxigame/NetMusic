package net.muxigame.musiccompat.burn;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The account authorization is confined to its exact HTTPS service origin. */
public final class OssBurnBackend implements BurnCoordinator.Backend {
    private final URI api,oss;
    private final Supplier<String> accountAuthorization;
    private final String session;
    private final Supplier<String> burnNonce;
    private record Upload(URI uri,Map<String,String> fields,double expiresAt){}
    public OssBurnBackend(URI api,URI oss,String session,Supplier<String> accountAuthorization){
        this(api,oss,session,accountAuthorization,()->"");
    }
    public OssBurnBackend(URI api,URI oss,String session,Supplier<String> accountAuthorization,Supplier<String> burnNonce){
        if(!safeOrigin(api)||!safeOrigin(oss)||!oss.getHost().endsWith(".aliyuncs.com"))
            throw new IllegalArgumentException("invalid_service_origin");
        UUID.fromString(session);this.api=api;this.oss=oss;this.session=session;this.accountAuthorization=accountAuthorization;this.burnNonce=burnNonce;
    }
    private static boolean safeOrigin(URI uri){
        return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null
            &&uri.getQuery()==null&&uri.getFragment()==null&&(uri.getPort()==-1||uri.getPort()==443)
            &&(uri.getPath().isEmpty()||uri.getPath().equals("/"));
    }
    private JsonObject session(){JsonObject body=new JsonObject();body.addProperty("gameSession",session);return body;}
    @Override public BurnCoordinator.Ticket authorize(BurnCoordinator.Checked audio,UUID request,
            BurnCoordinator.Cancellation cancel)throws Exception{
        JsonObject body=session();body.addProperty("requestId",request.toString());
        body.addProperty("burnNonce",uuid(burnNonce.get()));
        body.addProperty("bytes",audio.bytes());body.addProperty("sha256",audio.sha256());
        body.addProperty("title",audio.audio().title());body.addProperty("license",audio.audio().license());body.addProperty("credit",audio.audio().credit());
        body.add("lyricOrigin",audio.audio().lyricOrigin().json());
        JsonObject answer=api("/api/launcher/music/uploads",body,cancel);
        String job=uuid(answer.get("jobId").getAsString());JsonObject upload=answer.getAsJsonObject("upload");
        URI uri=URI.create(upload.get("url").getAsString());if(!uri.equals(oss))throw new IOException("unexpected_upload_origin");
        Map<String,String> fields=new LinkedHashMap<>();
        for(var entry:upload.getAsJsonObject("fields").entrySet()){
            if(!entry.getKey().matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("invalid_upload_field");
            String value=entry.getValue().getAsString();
            if(value.length()>16384||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IOException("invalid_upload_field");
            fields.put(entry.getKey(),value);
        }
        if(fields.containsKey("file")||!"private".equals(fields.get("x-oss-object-acl"))
            ||!"true".equals(fields.get("x-oss-forbid-overwrite"))||!"OSS4-HMAC-SHA256".equals(fields.get("x-oss-signature-version")))
            throw new IOException("invalid_upload_constraints");
        return new BurnCoordinator.Ticket(job,new Upload(uri,Map.copyOf(fields),upload.get("expiresAt").getAsDouble()));
    }
    @Override public void upload(BurnCoordinator.Ticket ticket,BurnCoordinator.Checked audio,
            BurnCoordinator.Cancellation cancel,Consumer<BurnCoordinator.Progress> progress)throws Exception{
        Upload upload=(Upload)ticket.transport();
        if(System.currentTimeMillis()/1000.0>=upload.expiresAt())throw new IOException("upload_ticket_expired");
        String boundary="muxi-"+UUID.randomUUID();ByteArrayOutputStream prefix=new ByteArrayOutputStream();
        for(var field:upload.fields().entrySet())prefix.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""
            +field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        prefix.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.bin\""
            +"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var publisher=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix.toByteArray()),
            HttpRequest.BodyPublishers.ofFile(audio.audio().path()),HttpRequest.BodyPublishers.ofByteArray(suffix));
        if(publisher.contentLength()!=prefix.size()+audio.bytes()+suffix.length)throw new IOException("audio_changed");
        var counted=BurnHttp.progress(publisher,prefix.size(),audio.bytes(),n->progress.accept(
            new BurnCoordinator.Progress(null,BurnCoordinator.Stage.UPLOADING,n,audio.bytes(),"")));
        HttpRequest request=HttpRequest.newBuilder(upload.uri()).timeout(Duration.ofSeconds(120))
            .header("Content-Type","multipart/form-data; boundary="+boundary).POST(counted).build();
        // No original filename, Cookie or Authorization is sent to OSS.
        if(BurnHttp.send(request,65536,cancel,n->{}).statusCode()!=201)throw new IOException("oss_upload_failed");
    }
    @Override public BurnCoordinator.Receipt verify(BurnCoordinator.Ticket ticket,BurnCoordinator.Cancellation cancel)throws Exception{
        JsonObject result=api("/api/launcher/music/uploads/"+uuid(ticket.jobId())+"/complete",session(),cancel,120);
        URI url=URI.create(result.get("url").getAsString());String job=uuid(result.get("jobId").getAsString()),receipt=uuid(result.get("receiptId").getAsString());
        if(!"https".equals(url.getScheme())||!oss.getHost().equals(url.getHost())||url.getPort()!=-1||url.getUserInfo()!=null
            ||url.getQuery()!=null||url.getFragment()!=null||!url.getRawPath().matches("/media/[0-9]+/"+job+"\\.(wav|mp3)")||result.get("version").getAsInt()!=1)
            throw new IOException("invalid_receipt");
        String proof=result.get("proof").getAsString();
        if(proof.isEmpty()||proof.length()>8192)throw new IOException("invalid_receipt");
        return new BurnCoordinator.Receipt(job,receipt,url.toString(),result.get("title").getAsString(),result.get("duration").getAsInt(),proof);
    }
    @Override public void cancel(BurnCoordinator.Ticket ticket)throws Exception{
        api("/api/launcher/music/uploads/"+uuid(ticket.jobId())+"/cancel",session(),new BurnCoordinator.Cancellation());
    }
    private JsonObject api(String path,JsonObject body,BurnCoordinator.Cancellation cancel)throws Exception{
        return api(path,body,cancel,30);
    }
    private JsonObject api(String path,JsonObject body,BurnCoordinator.Cancellation cancel,int timeoutSeconds)throws Exception{
        String token=accountAuthorization.get();
        if(token==null||token.isBlank()||token.length()>8192||token.indexOf('\r')>=0||token.indexOf('\n')>=0)
            throw new IOException("account_authorization_required");
        HttpRequest request=HttpRequest.newBuilder(api.resolve(path)).timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        HttpResponse<byte[]> response=BurnHttp.send(request,65536,cancel,n->{});
        if(response.statusCode()!=200){
            String code="music_service_unavailable";
            try {
                JsonObject failure=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
                String returned=failure.get("error").getAsString();
                code=MusicServiceErrors.safe(returned);
            } catch(RuntimeException ignored){}
            throw new IOException(code);
        }
        return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static String uuid(String value)throws IOException{
        try{if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();return value;}
        catch(IllegalArgumentException error){throw new IOException("invalid_job_id");}
    }
}
