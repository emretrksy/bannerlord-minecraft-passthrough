package blpassthrough.mixin;

import blpassthrough.Passthrough;
import blpassthrough.SteveFireball;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	public abstract Vec3 center();

	@Shadow
	public abstract float radius();

	@Shadow
	public abstract Entity getDirectSourceEntity();

	@Shadow
	public abstract Explosion.BlockInteraction getBlockInteraction();

	@Inject(method = "explode", at = @At("HEAD"))
	private void blpassthrough$report(CallbackInfoReturnable<Integer> cir) {
		if (this.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) {
			return;
		}
		Passthrough.get().worldBridge().onExplosion(this.center(), this.radius(), this.getDirectSourceEntity() instanceof Creeper || this.getDirectSourceEntity() instanceof SteveFireball);
	}
}
