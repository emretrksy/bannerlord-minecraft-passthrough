package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$notAnEnemy(CallbackInfoReturnable<Boolean> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive()) {
			cir.setReturnValue(false);
		}
	}
}
