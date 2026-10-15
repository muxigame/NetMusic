package net.muxigame.musiccompat.burn;
public interface PrivateSoundAccess {
    void muxiPlayback$state(PrivateSoundState state);
    PrivateSoundState muxiPlayback$state();
    boolean muxiPlayback$alive();
    void muxiPlayback$terminate();
}
