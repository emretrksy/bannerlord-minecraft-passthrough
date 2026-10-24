package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$hostPause(CallbackInfoReturnable<Boolean> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isHostPaused()) {
			cir.setReturnValue(true);
		}
	}
}
