package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin {
	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void blpassthrough$huntEnemies(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		((MobAccessor) self).blpassthrough$targetSelector().addGoal(1, new NearestAttackableTargetGoal<>(
			self, Husk.class, false, (target, level) -> Passthrough.get().worldBridge().isEnemyProxy(target)));
	}
}
