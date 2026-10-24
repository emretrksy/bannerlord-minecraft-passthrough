package blpassthrough.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	private static final boolean OWN_SKIN = Boolean.getBoolean("blpassthrough.ownSkin");
	private static final PlayerSkin CLASSIC_STEVE = new PlayerSkin(
		new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/wide/steve")), null, null, PlayerModelType.WIDE, true);

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void blpassthrough$steve(CallbackInfoReturnable<PlayerSkin> cir) {
		if (!OWN_SKIN) {
			cir.setReturnValue(CLASSIC_STEVE);
		}
	}
}
