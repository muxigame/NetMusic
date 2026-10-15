package net.muxigame.musiccompat.burn;

import java.util.Objects;

/** Identity-bound, terminal lifetimes. Positions and matching URLs never revive an old owner. */
public final class PlaybackLifecycle {
    public static final class StartGate {
        public enum Decision {WAIT,RUN,STOP}
        private final long deadline;
        private boolean started,stopped;
        public StartGate(long now){deadline=now+3_000_000_000L;}
        public synchronized Decision observe(boolean identityCurrent,boolean playing,long now){
            if(stopped||!identityCurrent){stopped=true;return Decision.STOP;}
            if(playing){started=true;return Decision.RUN;}
            if(started||now>=deadline){stopped=true;return Decision.STOP;}
            return Decision.WAIT;
        }
    }
    /** Legacy packets also construct the exact standard sound, without PlaybackClient. */
    public static PrivateSoundState initialPrivateState(boolean standard,String reference,int duration){
        if(!standard||!BurnProtocol.media(reference))return null;
        var state=new PrivateSoundState(0,false,Math.max(1,Math.min(14400,duration)));
        if(duration<1||duration>14400)state.cancel();
        return state;
    }
    /** The original tick emits NOTE before checking the player; ownership must run first. */
    public static void tick(boolean guarded,java.util.function.BooleanSupplier current,Runnable terminate,Runnable original){
        if(guarded&&!current.getAsBoolean()){terminate.run();return;}
        original.run();
    }
    public static final class Binding {
        private final Object connection, level, owner, disc;
        private final String reference;
        private boolean retired;

        public Binding(Object connection, Object level, Object owner, Object disc, String reference) {
            this.connection=Objects.requireNonNull(connection);
            this.level=Objects.requireNonNull(level);
            this.owner=Objects.requireNonNull(owner);
            this.disc=Objects.requireNonNull(disc);
            this.reference=Objects.requireNonNull(reference);
        }
        public Object connection(){return connection;}
        public Object owner(){return owner;}
        public Object disc(){return disc;}
        public synchronized void retire(){retired=true;}
        public synchronized boolean current(Object connection,Object level,Object owner,String reference,
                                            boolean sameDisc,boolean removed,boolean playing){
            if(retired)return false;
            boolean valid=this.connection==connection&&this.level==level&&this.owner==owner
                &&this.reference.equals(reference)&&sameDisc&&!removed&&playing;
            if(!valid)retired=true;
            return valid;
        }
    }

    /** Captured before an original asynchronous resolver is attached to the server executor. */
    public static final class Generation {
        public record Ticket(long generation,Object level,Object owner,Object disc,String reference){}
        private long generation;
        public synchronized Ticket begin(Object level,Object owner,Object disc,String reference){
            return new Ticket(++generation,Objects.requireNonNull(level),Objects.requireNonNull(owner),
                Objects.requireNonNull(disc),Objects.requireNonNull(reference));
        }
        public synchronized void invalidate(){generation++;}
        public synchronized boolean current(Ticket ticket,Object level,Object owner,Object disc,
                                            String reference,boolean removed,boolean present,boolean sameDisc){
            return ticket!=null&&ticket.generation==generation&&ticket.level==level&&ticket.owner==owner
                &&ticket.disc==disc&&ticket.reference.equals(reference)&&!removed&&present&&sameDisc;
        }
    }
    private PlaybackLifecycle(){}
}
