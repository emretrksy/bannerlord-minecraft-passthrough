package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void blpassthrough$input(CallbackInfo ci) {
		Passthrough.get().tickInput((Minecraft) (Object) this);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void blpassthrough$tick(CallbackInfo ci) {
		Passthrough.get().tick((Minecraft) (Object) this);
	}

	@Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$active(CallbackInfoReturnable<Boolean> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive()) {
			cir.setReturnValue(true);
		}
	}
}
