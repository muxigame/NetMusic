package net.muxigame.musiccompat.burn;

import com.github.tartaricacid.netmusic.inventory.CDBurnerMenu;
import com.github.tartaricacid.netmusic.inventory.ComputerMenu;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.muxigame.musiccompat.burn.BurnMenuAccess;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Reservations, escrow, settlement and inventory writes run on the server thread. */
public final class BurnServer {
    private static final String ESCROW="muxi_music_compat:burn_escrow";
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final class Context {
        final ServerPlayer player;final int menuId;final BurnEscrow escrow;
        final String request,nonce;final long expires;final boolean readOnly;
        AbstractContainerMenu menu;
        Context(ServerPlayer player,AbstractContainerMenu menu,BurnEscrow escrow,String request,boolean readOnly){
            this.player=player;this.menu=menu;menuId=menu.containerId;this.escrow=escrow;this.request=request;
            nonce=UUID.randomUUID().toString();expires=System.currentTimeMillis()+600_000;this.readOnly=readOnly;
        }
    }
    private record TerminalKey(UUID player,String request){}
    private record Terminal(ServerPlayer player,String request,String nonce,int menu,int status,long expires){}
    private static final Map<UUID,Context> contexts=new HashMap<>();
    private static final Map<UUID,Context> pending=new HashMap<>();
    private static final Map<ItemStackHandler,Context> slots=new ConcurrentHashMap<>();
    private static final Map<AbstractContainerMenu,Context> menus=new ConcurrentHashMap<>();
    private static final LinkedHashMap<TerminalKey,Terminal> terminal=new LinkedHashMap<>();
    private static final ThreadLocal<AbstractContainerMenu> writing=new ThreadLocal<>();
    private static final ExecutorService io=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"Muxi music receipt");t.setDaemon(true);return t;});
    private static final BurnProofVerifier verifier=new BurnProofVerifier();
    private static volatile boolean accepting=true;
    private static boolean registered;
    private BurnServer(){}
    /** Called once by the integration owner, alongside the existing burn listeners. */
    public static synchronized void registerEvents(){
        if(registered)return;registered=true;
        var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(BurnServer::login);bus.addListener(BurnServer::clonePlayer);
        bus.addListener((ServerStartingEvent event)->accepting=true);
    }
    public static boolean verifiedWrite(AbstractContainerMenu menu){return writing.get()==menu;}
    public static boolean locked(AbstractContainerMenu menu){return menus.containsKey(menu)&&!verifiedWrite(menu);}
    public static boolean locked(ItemStackHandler handler){Context value=slots.get(handler);return value!=null&&!verifiedWrite(value.menu);}
    private static void detach(Context value){
        AbstractContainerMenu menu=value.menu;if(menu==null)return;
        menus.remove(menu,value);slots.remove(input(menu),value);slots.remove(output(menu),value);value.menu=null;
    }
    public static void menuClosed(ServerPlayer player,AbstractContainerMenu menu){
        Context value=contexts.get(player.getUUID());if(value!=null&&value.player==player&&value.menu==menu)detach(value);
    }
    /** Original removed() has returned only non-escrow contents. Clear its handlers once. */
    public static void removed(AbstractContainerMenu menu){input(menu).setStackInSlot(0,ItemStack.EMPTY);output(menu).setStackInSlot(0,ItemStack.EMPTY);}
    private static void persist(Context value,ItemStack item){
        CompoundTag tag=new CompoundTag();tag.putUUID("owner",value.player.getUUID());tag.putString("request",value.request);tag.put("item",item.save(value.player.registryAccess()));
        value.player.getPersistentData().put(ESCROW,tag);
    }
    private static void clearPersistent(Context value){
        var data=value.player.getPersistentData();
        if(data.getCompound(ESCROW).getString("request").equals(value.request))data.remove(ESCROW);
    }
    private static void save(ServerPlayer player){
        if(!player.server.isSameThread())throw new IllegalStateException("burn_inventory_not_on_server_thread");
        // A reconnect can use the same UUID with a different player object. Never overwrite its file.
        if(player.server.getPlayerList().getPlayer(player.getUUID())==player)
            ((net.muxigame.musiccompat.mixin.BurnPlayerListAccess)player.server.getPlayerList()).muxiBurn$save(player);
    }
    private static void deliver(ServerPlayer player,ItemStack item){ItemHandlerHelper.giveItemToPlayer(player,item);}
    private static boolean settle(Context value,ItemStack result,boolean success){
        if(contexts.get(value.player.getUUID())!=value)return false;
        ItemStack owned=value.escrow.finish(result);if(owned.isEmpty())return false;
        // The recoverable result and removed menu stack are saved together in the player file.
        persist(value,owned);detach(value);contexts.remove(value.player.getUUID(),value);
        deliver(value.player,owned);clearPersistent(value);save(value.player);
        remember(value,success?2:3);
        if(connected(value))reply(value.player,value.request,value.nonce,success?2:3);
        return true;
    }
    private static void remember(Context value,int status){
        terminal.put(new TerminalKey(value.player.getUUID(),value.request),new Terminal(value.player,value.request,value.nonce,value.menuId,status,System.currentTimeMillis()+600_000));
        while(terminal.size()>256)terminal.remove(terminal.keySet().iterator().next());
    }
    private static void abandon(Context value){
        value.escrow.finish(value.escrow.original());detach(value);contexts.remove(value.player.getUUID(),value);
        // The saved tag survives for login recovery; the old callback cannot deliver or save again.
    }
    private static void fail(Context value){
        ServerPlayer replacement=value.player.server.getPlayerList().getPlayer(value.player.getUUID());
        if(replacement!=null&&replacement!=value.player){abandon(value);return;}
        settle(value,value.escrow.original(),false);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            Context value=contexts.get(player.getUUID());if(value!=null&&value.player==player)fail(value);
            if(supportedMenu(player.containerMenu))player.closeContainer();
            terminal.values().removeIf(t->t.player==player);
        }
    }
    public static void death(LivingDeathEvent event){
        if(!event.isCanceled()&&event.getEntity() instanceof ServerPlayer player){
            Context value=contexts.get(player.getUUID());if(value!=null&&value.player==player)fail(value);
            if(supportedMenu(player.containerMenu))player.closeContainer();
        }
    }
    public static void tick(ServerTickEvent.Post event){
        for(Context value:List.copyOf(contexts.values()))if(!current(value))fail(value);
        long now=System.currentTimeMillis();terminal.values().removeIf(t->t.expires<=now);
    }
    public static void stopping(ServerStoppingEvent event){
        accepting=false;
        for(Context value:List.copyOf(contexts.values()))fail(value);
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers())if(supportedMenu(player.containerMenu))player.closeContainer();
    }
    public static void stopped(ServerStoppedEvent event){contexts.clear();pending.clear();slots.clear();menus.clear();terminal.clear();writing.remove();}
    public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            Context old=contexts.get(player.getUUID());if(old!=null&&old.player!=player)abandon(old);
            recover(player);
        }
    }
    public static void clonePlayer(PlayerEvent.Clone event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getOriginal() instanceof ServerPlayer original){
            Context value=contexts.get(original.getUUID());if(value!=null&&value.player==original)abandon(value);
            // Normal death/refund clears the source first. Only an actual saved remainder is transferred.
            if(original.getPersistentData().contains(ESCROW)&&!player.getPersistentData().contains(ESCROW)){
                player.getPersistentData().put(ESCROW,original.getPersistentData().getCompound(ESCROW).copy());
                original.getPersistentData().remove(ESCROW);
            }
            recover(player);
        }
    }
    private static void recover(ServerPlayer player){
        if(contexts.containsKey(player.getUUID())||!player.getPersistentData().contains(ESCROW))return;
        CompoundTag tag=player.getPersistentData().getCompound(ESCROW);
        if(!tag.hasUUID("owner")||!tag.getUUID("owner").equals(player.getUUID())||!BurnProtocol.uuid(tag.getString("request"))){
            LOGGER.error("Music escrow recovery refused invalid saved owner for {}",player.getUUID());return;
        }
        ItemStack item=ItemStack.parseOptional(player.registryAccess(),tag.getCompound("item"));
        if(item.isEmpty()||item.getCount()!=1||!(item.getItem() instanceof ItemMusicCD)){
            LOGGER.error("Music escrow recovery refused invalid saved item for {}",player.getUUID());return;
        }
        deliver(player,item);player.getPersistentData().remove(ESCROW);save(player);
        LOGGER.info("Returned saved music escrow to {} without resuming the old task",player.getUUID());
    }
    private static boolean connected(Context value){return value.player.server.getPlayerList().getPlayer(value.player.getUUID())==value.player&&value.player.connection.getConnection().isConnected();}
    private static boolean current(Context value){
        return accepting&&connected(value)&&value.player.isAlive()&&value.expires>System.currentTimeMillis()&&!value.escrow.settled();
    }
    private static boolean supportedMenu(AbstractContainerMenu menu){return menu instanceof CDBurnerMenu||menu instanceof ComputerMenu;}
    private static ItemStackHandler input(AbstractContainerMenu menu){
        if(menu instanceof CDBurnerMenu burner)return burner.getInput();if(menu instanceof ComputerMenu computer)return computer.getInput();
        throw new IllegalArgumentException("unsupported_burn_menu");
    }
    private static ItemStackHandler output(AbstractContainerMenu menu){return ((BurnMenuAccess)menu).muxiBurn$output();}
    private static boolean canBurn(AbstractContainerMenu menu){
        ItemStack stack=input(menu).getStackInSlot(0);var old=ItemMusicCD.getSongInfo(stack);
        return stack.getCount()==1&&stack.getItem() instanceof ItemMusicCD&&(old==null||!old.readOnly)&&output(menu).getStackInSlot(0).isEmpty();
    }
    private static boolean identity(ServerPlayer player){
        String name=player.getGameProfile().getName();return name.matches("[1-9][0-9]{4,15}")&&UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8)).equals(player.getUUID());
    }
    private static boolean matches(Context value,ServerPlayer player,BurnNetwork.Request request){return value!=null&&value.player==player&&value.request.equals(request.requestId())&&value.menuId==request.menu();}
    public static void request(ServerPlayer player,BurnNetwork.Request request){
        if(!BurnProtocol.uuid(request.requestId())||!identity(player)||request.action()<0||request.action()>3)return;
        UUID uid=player.getUUID();Context value=contexts.get(uid);
        if(value!=null&&!current(value)){fail(value);value=null;}
        Terminal ended=terminal.get(new TerminalKey(player.getUUID(),request.requestId()));
        if(request.action()==3){
            if(matches(value,player,request)&&value.nonce.equals(request.proof()))reply(player,value.request,value.nonce,4);
            else if(ended!=null&&ended.player==player&&ended.menu==request.menu()&&ended.nonce.equals(request.proof()))reply(player,ended.request,ended.nonce,ended.status);
            return;
        }
        if(request.action()==2){
            if(matches(value,player,request)&&(request.proof().isEmpty()||value.nonce.equals(request.proof())))fail(value);
            else if(ended!=null&&ended.player==player&&ended.menu==request.menu()&&(request.proof().isEmpty()||ended.nonce.equals(request.proof())))reply(player,ended.request,ended.nonce,ended.status);
            return;
        }
        if(ended!=null&&ended.player==player){reply(player,ended.request,ended.nonce,ended.status);return;}
        if(request.action()==0){
            if(value!=null){reply(player,request.requestId(),matches(value,player,request)&&value.readOnly==request.readOnly()?value.nonce:"",matches(value,player,request)&&value.readOnly==request.readOnly()?1:3);return;}
            AbstractContainerMenu menu=player.containerMenu;
            if(!accepting||pending.containsKey(uid)||player.getPersistentData().contains(ESCROW)||!request.proof().isEmpty()||!supportedMenu(menu)||menu.containerId!=request.menu()||!menu.stillValid(player)||!canBurn(menu)){
                reply(player,request.requestId(),"",3);return;
            }
            Context started=new Context(player,menu,BurnEscrow.reserve(input(menu)),request.requestId(),request.readOnly());
            contexts.put(uid,started);menus.put(menu,started);slots.put(input(menu),started);slots.put(output(menu),started);
            persist(started,started.escrow.original());save(player);menu.broadcastChanges();
            reply(player,started.request,started.nonce,1);return;
        }
        if(request.action()!=1||!matches(value,player,request)||!current(value)||request.proof().isEmpty()){
            reply(player,request.requestId(),matches(value,player,request)?value.nonce:"",3);return;
        }
        if(pending.putIfAbsent(uid,value)!=null)return; // One redeem in flight, even after a duplicate commit.
        Context accepted=value;long verifiedUid=Long.parseLong(player.getGameProfile().getName());
        CompletableFuture.supplyAsync(()->{try{return verifier.consume(request.proof(),verifiedUid,accepted.nonce);}catch(Exception unavailable){return null;}},io)
            .whenComplete((receipt,error)->player.server.execute(()->{
                try{
                    if(contexts.get(uid)!=accepted)return;
                    if(error!=null||receipt==null||!current(accepted)){fail(accepted);return;}
                    var song=new ItemMusicCD.SongInfo(receipt.get("url").getAsString(),receipt.get("title").getAsString(),receipt.get("duration").getAsInt(),accepted.readOnly);
                    song.artists=List.of(SongMetadata.clean(receipt.has("credit")?receipt.get("credit").getAsString():"",1024,"未知作者"));
                    ItemStack result=ItemMusicCD.setSongInfo(song,accepted.escrow.original());
                    var actual=ItemMusicCD.getSongInfo(result);
                    if(actual==null||!song.songUrl.equals(actual.songUrl)){fail(accepted);return;}
                    settle(accepted,result,true);
                }catch(Exception failed){
                    LOGGER.error("Music escrow commit failed for player {} request {}: {}",uid,accepted.request,failed.getClass().getSimpleName());
                    if(contexts.get(uid)==accepted&&!accepted.escrow.settled())fail(accepted);
                }finally{pending.remove(uid,accepted);}
            }));
    }
    private static void reply(ServerPlayer player,String request,String nonce,int status){
        if(player.server.getPlayerList().getPlayer(player.getUUID())!=player||!player.connection.getConnection().isConnected())return;
        if(!NetworkRegistry.hasChannel(player.connection,BurnNetwork.Result.TYPE.id())){
            LOGGER.warn("Music burn result unavailable for player {} request {} status {}",player.getUUID(),request,status);return;
        }
        try{PacketDistributor.sendToPlayer(player,new BurnNetwork.Result(request,nonce,status));}
        catch(RuntimeException failed){LOGGER.warn("Music burn result send failed for player {} request {} status {}: {}",player.getUUID(),request,status,failed.getClass().getSimpleName());}
    }
}
