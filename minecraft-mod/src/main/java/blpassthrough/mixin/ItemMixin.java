package blpassthrough.mixin;

import blpassthrough.SteveFireball;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$throwFireCharge(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!((Object) this instanceof FireChargeItem)) {
			return;
		}
		if (level instanceof ServerLevel serverLevel) {
			SteveFireball.throwFrom(serverLevel, player, hand);
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
