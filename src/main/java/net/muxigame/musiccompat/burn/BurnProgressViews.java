package net.muxigame.musiccompat.burn;

import java.util.*;
import java.util.function.*;

/** Presentation subscriptions have no authority to cancel the connection-owned worker. */
public final class BurnProgressViews<V> {
    private final Map<V,Consumer<BurnCoordinator.Progress>> views=new WeakHashMap<>();
    private final Predicate<V> visible;
    private BurnCoordinator.Progress latest;
    public BurnProgressViews(Predicate<V> visible){this.visible=visible;}
    public void attach(V view,Consumer<BurnCoordinator.Progress> subscriber){
        views.put(view,subscriber);if(latest!=null&&visible.test(view))subscriber.accept(latest);
    }
    public void detach(V view){views.remove(view);}
    public void publish(BurnCoordinator.Progress progress){
        latest=progress;
        for(var entry:List.copyOf(views.entrySet()))if(entry.getKey()!=null&&visible.test(entry.getKey()))entry.getValue().accept(progress);
    }
    public void reset(){latest=null;}
    public void clear(){views.clear();latest=null;}
}
