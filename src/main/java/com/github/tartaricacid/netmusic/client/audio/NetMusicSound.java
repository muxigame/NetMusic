package com.github.tartaricacid.netmusic.client.audio;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import com.github.tartaricacid.netmusic.init.InitSounds;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import net.muxigame.musiccompat.burn.*;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.minecraft.world.item.ItemStack;
import javax.sound.sampled.*;
import com.github.tartaricacid.netmusic.config.GeneralConfig;
import com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class NetMusicSound extends AbstractTickableSoundInstance implements net.muxigame.musiccompat.burn.PrivateSoundAccess, net.muxigame.musiccompat.burn.PrivateLyricsAccess {
    public static final ResourceLocation ERROR_SOUND = ResourceLocation.fromNamespaceAndPath(NetMusic.MOD_ID, "sounds/error.ogg");

    private final URL songUrl;
    private final int tickTimes;
    private final BlockPos pos;
    private @Nullable LyricRecord lyricRecord;
    private int tick;

    public NetMusicSound(BlockPos pos, URL songUrl, int timeSecond, @Nullable LyricRecord lyricRecord) {
        super(InitSounds.NET_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.songUrl = songUrl;
        this.x = pos.getX() + 0.5f;
        this.y = pos.getY() + 0.5f;
        this.z = pos.getZ() + 0.5f;
        this.tickTimes = timeSecond * 20;
        this.volume = 4.0f;
        this.tick = 0;
        this.pos = pos;
        this.lyricRecord = lyricRecord;
        muxiPlayback$legacyBinding(pos,songUrl,timeSecond);
        muxiLyrics$load(pos,songUrl,timeSecond,lyricRecord);
    }

    @Override
    public void tick() {
        if(getClass()==NetMusicSound.class||muxiPlayback$state!=null){
            if(!muxiPlayback$alive()){muxiPlayback$terminate();return;}
            if(muxiPlayback$startDecision==PlaybackLifecycle.StartGate.Decision.WAIT)return;
        }
        muxiLyrics$apply();
        if(muxiPlayback$state!=null)tick=muxiPlayback$state.lyricTick()-1;
        Level world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        tick++;
        if (tick > tickTimes + 50) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof TileEntityMusicPlayer musicPlay) {
                musicPlay.lyricRecord = null;
            }
            this.stop();
        } else {
            if (world.getGameTime() % 8 == 0) {
                for (int i = 0; i < 2; i++) {
                    world.addParticle(ParticleTypes.NOTE,
                            x - 0.5f + world.random.nextDouble(),
                            y + world.random.nextDouble() + 1,
                            z - 0.5f + world.random.nextDouble(),
                            world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextInt(3));
                }
            }
        }

        // 依据 tick 更新歌词显示
        if (lyricRecord != null) {
            lyricRecord.updateCurrentLine(tick);
        }

        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof TileEntityMusicPlayer musicPlay) {
            if (!musicPlay.isPlay()) {
                musicPlay.lyricRecord = null;
                this.stop();
            } else {
                musicPlay.lyricRecord = lyricRecord;
            }
        } else {
            this.stop();
        }
        if(isStopped())muxiPlayback$terminate();
    }

    private void errorStop() {
        // 直接把 tick 设置为结束的时间点，这样就能在下一次 tick 时正常结束
        this.tick = tickTimes;
        MutableComponent error = Component.translatable("message.netmusic.music_player.play_error");
        Minecraft.getInstance().gui.setOverlayMessage(error, false);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        if(muxiPlayback$state!=null)return muxiPlayback$privateStream();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new NetMusicAudioStream(this.songUrl);
            } catch (IOException | UnsupportedAudioFileException e) {
                NetMusic.LOGGER.error("Failed to create audio stream for URL: {}", songUrl, e);
                Minecraft.getInstance().submit(this::errorStop);
            }

            // 播放失败返回一个默认音频，避免 tick 里的音频实例不能够删除
            try {
                InputStream inputstream = Minecraft.getInstance().getResourceManager().open(ERROR_SOUND);
                return new JOrbisAudioStream(inputstream);
            } catch (IOException ioexception) {
                throw new CompletionException(ioexception);
            }
        }, Util.backgroundExecutor());
    }

 private PrivateSoundState muxiPlayback$state;
 private PlaybackLifecycle.Binding muxiPlayback$standardLifetime;
 private final PlaybackLifecycle.StartGate muxiPlayback$startGate=new PlaybackLifecycle.StartGate(System.nanoTime());
 private PlaybackLifecycle.StartGate.Decision muxiPlayback$startDecision=PlaybackLifecycle.StartGate.Decision.WAIT;
 private boolean muxiPlayback$awaitingBlockState;
 private void muxiPlayback$legacyBinding(BlockPos position,java.net.URL url,int seconds){
  if(((Object)this).getClass()!=NetMusicSound.class)return;
  var mc=Minecraft.getInstance();
  if(mc.getConnection()!=null&&mc.level!=null&&mc.level.hasChunkAt(position)){
   var owner=mc.level.getBlockEntity(position);
   if(owner!=null&&owner.getClass()==TileEntityMusicPlayer.class){
    var tile=(TileEntityMusicPlayer)owner;var disc=tile.getPlayerInv().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(disc);
    if(song!=null&&song.songUrl!=null){muxiPlayback$standardLifetime=new PlaybackLifecycle.Binding(mc.getConnection(),mc.level,tile,disc.copy(),song.songUrl);muxiPlayback$startDecision=muxiPlayback$startGate.observe(!tile.isRemoved(),tile.isPlay(),System.nanoTime());}
   }
  }
  // A standard sound created through the upstream packet/playlist path must not
  // fall through to Playlist's HEAD getStream replacement for private media.
  var initial=PlaybackLifecycle.initialPrivateState(true,url.toString(),seconds);
  if(initial!=null)muxiPlayback$state(initial);
 }
 public void muxiPlayback$state(PrivateSoundState value){
  if(((Object)this).getClass()!=NetMusicSound.class||!BurnProtocol.media(songUrl.toString()))return;
  if(muxiPlayback$state!=null&&muxiPlayback$state!=value)muxiPlayback$state.cancel();
  muxiPlayback$state=value;var mc=Minecraft.getInstance();
  if(mc.getConnection()==null||mc.level==null||!mc.level.hasChunkAt(pos)){value.cancel();return;}
  var owner=mc.level.getBlockEntity(pos);
  if(owner==null||owner.getClass()!=TileEntityMusicPlayer.class){value.cancel();return;}
  var tile=(TileEntityMusicPlayer)owner;var disc=tile.getPlayerInv().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(disc);
  if(song==null||!songUrl.toString().equals(song.songUrl)||tile.isRemoved()){value.cancel();return;}
  value.lifetime=new PlaybackLifecycle.Binding(mc.getConnection(),mc.level,tile,disc.copy(),song.songUrl);
  muxiPlayback$startDecision=muxiPlayback$startGate.observe(true,tile.isPlay(),System.nanoTime());
  muxiPlayback$awaitingBlockState=muxiPlayback$startDecision==PlaybackLifecycle.StartGate.Decision.WAIT;
  if(muxiPlayback$awaitingBlockState){value.followPause=true;value.timelinePaused=true;}
 }
 public PrivateSoundState muxiPlayback$state(){return muxiPlayback$state;}
 public boolean muxiPlayback$alive(){
  var state=muxiPlayback$state;if(state!=null&&state.cancelled)return false;
  var binding=state==null?muxiPlayback$standardLifetime:state.lifetime;if(binding==null)return false;
  var mc=Minecraft.getInstance();
  var owner=mc.level!=null&&mc.level.hasChunkAt(pos)?mc.level.getBlockEntity(pos):null;
  var tile=owner instanceof TileEntityMusicPlayer player?player:null;
  var disc=tile==null?ItemStack.EMPTY:tile.getPlayerInv().getStackInSlot(0);var song=ItemMusicCD.getSongInfo(disc);
  boolean identity=binding.current(mc.getConnection(),mc.level,owner,song==null?null:song.songUrl,
   ItemStack.matches((ItemStack)binding.disc(),disc),tile==null||tile.isRemoved(),true);
  muxiPlayback$startDecision=muxiPlayback$startGate.observe(identity,tile!=null&&tile.isPlay(),System.nanoTime());
  if(muxiPlayback$startDecision==PlaybackLifecycle.StartGate.Decision.STOP){binding.retire();return false;}
  if(state!=null&&muxiPlayback$awaitingBlockState&&muxiPlayback$startDecision==PlaybackLifecycle.StartGate.Decision.RUN){state.timelinePaused=false;muxiPlayback$awaitingBlockState=false;}
  return true;
 }
 public void muxiPlayback$terminate(){
  if(muxiPlayback$state==null&&((Object)this).getClass()!=NetMusicSound.class)return;
  if(muxiPlayback$state!=null)muxiPlayback$state.cancel(); // Cancel before channel stop.
  if(muxiPlayback$standardLifetime!=null)muxiPlayback$standardLifetime.retire();
  ((PrivateLyricsAccess)this).muxiLyrics$cancelPrivatePending();stop();
  Minecraft.getInstance().getSoundManager().stop((NetMusicSound)(Object)this);
 }

 public CompletableFuture<AudioStream> muxiPlayback$privateStream(){
  if(muxiPlayback$state==null)throw new IllegalStateException("private_audio_state_required");var state=muxiPlayback$state;
  return CompletableFuture.supplyAsync(()->{AudioInputStream input=null;MusicPlaybackMetrics.Phase phase=null;var cancellation=new BurnCoordinator.Cancellation();state.bindFetch(cancellation);try(var readScope=PrivatePlayback.bindRead(cancellation)){phase=MusicPlaybackMetrics.begin(songUrl.toURI(),state.lifetime==null?null:state.lifetime.connection()).phase("decode");if(state.cancelled)throw new java.io.IOException("playback_cancelled");input=AudioStreamHandlerManager.handle(songUrl);var f=input.getFormat();if(!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())){var decoded=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits(),f.getChannels(),f.getChannels()*(f.getSampleSizeInBits()<1?16:f.getSampleSizeInBits())/8,f.getSampleRate(),false);input=AudioSystem.getAudioInputStream(decoded,input);f=input.getFormat();}int channels=GeneralConfig.ENABLE_STEREO.get()?1:2;var pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),16,channels,channels*2,f.getSampleRate(),false);input=AudioSystem.getAudioInputStream(pcm,input);if(state.cancelled)throw new java.io.IOException("playback_cancelled");var stream=new PrivatePcmStream(input,state);if(state.cancelled){stream.close();throw new java.io.IOException("playback_cancelled");}phase.success();return (AudioStream)stream;}catch(Exception error){if(state.cancelled&&phase!=null)phase.cancelled();if(input!=null)try{input.close();}catch(Exception ignored){}state.cancel();throw new CompletionException(new java.io.IOException("private_audio_unavailable"));}finally{state.clearFetch(cancellation);if(phase!=null)phase.close();}},net.minecraft.Util.backgroundExecutor());
 }
    private CompletableFuture<LyricRecord> muxiLyrics$pending;
    private com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer muxiLyrics$owner;
    public void muxiLyrics$cancelPrivatePending(){
        if(((Object)this).getClass()!=NetMusicSound.class)return;
        if(muxiLyrics$pending!=null){muxiLyrics$pending.cancel(false);muxiLyrics$pending=null;}
        var owned=lyricRecord;lyricRecord=null;
        if(muxiLyrics$owner!=null&&owned!=null&&muxiLyrics$owner.lyricRecord==owned)muxiLyrics$owner.lyricRecord=null;
    }
    private void muxiLyrics$load(BlockPos pos,URL url,int seconds,LyricRecord original){
        if(((Object)this).getClass()==NetMusicSound.class){
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.level!=null&&mc.level.hasChunkAt(pos)){
                var owner=mc.level.getBlockEntity(pos);
                if(owner!=null&&owner.getClass()==com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer.class)
                    muxiLyrics$owner=(com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer)owner;
            }
        }
        if(original!=null||!GeneralConfig.ENABLE_PLAYER_LYRICS.get()||!BurnProtocol.media(url.toString()))return;
        try{muxiLyrics$pending=PrivateLyrics.load(url.toURI(),"");}catch(Exception unavailable){}
    }
    private void muxiLyrics$apply(){
        var state=((PrivateSoundAccess)this).muxiPlayback$state();
        if(state!=null&&(state.cancelled||!((PrivateSoundAccess)this).muxiPlayback$alive())){
            muxiLyrics$cancelPrivatePending();return;
        }
        if(muxiLyrics$pending!=null&&muxiLyrics$pending.isDone()){
            if(muxiLyrics$pending.isCancelled()||muxiLyrics$pending.isCompletedExceptionally()){muxiLyrics$pending=null;return;}
            // Only the client tick mutates the original field. Each playback has fresh maps.
            lyricRecord=muxiLyrics$pending.getNow(null);muxiLyrics$pending=null;
            if(lyricRecord!=null&&com.gly091020.netMusicListNeoforge.NetMusicList.CONFIG.allowLyricToServer)
                com.github.tartaricacid.netmusic.network.NetworkHandler.sendToServer(new com.gly091020.netMusicListNeoforge.packet.UpdateBlockLyricPacket(pos,com.gly091020.netMusicListNeoforge.util.NetMusicListUtil.Lyric.fromLyricRecord(lyricRecord)));
        }
    }
}
