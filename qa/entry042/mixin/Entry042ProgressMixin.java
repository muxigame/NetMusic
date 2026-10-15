package net.muxigame.musiccompat.qa.entry042.mixin;
import net.muxigame.musiccompat.burn.*;import net.muxigame.musiccompat.qa.entry042.Entry042Progress;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BurnScreenRuntime.class,remap=false)
public abstract class Entry042ProgressMixin {
 @Inject(method="message",at=@At("HEAD")) private static void observe(BurnCoordinator.Progress value,CallbackInfoReturnable<String> result){Entry042Progress.record(value);}
}
