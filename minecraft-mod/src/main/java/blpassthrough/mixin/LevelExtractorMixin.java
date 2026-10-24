package blpassthrough.mixin;

import blpassthrough.Passthrough;
import blpassthrough.WorldBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$visibility(
		Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks, long chunkFadeDuration,
		CallbackInfoReturnable<Boolean> cir) {
		if (!Passthrough.get().isActive()) {
			return;
		}

		if (WorldBridge.isProxy(entity)) {
			cir.setReturnValue(false);
		} else if (Passthrough.get().isBannerlordUi() && Minecraft.getInstance().player != null
			&& entity == Minecraft.getInstance().player.getVehicle() && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			cir.setReturnValue(false);
		} else if (entity == Minecraft.getInstance().player) {
			cir.setReturnValue(true);
		}
	}
}
