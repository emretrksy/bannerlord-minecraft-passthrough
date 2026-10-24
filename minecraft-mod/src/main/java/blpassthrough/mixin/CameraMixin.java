package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "update", at = @At("HEAD"))
	private void blpassthrough$beginFrame(DeltaTracker deltaTracker, CallbackInfo ci) {
		Passthrough.get().beginFrame(this.minecraft);
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void blpassthrough$publishCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
		Passthrough.get().cameraUpdated(this.minecraft, (Camera) (Object) this);
	}
}
