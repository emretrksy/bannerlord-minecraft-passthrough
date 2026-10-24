package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
	private static final float TURN_RATE = 10.0F;

	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$steer(LivingEntity controller, CallbackInfoReturnable<Vec2> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive()) {
			float yaw = ((AbstractHorse) (Object) this).getYRot() - controller.xxa * TURN_RATE;
			cir.setReturnValue(new Vec2(0.0F, yaw));
		}
	}

	@Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
	private void blpassthrough$noStrafe(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive()) {
			Vec3 input = cir.getReturnValue();
			cir.setReturnValue(new Vec3(0.0, input.y, input.z));
		}
	}
}
