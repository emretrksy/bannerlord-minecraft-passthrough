package blpassthrough;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SteveFireball extends Fireball {
	private static final int POWER = 2;
	private static final float HIT_DAMAGE = 6.0F;
	private static final double SPEED = 1.2;
	private static final int COOLDOWN_TICKS = 10;

	private SteveFireball(Level level, Player owner, Vec3 direction) {
		super(EntityTypes.FIREBALL, owner, direction, level);
	}

	public static void throwFrom(ServerLevel level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Vec3 look = player.getLookAngle();
		SteveFireball fireball = new SteveFireball(level, player, look);
		fireball.setItem(stack);
		Vec3 at = player.getEyePosition().add(look);
		fireball.setPos(at.x, at.y, at.z);
		fireball.setDeltaMovement(look.scale(SPEED));
		level.addFreshEntity(fireball);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.6F, 1.2F);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		stack.consume(1, player);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel level) {
			this.level().explode(this, this.getX(), this.getY(), this.getZ(), POWER, false, Level.ExplosionInteraction.MOB);
			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		if (this.level() instanceof ServerLevel level) {
			DamageSource source = this.damageSources().fireball(this, this.getOwner());
			hitResult.getEntity().hurtServer(level, source, HIT_DAMAGE);
		}
	}
}
