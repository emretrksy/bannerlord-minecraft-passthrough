package blpassthrough.mixin;

import blpassthrough.CameraPose;
import blpassthrough.Passthrough;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private FogRenderer fogRenderer;

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	@Shadow
	public abstract Camera mainCamera();

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
	private void blpassthrough$exportLevel(CallbackInfo ci) {
		CameraPose pose = Passthrough.get().framePose();
		if (pose != null) {
			Passthrough.get().frameExporter().captureLevel(
				this.mainRenderTarget(), ((CameraAccessor) this.mainCamera()).blpassthrough$projection(), pose);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void blpassthrough$exportHud(CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean hudOnly = minecraft.gui.overlay() == null
			&& (minecraft.gui.screen() == null || minecraft.gui.screen() instanceof AbstractContainerScreen<?> || minecraft.gui.screen().isInGameUi());
		Passthrough.get().frameExporter().captureOverlay(this.mainRenderTarget(), hudOnly);
	}

	@Inject(method = "render3dHud", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$noHandOverMap(CallbackInfo ci) {
		if (Passthrough.get().isMenu()) {
			RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
			ci.cancel();
		}
	}

	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$noBarrierOutline(CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (Passthrough.get().isActive() && minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit
			&& minecraft.level.getBlockState(hit.getBlockPos()).is(Blocks.BARRIER)) {
			cir.setReturnValue(false);
		}
	}
}
