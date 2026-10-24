package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$neverGrab(CallbackInfo ci) {
		if (Passthrough.get().isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$grabbedForGameplay(CallbackInfoReturnable<Boolean> cir) {
		if (Passthrough.get().isActive() && Minecraft.getInstance().gui.screen() == null) {
			cir.setReturnValue(true);
		}
	}
}
