package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$throughAllies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive() && !entity.level().isClientSide()
			&& passthrough.worldBridge().isAllyProxy(entity)) {
			cir.setReturnValue(false);
		}
	}
}
