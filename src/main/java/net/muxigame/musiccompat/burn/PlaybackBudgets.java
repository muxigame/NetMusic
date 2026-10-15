package net.muxigame.musiccompat.burn;
/** Finite independent budgets; a cold account cannot consume the entire preparation deadline. */
public final class PlaybackBudgets {
 public static final long ACCOUNT_NANOS=26_000_000_000L,PREPARATION_NANOS=90_000_000_000L;
 public static final int LYRICS_SECONDS=60;
 private PlaybackBudgets(){}
}
