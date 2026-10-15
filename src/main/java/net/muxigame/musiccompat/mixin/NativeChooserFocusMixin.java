package net.muxigame.musiccompat.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import net.muxigame.musiccompat.burn.LocalAudioChooser;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** MC1.21.1 GameRenderer.render reads this setting before the automatic
 * focus-loss pause. The manual KeyboardHandler ESC path is not intercepted.
 * No Options value is mutated or saved.
 */
@Mixin(GameRenderer.class)
public abstract class NativeChooserFocusMixin {
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/Options;pauseOnLostFocus:Z",
            opcode=Opcodes.GETFIELD),require=1)
    private boolean muxiBurn$automaticPause(Options options) {
        return options.pauseOnLostFocus && !LocalAudioChooser.active();
    }
}
