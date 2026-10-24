package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "die", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$revive(DamageSource source, CallbackInfo ci) {
		Passthrough passthrough = Passthrough.get();
		if (passthrough != null && passthrough.isActive() && passthrough.worldBridge().autoRevive()) {
			ServerPlayer self = (ServerPlayer) (Object) this;
			self.setHealth(self.getMaxHealth());
			self.clearFire();
			self.resetFallDistance();
			ci.cancel();
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$alliedCreepers(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (Passthrough.get().isActive() && source.is(DamageTypeTags.IS_EXPLOSION) && source.getDirectEntity() instanceof Creeper) {
			cir.setReturnValue(false);
		}
	}
}
