package net.muxigame.musiccompat.burn;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.github.tartaricacid.netmusic.client.gui.ComputerMenuScreen;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** A connection owns its job; screens only subscribe to its latest progress. */
public final class BurnClientSession implements BurnScreenRuntime.Session {
    private final BurnConfirmation confirmation=new BurnConfirmation(Minecraft.getInstance().gameDirectory.toPath().resolve("config/muxi-music-confirmation.json"));
    private final Map<AbstractContainerScreen<?>,State> screens=new WeakHashMap<>();
    private final LocalAudioChooser localAudioChooser=new LocalAudioChooser(command->Minecraft.getInstance().execute(command));
    private final Map<String,CompletableFuture<String>> waiting=new HashMap<>();
    private TaskOwner active;
    private static final class TaskOwner {
        final ClientPacketListener connection;
        BurnCoordinator job;
        final BurnProgressViews<AbstractContainerScreen<?>> views;
        volatile String request="",nonce="";
        volatile int menuId;
        volatile SongMetadata metadata;
        BurnCoordinator.Progress latest;
        long nextQuery;
        boolean serverTerminal;
        boolean readOnly=true,confirmed;
        String sourceType="netease",input="";
        TaskOwner(ClientPacketListener connection){
            this.connection=connection;views=new BurnProgressViews<>(screen->{
                var mc=Minecraft.getInstance();return live(this)&&mc.screen==screen&&mc.player.containerMenu==screen.getMenu();
            });
        }
    }
    private static final class State {
        final TaskOwner owner;
        boolean confirmed,readOnly=true;
        ExistingPlatformClientSource.Platform lastPlatform=ExistingPlatformClientSource.Platform.INVALID;
        String sourceType="netease";
        String input="";
        State(TaskOwner owner){this.owner=owner;if(owner.job!=null&&owner.job.running()){readOnly=owner.readOnly;sourceType=owner.sourceType;confirmed=owner.confirmed;input=owner.input;}}
    }
    public BurnClientSession(){
        BurnNetwork.clientReceiver(this::receive);
        var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(BurnLoginFlow::tick);bus.addListener(this::tick);
    }
    @Override public boolean enabled(){return true;}
    private TaskOwner owner(){
        var connection=Minecraft.getInstance().getConnection();
        if(active!=null&&active.connection!=connection)retire(active);
        if(active==null){active=new TaskOwner(connection);createJob(active);}
        return active;
    }
    private State state(AbstractContainerScreen<?> screen){
        TaskOwner task=owner();State old=screens.get(screen);
        if(old!=null&&old.owner==task)return old;
        State value=new State(task);if(!task.job.running())value.confirmed=confirmation.get();screens.put(screen,value);return value;
    }
    private void createJob(TaskOwner task){
        var backend=new BoundBurnBackend((audio,request,cancel)->{
            JsonObject account=account(task,cancel);cancel.check();String nonce=task.nonce;
            if(!live(task)||!request.toString().equals(task.request)||!BurnProtocol.uuid(nonce))throw new IOException("burn_context_required");
            OssBurnBackend delegate=new OssBurnBackend(BurnProtocol.ACCOUNT,BurnProtocol.OSS,account.get("gameSession").getAsString(),()->freshToken(task),()->nonce);
            BurnCoordinator.Ticket ticket;
            try{ticket=delegate.authorize(audio,request,cancel);}
            catch(Exception failed){Minecraft.getInstance().execute(()->cancelContext(task,request.toString()));throw failed;}
            return new BoundBurnBackend.Binding(delegate,ticket,()->Minecraft.getInstance().execute(()->cancelContext(task,request.toString())));
        });
        task.job=new BurnCoordinator(Minecraft.getInstance()::execute,backend,BurnSources::supportedHeader,p->{
            if(active!=task||!live(task))return;
            task.latest=p;
            task.views.publish(p);
            if(p.stage()==BurnCoordinator.Stage.FAILED||p.stage()==BurnCoordinator.Stage.CANCELLED)cancelContext(task,p.generation().toString());
            if(p.stage()==BurnCoordinator.Stage.FAILED&&Set.of("platform_login_required","platform_login_expired").contains(p.code())){
                for(var entry:List.copyOf(screens.entrySet()))if(entry.getValue().owner==task&&current(entry.getKey(),entry.getValue())){BurnLoginFlow.open(entry.getKey(),entry.getValue().lastPlatform);break;}
            }
        });
    }
    @Override public BurnCoordinator open(AbstractContainerScreen<?> screen,Consumer<BurnCoordinator.Progress> progress){
        State state=state(screen);state.owner.views.attach(screen,progress);
        return state.owner.job;
    }
    private static boolean live(TaskOwner task){
        var mc=Minecraft.getInstance();return task.connection!=null&&mc.getConnection()==task.connection&&mc.player!=null&&task.connection.getConnection().isConnected();
    }
    private static boolean current(AbstractContainerScreen<?> screen,State state){
        var mc=Minecraft.getInstance();return live(state.owner)&&mc.screen==screen&&mc.player.containerMenu==screen.getMenu();
    }
    @Override public BurnCoordinator.Commit commit(AbstractContainerScreen<?> screen){
        State state=state(screen);TaskOwner task=state.owner;int menuId=screen.getMenu().containerId;boolean readOnly=state.readOnly;String input=state.input,sourceType=state.sourceType;boolean confirmed=state.confirmed;
        return new BurnCoordinator.Commit(){
            CompletableFuture<String> reservation;
            String requestId="";
            public void begin(UUID request)throws Exception{
                // Executed synchronously on the UI/game thread. Reserve precedes the close-menu packet.
                if(!current(screen,state)||!NetworkRegistry.hasChannel(task.connection,BurnNetwork.Request.TYPE.id()))throw new IOException("burn_context_required");
                requestId=request.toString();reservation=new CompletableFuture<>();
                task.request=requestId;task.nonce="";task.menuId=menuId;task.metadata=null;task.nextQuery=0;task.serverTerminal=false;
                task.readOnly=readOnly;task.sourceType=sourceType;task.input=input;task.confirmed=confirmed;task.views.reset();
                waiting.put(requestId,reservation);
                try{PacketDistributor.sendToServer(new BurnNetwork.Request(requestId,menuId,0,"",readOnly));}
                catch(RuntimeException failed){waiting.remove(requestId,reservation);throw new IOException("burn_context_required");}
            }
            public void prepare(UUID request,BurnCoordinator.Cancellation cancel)throws Exception{
                boolean accepted=false;
                try{
                    if(reservation==null||!request.toString().equals(requestId))throw new IOException("burn_context_required");
                    String nonce=await(reservation,10,cancel,task);
                    if(!BurnProtocol.uuid(nonce)||!requestId.equals(task.request))throw new IOException("burn_context_required");
                    task.nonce=nonce;accepted=true;
                }finally{
                    boolean abort=!accepted;Minecraft.getInstance().execute(()->{waiting.remove(requestId,reservation);if(abort)cancelContext(task,requestId);});
                }
            }
            public boolean stillCurrent(){return live(task)&&task.request.equals(requestId)&&BurnProtocol.uuid(task.nonce);}
            public void send(BurnCoordinator.Receipt receipt)throws Exception{
                if(!stillCurrent()||receipt.proof().isEmpty())throw new IOException("invalid_receipt");
                PacketDistributor.sendToServer(new BurnNetwork.Request(requestId,menuId,1,receipt.proof(),readOnly));
            }
        };
    }
    private JsonObject account(TaskOwner task,BurnCoordinator.Cancellation cancel)throws Exception{
        JsonObject remembered=PrivatePlayback.remembered(task.connection);if(remembered!=null&&live(task))return remembered;
        CompletableFuture<String> result=new CompletableFuture<>();
        Minecraft.getInstance().execute(()->{
            if(!live(task)){result.complete("");return;}
            try{
                Class<?> api=Class.forName("net.muxigame.core.client.TerminalPassportApi");api.getMethod("freshAccessToken");
                api.getMethod("request",Consumer.class).invoke(null,(Consumer<String>)value->{
                    if(live(task))result.complete(value);else result.complete("");
                });
            }catch(ReflectiveOperationException unavailable){result.completeExceptionally(new IOException("core_account_api_update_required"));}
        });
        String value=await(result,26,cancel,task);
        if(!live(task)||value==null||value.isBlank()||value.length()>16384)throw new IOException("account_authorization_required");
        JsonObject account=JsonParser.parseString(value).getAsJsonObject();
        if(!BurnProtocol.uuid(account.get("gameSession").getAsString())||account.get("uid").getAsLong()<10000)throw new IOException("account_authorization_required");
        PrivatePlayback.remember(account,task.connection);return account;
    }
    private static String await(CompletableFuture<String> value,int seconds,BurnCoordinator.Cancellation cancel,TaskOwner task)throws Exception{
        long expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(true){
            cancel.check();if(!live(task))throw new IOException("live_account_connection_required");
            try{return value.get(100,TimeUnit.MILLISECONDS);}
            catch(ExecutionException failed){if(failed.getCause() instanceof IOException problem)throw problem;throw new IOException("account_authorization_required");}
            catch(TimeoutException pending){if(System.nanoTime()>expires)throw new IOException("account_authorization_required");}
        }
    }
    private static String freshToken(TaskOwner task){
        if(!live(task))return "";
        try{return (String)Class.forName("net.muxigame.core.client.TerminalPassportApi").getMethod("freshAccessToken").invoke(null);}
        catch(ReflectiveOperationException unavailable){return "";}
    }
    private void receive(BurnNetwork.Result result){
        TaskOwner task=active;if(task==null||!live(task)||!task.request.equals(result.requestId()))return;
        CompletableFuture<String> pending=waiting.get(result.requestId());
        if(pending!=null){
            if(result.status()==1&&BurnProtocol.uuid(result.nonce())){task.nonce=result.nonce();pending.complete(result.nonce());}
            else if(result.status()==3&&(result.nonce().isEmpty()||result.nonce().equals(task.nonce)))pending.complete("");
            return;
        }
        if(!BurnProtocol.uuid(task.nonce)||!task.nonce.equals(result.nonce()))return;
        if(result.status()==2||result.status()==3){task.serverTerminal=true;task.job.serverSettled(UUID.fromString(result.requestId()),result.status()==2);}
    }
    private void tick(ClientTickEvent.Post event){
        TaskOwner task=active;if(task==null)return;
        if(Minecraft.getInstance().getConnection()!=task.connection){retire(task);return;}
        long now=System.currentTimeMillis();
        if(!task.serverTerminal&&live(task)&&BurnProtocol.uuid(task.nonce)&&!task.request.isEmpty()&&now>=task.nextQuery){
            task.nextQuery=now+2000;
            // Query is read-only: it never repeats receipt redemption.
            if(task.job.needsServerSettlement()){
                try{if(NetworkRegistry.hasChannel(task.connection,BurnNetwork.Request.TYPE.id()))PacketDistributor.sendToServer(new BurnNetwork.Request(task.request,task.menuId,3,task.nonce));}
                catch(RuntimeException unavailable){task.nextQuery=now+5000;}
            }
        }
    }
    private void retire(TaskOwner task){
        if(active!=task)return;active=null;
        task.job.close();task.views.clear();for(var future:waiting.values())future.complete("");waiting.clear();
        screens.entrySet().removeIf(entry->entry.getValue().owner==task);
    }
    @Override public BurnCoordinator.Source link(AbstractContainerScreen<?> screen,String value){
        var state=state(screen);TaskOwner owner=state.owner;
        if(screen instanceof ComputerMenuScreen){try{Path local=ComputerSourceInput.localFile(value);if(local!=null)return local(screen,local);}catch(RuntimeException invalid){return (cancel,progress)->{throw new IOException("not_local_audio");};}}
        var route=route(screen,value);state.lastPlatform=route.platform();state.input=value;
        state.sourceType=switch(route.platform()){case QQ->"qq_music";case BILIBILI->"bilibili";default->"netease";};
        BurnCoordinator.Source source=switch(route.platform()){
            case NETEASE -> new BetterLoginClientSource(owner.connection).createAutomatic(Long.parseLong(route.identifier()),temp());
            case QQ,BILIBILI -> ExistingPlatformClientSource.createAutomaticClient(route,temp(),owner.connection);
            case DIRECT -> (cancel,progress)->{
                String filename=java.net.URI.create(route.identifier()).getPath();filename=filename==null?"音频":filename.substring(filename.lastIndexOf('/')+1).replaceFirst("\\.[^.]+$","");
                var audio=PlatformDownloadSource.create(route.identifier(),temp(),SongMetadata.clean(filename,120,"音频"),"OWNED-REDISTRIBUTION","未知作者").acquire(cancel,progress);
                var info=SongMetadata.local(audio.path(),audio.title());return new BurnCoordinator.Audio(audio.path(),info.title(),audio.license(),info.artist());
            };
            case INVALID -> (cancel,progress)->{throw new IOException("platform_source_invalid");};
        };
        return metadata(state,source);
    }
    private ExistingPlatformClientSource.Route route(AbstractContainerScreen<?> screen,String value){var selected=com.gly091020.netMusicListNeoforge.util.MSMixinFunctions.currentSource;String type=screen instanceof ComputerMenuScreen?sourceType(screen):selected==null?"netease":selected.getType();return ExistingPlatformClientSource.route(value,type);}
    private BurnCoordinator.Source metadata(State state,BurnCoordinator.Source source){
        boolean confirmed=state.confirmed;TaskOwner task=state.owner;
        return (cancel,progress)->{
            if(!confirmed)throw new IOException("redistribution_permission_required");
            if(!live(task))throw new IOException("live_account_connection_required");
            String request=task.request;var audio=source.acquire(cancel,progress);
            cancel.check();if(!live(task))throw new IOException("live_account_connection_required");
            SongMetadata metadata=new SongMetadata(audio.title(),audio.credit());
            Minecraft.getInstance().execute(()->{if(active==task&&live(task)&&task.request.equals(request)&&!cancel.cancelled())task.metadata=metadata;});
            return audio;
        };
    }
    @Override public String title(AbstractContainerScreen<?> screen){var info=state(screen).owner.metadata;return info==null?"":info.title();}
    @Override public boolean prepareLink(AbstractContainerScreen<?> screen,String value){
        var state=state(screen);if(state.owner.job.running())return true;var target=route(screen,value);
        if(target.platform()==ExistingPlatformClientSource.Platform.NETEASE&&!BetterLoginClientSource.hasLocalLogin()){state.lastPlatform=target.platform();BurnLoginFlow.open(screen,target.platform());return false;}return true;
    }
    @Override public void login(AbstractContainerScreen<?> screen){
        if(state(screen).owner.job.running())return;
        var selected=com.gly091020.netMusicListNeoforge.util.MSMixinFunctions.currentSource;String type=screen instanceof ComputerMenuScreen?sourceType(screen):selected==null?"netease":selected.getType();BurnLoginFlow.open(screen,type.equals("qq_music")?ExistingPlatformClientSource.Platform.QQ:type.equals("bilibili")?ExistingPlatformClientSource.Platform.BILIBILI:ExistingPlatformClientSource.Platform.NETEASE);
    }
    @Override public void login(AbstractContainerScreen<?> screen,String value){var state=state(screen);if(state.owner.job.running())return;var target=route(screen,value);if(target.platform()==ExistingPlatformClientSource.Platform.DIRECT||target.platform()==ExistingPlatformClientSource.Platform.INVALID){login(screen);return;}state.lastPlatform=target.platform();BurnLoginFlow.open(screen,target.platform());}
    @Override public String sourceType(AbstractContainerScreen<?> screen){return state(screen).sourceType;}
    @Override public void sourceType(AbstractContainerScreen<?> screen,String type){if(!Set.of("netease","qq_music","bilibili").contains(type))throw new IllegalArgumentException("platform_source_invalid");State state=state(screen);if(!state.owner.job.running())state.sourceType=type;}
    @Override public BurnCoordinator.Source local(AbstractContainerScreen<?> screen,Path file){var state=state(screen);state.lastPlatform=ExistingPlatformClientSource.Platform.DIRECT;state.input=file.toUri().toString();return metadata(state,BurnSources.localAutomatic(file,temp()));}
    private static Path temp(){return Minecraft.getInstance().gameDirectory.toPath().resolve("muxi-music-private-tmp");}
    @Override public void chooseLocal(Consumer<Path> selected){chooseLocalOnce(selected);}
    @Override public boolean chooseLocalOnce(Consumer<Path> selected){return localAudioChooser.choose(selected);}
    @Override public boolean permissionConfirmed(AbstractContainerScreen<?> screen){return state(screen).confirmed;}
    @Override public void confirmation(AbstractContainerScreen<?> screen,boolean value){var state=state(screen);state.confirmed=value;confirmation.set(value);if(!value){state.owner.job.cancel();cancelContext(state.owner,state.owner.request);}}
    @Override public void permissionScreen(AbstractContainerScreen<?> screen){Minecraft.getInstance().setScreen(new BurnPermissionScreen(screen,state(screen).confirmed,value->confirmation(screen,value)));}
    @Override public void closed(AbstractContainerScreen<?> screen){State state=screens.get(screen);if(state!=null)state.owner.views.detach(screen);}
    @Override public boolean readOnlySelected(AbstractContainerScreen<?> screen){State state=state(screen);return state.owner.job.running()?state.owner.readOnly:state.readOnly;}
    @Override public String activeInput(AbstractContainerScreen<?> screen){State state=state(screen);return state.owner.job.running()?state.owner.input:"";}
    @Override public void readOnly(AbstractContainerScreen<?> screen,boolean selected){State state=state(screen);if(!state.owner.job.running())state.readOnly=selected;}
    private void cancelContext(TaskOwner task,String request){
        if(!request.equals(task.request)||!BurnProtocol.uuid(request)||!live(task))return;
        CompletableFuture<String> value=waiting.remove(request);if(value!=null)value.complete("");
        try{if(NetworkRegistry.hasChannel(task.connection,BurnNetwork.Request.TYPE.id()))PacketDistributor.sendToServer(new BurnNetwork.Request(request,task.menuId,2,task.nonce));}
        catch(RuntimeException unavailable){/* Server connection/expiry cleanup retains the original CD. */}
    }
}
