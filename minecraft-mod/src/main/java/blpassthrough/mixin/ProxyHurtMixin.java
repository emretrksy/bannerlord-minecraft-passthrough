package blpassthrough.mixin;

import blpassthrough.Passthrough;
import blpassthrough.WorldBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ProxyHurtMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$notPushable(CallbackInfoReturnable<Boolean> cir) {
		if (WorldBridge.isProxy((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$forwardToBannerlord(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough == null) {
			return;
		}

		WorldBridge bridge = passthrough.worldBridge();
		Integer agent = bridge.proxyIndex((LivingEntity) (Object) this);
		if (agent != null) {
			cir.setReturnValue(bridge.onProxyHurt(agent, (LivingEntity) (Object) this, source, damage));
		}
	}
}
